package com.myvault.app.data.repository

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/** Immutable transport. Implementations bind every call to the verified account/lineage/namespace. */
internal interface DisposableGraphObjectStore {
    val context: GraphWriterContext
    /** Exact enrolled layout proof for production recovery; absent only in disposable legacy fixtures. */
    val namespaceProof: String? get() = null
    suspend fun reserveId(): String
    suspend fun reserveIds(count: Int): List<String> = List(count) { reserveId() }
    /** Complete inventory of the enrolled namespace. Missing/ambiguous/incomplete discovery must throw, not return empty. */
    suspend fun commits(): List<GraphObject>
    suspend fun read(objectId: String): InputStream?
    /** Create exclusively, never update. An existing ID must return unchanged for exact-byte readback. */
    suspend fun create(objectId: String, role: String, file: File)
}

internal data class GraphWriterContext(val accountScope: String, val driveAccountId: String, val lineageId: String) {
    fun validate() {
        check(accountScope == normalizeGoogleDriveAccount(accountScope) && '@' in accountScope)
        BackupGraphProtocol.id(driveAccountId); BackupGraphProtocol.id(lineageId)
    }
}
internal data class GraphWriterMetrics(val pendingRows: Int, val payloadRows: Int, val binariesStaged: Int,
    val binariesCreated: Int, val deltasCreated: Int, val commitsCreated: Int)
internal data class GraphWriterResult(val operationId: String?, val commitId: String?, val alreadyBackedUp: Boolean,
    val forkDetected: Boolean, val metrics: GraphWriterMetrics)

/** Internal/disposable orchestration. Room owns retry intent and completion; only immutable remote creates exist. */
internal class InternalBackupGraphWriter(
    private val database: VaultDatabase,
    private val journal: BackupChangeJournal,
    private val capture: PendingBackupCapture,
    private val privateFilesDir: File,
    private val stagingRoot: File,
    private val store: DisposableGraphObjectStore,
    private val boundary: suspend (String) -> Unit = {},
    private val timing: BackupGraphTiming = BackupGraphTiming(),
    private val onProgress: suspend (GraphBackupProgress) -> Unit = {},
) {
    init {
        check(stagingRoot.canonicalPath.startsWith(privateFilesDir.canonicalPath + File.separator)) { "Publication staging must use durable private files, not cache/external storage." }
    }
    private val dao get() = database.backupGraphDao()
    private val context get() = store.context.also { it.validate() }

    suspend fun publish(): GraphWriterResult = timing.measure("writer.total") { publishInternal() }
    private suspend fun publishInternal(): GraphWriterResult {
        onProgress(GraphBackupProgress(GraphBackupStage.CHECKING))
        val c = context
        val unfinished = dao.unfinished(c.accountScope)
        if (unfinished.isNotEmpty()) {
            check(unfinished.size == 1) { "Ambiguous pending publication; reconciliation required." }
            return resume(unfinished.single().operationId)
        }
        val binding = timing.measure("baseline.resolve") { dao.binding(c.accountScope, c.lineageId) ?: error("A verified graph root/baseline is required.") }
        val graph = checkedGraph(binding)
        onProgress(GraphBackupProgress(GraphBackupStage.CAPTURING_CHANGES))
        val batch = timing.measure("journal.capture") { capture.capture(c.accountScope, timing) }
        checkParent(binding, batch.journal)
        if (batch.records.isEmpty()) {
            onProgress(GraphBackupProgress(GraphBackupStage.ALREADY_BACKED_UP))
            return GraphWriterResult(null, binding.commitId, true, false, GraphWriterMetrics(0, 0, 0, 0, 0, 0))
        }
        check(graph.plan().deltas.size < 4096) { "A verified new full checkpoint is required before extending this delta epoch." }
        val parent = graph.commits.getValue(binding.commitId)
        val publication = timing.measure("publication.prepare") { prepareDelta(batch, binding, parent) }
        return resume(publication.operationId)
    }

    suspend fun createRoot(prepared: PreparedBackupBaseline): GraphWriterResult = createFullCheckpoint(prepared, null)

    /** Explicit full-snapshot reconciliation. The old tip remains an immutable parent. */
    suspend fun replaceCheckpoint(prepared: PreparedBackupBaseline): GraphWriterResult {
        val c = context
        val parent = dao.binding(c.accountScope, c.lineageId) ?: error("A verified graph parent is required.")
        return createFullCheckpoint(prepared, parent)
    }

    /** Explicit one-time bridge from the verified old tip to a frozen authoritative Vault. */
    suspend fun previewTransition(prepared: PreparedBackupBaseline): Pair<String, BackupGraphTransition> {
        val c = context
        check(prepared.journal.account.accountScope == c.accountScope && !prepared.journal.account.trusted)
        check(dao.unfinished(c.accountScope).isEmpty() && database.backupGraphRestoreDao().unfinished().isEmpty())
        val binding = dao.binding(c.accountScope, c.lineageId) ?: error("Existing graph binding is required.")
        val graph = checkedGraph(binding)
        check(graph.plan().deltas.size < 4096) { "Existing delta epoch is full; transition cannot be represented." }
        val old = verifiedGraphSnapshot(store, graph)
        val diff = calculateGraphTransition(old, prepared)
        check(prepared.journal.originEpoch == database.backupJournalDao().clock().originEpoch)
        if (diff.records.isNotEmpty()) IncrementalBackupFormat.createDelta(binding.checkpointId, binding.deltaHeadId,
            UUID.randomUUID().toString(), diff.records.map { it.protocolChange() }, emptyList())
        return binding.commitId to diff
    }

    suspend fun transition(prepared: PreparedBackupBaseline): Pair<GraphWriterResult, BackupGraphTransition> {
        val c = context
        val (oldTip, diff) = previewTransition(prepared)
        val binding = dao.binding(c.accountScope, c.lineageId) ?: error("Graph binding changed.")
        check(binding.commitId == oldTip)
        val graph = checkedGraph(binding)
        if (diff.records.isEmpty()) {
            database.withTransaction {
                check(dao.binding(c.accountScope, c.lineageId) == binding)
                journal.establishVerifiedTransition(prepared.journal, ConfirmedBackupCommit(c.accountScope, prepared.journal.generation,
                    binding.checkpointFileId, binding.checkpointSha256, binding.checkpointId, binding.deltaHeadId),
                    diff.oldBinaryReferences.map { BackupBinaryReference(c.accountScope, it.attachmentId, binding.checkpointId,
                        binding.deltaHeadId, it.cloudFileId, it.size, it.sha256) })
                dao.putBinding(binding.copy(originEpoch = prepared.journal.originEpoch))
            }
            return GraphWriterResult(null, binding.commitId, true, false, GraphWriterMetrics(0, 0, 0, 0, 0, 0)) to diff
        }
        val batch = PendingBackupBatch(prepared.journal, diff.records, diff.binaries, emptyList(), 0)
        val publication = prepareDelta(batch, binding, graph.commits.getValue(binding.commitId), diff,
            prepared.objects.filter { it.kind == "file" }.mapNotNull { it.attachmentId }, prepared.directory)
        return resume(publication.operationId) to diff
    }

    private suspend fun createFullCheckpoint(prepared: PreparedBackupBaseline, parent: BackupGraphBinding?): GraphWriterResult {
        onProgress(GraphBackupProgress(GraphBackupStage.CHECKING))
        val c = context
        check(prepared.journal.account.accountScope == c.accountScope)
        check(dao.unfinished(c.accountScope).isEmpty() && database.backupGraphRestoreDao().unfinished().isEmpty()) {
            "Recover the existing operation before creating a checkpoint."
        }
        val parentCommit = if (parent == null) {
            check(dao.binding(c.accountScope, c.lineageId) == null && store.commits().isEmpty()) {
                "Existing graph requires reconciliation, not a new root."
            }
            null
        } else {
            check(dao.binding(c.accountScope, c.lineageId) == parent)
            checkedGraph(parent).commits.getValue(parent.commitId)
        }
        check(prepared.objects.filter { it.kind == "metadata" }.map { it.backupEntry }.toSet() == BackupRecordKeys.keys + setOf("settings.json", "manifest.json"))
        check(prepared.objects.map { it.path }.distinct().size == prepared.objects.size)
        val op = UUID.randomUUID().toString()
        val ids = reserveObjectIds(prepared.objects.size + 2)
        val objects = mutableListOf<BackupGraphPublicationObject>()
        val inventory = JSONArray()
        onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, 0, prepared.objects.size + 2))
        for (source in prepared.objects) {
            val target = stage(op, objects.size, source.file, source.sha256, source.byteSize)
            val obj = newObject(ids[objects.size], op, objects.size, if (source.kind == "file") "BINARY" else "METADATA", source.attachmentId, target, source.sha256, source.byteSize)
            objects += obj
            inventory.put(JSONObject().put("path", source.path).put("backupEntry", source.backupEntry).put("kind", source.kind)
                .put("objectId", obj.objectId).put("attachmentId", source.attachmentId ?: JSONObject.NULL))
            onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, objects.size, prepared.objects.size + 2))
        }
        val entries = BackupGraphIntentCodec.array(inventory).map { item ->
            val obj = objects.single { it.objectId == item.getString("objectId") }
            JSONObject().put("path", item.getString("path")).put("backupEntry", item.getString("backupEntry"))
                .put("fileName", item.getString("backupEntry")).put("kind", item.getString("kind"))
                .put("size", obj.byteCount).put("sha256", obj.sha256).put("cloudFileId", obj.objectId)
        }
        val checkpointBytes = JSONObject().put("schemaVersion", 1).put("storage", "google-drive-api").put("cloudVersion", 1)
            .put("entries", JSONArray(entries)).toString().toByteArray(Charsets.UTF_8)
        val cp = byteObject(ids[objects.size], op, objects.size, "CHECKPOINT", checkpointBytes).also { objects += it }
        val commit = BackupGraphCommit(c.driveAccountId, c.lineageId, op, "checkpoint",
            parentCommit?.requiredReaders ?: listOf(BackupGraphCapability),
            parent?.let { listOf(GraphParent(it.commitId, it.commitRef())) } ?: emptyList(),
            GraphCheckpoint("full-${cp.sha256}", cp.ref()), null)
        check(parent == null || commit.checkpoint != parent.checkpoint()) { "The replacement checkpoint must have new verified content." }
        objects += byteObject(ids[objects.size], op, objects.size, "COMMIT", BackupGraphProtocol.encode(commit))
        onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, objects.size, objects.size))
        val frozen = JSONObject(BackupGraphIntentCodec.batch(PendingBackupBatch(prepared.journal, emptyList(), emptyList(), emptyList(), 0)))
            .put("rootInventory", inventory).put("preparationId", prepared.preparationId).toString()
        val p = publication(op, prepared.journal, parent, frozen, commit)
        persist(p, objects)
        return resume(op)
    }

    private suspend fun prepareDelta(batch: PendingBackupBatch, binding: BackupGraphBinding, parent: BackupGraphCommit,
        transition: BackupGraphTransition? = null, currentAttachmentIds: List<String> = emptyList(),
        transitionDirectory: File? = null): BackupGraphPublication {
        val stagedCount = batch.binaries.count { it.reusableCloudFileId == null } + 2
        onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, 0, stagedCount))
        val op = UUID.randomUUID().toString()
        val ids = reserveObjectIds(batch.binaries.count { it.reusableCloudFileId == null } + 2)
        val objects = mutableListOf<BackupGraphPublicationObject>()
        check((transition != null || batch.records.size == batch.journal.changes.size) && batch.records.all { r ->
            transition != null ||
            batch.journal.changes.any { it.recordGroup == r.group && it.stableKey() == r.key && it.operation == r.operation && it.generation == r.generation }
        })
        val binaries = mutableListOf<BackupBinaryDescriptor>()
        batch.binaries.forEach { binary ->
            check(binary.status == "VERIFIED" && binary.sha256 != null) { "Unknown binary requires verification/reconciliation." }
            if (binary.reusableCloudFileId == null) {
                check(batch.records.any { it.group == "attachments.json" && it.key == listOf(binary.attachmentId) && it.operation == "UPSERT" }) {
                    "Changed dependency bytes require an explicit attachment upsert."
                }
                val source = File(binary.localPath)
                check(if (transitionDirectory == null) isDurableBackupBinary(privateFilesDir, source)
                    else source.isFile && source.canonicalPath.startsWith(transitionDirectory.canonicalPath + File.separator)) {
                    "Binary source is outside the verified local capture."
                }
                val staged = stage(op, objects.size, source, binary.sha256, binary.byteSize)
                val obj = newObject(ids[objects.size], op, objects.size, "BINARY", binary.attachmentId, staged, binary.sha256, binary.byteSize).also { objects += it }
                binaries += BackupBinaryDescriptor(binary.attachmentId, obj.objectId, obj.sha256, obj.byteCount).validate()
                onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, objects.size, stagedCount))
            }
        }
        val binaryCapable = BackupBinaryReaderCapability in parent.requiredReaders || batch.records.any { it.group == "attachments.json" }
        val deltaId = UUID.randomUUID().toString()
        val delta = timing.local("delta.creation") { IncrementalBackupFormat.createDelta(binding.checkpointId, binding.deltaHeadId, deltaId,
            batch.records.map { it.protocolChange() }, if (binaryCapable) binaries else null) }
        val deltaObj = byteObject(ids[objects.size], op, objects.size, "DELTA", delta.toString().toByteArray(Charsets.UTF_8)).also { objects += it }
        val commit = BackupGraphCommit(context.driveAccountId, context.lineageId, op, "delta",
            (parent.requiredReaders + "checkpoint-delta-v1" + if (binaryCapable) listOf(BackupBinaryReaderCapability) else emptyList()).distinct().sorted(),
            listOf(GraphParent(binding.commitId, binding.commitRef())), binding.checkpoint(), GraphDelta(deltaId, binding.deltaHeadId, deltaObj.ref()))
        objects += byteObject(ids[objects.size], op, objects.size, "COMMIT", timing.local("commit.creation") { BackupGraphProtocol.encode(commit) })
        onProgress(GraphBackupProgress(GraphBackupStage.STAGING_PUBLICATION, objects.size, stagedCount))
        val frozen = JSONObject(BackupGraphIntentCodec.batch(batch))
        if (transition != null) frozen.put("transition", true)
            .put("transitionOldBinaries", JSONArray(transition.oldBinaryReferences.map { it.toJson() }))
            .put("transitionCurrentAttachments", JSONArray(currentAttachmentIds))
        return publication(op, batch.journal, binding, frozen.toString(), commit).also { persist(it, objects) }
    }

    private fun publication(op: String, snapshot: CapturedBackupChanges, binding: BackupGraphBinding?, frozen: String, commit: BackupGraphCommit) =
        BackupGraphPublication(context.accountScope, op, context.lineageId, context.driveAccountId, snapshot.generation, snapshot.originEpoch,
            BackupGraphIntentCodec.account(snapshot.account), binding?.let(BackupGraphIntentCodec::binding),
            JSONObject(frozen).put("namespaceProof", store.namespaceProof ?: JSONObject.NULL).toString(),
            BackupGraphProtocol.utf8(BackupGraphProtocol.encode(commit)), "PREPARED")

    private suspend fun persist(p: BackupGraphPublication, objects: List<BackupGraphPublicationObject>) = database.withTransaction {
        check(dao.unfinished(p.accountScope).isEmpty()) { "Only one unfinished publication per local account is allowed." }
        validateOriginal(p)
        check(objects.map { it.objectId }.distinct().size == objects.size)
        objects.forEach { check(dao.objectsWithId(p.accountScope, it.objectId).isEmpty()) { "Intended object ID is already assigned." } }
        dao.insertPublication(p); dao.insertObjects(objects)
    }

    suspend fun resume(operation: String): GraphWriterResult {
        onProgress(GraphBackupProgress(GraphBackupStage.CHECKING))
        val c = context
        val p = dao.publication(c.accountScope, operation) ?: error("No operation for this account.")
        check(p.driveAccountId == c.driveAccountId && p.lineageId == c.lineageId)
        val frozenNamespace = JSONObject(p.frozenBatchJson).let { if (it.isNull("namespaceProof") || !it.has("namespaceProof")) null else it.getString("namespaceProof") }
        check(frozenNamespace == store.namespaceProof) { "Publication recovery requires its original verified namespace." }
        val objects = dao.objects(c.accountScope, operation)
        val commitObject = objects.single { it.role == "COMMIT" }
        val commitBytes = p.commitJson.toByteArray(Charsets.UTF_8)
        val commit = BackupGraphProtocol.parse(GraphObject(commitObject.ref(), commitBytes))
        check(commit.commitId == p.operationId && commit.accountId == c.driveAccountId && commit.lineageId == c.lineageId)
        val metricsJson = JSONObject(p.frozenBatchJson)
        fun metrics(binary: Int = 0, delta: Int = 0, commits: Int = 0) = GraphWriterMetrics(BackupGraphIntentCodec.records(p.frozenBatchJson).size,
            metricsJson.getJSONArray("queries").length() + metricsJson.getInt("settingsReads"), objects.count { it.role == "BINARY" }, binary, delta, commits)
        check(p.status in setOf("PREPARED", "PUBLISHING", "COMMIT_VERIFIED", "COMPLETE"))
        if (p.status == "COMPLETE") {
            check(objects.all { it.verifiedSha256 == it.sha256 && it.verifiedByteCount == it.byteCount })
            check(verifyExisting(commitObject)) { "Previously committed object is missing." }
            val graph = BackupGraph.discover(store.commits(), c.driveAccountId, c.lineageId)
            check(graph.status in setOf(GraphStatus.SINGLE_TIP, GraphStatus.FORK) && graph.commits[commit.commitId] == commit) { "Previously committed graph is no longer verifiable." }
            if (graph.status != GraphStatus.FORK) onProgress(GraphBackupProgress(GraphBackupStage.COMPLETE))
            return GraphWriterResult(operation, commit.commitId, false, graph.status == GraphStatus.FORK, metrics())
        }
        validateOriginal(p)
        // A prior uncertain commit create must be recovered even if a sibling has since appeared.
        val published = verifyExisting(commitObject)
        if (!published) {
            if (p.originalBindingJson == null) check(store.commits().isEmpty()) { "A graph now exists; root enrollment requires reconciliation." }
            else checkedGraph(BackupGraphIntentCodec.binding(p.originalBindingJson))
        }
        validateIntent(p, objects, commit)
        dao.status(c.accountScope, operation, "PUBLISHING")
        boundary("PUBLISHING")
        var binaryCount = 0; var deltaCount = 0; var commitCount = 0
        for ((index, obj) in objects.withIndex()) {
            check(obj.ordinal == index) { "Publication order is inconsistent." }
            verifyStaged(obj)
            val exists = verifyExisting(obj)
            if (!exists) {
                onProgress(GraphBackupProgress(GraphBackupStage.UPLOADING, index, objects.size))
                timing.measure("upload.${obj.role}") { store.create(obj.objectId, obj.role, File(obj.stagedPath)) }
                if (obj.role == "BINARY") binaryCount++
                if (obj.role == "DELTA") deltaCount++
                if (obj.role == "COMMIT") commitCount++
                boundary("CREATED_${obj.role}")
                onProgress(GraphBackupProgress(GraphBackupStage.VERIFYING, index, objects.size))
                check(verifyExisting(obj)) { "Immutable object is missing after create." }
            }
            check(dao.receipt(c.accountScope, operation, obj.objectId, obj.sha256, obj.byteCount) == 1)
            boundary("VERIFIED_${obj.role}")
            onProgress(GraphBackupProgress(GraphBackupStage.VERIFYING, index + 1, objects.size))
        }
        dao.status(c.accountScope, operation, "COMMIT_VERIFIED")
        boundary("COMMIT_VERIFIED")
        boundary("BEFORE_COMPLETION")
        val rootProof = if (commit.kind == "checkpoint") rootProof(p, objects, commit) else null
        onProgress(GraphBackupProgress(GraphBackupStage.COMPLETING))
        timing.measure("local.completion") { complete(p, objects, commit, rootProof) }
        boundary("COMPLETE")
        val inventory = timing.measure("graph.discovery") { store.commits() }
        val graph = timing.local("graph.validation") { BackupGraph.discover(inventory, c.driveAccountId, c.lineageId) }
        if (graph.status == GraphStatus.SINGLE_TIP) onProgress(GraphBackupProgress(GraphBackupStage.COMPLETE))
        return GraphWriterResult(operation, commit.commitId, false, graph.status == GraphStatus.FORK, metrics(binaryCount, deltaCount, commitCount))
    }

    private suspend fun complete(p: BackupGraphPublication, objects: List<BackupGraphPublicationObject>, commit: BackupGraphCommit,
        rootProof: Pair<PreparedBackupBaseline, VerifiedBackupBaseline>?) = database.withTransaction {
        val persisted = dao.publication(context.accountScope, p.operationId)!!
        if (persisted.status == "COMPLETE") return@withTransaction
        check(persisted.copy(status = p.status) == p) { "Publication intent changed." }
        validateOriginal(p)
        val receipts = dao.objects(p.accountScope, p.operationId)
        check(receipts.map { it.copy(verifiedSha256 = null, verifiedByteCount = null) } == objects.map { it.copy(verifiedSha256 = null, verifiedByteCount = null) })
        check(receipts.all { it.verifiedSha256 == it.sha256 && it.verifiedByteCount == it.byteCount }) { "Every dependency and commit requires byte verification." }
        validateIntent(p, receipts, commit)
        val snapshot = BackupGraphIntentCodec.snapshot(p)
        val frozen = JSONObject(p.frozenBatchJson)
        if (rootProof != null) journal.establishVerifiedBaseline(rootProof.first, rootProof.second)
        else if (frozen.optBoolean("transition")) {
            val delta = objects.single { it.role == "DELTA" }
            val raw = JSONObject(File(delta.stagedPath).readText())
            val changes = IncrementalBackupFormat.parseChanges(raw)
            val resolved = BackupGraphIntentCodec.array(frozen.getJSONArray("transitionOldBinaries"))
                .map(BackupBinaryDescriptor.Companion::parse).associateBy { it.attachmentId }.toMutableMap()
            changes.filter { it.file == "attachments.json" && it.deleted }.forEach { resolved.remove(it.key.single()) }
            parseBackupBinaries(raw, changes).forEach { resolved[it.attachmentId] = it }
            val expectedIds = (0 until frozen.getJSONArray("transitionCurrentAttachments").length())
                .map { frozen.getJSONArray("transitionCurrentAttachments").getString(it) }
            check(expectedIds.size == expectedIds.toSet().size && resolved.keys == expectedIds.toSet())
            journal.establishVerifiedTransition(snapshot, ConfirmedBackupCommit(p.accountScope, p.capturedGeneration,
                commit.checkpoint.objectRef.cloudFileId, commit.checkpoint.objectRef.sha256, commit.checkpoint.checkpointId, commit.deltaHead),
                resolved.values.map { BackupBinaryReference(p.accountScope, it.attachmentId, commit.checkpoint.checkpointId,
                    commit.deltaHead, it.cloudFileId, it.size, it.sha256) })
        } else journal.acknowledgeConfirmedDelta(snapshot, ConfirmedBackupCommit(p.accountScope, p.capturedGeneration,
            commit.checkpoint.objectRef.cloudFileId, commit.checkpoint.objectRef.sha256, commit.checkpoint.checkpointId, commit.deltaHead))
        val refs = if (rootProof == null) {
            val delta = objects.single { it.role == "DELTA" }
            val json = JSONObject(File(delta.stagedPath).readText())
            parseBackupBinaries(json, IncrementalBackupFormat.parseChanges(json)).map { b ->
                BackupBinaryReference(p.accountScope, b.attachmentId, commit.checkpoint.checkpointId, commit.deltaHead, b.cloudFileId, b.size, b.sha256)
            }
        } else emptyList()
        database.backupJournalDao().putBinaryReferences(refs)
        BackupGraphIntentCodec.records(p.frozenBatchJson).filter { it.group == "attachments.json" && it.operation == "DELETE" }
            .forEach { database.backupJournalDao().removeBinaryReference(p.accountScope, it.key.single()) }
        boundary("IN_COMPLETION_TRANSACTION")
        val receipt = objects.single { it.role == "COMMIT" }
        dao.putBinding(BackupGraphBinding(p.accountScope, p.lineageId, p.driveAccountId, commit.commitId, receipt.objectId, receipt.sha256, receipt.byteCount,
            commit.checkpoint.checkpointId, commit.checkpoint.objectRef.cloudFileId, commit.checkpoint.objectRef.sha256, commit.checkpoint.objectRef.size,
            commit.deltaHead, p.capturedOriginEpoch))
        dao.status(p.accountScope, p.operationId, "COMPLETE")
    }

    private suspend fun validateOriginal(p: BackupGraphPublication) {
        check(p.accountScope == context.accountScope && p.driveAccountId == context.driveAccountId && p.lineageId == context.lineageId)
        val current = database.backupJournalDao().account(p.accountScope) ?: error("Account is not enrolled.")
        val original = BackupGraphIntentCodec.account(p.originalAccountJson)
        check(current == original) { "Baseline changed; pending changes were preserved." }
        val clock = database.backupJournalDao().clock()
        check(clock.originEpoch == p.capturedOriginEpoch && clock.generation >= p.capturedGeneration && clock.settingsToken == null && clock.suppressionDepth == 0)
        val expected = p.originalBindingJson?.let(BackupGraphIntentCodec::binding)
        check(dao.binding(p.accountScope, p.lineageId) == expected) { "Graph binding changed; reconciliation required." }
        if (expected != null && JSONObject(p.commitJson).getString("kind") == "delta" && !JSONObject(p.frozenBatchJson).optBoolean("transition")) {
            checkParent(expected, BackupGraphIntentCodec.snapshot(p))
        }
    }

    private fun checkParent(b: BackupGraphBinding, snapshot: CapturedBackupChanges) {
        check(b.accountScope == context.accountScope && b.driveAccountId == context.driveAccountId && b.lineageId == context.lineageId)
        val a = snapshot.account
        check(a.trusted && a.checkpointId == b.checkpointId && a.headId == b.deltaHeadId &&
            a.manifestId == b.checkpointFileId && a.manifestSha256 == b.checkpointSha256 && snapshot.originEpoch == b.originEpoch) { "Graph parent is no longer trusted." }
    }

    private suspend fun checkedGraph(binding: BackupGraphBinding): BackupGraph {
        val inventory = timing.measure("graph.discovery") { store.commits() }
        val graph = timing.local("graph.validation") { BackupGraph.discover(inventory, context.driveAccountId, context.lineageId) }
        check(graph.status == GraphStatus.SINGLE_TIP && graph.tips.single() == binding.commitId) { "Fork, unsupported/missing ancestry or newer branch requires reconciliation." }
        val parent = graph.commits.getValue(binding.commitId)
        check(parent.checkpoint == binding.checkpoint() && parent.deltaHead == binding.deltaHeadId)
        // The complete fresh inventory already contains these exact verified bytes. It is not a cached head.
        val proof = inventory.single { it.objectRef.cloudFileId == binding.commitFileId }
        check(proof.objectRef == binding.commitRef()) { "Parent descriptor differs from the trusted binding." }
        val verified = timing.local("baseline.verify") { BackupGraphProtocol.parse(proof) }
        check(verified == parent)
        return graph
    }

    private fun validateIntent(p: BackupGraphPublication, objects: List<BackupGraphPublicationObject>, commit: BackupGraphCommit) {
        check(objects.isNotEmpty() && objects.last().role == "COMMIT" && objects.count { it.role == "COMMIT" } == 1)
        check(objects.map { it.objectId }.distinct().size == objects.size)
        objects.forEach { check(it.accountScope == p.accountScope && it.operationId == p.operationId); verifyStaged(it) }
        if (commit.kind == "delta") {
            val parent = p.originalBindingJson?.let(BackupGraphIntentCodec::binding) ?: error("Delta has no trusted original binding.")
            check(commit.parents == listOf(GraphParent(parent.commitId, parent.commitRef())) && commit.checkpoint == parent.checkpoint() && commit.delta!!.parentId == parent.deltaHeadId)
            val d = objects.single { it.role == "DELTA" }
            check(d.ref() == commit.delta.objectRef && objects[objects.lastIndex - 1] == d)
            val raw = JSONObject(File(d.stagedPath).readText())
            check(raw.getString("deltaId") == commit.delta.deltaId && raw.getString("parentId") == commit.delta.parentId && raw.getString("checkpointId") == commit.checkpoint.checkpointId)
            val parsed = IncrementalBackupFormat.parseChanges(raw)
            val captured = BackupGraphIntentCodec.records(p.frozenBatchJson).map { it.protocolChange() }
            check(sameJson(JSONArray(parsed.map { it.toJson() }), JSONArray(captured.map { it.toJson() }))) { "Delta differs from frozen capture." }
            val binaries = parseBackupBinaries(raw, parsed)
            check(binaries.map { it.cloudFileId }.toSet() == objects.filter { it.role == "BINARY" }.map { it.objectId }.toSet())
            binaries.forEach { b -> check(objects.single { it.objectId == b.cloudFileId }.let { it.attachmentId == b.attachmentId && it.sha256 == b.sha256 && it.byteCount == b.size }) }
            check(objects.all { it.role in setOf("BINARY", "DELTA", "COMMIT") })
        } else {
            val parent = p.originalBindingJson?.let(BackupGraphIntentCodec::binding)
            check(commit.parents == (parent?.let { listOf(GraphParent(it.commitId, it.commitRef())) } ?: emptyList<GraphParent>()))
            check(parent == null || commit.checkpoint != parent.checkpoint())
            val cp = objects.single { it.role == "CHECKPOINT" }
            check(cp.ref() == commit.checkpoint.objectRef && objects[objects.lastIndex - 1] == cp)
            check(objects.dropLast(2).all { it.role in setOf("BINARY", "METADATA") })
        }
    }

    private suspend fun rootProof(p: BackupGraphPublication, objects: List<BackupGraphPublicationObject>, commit: BackupGraphCommit): Pair<PreparedBackupBaseline, VerifiedBackupBaseline> {
        val frozen = JSONObject(p.frozenBatchJson)
        val inventory = BackupGraphIntentCodec.array(frozen.getJSONArray("rootInventory"))
        val prepared = PreparedBackupBaseline(frozen.getString("preparationId"), BackupGraphIntentCodec.snapshot(p), stagingRoot,
            inventory.map { row ->
                val obj = objects.single { it.objectId == row.getString("objectId") }
                PreparedBaselineObject(row.getString("path"), row.getString("backupEntry"), row.getString("kind"), File(obj.stagedPath), obj.byteCount,
                    obj.sha256, if (row.isNull("attachmentId")) null else row.getString("attachmentId"))
            })
        val checkpoint = objects.single { it.role == "CHECKPOINT" }
        val bytes = File(checkpoint.stagedPath).readBytes()
        onProgress(GraphBackupProgress(GraphBackupStage.VERIFYING_BASELINE, 0, inventory.size + 1))
        val readback = store.read(checkpoint.objectId)!!.use { it.readBytes() }
        onProgress(GraphBackupProgress(GraphBackupStage.VERIFYING_BASELINE, 1, inventory.size + 1))
        val receipts = inventory.mapIndexed { index, row ->
            val id = row.getString("objectId")
            store.read(id)!!.use { VerifiedRemoteBackupObject.read(p.accountScope, id, it) }.also {
                onProgress(GraphBackupProgress(GraphBackupStage.VERIFYING_BASELINE, index + 2, inventory.size + 1))
            }
        }
        val proof = VerifiedBackupBaseline.verify(prepared, p.accountScope, checkpoint.objectId, bytes, readback, receipts)
        check(proof.commit.checkpointId == commit.checkpoint.checkpointId)
        return prepared to proof
    }

    private suspend fun reserveObjectIds(count: Int): List<String> = timing.measure("ids.generate") {
        store.reserveIds(count).also { ids ->
            check(ids.size == count && ids.distinct().size == count) { "Reserved immutable IDs are incomplete/ambiguous." }
            ids.forEach(BackupGraphProtocol::id)
        }
    }
    private fun newObject(id: String, op: String, ordinal: Int, role: String, attachment: String?, file: File, hash: String, size: Long): BackupGraphPublicationObject {
        return BackupGraphPublicationObject(context.accountScope, op, id, ordinal, role, attachment, file.canonicalPath, hash, size, null, null)
    }
    private fun byteObject(id: String, op: String, ordinal: Int, role: String, bytes: ByteArray): BackupGraphPublicationObject {
        val file = target(op, ordinal)
        timing.local("local.staging") { FileOutputStream(file).use { it.write(bytes); it.fd.sync() } }
        return newObject(id, op, ordinal, role, null, file, IncrementalBackupFormat.sha256(bytes), bytes.size.toLong())
    }
    private fun target(op: String, ordinal: Int): File {
        val dir = File(stagingRoot, op).apply { check(mkdirs() || isDirectory) }
        val file = File(dir, "$ordinal.bin")
        check(!file.exists()) { "Staged immutable path already exists." }
        return file
    }
    private fun stage(op: String, ordinal: Int, source: File, hash: String, size: Long): File {
        val target = target(op, ordinal)
        val digest = timing.local("local.staging") { source.inputStream().use { input -> FileOutputStream(target).use { output ->
            fingerprintBackupBytes(input, output).also { output.fd.sync() }
        } } }
        check(digest.sha256 == hash && digest.size == size) { "Source binary changed after capture; nothing was committed." }
        return target
    }
    private fun verifyStaged(obj: BackupGraphPublicationObject) {
        val file = File(obj.stagedPath)
        check(file.canonicalFile.toPath().startsWith(stagingRoot.canonicalFile.toPath()) && file.isFile) { "Durable staging file is missing/unsafe." }
        val digest = timing.local("local.staged_verification") { file.inputStream().use { fingerprintBackupBytes(it) } }
        check(digest.sha256 == obj.sha256 && digest.size == obj.byteCount) { "Staged bytes are corrupt; pending changes were preserved." }
        check(obj.verifiedSha256 == null || obj.verifiedSha256 == obj.sha256)
        check(obj.verifiedByteCount == null || obj.verifiedByteCount == obj.byteCount)
    }
    private suspend fun verifyExisting(obj: BackupGraphPublicationObject): Boolean = timing.measure("verification.${obj.role}") {
        val input = store.read(obj.objectId) ?: return@measure false
        val digest = input.use { fingerprintBackupBytes(it) }
        check(digest.sha256 == obj.sha256 && digest.size == obj.byteCount) { "Intended immutable ID resolves to different bytes." }
        true
    }
}

internal fun BackupGraphPublicationObject.ref() = GraphObjectRef(objectId, sha256, byteCount)
internal fun BackupGraphBinding.commitRef() = GraphObjectRef(commitFileId, commitSha256, commitSize)
internal fun BackupGraphBinding.checkpoint() = GraphCheckpoint(checkpointId, GraphObjectRef(checkpointFileId, checkpointSha256, checkpointSize))
private fun sameJson(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { sameJson(a.get(it), b.get(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameJson(a.get(it), b.get(it)) }
    else -> a == b
}
