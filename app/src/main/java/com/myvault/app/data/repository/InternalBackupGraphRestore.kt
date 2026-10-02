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
internal data class GraphRestoreResult(val status: GraphRestoreStatus, val commitsApplied: Int = 0, val rowsWritten: Int = 0, val binariesDownloaded: Int = 0)

internal interface GraphRestoreSettings {
    suspend fun read(): JSONObject
    suspend fun apply(value: JSONObject)
}
internal class GraphRestorePreferences(private val preferences: VaultPreferences) : GraphRestoreSettings {
    override suspend fun read() = preferences.userPreferences.first().toBackupJson()
    override suspend fun apply(value: JSONObject) = preferences.applyGraphRestorePreferences(value.toValidatedBackupPreferences())
}

/** Internal/disposable only. Read-only transport; publication bindings and pending rows are never advanced/acked. */
internal class InternalBackupGraphRestore(
    private val db: VaultDatabase, private val journal: BackupChangeJournal,
    private val privateFiles: File, private val restoreRoot: File,
    private val store: DisposableGraphObjectStore, private val settings: GraphRestoreSettings,
    private val boundary: suspend (String) -> Unit = {}, private val timing: BackupGraphTiming = BackupGraphTiming(),
) {
    private val dao get() = db.backupGraphRestoreDao()
    private val context get() = store.context.also { it.validate() }
    private class Blocked(val status: GraphRestoreStatus) : IllegalStateException(status.name)
    init { check(restoreRoot.canonicalFile.toPath().startsWith(privateFiles.canonicalFile.toPath()) && restoreRoot.canonicalFile != privateFiles.canonicalFile) }

    suspend fun restore(): GraphRestoreResult = journal.binaryMutex.withLock {
        journal.settingsMutex.withLock {
            try { timing.measure("restore.total") { run() } } catch (e: Blocked) { GraphRestoreResult(e.status) }
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
        var committed = 0; var written = 0; var downloaded = 0
        outstanding.singleOrNull()?.let { val result = resume(it.operationId); committed++; written += result.first; downloaded += result.second }
        val (objects, graph) = inventory(); allowed(graph)
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
        if (position != null && plan.requiresCheckpoint) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        checkNoLocalChanges()
        if (position == null && hasUserRows()) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
        val missing = if (position == null) plan.commits.drop(plan.commits.indexOfLast { it.kind == "checkpoint" }) else plan.descendants
        for ((index, commit) in missing.withIndex()) {
            val receipt = objects.single { BackupGraphProtocol.parse(it).commitId == commit.commitId }
            val intent = prepare(commit, receipt.objectRef, publication.takeIf { index == 0 })
            boundary("INTENT_PERSISTED")
            val result = resume(intent.operationId); committed++; written += result.first; downloaded += result.second
        }
        return GraphRestoreResult(GraphRestoreStatus.APPLIED, committed, written, downloaded)
    }

    private suspend fun checkNoLocalChanges() {
        if (db.backupJournalDao().pending(context.accountScope).isNotEmpty()) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        if (db.backupGraphDao().unfinishedMetadata(context.accountScope).isNotEmpty()) throw Blocked(GraphRestoreStatus.RECONCILIATION_REQUIRED)
    }
    private fun hasUserRows(): Boolean = BackupRecordKeys.keys.any { group ->
        db.openHelper.readableDatabase.query("SELECT 1 FROM `${backupRecordTable(group)}` LIMIT 1").use { it.moveToFirst() }
    }
    private suspend fun load(ref: GraphObjectRef): ByteArray {
        BackupGraphProtocol.reference(ref)
        val input = store.read(ref.cloudFileId) ?: error("Required immutable object is missing.")
        val bytes = input.use { it.readBytes() }; BackupGraphProtocol.verify(ref, bytes); return bytes
    }
    private suspend fun prepare(commit: BackupGraphCommit, ref: GraphObjectRef, publication: BackupGraphBinding? = null): BackupGraphRestore {
        val changes: List<BackupRecordChange>
        val binaries: List<BackupBinaryDescriptor>
        if (commit.kind == "delta") {
            val delta = commit.delta!!
            val value = JSONObject(BackupGraphProtocol.utf8(load(delta.objectRef)))
            check(value.getString("checkpointId") == commit.checkpoint.checkpointId && value.getString("parentId") == delta.parentId && value.getString("deltaId") == delta.deltaId)
            check(value.getInt("version") != 2 || BackupBinaryReaderCapability in commit.requiredReaders)
            changes = IncrementalBackupFormat.parseChanges(value); binaries = parseBackupBinaries(value, changes)
        } else {
            val manifest = JSONObject(BackupGraphProtocol.utf8(load(commit.checkpoint.objectRef)))
            check(manifest.getInt("schemaVersion") == 1 && manifest.getString("storage") == "google-drive-api" && !manifest.has(IncrementalBackupField))
            val rows = mutableListOf<BackupRecordChange>(); val bytes = mutableListOf<BackupBinaryDescriptor>(); val groups = mutableSetOf<String>()
            val seen = mutableMapOf<String, GraphObjectRef>()
            for (entry in BackupGraphIntentCodec.array(manifest.getJSONArray("entries"))) {
                val source = GraphObjectRef(entry.getString("cloudFileId"), entry.getString("sha256"), entry.getLong("size"))
                BackupGraphProtocol.reference(source, 9007199254740991, entry.getString("kind") == "file")
                check(seen.putIfAbsent(source.cloudFileId, source)?.let { it == source } != false)
                when (entry.getString("kind")) {
                    "file" -> { val path = entry.getString("backupEntry"); check(path.startsWith("files/")); bytes += BackupBinaryDescriptor(path.removePrefix("files/"),source.cloudFileId,source.sha256,source.size).validate() }
                    "metadata" -> {
                        val group = entry.getString("fileName"); check(group in BackupRecordKeys || group in listOf("settings.json", "manifest.json")); check(groups.add(group))
                        val text = BackupGraphProtocol.utf8(load(source))
                        if (group == "settings.json") rows += BackupRecordChange(group,listOf("settings"),JSONObject(text))
                        else if (group != "manifest.json") for (row in BackupGraphIntentCodec.array(JSONArray(text))) rows += BackupRecordChange(group,IncrementalBackupFormat.key(group,row),row)
                    }
                    else -> error("Unknown checkpoint entry.")
                }
            }
            // Historical full checkpoints may omit later optional groups. Absence is never a deletion.
            check(groups.containsAll(listOf("folders.json", "notes.json")))
            check(rows.map { it.file to it.key }.distinct().size == rows.size && bytes.map { it.attachmentId }.distinct().size == bytes.size)
            changes = rows; binaries = bytes
        }
        // Resolve only attachment rows in this commit, not the entire checkpoint binary index.
        val replacements = binaries.associateBy { it.attachmentId }
        val resolved = mutableListOf<BackupBinaryDescriptor>()
        for (change in changes.filter { it.file == "attachments.json" && !it.deleted }) {
            val id = change.key.single()
            val previous = db.backupJournalDao().binaryReference(context.accountScope,id)
            val binary = replacements[id] ?: previous?.let { BackupBinaryDescriptor(id,it.cloudFileId,it.sha256,it.byteSize) }
            val resolution = BackupBinaryResolution(listOfNotNull(binary))
            resolution.apply(listOf(change),listOfNotNull(replacements[id]))
            check(binary != null) { "New or unknown attachment requires a verified descriptor." }
            check(db.backupJournalDao().binaryReferencesForObject(context.accountScope,binary.cloudFileId).all { it.sha256 == binary.sha256 && it.byteSize == binary.size }) { "Immutable binary object identity conflicts with an existing receipt." }
            resolved += binary
        }
        check(binaries.all { b -> changes.any { it.file == "attachments.json" && it.key == listOf(b.attachmentId) && !it.deleted } })
        changes.filter { it.file == "settings.json" }.forEach { it.value!!.toValidatedBackupPreferences() }
        val frozen = JSONObject().put("changes",JSONArray(changes.map { it.toJson() })).put("binaries",JSONArray(resolved.map { it.toJson() }))
        val operation = UUID.randomUUID().toString()
        val settingsBefore = if (changes.any { it.file == "settings.json" }) settings.read().toString() else null
        return db.withTransaction {
            check(dao.unfinishedMetadata().isEmpty()) { "Another Restore intent requires recovery first." }
            checkNoLocalChanges()
            val clock = db.backupJournalDao().clock(); check(clock.suppressionDepth == 0 && clock.settingsToken == null)
            val original = dao.applied(context.accountScope,context.lineageId)
            if (publication != null) {
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
        if (clock.generation != intent.capturedGeneration || clock.originEpoch != intent.capturedOriginEpoch) throw Blocked(GraphRestoreStatus.LOCAL_CHANGES)
        checkNoLocalChanges()
        check(clock.suppressionDepth == 0 && clock.settingsToken in listOf(null,"graph-restore:${intent.operationId}"))
    }
    private suspend fun resume(operationId: String): Pair<Int,Int> {
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
        var downloads = 0
        for (obj in dao.objects(intent.accountScope,intent.operationId)) {
            if (obj.status == "REUSE") {
                // No hashing/downloading unrelated files. This one dependency must still match its verified local claim.
                checkPrivate(obj.destinationPath); verifyFile(obj.destinationPath,obj); continue
            }
            checkOwned(obj.stagingPath,intent.operationId); checkOwned(obj.destinationPath,intent.operationId)
            if (obj.status == "PENDING") {
                val temp = File(obj.stagingPath + ".part")
                boundary("BEFORE_BINARY_DOWNLOAD")
                val source = store.read(obj.cloudFileId) ?: error("Required binary is missing; no stale fallback.")
                val digest = source.use { input -> FileOutputStream(temp).use { output -> fingerprintBackupBytes(input,output).also { output.fd.sync() } } }
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
            // Suppression and origin invalidation are transactional, not a persisted process-wide mode.
            val count = journal.withRestoreOrigin {
                var changed = 0
                val upserts = changes.filter { !it.deleted && it.file != "settings.json" }
                validateDependencies(upserts,changes)
                for (change in upserts.sortedBy { if (it.file == "pdf_annotation_geometry.json") 1 else 0 }) {
                    val path = dependencies[change.key.singleOrNull()]?.takeIf { change.file == "attachments.json" }?.destinationPath
                    if (applyRow(change,path)) changed++
                }
                for (change in changes.filter { it.deleted }.sortedBy { if (it.file == "pdf_annotation_geometry.json") 0 else 1 }) {
                    val (sql,args) = permanentBackupDeletionSql(change)
                    db.openHelper.writableDatabase.execSQL(sql,args); changed++
                    if (change.file == "attachments.json") db.backupJournalDao().removeBinaryReference(intent.accountScope,change.key.single())
                }
                dependencies.values.forEach { obj ->
                    db.backupJournalDao().putFingerprint(BackupBinaryFingerprint(obj.attachmentId,obj.destinationPath,obj.byteCount,obj.sha256,"VERIFIED",clock.generation))
                }
                db.backupJournalDao().putBinaryReferences(dependencies.values.map { obj -> BackupBinaryReference(intent.accountScope,obj.attachmentId,commit.checkpoint.checkpointId,commit.deltaHead,obj.cloudFileId,obj.byteCount,obj.sha256) })
                boundary("ROOM_OPERATIONS_APPLIED")
                dao.putApplied(BackupGraphAppliedState(intent.accountScope,intent.lineageId,intent.driveAccountId,commit.commitId,intent.commitFileId,intent.commitSha256,intent.commitSize,
                    commit.checkpoint.checkpointId,commit.checkpoint.objectRef.cloudFileId,commit.checkpoint.objectRef.sha256,commit.checkpoint.objectRef.size,commit.deltaHead,db.backupJournalDao().clock().originEpoch))
                db.backupJournalDao().setSettingsToken(null)
                dao.phase(intent.accountScope,intent.operationId,if (targetSettings == null) "NONE" else "VERIFIED","COMPLETE")
                changed
            }
            count
        } }
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
    private suspend fun validateDependencies(upserts: List<BackupRecordChange>, all: List<BackupRecordChange>) {
        val added = upserts.map { BackupRecordIdentity(it.file,it.key) }.toSet()
        val removed = all.filter { it.deleted }.map { BackupRecordIdentity(it.file,it.key) }.toSet()
        for (row in upserts) for (dep in backupRecordDependencies(row.file,row.value!!)) {
            check(dep !in removed && (dep in added || db.backupCaptureDao().readBackupRecord(dep.group,dep.key) != null)) { "Missing direct Restore dependency." }
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
        check(digest.sha256 == obj.sha256 && digest.size == obj.byteCount) { "Staged/reused binary is corrupt or missing." }
    }
}

private fun BackupGraphAppliedState.ref() = GraphObjectRef(commitFileId,commitSha256,commitSize)
private fun BackupGraphAppliedState.proof(): String = JSONObject().put("account",accountScope).put("lineage",lineageId).put("driveAccount",driveAccountId)
    .put("commit",commitId).put("file",commitFileId).put("hash",commitSha256).put("size",commitSize).put("checkpoint",checkpointId)
    .put("checkpointFile",checkpointFileId).put("checkpointHash",checkpointSha256).put("checkpointSize",checkpointSize).put("deltaHead",deltaHeadId).put("epoch",originEpoch).toString()
