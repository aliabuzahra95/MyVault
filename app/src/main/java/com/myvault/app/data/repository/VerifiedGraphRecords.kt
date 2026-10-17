package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject

/** Shared authenticated metadata reader. Dependency checks run on the final fold, not history. */
internal class VerifiedGraphRecordCache {
    val records = mutableMapOf<BackupGraphCommit, Pair<List<BackupRecordChange>, List<BackupBinaryDescriptor>>>()
    val references = mutableMapOf<BackupGraphCommit, Set<Pair<GraphObjectRef, String>>>()
    val checkpointGroups = mutableMapOf<String, Set<String>>()
}

internal class VerifiedGraphRecords(private val store: DisposableGraphObjectStore,
    private val timing: BackupGraphTiming = BackupGraphTiming(),
    private val cache: VerifiedGraphRecordCache = VerifiedGraphRecordCache(),
    private val refreshCached: Boolean = false) {
    private val checkedHere = mutableSetOf<BackupGraphCommit>()
    private val loadedReferences = mutableMapOf<String, MutableSet<Pair<GraphObjectRef, String>>>()
    val checkpointGroups get() = cache.checkpointGroups

    private suspend fun load(ref: GraphObjectRef, commitId: String, folder: String): ByteArray {
        BackupGraphProtocol.reference(ref)
        loadedReferences.getOrPut(commitId) { mutableSetOf() }.add(ref to folder)
        val input = store.readMetadata(ref, folder) ?: throw BackupGraphRestoreFailure(
            "A required Drive backup file is unavailable. Check Drive access and retry. Local data was preserved.",
            "phase=metadata_verification commit=$commitId object=${ref.cloudFileId} source=remote missing=true")
        return timing.measure("metadata.load") {
            input.use { it.readBytes() }.also {
                timing.count("metadata.loads"); timing.count("metadata.verified_bytes", it.size.toLong())
                BackupGraphProtocol.verify(ref, it)
            }
        }
    }

    suspend fun read(commit: BackupGraphCommit): Pair<List<BackupRecordChange>, List<BackupBinaryDescriptor>> {
        cache.records[commit]?.let {
            // A later publication preflight still verifies availability/bytes of ALL required
            // objects. Only JSON parsing is shared; the transport checks fresh metadata.
            if (refreshCached && commit !in checkedHere) {
                for ((ref, folder) in cache.references.getValue(commit)) load(ref, commit.commitId, folder)
            }
            checkedHere.add(commit)
            timing.count("metadata.parsed_cache_hits"); return it
        }
        loadedReferences.remove(commit.commitId)
        timing.count("metadata.commit_parses")
        val changes: List<BackupRecordChange>
        val binaries: List<BackupBinaryDescriptor>
        if (commit.kind == "delta") {
            val delta = commit.delta!!
            val value = JSONObject(BackupGraphProtocol.utf8(load(delta.objectRef, commit.commitId, "deltas")))
            check(value.getString("checkpointId") == commit.checkpoint.checkpointId && value.getString("parentId") == delta.parentId && value.getString("deltaId") == delta.deltaId)
            check(value.getInt("version") != 2 || BackupBinaryReaderCapability in commit.requiredReaders)
            changes = IncrementalBackupFormat.parseChanges(value)
            binaries = parseBackupBinaries(value, changes)
        } else {
            val manifest = JSONObject(BackupGraphProtocol.utf8(load(commit.checkpoint.objectRef, commit.commitId, "checkpoints")))
            check(manifest.getInt("schemaVersion") == 1 && manifest.getString("storage") == "google-drive-api" && !manifest.has(IncrementalBackupField))
            val rows = mutableListOf<BackupRecordChange>()
            val files = mutableListOf<BackupBinaryDescriptor>()
            val groups = mutableSetOf<String>()
            val seen = mutableMapOf<String, GraphObjectRef>()
            for (entry in BackupGraphIntentCodec.array(manifest.getJSONArray("entries"))) {
                val source = GraphObjectRef(entry.getString("cloudFileId"), entry.getString("sha256"), entry.getLong("size"))
                BackupGraphProtocol.reference(source, 9007199254740991, entry.getString("kind") == "file")
                check(seen.putIfAbsent(source.cloudFileId, source)?.let { it == source } != false)
                when (entry.getString("kind")) {
                    "file" -> {
                        val path = entry.getString("backupEntry"); check(path.startsWith("files/"))
                        files += BackupBinaryDescriptor(path.removePrefix("files/"), source.cloudFileId, source.sha256, source.size).validate()
                    }
                    "metadata" -> {
                        val group = entry.getString("fileName")
                        check(group in BackupRecordKeys || group in listOf("settings.json", "manifest.json"))
                        check(groups.add(group))
                        val text = BackupGraphProtocol.utf8(load(source, commit.commitId, "checkpoints"))
                        if (group == "settings.json") rows += BackupRecordChange(group, listOf("settings"), JSONObject(text))
                        else if (group != "manifest.json") for (row in BackupGraphIntentCodec.array(JSONArray(text))) {
                            rows += BackupRecordChange(group, IncrementalBackupFormat.key(group, row), row)
                        }
                    }
                    else -> error("Unknown checkpoint entry.")
                }
            }
            check(groups.containsAll(listOf("folders.json", "notes.json")))
            check(rows.map { it.file to it.key }.distinct().size == rows.size && files.map { it.attachmentId }.distinct().size == files.size)
            checkpointGroups[commit.commitId] = groups.filter { it in BackupRecordKeys }.toSet()
            changes = rows; binaries = files
        }
        return (changes to binaries).also {
            cache.records[commit] = it
            cache.references[commit] = loadedReferences.getValue(commit.commitId).toSet()
            checkedHere.add(commit)
        }
    }

    suspend fun fold(graph: BackupGraph): BackupGraphRestoreState = timing.measure("metadata.fold") { BackupGraphRestoreState().also { state ->
        timing.count("metadata.folds")
        check(graph.status == GraphStatus.SINGLE_TIP)
        for (commit in graph.plan().commits) state.apply(read(commit).first, checkpointGroups[commit.commitId], commit.commitId)
    } }
}

internal data class BackupReferenceViolation(val dependent: BackupRecordIdentity, val dependency: BackupRecordIdentity,
    val field: String, val dependentCommit: String?, val dependencyCommit: String?, val remoteDeleted: Boolean) {
    fun diagnostic(stage: String, commit: String) = "phase=$stage commit=$commit dependent=${dependent.group}:${dependent.key} " +
        "field=$field dependency=${dependency.group}:${dependency.key} dependent_commit=$dependentCommit " +
        "dependency_commit=$dependencyCommit remote_deleted=$remoteDeleted source=verified_snapshot"
}

internal fun backupReferenceViolations(state: BackupGraphRestoreState): List<BackupReferenceViolation> {
    val rows = state.changes.associateBy { BackupRecordIdentity(it.file, it.key) }
    return rows.filterValues { !it.deleted && it.file != "settings.json" }.flatMap { (id, row) ->
        backupRecordDependencyFields(row.file, row.value!!).mapNotNull { (field, dependency) ->
            if (rows[dependency]?.deleted == false) null else BackupReferenceViolation(id, dependency, field,
                state.origins[id], state.origins[dependency], rows[dependency]?.deleted == true)
        }
    }
}

internal fun requireBackupReferences(state: BackupGraphRestoreState, commit: String, stage: String) {
    val violations = backupReferenceViolations(state)
    if (violations.isNotEmpty()) throw BackupGraphRestoreFailure(
        "The backup contains an item with a missing required parent or reference. No backup commit or restore position was applied. " +
            "On the primary phone, install the corrected app and choose Back up now before retrying Restore.",
        violations.joinToString("\n") { it.diagnostic(stage, commit) })
}
