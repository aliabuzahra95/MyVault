package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.local.dao.readIntent
import com.myvault.app.data.preferences.VaultPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal const val BackupGraphTargetedRestoreEnabled = true
internal enum class GraphRestoreStatus { APPLIED, ALREADY_CURRENT, LOCAL_CHANGES, RECONCILIATION_REQUIRED, FORK, DIVERGENT, UNSUPPORTED, CORRUPT, MISSING_ANCESTRY, ACCOUNT_MISMATCH }
internal data class GraphRestoreResult(val status: GraphRestoreStatus, val commitsApplied: Int = 0, val rowsWritten: Int = 0, val binariesDownloaded: Int = 0, val localNotesPreserved: Int = 0)

internal interface GraphRestoreSettings {
    suspend fun read(): JSONObject
    suspend fun apply(value: JSONObject)
}
internal class GraphRestorePreferences(
    private val preferences: VaultPreferences,
    private val dashboardRepository: DashboardActivityRepository? = null,
) : GraphRestoreSettings {
    override suspend fun read() = preferences.userPreferences.first().toBackupJson()
    override suspend fun apply(value: JSONObject) {
        val validated = value.toValidatedBackupPreferences()
        preferences.applyGraphRestorePreferences(validated)
        dashboardRepository?.applyRestoredState(
            folderPins = validated.dashboardFolderPins,
            activityJson = validated.dashboardActivity,
            pinnedOrder = validated.dashboardPinnedOrder,
        )
    }
}

/** Read-only remote transport. Explicit Restore supersedes only graph-known local edits. */
internal class InternalBackupGraphRestore(
    private val db: VaultDatabase, private val journal: BackupChangeJournal,
    private val privateFiles: File, private val restoreRoot: File,
    private val store: DisposableGraphObjectStore, private val settings: GraphRestoreSettings,
    private val boundary: suspend (String) -> Unit = {}, private val timing: BackupGraphTiming = BackupGraphTiming(),
) {
    private val dao get() = db.backupGraphRestoreDao()
    private val context get() = store.context.also { it.validate() }
    private class Blocked(val status: GraphRestoreStatus) : IllegalStateException(status.name)
    private var metadataRecords: VerifiedGraphRecords? = null
    private val records get() = checkNotNull(metadataRecords)
    init {
        check(restoreRoot.canonicalFile.toPath().startsWith(privateFiles.canonicalFile.toPath()) && restoreRoot.canonicalFile != privateFiles.canonicalFile)
        VerifiedCommitGraphCache.init(privateFiles)
    }

    suspend fun restore(): GraphRestoreResult = journal.binaryMutex.withLock {
        journal.settingsMutex.withLock {
            metadataRecords = null
            try {
                timing.measure("restore.total") {
                    try { run() } catch (e: Blocked) {
                        if (e.status != GraphRestoreStatus.LOCAL_CHANGES) throw e
                        metadataRecords = null
                        run() // A concurrent local edit upgrades the retry to authoritative Restore.
                    }
                }
            } catch (e: Blocked) { GraphRestoreResult(e.status) }
        }
    }

    private suspend fun inventory(): Pair<List<GraphObject>, BackupGraph> {
        val objects = timing.measure("restore.discovery") { store.commits() }
        return objects to timing.local("restore.graph_validation") { BackupGraph.discover(objects, context.driveAccountId, context.lineageId) }
    }
    private fun allowed(graph: BackupGraph) {
        if (graph.status != GraphStatus.SINGLE_TIP) throw Blocked(when (graph.status) {
            GraphStatus.FORK -> GraphRestoreStatus.FORK
            GraphStatus.UNSUPPORTED -> GraphRestoreStatus.UNSUPPORTED
            GraphStatus.MISSING_ANCESTRY -> GraphRestoreStatus.MISSING_ANCESTRY
            else -> GraphRestoreStatus.CORRUPT
        })
    }
    private suspend fun run(): GraphRestoreResult {
        val c = context
        val outstanding = dao.unfinishedMetadata()
        if (outstanding.any { it.accountScope != c.accountScope || it.lineageId != c.lineageId || it.driveAccountId != c.driveAccountId }) throw Blocked(GraphRestoreStatus.ACCOUNT_MISMATCH)
        check(outstanding.size <= 1) { "Ambiguous Restore intent." }
        journal.registerAccount(c.accountScope)
        var (objects, graph) = inventory(); allowed(graph)
        var committed = 0; var written = 0; var downloaded = 0
        if (outstanding.isNotEmpty()) {
            val intent = dao.readIntent(c.accountScope, outstanding.single().operationId)!!
            val frozen = JSONObject(intent.frozenChangesJson)
            check(IncrementalBackupFormat.sha256(intent.frozenChangesJson.toByteArray()) == intent.frozenChangesSha256)
            if (db.backupJournalDao().clock().originEpoch != intent.capturedOriginEpoch ||
                frozen.optBoolean("authoritativeSnapshot") && frozen.optInt("snapshotFoldVersion") != 2 ||
                !frozen.optBoolean("authoritativeSnapshot") &&
                (db.backupJournalDao().pending(c.accountScope).isNotEmpty() || frozenChanges(frozen).any { it.deleted })) {
                return restoreAuthoritative(objects, graph, intent)
            }
        }
        outstanding.singleOrNull()?.let {
            val result = resume(it.operationId); committed++; written += result.first; downloaded += result.second
            // Recovery may have observed newer descendants. Continue from a fresh graph,
            // not the pre-recovery header/metadata view used to enter this operation.
            metadataRecords = null
            val current = inventory(); objects = current.first; graph = current.second; allowed(graph)
        }
        // Explicit Restore accepts local edits. Reapply the verified final state, not merely
        // missing deltas, so even a locally edited row unchanged in newer commits is replaced.
        val localApplied = dao.applied(c.accountScope, c.lineageId)
        if (db.backupJournalDao().pending(c.accountScope).isNotEmpty() ||
            localApplied != null && localApplied.originEpoch != db.backupJournalDao().clock().originEpoch || hasUserRows() &&
            dao.applied(c.accountScope, c.lineageId) == null && trustedPublicationPosition(db, c) == null) {
            val result = restoreAuthoritative(objects, graph)
            return result.copy(commitsApplied = result.commitsApplied + committed,
                rowsWritten = result.rowsWritten + written, binariesDownloaded = result.binariesDownloaded + downloaded)
        }
        val applied = dao.applied(c.accountScope, c.lineageId)
        val publication = trustedPublicationPosition(db, c)
        if (applied != null) {
            if (applied.driveAccountId != c.driveAccountId || db.backupJournalDao().clock().originEpoch != applied.originEpoch) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
            val receipt = objects.singleOrNull { it.objectRef.cloudFileId == applied.commitFileId }
            if (receipt == null || receipt.objectRef != applied.ref()) throw Blocked(GraphRestoreStatus.DIVERGENT)
        }
        if (publication != null) {
            val ref = GraphObjectRef(publication.commitFileId, publication.commitSha256, publication.commitSize)
            if (objects.singleOrNull { it.objectRef.cloudFileId == ref.cloudFileId }?.objectRef != ref) throw Blocked(GraphRestoreStatus.DIVERGENT)
            val commit = graph.commits[publication.commitId] ?: throw Blocked(GraphRestoreStatus.DIVERGENT)
            if (commit.checkpoint != GraphCheckpoint(publication.checkpointId,
                    GraphObjectRef(publication.checkpointFileId, publication.checkpointSha256, publication.checkpointSize)) ||
                commit.deltaHead != publication.deltaHeadId ||
                applied != null && graph.plan().commits.let { path ->
                    val restoredIndex = path.indexOfFirst { it.commitId == applied.commitId }
                    val publishedIndex = path.indexOfFirst { it.commitId == publication.commitId }
                    restoredIndex < 0 || publishedIndex < restoredIndex
                }) throw Blocked(GraphRestoreStatus.DIVERGENT)
        }
        val position = publication?.commitId ?: applied?.commitId
        val plan = graph.plan(position)
        if (plan.status == GraphStatus.DIVERGENT) throw Blocked(GraphRestoreStatus.DIVERGENT)
        // Nothing is overwritten on the fast path, including locally modified rows.
        if (plan.status == GraphStatus.ALREADY_CURRENT) return GraphRestoreResult(if (committed == 0) GraphRestoreStatus.ALREADY_CURRENT else GraphRestoreStatus.APPLIED, committed, written, downloaded)
        if (position != null && plan.requiresCheckpoint) return restoreAuthoritative(objects, graph)
        if (position == null && hasUserRows()) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        if (position == null) {
            metadataRecords = null
            val result = restoreAuthoritative(objects, graph)
            return result.copy(commitsApplied = committed + 1, rowsWritten = written + result.rowsWritten,
                binariesDownloaded = downloaded + result.binariesDownloaded)
        }
        val missing = plan.descendants
        // Keep the clean single-delta reader narrow. Deletions or local-history proof
        // below discard it and freshly validate the required full history per object.
        if (missing.singleOrNull()?.kind == "delta") metadataRecords = VerifiedGraphRecords(store, timing)
        // A deletion must validate surviving remote dependents too, not only changed
        // rows. Multi-commit restores likewise stage the final state before any DB apply.
        if (missing.size > 1 || missing.any { commit -> readChanges(commit).first.any { it.deleted } }) {
            metadataRecords = null
            val result = restoreAuthoritative(objects, graph)
            return result.copy(commitsApplied = committed + missing.size, rowsWritten = written + result.rowsWritten,
                binariesDownloaded = downloaded + result.binariesDownloaded)
        }
        val preserved = db.withTransaction { capturePendingNotes() }
        if (preserved.isNotEmpty()) {
            metadataRecords = null
            verifyPreservedNotes(preserved, graph, missing)
        }
        checkLocalChanges(preserved)
        for ((index, commit) in missing.withIndex()) {
            val receipt = objects.single { BackupGraphProtocol.parse(it).commitId == commit.commitId }
            val intent = prepare(commit, receipt.objectRef, publication.takeIf { index == 0 }, preserved)
            boundary("INTENT_PERSISTED")
            val result = resume(intent.operationId); committed++; written += result.first; downloaded += result.second
        }
        return GraphRestoreResult(GraphRestoreStatus.APPLIED, committed, written, downloaded, preserved.count { it.group == "notes.json" })
    }

    private suspend fun restoreAuthoritative(objects: List<GraphObject>, graph: BackupGraph,
        superseded: BackupGraphRestore? = null): GraphRestoreResult {
        val state = BackupGraphRestoreState()
        val binaries = linkedMapOf<String, BackupBinaryDescriptor>()
        val resolution = BackupBinaryResolution(emptyList())
        for (commit in graph.plan().commits) {
            val (changes, descriptors) = readChanges(commit)
            val applied = state.apply(changes, records.checkpointGroups[commit.commitId], commit.commitId)
            resolution.apply(applied, descriptors)
            for (change in applied) {
                if (change.file == "attachments.json" && change.deleted) binaries.remove(change.key.single())
            }
            descriptors.forEach { binaries[it.attachmentId] = it }
        }
        val rows = state.changes
        resolution.finish(mapOf("attachments.json" to JSONArray(rows.filter {
            it.file == "attachments.json" && !it.deleted
        }.map { it.value }).toString()))
        val tip = graph.commits.getValue(graph.tips.single())
        val receipt = objects.single { BackupGraphProtocol.parse(it).commitId == tip.commitId }
        requireBackupReferences(state, tip.commitId, "restore_final_state_validation")
        // Validate the remote final state on its own, never borrow a new local parent
        // to conceal a broken remote reference. Repeat inside the application transaction.
        validateDependencies(rows.filter { !it.deleted && it.file != "settings.json" }, rows, tip.commitId, true)
        if (superseded != null) db.withTransaction {
            val token = db.backupJournalDao().clock().settingsToken
            check(token == null || token == "graph-restore:${superseded.operationId}")
            db.backupJournalDao().setSettingsToken(null)
            // The applied cursor is untouched. Retain the old intent/files for audit/recovery,
            // replacing only its obsolete local-edit policy after remote metadata verification.
            dao.phase(superseded.accountScope, superseded.operationId, "SUPERSEDED", "COMPLETE")
        }
        val known = rows.map { BackupRecordIdentity(it.file, it.key) }.toSet()
        val newNotes = db.backupJournalDao().pending(context.accountScope).count {
            it.recordGroup == "notes.json" && it.operation == "UPSERT" &&
                BackupRecordIdentity(it.recordGroup, it.stableKey()) !in known
        }
        val required = requiredBackupBinaries(rows, binaries.values.toList())
        val intent = prepare(tip, receipt.objectRef, authoritative = rows to required)
        boundary("INTENT_PERSISTED")
        val result = resume(intent.operationId)
        return GraphRestoreResult(GraphRestoreStatus.APPLIED, 1, result.first, result.second, newNotes)
    }

    private suspend fun capturePendingNotes(): List<CapturedBackupRecord> = db.backupJournalDao().pending(context.accountScope).map { pending ->
        if (pending.operation != "UPSERT" || pending.recordGroup == "settings.json") throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        val row = db.backupCaptureDao().readBackupRecord(pending.recordGroup, pending.stableKey()) ?: throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        CapturedBackupRecord(pending.recordGroup, pending.stableKey(), pending.operation, pending.generation, row.toString(), backupRecordDependencies(pending.recordGroup, row))
    }

    private suspend fun checkLocalChanges(preserved: List<CapturedBackupRecord> = emptyList()) {
        val current = capturePendingNotes()
        if (current.size != preserved.size || current.zip(preserved).any { (now, before) ->
            now.copy(payloadJson = before.payloadJson) != before || !sameBackupJson(JSONObject(now.payloadJson!!), JSONObject(before.payloadJson!!))
        }) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        if (db.backupGraphDao().unfinishedMetadata(context.accountScope).isNotEmpty()) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
    }

    private suspend fun verifyPreservedNotes(records: List<CapturedBackupRecord>, graph: BackupGraph, incoming: List<BackupGraphCommit>) {
        val history = graph.plan().commits.flatMap { readChanges(it).first }
        val changes = incoming.flatMap { readChanges(it).first }
        if (!canPreserveGraphLocalNotes(records, history, changes)) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        val local = records.map { BackupRecordIdentity(it.group, it.key) }.toSet()
        for (record in records) for (dep in record.dependencies) {
            if (dep !in local && db.backupCaptureDao().readBackupRecord(dep.group, dep.key) == null) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        }
    }
    private fun hasUserRows(): Boolean = BackupRecordKeys.keys.any { group ->
        db.openHelper.readableDatabase.query("SELECT 1 FROM `${backupRecordTable(group)}` LIMIT 1").use { it.moveToFirst() }
    }
    private suspend fun readChanges(commit: BackupGraphCommit): Pair<List<BackupRecordChange>, List<BackupBinaryDescriptor>> {
        val reader = metadataRecords ?: VerifiedGraphRecords(
            timing.measure("metadata.inventory") { store.metadataBoundary() }, timing).also { metadataRecords = it }
        return reader.read(commit)
    }

    private suspend fun prepare(commit: BackupGraphCommit, ref: GraphObjectRef, publication: BackupGraphBinding? = null,
        preserved: List<CapturedBackupRecord> = emptyList(),
        authoritative: Pair<List<BackupRecordChange>, List<BackupBinaryDescriptor>>? = null): BackupGraphRestore {
        val (changes, binaries) = authoritative ?: readChanges(commit)
        validateDependencies(changes.filter { !it.deleted && it.file != "settings.json" }, changes,
            commit.commitId, authoritative != null || commit.kind == "checkpoint")
        // Resolve only attachment rows in this commit, not the entire checkpoint binary index.
        val replacements = binaries.associateBy { it.attachmentId }
        val resolved = mutableListOf<BackupBinaryDescriptor>()
        for (change in changes.filter { it.file == "attachments.json" && !it.deleted }) {
            val row = change.value!!
            if (!isLiveBackupAttachment(row) || isMetadataOnlyAttachment(row)) continue
            val id = change.key.single()
            val previous = db.backupJournalDao().binaryReference(context.accountScope, id)
            val binary = replacements[id] ?: previous?.let { BackupBinaryDescriptor(id, it.cloudFileId, it.sha256, it.byteSize) }
            val resolution = BackupBinaryResolution(listOfNotNull(binary))
            resolution.apply(listOf(change), listOfNotNull(replacements[id]))
            check(binary != null) { "New or unknown attachment requires a verified descriptor." }
            check(db.backupJournalDao().binaryReferencesForObject(context.accountScope, binary.cloudFileId).all { it.sha256 == binary.sha256 && it.byteSize == binary.size }) { "Immutable binary object identity conflicts with an existing receipt." }
            resolved += binary
        }
        check(binaries.all { b -> changes.any { it.file == "attachments.json" && it.key == listOf(b.attachmentId) && !it.deleted && isLiveBackupAttachment(it.value!!) } })
        changes.filter { it.file == "settings.json" }.forEach { it.value!!.toValidatedBackupPreferences() }
        val frozen = JSONObject().put("changes",JSONArray(changes.map { it.toJson() })).put("binaries",JSONArray(resolved.map { it.toJson() }))
        if (authoritative != null) frozen.put("authoritativeSnapshot", true).put("snapshotFoldVersion", 2)
        if (preserved.isNotEmpty()) frozen.put("preservedLocalNotes", preservedGraphNotesJson(preserved))
        val operation = UUID.randomUUID().toString()
        val settingsBefore = if (changes.any { it.file == "settings.json" }) settings.read().toString() else null
        return db.withTransaction {
            check(dao.unfinishedMetadata().isEmpty()) { "Another Restore intent requires recovery first." }
            if (authoritative == null) checkLocalChanges(preserved)
            val clock = db.backupJournalDao().clock(); check(clock.suppressionDepth == 0 && clock.settingsToken == null)
            val original = dao.applied(context.accountScope,context.lineageId)
            if (authoritative != null) {
                check(commit.accountId == context.driveAccountId && commit.lineageId == context.lineageId)
            } else if (publication != null) {
                check(trustedPublicationPosition(db, context) == publication)
                check(commit.parents.single().commitId == publication.commitId && commit.checkpoint.checkpointId == publication.checkpointId && commit.delta!!.parentId == publication.deltaHeadId)
                frozen.put("publicationSource", BackupGraphIntentCodec.binding(publication))
                    .put("publicationAccount", BackupGraphIntentCodec.account(db.backupJournalDao().account(context.accountScope)!!))
            } else if (original != null) check(commit.parents.single().commitId == original.commitId && commit.checkpoint.checkpointId == original.checkpointId && commit.delta!!.parentId == original.deltaHeadId)
            else check(commit.kind == "checkpoint")
            val frozenJson = frozen.toString()
            val intent = BackupGraphRestore(context.accountScope,operation,context.lineageId,context.driveAccountId,
                original?.proof(),clock.generation,clock.originEpoch,BackupGraphProtocol.utf8(BackupGraphProtocol.encode(commit)),
                ref.cloudFileId,ref.sha256,ref.size,frozenJson,IncrementalBackupFormat.sha256(frozenJson.toByteArray()),settingsBefore,"NONE","PREPARED")
            dao.insert(intent)
            dao.insertObjects(resolved.mapIndexed { index, b ->
                val fp = db.backupJournalDao().fingerprint(b.attachmentId)
                val existing = db.attachmentDao().getByIdIncludingDeleted(b.attachmentId)
                val prior = db.backupJournalDao().binaryReference(context.accountScope,b.attachmentId)
                val reusable = prior?.cloudFileId == b.cloudFileId && prior.sha256 == b.sha256 && prior.byteSize == b.size &&
                    fp?.status == "VERIFIED" && fp.sha256 == b.sha256 && fp.byteSize == b.size && fp.localPath == existing?.localPath
                val directory = File(restoreRoot,operation).apply { check(mkdirs() || isDirectory) }
                BackupGraphRestoreObject(context.accountScope,operation,b.attachmentId,b.cloudFileId,b.sha256,b.size,
                    File(directory,"$index.staged").canonicalPath,if (reusable) fp.localPath else File(directory,"$index.verified").canonicalPath,
                    if (reusable) "REUSE" else "PENDING")
            })
            intent
        }
    }

    private suspend fun guard(intent: BackupGraphRestore) {
        check(intent.accountScope == context.accountScope && intent.lineageId == context.lineageId && intent.driveAccountId == context.driveAccountId)
        if (dao.applied(intent.accountScope,intent.lineageId)?.proof() != intent.originalAppliedJson) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        val frozen = JSONObject(intent.frozenChangesJson)
        if (frozen.has("publicationSource")) {
            if (trustedPublicationPosition(db, context, "graph-restore:${intent.operationId}") != BackupGraphIntentCodec.binding(frozen.getString("publicationSource")) ||
                db.backupJournalDao().account(intent.accountScope) != BackupGraphIntentCodec.account(frozen.getString("publicationAccount"))) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        }
        val clock = db.backupJournalDao().clock()
        if (clock.originEpoch != intent.capturedOriginEpoch) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        if (!frozen.optBoolean("authoritativeSnapshot")) {
            if (clock.generation != intent.capturedGeneration) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
            checkLocalChanges(readPreservedGraphNotes(frozen))
        }
        check(clock.suppressionDepth == 0 && clock.settingsToken in listOf(null,"graph-restore:${intent.operationId}"))
    }
    private suspend fun resume(operationId: String): Pair<Int,Int> {
        // Frozen verified changes remain immutable, but preserved-history checks belong
        // to this resume's new graph inventory, never an earlier preparation's LIST view.
        metadataRecords = null
        val intent = dao.readIntent(context.accountScope,operationId) ?: error("Restore intent is missing.")
        if (intent.status == "COMPLETE") return 0 to 0
        guard(intent)
        check(IncrementalBackupFormat.sha256(intent.frozenChangesJson.toByteArray()) == intent.frozenChangesSha256)
        val commit = BackupGraphProtocol.parse(GraphObject(GraphObjectRef(intent.commitFileId,intent.commitSha256,intent.commitSize),intent.commitJson.toByteArray()))
        check(commit.accountId == context.driveAccountId && commit.lineageId == context.lineageId)
        val (objects, graph) = inventory(); allowed(graph)
        check(graph.plan().commits.any { it == commit } && objects.any { it.objectRef == GraphObjectRef(intent.commitFileId,intent.commitSha256,intent.commitSize) })
        val frozen = JSONObject(intent.frozenChangesJson)
        val changes = frozenChanges(frozen)
        val preserved = readPreservedGraphNotes(frozen)
        if (preserved.isNotEmpty()) {
            val path = graph.plan().commits
            val position = path.indexOfFirst { it.commitId == commit.commitId }
            verifyPreservedNotes(preserved, graph, path.drop(position))
        }
        var downloads = 0
        for (obj in dao.objects(intent.accountScope,intent.operationId)) {
            if (obj.status == "REUSE") {
                // No hashing/downloading unrelated files. This one dependency must still match its verified local claim.
                checkPrivate(obj.destinationPath); verifyFile(obj.destinationPath,obj); continue
            }
            checkOwned(obj.stagingPath,intent.operationId); checkOwned(obj.destinationPath,intent.operationId)
            if (obj.status == "PENDING") {
                val temp = File(obj.stagingPath + ".part")
                val digest = timing.measure("restore.binary_transfer") {
                    val source = store.read(obj.cloudFileId) ?: throw BackupGraphRestoreFailure(
                        "A required Drive attachment is unavailable. Check Drive access and retry Restore. Local data was preserved.",
                        "phase=binary_staging commit=${commit.commitId} attachment=${obj.attachmentId} object=${obj.cloudFileId} source=remote missing=true")
                    source.use { input -> FileOutputStream(temp).use { output -> fingerprintBackupBytes(input,output).also { output.fd.sync() } } }
                }
                boundary("BINARY_DOWNLOADED")
                check(digest.sha256 == obj.sha256 && digest.size == obj.byteCount) { "Binary mismatch; old files remain intact." }
                check(temp.renameTo(File(obj.stagingPath)))
                dao.objectStatus(intent.accountScope,intent.operationId,obj.attachmentId,"STAGED"); downloads++
                boundary("BINARY_STAGED")
            }
            val current = dao.objects(intent.accountScope,intent.operationId).single { it.attachmentId == obj.attachmentId }
            if (current.status == "STAGED") {
                // Rename can finish before the receipt. Recover that exact destination, never overwrite it.
                if (File(obj.destinationPath).exists()) verifyFile(obj.destinationPath,obj)
                else { verifyFile(obj.stagingPath,obj); check(File(obj.stagingPath).renameTo(File(obj.destinationPath))) }
                boundary("BINARY_DESTINATION_READY")
                dao.objectStatus(intent.accountScope,intent.operationId,obj.attachmentId,"READY")
            }
            verifyFile(obj.destinationPath,obj)
        }
        val targetSettings = changes.singleOrNull { it.file == "settings.json" }?.value
        if (targetSettings != null) {
            val before = JSONObject(intent.settingsBeforeJson!!).toValidatedBackupPreferences()
            val target = targetSettings.toValidatedBackupPreferences()
            val current = settings.read().toValidatedBackupPreferences()
            check(current == before || current == target) { "Preferences diverged during Restore; reconciliation required." }
            db.withTransaction { guard(intent); db.backupJournalDao().setSettingsToken("graph-restore:${intent.operationId}"); dao.phase(intent.accountScope,intent.operationId,"APPLYING","PREPARED") }
            boundary("SETTINGS_APPLYING")
            if (current != target) settings.apply(targetSettings)
            boundary("SETTINGS_WRITTEN")
            check(settings.read().toValidatedBackupPreferences() == target)
            dao.phase(intent.accountScope,intent.operationId,"VERIFIED","PREPARED")
        }
        boundary("BEFORE_ROOM_APPLY")
        val rows = timing.measure("restore.apply") { db.withTransaction {
            guard(intent)
            val dependencies = dao.objects(intent.accountScope,intent.operationId).associateBy { it.attachmentId }
            dependencies.values.forEach { verifyFile(it.destinationPath,it) }
            val clock = db.backupJournalDao().clock()
            if (frozen.optBoolean("authoritativeSnapshot")) recoverNewLocalChildren(changes)
            // Suppression and origin invalidation are transactional, not a persisted process-wide mode.
            val count = journal.withRestoreOrigin {
                var changed = 0
                val upserts = changes.filter { !it.deleted && it.file != "settings.json" }
                validateDependencies(upserts, changes, commit.commitId, frozen.optBoolean("authoritativeSnapshot"))

                val promotedPaths = mutableMapOf<String, String>()
                timing.measure("restore.binary_promotion") {
                    for (change in upserts.filter { it.file == "attachments.json" }) {
                        val attachmentId = change.key.single()
                        val obj = dependencies[attachmentId] ?: continue
                        val noteId = change.value?.optString("noteId")
                        val libraryFolderId = change.value?.optString("libraryFolderId")?.takeUnless { it.isNullOrBlank() }
                        val rawName = change.value?.optString("fileName").orEmpty()
                        val cleanName = sanitizeAttachmentFileName(rawName).ifBlank { "attachment-$attachmentId" }

                        val canonicalDir = if (!noteId.isNullOrBlank()) {
                            File(privateFiles, "attachments/$noteId").apply { mkdirs() }
                        } else {
                            File(privateFiles, "library/${libraryFolderId ?: "root"}").apply { mkdirs() }
                        }
                        val canonicalFile = File(canonicalDir, "${attachmentId}_$cleanName")
                        val sourceFile = File(obj.destinationPath)
                        if (sourceFile.exists() && sourceFile.canonicalPath != canonicalFile.canonicalPath) {
                            if (!sourceFile.renameTo(canonicalFile)) {
                                val temp = File(canonicalDir, "${canonicalFile.name}.tmp")
                                sourceFile.inputStream().use { input ->
                                    temp.outputStream().use { output -> input.copyTo(output) }
                                }
                                check(temp.renameTo(canonicalFile))
                                sourceFile.delete()
                            }
                        }
                        check(canonicalFile.exists() && canonicalFile.length() == obj.byteCount) {
                            "Failed to promote restored attachment $attachmentId to ${canonicalFile.path}"
                        }
                        promotedPaths[attachmentId] = canonicalFile.canonicalPath
                    }
                }

                for (change in upserts.sortedBy { if (it.file == "pdf_annotation_geometry.json") 1 else 0 }) {
                    val path = dependencies[change.key.singleOrNull()]?.takeIf { change.file == "attachments.json" }?.let {
                        promotedPaths[it.attachmentId] ?: it.destinationPath
                    }
                    if (applyRow(change,path)) changed++
                }
                for (change in changes.filter { it.deleted }.sortedBy { if (it.file == "pdf_annotation_geometry.json") 0 else 1 }) {
                    val (sql,args) = permanentBackupDeletionSql(change)
                    db.openHelper.writableDatabase.execSQL(sql,args); changed++
                    if (change.file == "attachments.json") db.backupJournalDao().removeBinaryReference(intent.accountScope,change.key.single())
                }
                if (frozen.optBoolean("authoritativeSnapshot")) {
                    // Only identities proven in this graph are superseded. New local IDs,
                    // including new PDFs and N+1 creations during download, remain pending.
                    for (change in changes) db.backupJournalDao().acknowledgeRestoredRecord(intent.accountScope,
                        change.file, change.key[0], change.key.getOrElse(1) { "" },
                        change.key.getOrElse(2) { "" }, clock.generation)
                }
                dependencies.values.forEach { obj ->
                    val finalPath = promotedPaths[obj.attachmentId] ?: obj.destinationPath
                    db.backupJournalDao().putFingerprint(BackupBinaryFingerprint(obj.attachmentId,finalPath,obj.byteCount,obj.sha256,"VERIFIED",clock.generation))
                }
                db.backupJournalDao().putBinaryReferences(dependencies.values.map { obj -> BackupBinaryReference(intent.accountScope,obj.attachmentId,commit.checkpoint.checkpointId,commit.deltaHead,obj.cloudFileId,obj.byteCount,obj.sha256) })
                boundary("ROOM_OPERATIONS_APPLIED")
                dao.putApplied(BackupGraphAppliedState(intent.accountScope,intent.lineageId,intent.driveAccountId,commit.commitId,intent.commitFileId,intent.commitSha256,intent.commitSize,
                    commit.checkpoint.checkpointId,commit.checkpoint.objectRef.cloudFileId,commit.checkpoint.objectRef.sha256,commit.checkpoint.objectRef.size,commit.deltaHead,db.backupJournalDao().clock().originEpoch))
                VerifiedCommitGraphCache.recordLineage(intent.accountScope, intent.lineageId, graph, commit.commitId)
                db.backupJournalDao().setSettingsToken(null)
                dao.phase(intent.accountScope,intent.operationId,if (targetSettings == null) "NONE" else "VERIFIED","COMPLETE")
                changed
            }
            count
        } }
        runCatching { File(restoreRoot, intent.operationId).deleteRecursively() }
        boundary("COMMIT_APPLIED")
        return rows to downloads
    }

    private fun frozenChanges(frozen: JSONObject): List<BackupRecordChange> = BackupGraphIntentCodec.array(frozen.getJSONArray("changes")).map { value ->
        val key = value.getJSONArray("key"); val group = value.getString("file")
        val parts = (0 until key.length()).map { key.getString(it) }
        check(parts.size == if (group == "settings.json") 1 else BackupRecordKeys.getValue(group).size)
        val row = if (value.getString("operation") == "delete") null else value.getJSONObject("value")
        if (row != null) check(IncrementalBackupFormat.key(group,row) == parts) else check(group != "settings.json")
        BackupRecordChange(group,parts,row)
    }
    private suspend fun recoverNewLocalChildren(changes: List<BackupRecordChange>) {
        val known = changes.groupBy { it.file }.mapValues { (_, rows) -> rows.map { it.key.singleOrNull() }.toSet() }
        val removed = changes.filter { it.deleted || it.value?.isNull("deletedAt") == false }
            .groupBy { it.file }.mapValues { (_, rows) -> rows.map { it.key.singleOrNull() }.toSet() }
        // New local notes/PDFs must remain reachable when their old backed-up container
        // is deleted. Detach only those new IDs; never invent a deletion from absence.
        fun detach(group: String, column: String, parentGroup: String, replacement: Any?) {
            val parents = removed[parentGroup].orEmpty()
            if (parents.isEmpty()) return
            val table = backupRecordTable(group)
            val ids = mutableListOf<String>()
            db.openHelper.readableDatabase.query("SELECT id, `$column` FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) if (cursor.getString(0) !in known[group].orEmpty() &&
                    cursor.getString(1) in parents) ids += cursor.getString(0)
            }
            for (id in ids) db.openHelper.writableDatabase.execSQL("UPDATE `$table` SET `$column`=? WHERE id=?", arrayOf(replacement, id))
        }
        detach("notes.json", "parentNoteId", "notes.json", null)
        detach("notes.json", "folderId", "folders.json", null)
        detach("folders.json", "parentId", "folders.json", null)
        detach("attachments.json", "noteId", "notes.json", "")
        detach("attachments.json", "libraryFolderId", "folders.json", null)
        detach("courses.json", "rootFolderId", "folders.json", null)
        detach("pdf_annotations.json", "libraryFolderId", "folders.json", null)
        detach("pdf_annotations.json", "displayFolderId", "folders.json", null)

        // Required children (new tables, blocks, annotations, sticky notes, etc.)
        // cannot be detached. Give only these local additions explicit local recovery
        // containers with NEW IDs; never resurrect the remote-deleted identities.
        val removedIds = changes.filter { it.deleted || it.value?.isNull("deletedAt") == false }
            .map { BackupRecordIdentity(it.file, it.key) }.toSet()
        val knownIds = changes.map { BackupRecordIdentity(it.file, it.key) }.toSet()
        val recovered = mutableMapOf<BackupRecordIdentity, BackupRecordIdentity>()
        val visiting = mutableSetOf<BackupRecordIdentity>()
        suspend fun recoverParent(id: BackupRecordIdentity): BackupRecordIdentity {
            recovered[id]?.let { return it }
            check(visiting.add(id)) { "Cyclic local recovery dependency; Restore was not applied." }
            val original = db.backupCaptureDao().readBackupRecord(id.group, id.key)
                ?: error("A new local item's required recovery source is missing; Restore was not applied.")
            check(BackupRecordKeys.getValue(id.group).size == 1)
            val row = JSONObject(original.toString())
            val newId = "local-recovery-${UUID.randomUUID()}"
            row.put(BackupRecordKeys.getValue(id.group).single(), newId)
            for (label in listOf("title", "name", "fileName")) if (row.has(label) && label !in BackupRecordKeys.getValue(id.group)) {
                row.put(label, "Recovered local - ${row.getString(label)}")
            }
            if (row.has("deletedAt")) row.put("deletedAt", JSONObject.NULL)
            if (id.group == "folders.json") row.put("mode", FOLDER_MODE_STUDY)
            if (id.group == "pdf_annotations.json") for (field in listOf("libraryFolderId", "displayFolderId")) {
                if (row.optString(field) in removed["folders.json"].orEmpty()) row.put(field, JSONObject.NULL)
            }
            for ((field, dep) in backupRecordDependencyFields(id.group, row)) if (dep in removedIds) {
                when {
                    id.group == "notes.json" || id.group == "folders.json" || id.group == "courses.json" -> row.put(field, JSONObject.NULL)
                    id.group == "attachments.json" -> row.put(field, if (field == "noteId") "" else JSONObject.NULL)
                    else -> row.put(field, recoverParent(dep).key.single())
                }
            }
            var recoveredFingerprint: BackupByteFingerprint? = null
            val path = if (id.group == "attachments.json") {
                val source = File(original.getString("localPath"))
                checkPrivate(source.absolutePath)
                check(source.isFile)
                val target = File(privateFiles, "attachments/local-recovery/$newId.bin")
                check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                val digest = source.inputStream().use { input -> FileOutputStream(target).use { output ->
                    fingerprintBackupBytes(input, output).also { output.fd.sync() }
                } }
                check(digest.size == original.getLong("sizeBytes"))
                db.backupJournalDao().fingerprint(id.key.single())?.takeIf {
                    it.status == "VERIFIED" && it.localPath == source.absolutePath
                }?.let { check(it.sha256 == digest.sha256 && it.byteSize == digest.size) { "Local recovery binary verification failed." } }
                recoveredFingerprint = digest
                target.absolutePath
            } else null
            val result = BackupRecordIdentity(id.group, listOf(newId))
            applyRow(BackupRecordChange(id.group, result.key, row), path)
            recoveredFingerprint?.let { digest -> db.backupJournalDao().putFingerprint(
                BackupBinaryFingerprint(newId, path!!, digest.size, digest.sha256, "VERIFIED", db.backupJournalDao().clock().generation)) }
            visiting.remove(id)
            recovered[id] = result
            return result
        }
        for ((group, keys) in BackupRecordKeys) {
            val ids = mutableListOf<List<String>>()
            db.openHelper.readableDatabase.query("SELECT ${keys.joinToString { "`$it`" }} FROM `${backupRecordTable(group)}`").use { cursor ->
                while (cursor.moveToNext()) ids += keys.indices.map { cursor.getString(it) }
            }
            for (key in ids) {
                if (BackupRecordIdentity(group, key) in knownIds) continue
                val row = db.backupCaptureDao().readBackupRecord(group, key) ?: continue
                val replacements = backupRecordDependencyFields(group, row).filter { it.second in removedIds }
                    .map { (field, dep) -> field to recoverParent(dep).key.single() }
                if (replacements.isEmpty()) continue
                replacements.forEach { (field, value) -> row.put(field, value) }
                val newKey = IncrementalBackupFormat.key(group, row)
                if (newKey != key) {
                    // Composite IDs include foreign keys. Journal an explicit local
                    // delete + insert so the old pending UPSERT cannot become dangling.
                    val (deleteSql, deleteArgs) = permanentBackupDeletionSql(BackupRecordChange(group, key))
                    db.openHelper.writableDatabase.execSQL(deleteSql, deleteArgs)
                    applyRow(BackupRecordChange(group, newKey, row), null)
                    continue
                }
                val sql = "UPDATE `${backupRecordTable(group)}` SET ${replacements.joinToString { "`${it.first}`=?" }} " +
                    "WHERE ${keys.joinToString(" AND ") { "`$it`=?" }}"
                db.openHelper.writableDatabase.execSQL(sql, (replacements.map { it.second } + key).toTypedArray())
            }
        }
    }
    private suspend fun validateDependencies(upserts: List<BackupRecordChange>, all: List<BackupRecordChange>,
        commitId: String, authoritative: Boolean) {
        val added = upserts.map { BackupRecordIdentity(it.file,it.key) }.toSet()
        val removed = all.filter { it.deleted }.map { BackupRecordIdentity(it.file,it.key) }.toSet()
        for (row in upserts) for (dep in backupRecordDependencies(row.file,row.value!!)) {
            val localPresent = if (dep in added) false else db.backupCaptureDao().readBackupRecord(dep.group,dep.key) != null
            if (dep in removed || dep !in added && (authoritative || !localPresent)) throw BackupGraphRestoreFailure(
                "The verified backup contains an item with a missing required parent or reference. Nothing from this restore step was applied.",
                "phase=dependency_validation commit=$commitId dependent=${row.file}:${row.key} dependency=${dep.group}:${dep.key} " +
                    "remote_deleted=${dep in removed} local_present=$localPresent source=${if (authoritative) "verified_snapshot" else "delta_and_applied_state"}")
        }
    }
    private suspend fun applyRow(change: BackupRecordChange, path: String?): Boolean {
        val row = normalizedGraphRestoreRow(change.file,change.value!!,path)
        val old = db.backupCaptureDao().readBackupRecord(change.file,change.key)
        if (old != null && row.toString() == old.toString()) return false
        val table = backupRecordTable(change.file)
        val columns = mutableListOf<String>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { c -> while(c.moveToNext()) columns += c.getString(c.getColumnIndexOrThrow("name")) }
        val values = columns.map { column -> if (row.isNull(column)) null else when(val v = row.get(column)) { is Boolean -> if(v) 1L else 0L; is String, is Number -> v; else -> error("Unsupported typed Restore column.") } }
        val keys = BackupRecordKeys.getValue(change.file)
        val sql = if (old == null) "INSERT INTO `$table` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})"
            else "UPDATE `$table` SET ${columns.joinToString { "`$it`=?" }} WHERE ${keys.joinToString(" AND ") { "`$it`=?" }}"
        val args = values + if (old == null) emptyList() else change.key
        // UPDATE, not INSERT OR REPLACE: preserve unrelated child geometry on annotation updates.
        db.openHelper.writableDatabase.execSQL(sql,args.toTypedArray())
        return true
    }
    private fun checkPrivate(path: String) { check(File(path).canonicalFile.toPath().startsWith(privateFiles.canonicalFile.toPath())) }
    private fun checkOwned(path: String, operation: String) { check(File(path).canonicalFile.toPath().startsWith(File(restoreRoot,operation).canonicalFile.toPath())) }
    private fun verifyFile(path: String,obj: BackupGraphRestoreObject) {
        val digest = File(path).inputStream().use { fingerprintBackupBytes(it) }
        timing.count("local.hashed_bytes", digest.size)
        check(digest.sha256 == obj.sha256 && digest.size == obj.byteCount) { "Staged/reused binary is corrupt or missing." }
    }
}

private fun BackupGraphAppliedState.ref() = GraphObjectRef(commitFileId,commitSha256,commitSize)
private fun BackupGraphAppliedState.proof(): String = JSONObject().put("account",accountScope).put("lineage",lineageId).put("driveAccount",driveAccountId)
    .put("commit",commitId).put("file",commitFileId).put("hash",commitSha256).put("size",commitSize).put("checkpoint",checkpointId)
    .put("checkpointFile",checkpointFileId).put("checkpointHash",checkpointSha256).put("checkpointSize",checkpointSize).put("deltaHead",deltaHeadId).put("epoch",originEpoch).toString()
