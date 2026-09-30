package com.myvault.app.data.repository

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.ArrayDeque

internal const val BackupGraphCapability = "backup-commit-graph-v1"
internal const val BackupGraphNamespace = "MyVault Backup Graph v1"
internal val BackupGraphDirectories = listOf("checkpoints", "commits", "deltas", "binaries")
internal const val BackupGraphPublicationEnabled = true

internal data class GraphObjectRef(val cloudFileId: String, val sha256: String, val size: Long)
internal data class GraphParent(val commitId: String, val objectRef: GraphObjectRef)
internal data class GraphCheckpoint(val checkpointId: String, val objectRef: GraphObjectRef)
internal data class GraphDelta(val deltaId: String, val parentId: String, val objectRef: GraphObjectRef)
internal data class BackupGraphCommit(
    val accountId: String, val lineageId: String, val commitId: String, val kind: String,
    val requiredReaders: List<String>, val parents: List<GraphParent>,
    val checkpoint: GraphCheckpoint, val delta: GraphDelta?,
) {
    val deltaHead: String get() = delta?.deltaId ?: checkpoint.checkpointId
}

/** Receipts bind ORIGINAL bytes, not a reserialized interpretation. Namespace membership is a caller precondition. */
internal data class GraphObject(val objectRef: GraphObjectRef, val bytes: ByteArray)
internal enum class GraphStatus { HISTORICAL, SINGLE_TIP, ALREADY_CURRENT, DESCENDANTS, FORK, UNSUPPORTED, CORRUPT, MISSING_ANCESTRY, DIVERGENT }
internal data class GraphIssue(val status: GraphStatus, val cloudFileId: String, val reason: String)
internal data class GraphPlan(
    val status: GraphStatus, val commits: List<BackupGraphCommit> = emptyList(),
    val descendants: List<BackupGraphCommit> = emptyList(), val checkpoint: GraphCheckpoint? = null,
    val deltas: List<GraphDelta> = emptyList(), val requiresCheckpoint: Boolean = false,
)

internal object BackupGraphProtocol {
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val hash = Regex("[0-9a-f]{64}")
    private val capabilities = setOf(BackupGraphCapability, "checkpoint-delta-v1", BackupBinaryReaderCapability)
    internal class Unsupported(message: String) : IllegalStateException(message)

    fun id(value: String) {
        check(value.isNotEmpty() && value.length <= 256 && value.none { it.code < 0x21 || it.code == 0x7f }) { "Invalid graph ID." }
        quote(value) // Reject unpaired UTF-16 surrogates rather than changing byte identity.
    }
    fun reference(ref: GraphObjectRef, limit: Long = 16L * 1024 * 1024, allowEmpty: Boolean = false) {
        id(ref.cloudFileId)
        check(hash.matches(ref.sha256) && ref.size in (if (allowEmpty) 0 else 1)..limit) { "Invalid graph byte reference." }
    }
    fun verify(ref: GraphObjectRef, bytes: ByteArray) {
        check(bytes.size.toLong() == ref.size && IncrementalBackupFormat.sha256(bytes) == ref.sha256) { "Graph object byte verification failed." }
    }
    fun utf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    fun validate(commit: BackupGraphCommit) {
        id(commit.accountId); id(commit.lineageId)
        check(uuid.matches(commit.commitId)) { "Invalid commit UUID." }
        check(commit.requiredReaders == commit.requiredReaders.distinct().sorted() && BackupGraphCapability in commit.requiredReaders) { "Invalid graph capabilities." }
        if (commit.requiredReaders.any { it !in capabilities }) throw Unsupported("Unsupported graph reader capability.")
        check(commit.parents.size <= 1 && commit.kind in listOf("checkpoint", "delta")) { "Unsupported graph structure." }
        commit.parents.forEach {
            check(uuid.matches(it.commitId) && it.commitId != commit.commitId) { "Invalid graph parent UUID." }
            reference(it.objectRef, 65536)
        }
        reference(commit.checkpoint.objectRef)
        check(commit.checkpoint.checkpointId == "full-${commit.checkpoint.objectRef.sha256}") { "Checkpoint ID must bind exact full manifest bytes." }
        if (commit.kind == "checkpoint") check(commit.delta == null) { "Checkpoint cannot contain a delta." }
        else {
            check(commit.parents.size == 1 && commit.delta != null) { "Delta requires exactly one parent." }
            val delta = commit.delta
            id(delta.deltaId); id(delta.parentId); reference(delta.objectRef)
            check(delta.deltaId != delta.parentId && delta.deltaId != commit.checkpoint.checkpointId) { "Invalid delta identity." }
            check("checkpoint-delta-v1" in commit.requiredReaders || BackupBinaryReaderCapability in commit.requiredReaders) { "Missing delta capability." }
        }
    }

    // Fixed schema order, UTF-8, no whitespace/BOM, shortest integer form, raw Unicode and JSON control escapes.
    // UUID is the operation identity; this wire form only freezes retry bytes, not a content-addressed commit ID.
    fun encode(commit: BackupGraphCommit): ByteArray {
        validate(commit)
        fun fields(ref: GraphObjectRef) = "\"cloudFileId\":${quote(ref.cloudFileId)},\"sha256\":${quote(ref.sha256)},\"size\":${ref.size}"
        val parents = commit.parents.joinToString(",", "[", "]") { "{\"commitId\":${quote(it.commitId)},${fields(it.objectRef)}}" }
        val checkpoint = "{\"checkpointId\":${quote(commit.checkpoint.checkpointId)},${fields(commit.checkpoint.objectRef)}}"
        val delta = commit.delta?.let { "{\"deltaId\":${quote(it.deltaId)},\"parentId\":${quote(it.parentId)},${fields(it.objectRef)}}" } ?: "null"
        return ("{\"format\":\"myvault-backup-commit\",\"version\":1,\"requiredReaders\":" +
            commit.requiredReaders.joinToString(",", "[", "]", transform = ::quote) +
            ",\"accountId\":${quote(commit.accountId)},\"lineageId\":${quote(commit.lineageId)},\"commitId\":${quote(commit.commitId)}," +
            "\"kind\":${quote(commit.kind)},\"parents\":$parents,\"checkpoint\":$checkpoint,\"delta\":$delta}").toByteArray(Charsets.UTF_8)
            .also { check(it.size <= 65536) { "Graph commit too large." } }
    }

    fun parse(source: GraphObject): BackupGraphCommit {
        reference(source.objectRef, 65536); verify(source.objectRef, source.bytes)
        val text = utf8(source.bytes)
        val json = JSONObject(text)
        fun string(obj: JSONObject, field: String): String = (obj.get(field) as? String) ?: error("Graph field must be a string.")
        fun ref(obj: JSONObject): GraphObjectRef {
            val size = obj.get("size")
            check(size is Number && size.toDouble() == size.toLong().toDouble()) { "Invalid graph size type." }
            return GraphObjectRef(string(obj, "cloudFileId"), string(obj, "sha256"), size.toLong())
        }
        check(string(json, "format") == "myvault-backup-commit") { "Invalid graph format." }
        val version = json.get("version")
        check(version is Number && version.toDouble() == version.toLong().toDouble() && version.toLong() in 0..9007199254740991) { "Invalid graph version type." }
        if (version.toLong() != 1L) throw Unsupported("Unsupported graph version.")
        val required = json.getJSONArray("requiredReaders")
        val parents = json.getJSONArray("parents")
        val cp = json.getJSONObject("checkpoint")
        val delta = if (json.get("delta") == JSONObject.NULL) null else json.getJSONObject("delta").let {
            GraphDelta(string(it, "deltaId"), string(it, "parentId"), ref(it))
        }
        val commit = BackupGraphCommit(string(json, "accountId"), string(json, "lineageId"), string(json, "commitId"), string(json, "kind"),
            (0 until required.length()).map { (required.get(it) as? String) ?: error("Invalid graph capability type.") },
            (0 until parents.length()).map { parents.getJSONObject(it).let { p -> GraphParent(string(p, "commitId"), ref(p)) } },
            GraphCheckpoint(string(cp, "checkpointId"), ref(cp)), delta)
        // This also rejects duplicate keys, unknown fields, coercions, trailing JSON and non-canonical encodings on Android's permissive JSON parser.
        check(encode(commit).contentEquals(source.bytes)) { "Non-canonical or ambiguous graph JSON." }
        return commit
    }

    private fun quote(text: String): String = buildString {
        append('"')
        var index = 0
        while (index < text.length) {
            val c = text[index++]
            when (c) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000c' -> append("\\f")
                '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                else -> when {
                    c.code < 32 -> append("\\u%04x".format(c.code))
                    c.isHighSurrogate() -> { check(index < text.length && text[index].isLowSurrogate()); append(c); append(text[index++]) }
                    c.isLowSurrogate() -> error("Unpaired Unicode surrogate.")
                    else -> append(c)
                }
            }
        }
        append('"')
    }
}

/** O(objects + links) discovery; no checkpoint, delta or binary downloads. Known bad children block latest selection. */
internal class BackupGraph private constructor(
    val commits: Map<String, BackupGraphCommit>, val roots: List<String>, val tips: List<String>, val issues: List<GraphIssue>,
) {
    val status: GraphStatus get() = when {
        issues.any { it.status == GraphStatus.UNSUPPORTED } -> GraphStatus.UNSUPPORTED
        issues.any { it.status == GraphStatus.MISSING_ANCESTRY } -> GraphStatus.MISSING_ANCESTRY
        issues.isNotEmpty() -> GraphStatus.CORRUPT
        tips.size > 1 || roots.size > 1 -> GraphStatus.FORK
        tips.size == 1 -> GraphStatus.SINGLE_TIP
        else -> GraphStatus.MISSING_ANCESTRY
    }

    fun plan(appliedCommitId: String? = null): GraphPlan {
        if (status != GraphStatus.SINGLE_TIP) return GraphPlan(status)
        val reverse = mutableListOf<BackupGraphCommit>()
        val seen = mutableSetOf<String>()
        var current: String? = tips.single()
        while (current != null) {
            check(seen.add(current)) { "Graph cycle." }
            val commit = commits.getValue(current)
            reverse += commit; current = commit.parents.singleOrNull()?.commitId
        }
        val ordered = reverse.asReversed()
        val local = if (appliedCommitId == null) -1 else ordered.indexOfFirst { it.commitId == appliedCommitId }
        if (appliedCommitId != null && local < 0) return GraphPlan(GraphStatus.DIVERGENT)
        val descendants = ordered.drop(local + 1)
        val checkpointIndex = ordered.indexOfLast { it.kind == "checkpoint" }
        val needsCheckpoint = appliedCommitId == null || descendants.any { it.kind == "checkpoint" }
        val deltaCommits = if (needsCheckpoint) ordered.drop(checkpointIndex + 1) else descendants
        return GraphPlan(if (appliedCommitId == null) GraphStatus.SINGLE_TIP else if (descendants.isEmpty()) GraphStatus.ALREADY_CURRENT else GraphStatus.DESCENDANTS,
            ordered, descendants, ordered[checkpointIndex].checkpoint, deltaCommits.map { it.delta!! }, needsCheckpoint)
    }

    companion object {
        fun historicalPlan() = GraphPlan(GraphStatus.HISTORICAL)
        fun discover(objects: List<GraphObject>, accountId: String, lineageId: String): BackupGraph {
            BackupGraphProtocol.id(accountId); BackupGraphProtocol.id(lineageId)
            check(objects.size <= 100_000) { "Graph inventory exceeds v1 limit." }
            val issues = mutableListOf<GraphIssue>()
            val parsed = linkedMapOf<String, BackupGraphCommit>()
            val receipts = mutableMapOf<String, GraphObject>()
            val physicalCommits = mutableMapOf<String, String>()
            val bytesByCommit = mutableMapOf<String, ByteArray>()
            objects.forEach { obj ->
                try {
                    val commit = BackupGraphProtocol.parse(obj)
                    check(commit.accountId == accountId && commit.lineageId == lineageId) { "Foreign graph account or lineage." }
                    val previous = bytesByCommit.putIfAbsent(commit.commitId, obj.bytes)
                    check(previous == null || previous.contentEquals(obj.bytes)) { "Commit UUID resolves to conflicting bytes." }
                    val physical = receipts.putIfAbsent(obj.objectRef.cloudFileId, obj)
                    check(physical == null || physical.bytes.contentEquals(obj.bytes)) { "Physical object has conflicting bytes." }
                    physicalCommits[obj.objectRef.cloudFileId] = commit.commitId
                    parsed.putIfAbsent(commit.commitId, commit)
                } catch (error: Exception) {
                    issues += GraphIssue(if (error is BackupGraphProtocol.Unsupported) GraphStatus.UNSUPPORTED else GraphStatus.CORRUPT, obj.objectRef.cloudFileId, error.message ?: "Invalid commit")
                }
            }
            val children = mutableMapOf<String, MutableList<String>>()
            parsed.values.forEach { c -> c.parents.singleOrNull()?.let { children.getOrPut(it.commitId) { mutableListOf() }.add(c.commitId) } }
            val queue = ArrayDeque<String>()
            parsed.values.filter { it.parents.isEmpty() }.forEach { queue.add(it.commitId) }
            val valid = linkedMapOf<String, BackupGraphCommit>()
            val visited = mutableSetOf<String>()
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst(); val c = parsed.getValue(id); visited.add(id)
                try {
                    c.parents.singleOrNull()?.let { p ->
                        val parent = valid[p.commitId] ?: error("Unusable graph parent.")
                        val receipt = receipts[p.objectRef.cloudFileId] ?: error("Missing parent physical object.")
                        check(receipt.objectRef == p.objectRef && physicalCommits[p.objectRef.cloudFileId] == p.commitId) { "Parent byte reference differs." }
                        check(c.requiredReaders.containsAll(parent.requiredReaders)) { "Reader capability cannot be downgraded along ancestry." }
                        if (c.kind == "delta") check(c.checkpoint == parent.checkpoint && c.delta!!.parentId == parent.deltaHead) { "Wrong checkpoint or delta parent." }
                        else check(c.checkpoint != parent.checkpoint) { "Checkpoint replacement must be new." }
                    }
                    valid[id] = c
                } catch (error: Exception) { issues += GraphIssue(GraphStatus.CORRUPT, id, error.message ?: "Invalid ancestry") }
                children[id]?.forEach(queue::add)
            }
            parsed.values.filter { it.commitId !in visited }.forEach { c ->
                val parent = c.parents.single().commitId
                issues += GraphIssue(if (parent !in parsed) GraphStatus.MISSING_ANCESTRY else GraphStatus.CORRUPT, c.commitId, "Missing or cyclic ancestry.")
            }
            val parentIds = valid.values.mapNotNull { it.parents.singleOrNull()?.commitId }.toSet()
            return BackupGraph(valid.toMap(), valid.values.filter { it.parents.isEmpty() }.map { it.commitId }.sorted(),
                valid.keys.filter { it !in parentIds }.sorted(), issues.toList())
        }
    }
}

/** Pure reconstruction adapter. No database writes, network, fallback bytes or publication. */
internal object BackupGraphReconstruction {
    fun read(graph: BackupGraph, load: (String) -> ByteArray): ReconstructedBackup {
        val plan = graph.plan()
        check(plan.status == GraphStatus.SINGLE_TIP) { "Graph restore blocked: ${plan.status}" }
        val cp = plan.checkpoint!!
        val checkpointBytes = load(cp.objectRef.cloudFileId)
        BackupGraphProtocol.verify(cp.objectRef, checkpointBytes)
        val manifest = JSONObject(BackupGraphProtocol.utf8(checkpointBytes))
        val schemaVersion = manifest.get("schemaVersion")
        check(schemaVersion is Number && schemaVersion.toDouble() == 1.0 && manifest.getString("storage") == "google-drive-api" && !manifest.has(IncrementalBackupField)) { "Graph checkpoint must be a full historical manifest." }
        val files = linkedMapOf<String, String>()
        val binaries = mutableListOf<BackupBinaryDescriptor>()
        val entries = manifest.getJSONArray("entries")
        check(entries.length() in 1..100_000) { "Checkpoint has no valid inventory." }
        val objectIdentities = mutableMapOf<String, Pair<String, Long>>()
        fun remember(ref: GraphObjectRef) {
            val previous = objectIdentities.putIfAbsent(ref.cloudFileId, ref.sha256 to ref.size)
            check(previous == null || previous == ref.sha256 to ref.size) { "Immutable object identity conflict." }
        }
        remember(cp.objectRef)
        for (index in 0 until entries.length()) {
            val entry = entries.getJSONObject(index)
            val ref = GraphObjectRef(entry.getString("cloudFileId"), entry.getString("sha256"), entry.getLong("size"))
            BackupGraphProtocol.reference(ref, 9007199254740991, entry.getString("kind") == "file"); remember(ref)
            when (entry.getString("kind")) {
                "metadata" -> {
                    val name = entry.getString("fileName")
                    check(name in BackupRecordKeys || name == "settings.json" || name == "manifest.json") { "Unknown checkpoint metadata group." }
                    val bytes = load(ref.cloudFileId); BackupGraphProtocol.verify(ref, bytes)
                    check(files.put(name, BackupGraphProtocol.utf8(bytes)) == null) { "Duplicate checkpoint metadata." }
                }
                "file" -> {
                    val path = entry.getString("backupEntry")
                    check(path.startsWith("files/")) { "Invalid checkpoint binary path." }
                    binaries += BackupBinaryDescriptor(path.removePrefix("files/"), ref.cloudFileId, ref.sha256, ref.size).validate()
                }
                else -> error("Invalid checkpoint entry kind.")
            }
        }
        val deltaCommits = plan.commits.mapNotNull { c -> c.delta?.let { it.objectRef.cloudFileId to c } }.toMap()
        val deltaDescriptors = plan.deltas.map { d ->
            remember(d.objectRef)
            val bytes = load(d.objectRef.cloudFileId); BackupGraphProtocol.verify(d.objectRef, bytes)
            val value = JSONObject(BackupGraphProtocol.utf8(bytes))
            IncrementalBackupFormat.parseChanges(value)
            val c = deltaCommits.getValue(d.objectRef.cloudFileId)
            check(value.getInt("version") != 2 || BackupBinaryReaderCapability in c.requiredReaders) { "Hidden binary capability." }
            parseBackupBinaries(value, IncrementalBackupFormat.parseChanges(value)).forEach { b -> remember(GraphObjectRef(b.cloudFileId, b.sha256, b.size)) }
            JSONObject().put("deltaId", d.deltaId).put("parentId", d.parentId).put("cloudFileId", d.objectRef.cloudFileId).put("sha256", d.objectRef.sha256).put("size", d.objectRef.size)
        }
        val extension = JSONObject().put("version", 2).put("requiredReader", BackupBinaryReaderCapability)
            .put("checkpointId", cp.checkpointId).put("headId", plan.commits.last().deltaHead).put("deltas", JSONArray(deltaDescriptors))
        val result = IncrementalBackupFormat.reconstruct(files, extension, binaries) { load(it.getString("cloudFileId")) }
        result.binaries!!.forEach { it.verify(load(it.cloudFileId)) }
        return result
    }
}
