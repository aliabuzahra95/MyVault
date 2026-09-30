package com.myvault.app.data.repository

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

internal const val GraphFolderMime = "application/vnd.google-apps.folder"
internal data class GraphDriveFile(val id: String, val name: String, val mimeType: String,
    val parents: List<String>, val trashed: Boolean = false, val size: Long? = null, val sha256: String? = null)

/** The production adapter must check the active Google account before and after each call. */
internal interface GraphDriveApi {
    suspend fun assertAccount(account: String, driveAccountId: String)
    suspend fun roots(name: String): List<GraphDriveFile>
    suspend fun children(parent: String): List<GraphDriveFile>
    suspend fun metadata(id: String): GraphDriveFile?
    suspend fun download(id: String): InputStream
    suspend fun reserveIds(count: Int): List<String>
    suspend fun createFolder(id: String, name: String, parent: String?)
    suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File)
}

internal data class GraphDriveLayout(val rootId: String, val lineageId: String, val folders: Map<String, String>) {
    fun validate() {
        BackupGraphProtocol.id(rootId); BackupGraphProtocol.id(lineageId)
        check(folders.keys == BackupGraphDirectories.toSet())
        val ids = listOf(rootId) + folders.values
        check(ids.distinct().size == ids.size); ids.forEach(BackupGraphProtocol::id)
    }
    fun encode(): String = JSONObject().put("rootId", rootId).put("lineageId", lineageId)
        .put("folders", JSONObject().also { json -> BackupGraphDirectories.forEach { json.put(it, folders.getValue(it)) } }).toString()
    companion object {
        fun parse(json: JSONObject): GraphDriveLayout = GraphDriveLayout(json.getString("rootId"), json.getString("lineageId"),
            BackupGraphDirectories.associateWith { json.getJSONObject("folders").getString(it) }).also { it.validate() }
    }
}

/** No mutable head, updates or deletions. Fresh listings, not cached bytes, determine graph membership. */
internal class AccountBoundGraphStore(
    override val context: GraphWriterContext, private val api: GraphDriveApi, val layout: GraphDriveLayout,
) : DisposableGraphObjectStore {
    init { context.validate(); layout.validate(); check(context.lineageId == layout.lineageId) }
    override val namespaceProof: String get() = layout.encode()
    private val verified = mutableMapOf<String, GraphObject>()
    private suspend fun account() = api.assertAccount(context.accountScope, context.driveAccountId)
    suspend fun verifyLayout() {
        account()
        val roots = api.roots(BackupGraphNamespace)
        check(roots.size == 1 && roots.single().id == layout.rootId && !roots.single().trashed && roots.single().mimeType == GraphFolderMime) {
            "The graph namespace is missing or ambiguous. No existing backup was changed."
        }
        val children = api.children(layout.rootId)
        for (name in BackupGraphDirectories) {
            val matching = children.filter { it.name == name }
            check(matching.size == 1 && matching.single().id == layout.folders.getValue(name) &&
                matching.single().mimeType == GraphFolderMime && !matching.single().trashed &&
                matching.single().parents == listOf(layout.rootId)) { "Graph directories changed or are ambiguous." }
        }
        account()
    }
    override suspend fun commits(): List<GraphObject> {
        verifyLayout()
        val files = api.children(layout.folders.getValue("commits"))
        check(files.map { it.id }.distinct().size == files.size)
        val result = files.map { file ->
            check(!file.trashed && file.mimeType != GraphFolderMime && file.parents == listOf(layout.folders.getValue("commits")))
            val ref = GraphObjectRef(file.id, file.sha256 ?: error("Graph commit has no verified checksum."), file.size ?: error("Graph commit has no size."))
            BackupGraphProtocol.reference(ref, 65536)
            val cached = verified[file.id]
            val bytes = if (cached?.objectRef == ref) cached.bytes.copyOf() else {
                account()
                api.download(file.id).use { input ->
                    input.readBytesLimited(65536).also { BackupGraphProtocol.verify(ref, it) }
                }.also { account() }
            }
            GraphObject(ref, bytes).also { verified[file.id] = GraphObject(ref, bytes.copyOf()) }
        }
        verified.keys.retainAll(files.map { it.id }.toSet())
        account(); return result
    }
    override suspend fun reserveId(): String = reserveIds(1).single()
    override suspend fun reserveIds(count: Int): List<String> {
        check(count > 0); account()
        val result = mutableListOf<String>()
        while (result.size < count) {
            val wanted = minOf(1000, count - result.size)
            val ids = api.reserveIds(wanted)
            check(ids.size == wanted); ids.forEach(BackupGraphProtocol::id); result += ids
        }
        check(result.distinct().size == count); account(); return result
    }
    override suspend fun read(objectId: String): InputStream? {
        BackupGraphProtocol.id(objectId); account()
        val file = api.metadata(objectId) ?: return null
        check(file.id == objectId && !file.trashed && file.mimeType != GraphFolderMime &&
            file.parents.size == 1 && file.parents.single() in layout.folders.values) { "Graph object is outside the enrolled namespace." }
        account(); return api.download(objectId)
    }
    override suspend fun create(objectId: String, role: String, file: File) {
        BackupGraphProtocol.id(objectId); account()
        val folder = when (role) {
            "BINARY" -> "binaries"; "COMMIT" -> "commits"; "DELTA" -> "deltas"
            "CHECKPOINT", "METADATA" -> "checkpoints"
            else -> error("Unknown immutable graph object role.")
        }
        api.createObject(objectId, "${role.lowercase()}-$objectId", layout.folders.getValue(folder),
            if (role == "BINARY") "application/octet-stream" else "application/json", file)
        // A 409 or uncertain create never permits an update. Writer readback proves exact identity.
        account()
    }
}

internal fun InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer); if (count < 0) break
        check(output.size() + count <= limit) { "Graph commit exceeds its byte limit." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

/** Resolve an existing immutable namespace without enrolling it or changing remote/local trust. */
internal suspend fun discoverGraphLayout(api: GraphDriveApi, account: String, driveId: String): GraphDriveLayout? {
    api.assertAccount(account, driveId)
    val roots = api.roots(BackupGraphNamespace)
    check(roots.size <= 1) { "Multiple graph roots require reconciliation." }
    val root = roots.singleOrNull() ?: return null
    check(root.mimeType == GraphFolderMime && !root.trashed)
    val children = api.children(root.id)
    val folders = BackupGraphDirectories.associateWith { name ->
        children.filter { it.name == name }.singleOrNull()?.also {
            check(!it.trashed && it.mimeType == GraphFolderMime && it.parents == listOf(root.id))
        }?.id ?: error("Graph namespace is incomplete or ambiguous.")
    }
    val files = api.children(folders.getValue("commits"))
    check(files.isNotEmpty()) { "Uncommitted graph namespace requires its original local enrollment intent." }
    val lineages = files.map { file ->
        check(file.parents == listOf(folders.getValue("commits")) && !file.trashed && file.mimeType != GraphFolderMime)
        val ref = GraphObjectRef(file.id, file.sha256 ?: error("Missing checksum."), file.size ?: error("Missing size."))
        BackupGraphProtocol.reference(ref, 65536)
        val bytes = api.download(file.id).use { it.readBytesLimited(65536) }
        val commit = BackupGraphProtocol.parse(GraphObject(ref, bytes))
        check(commit.accountId == driveId) { "Graph belongs to another Drive account." }
        commit.lineageId
    }.distinct()
    check(lineages.size == 1) { "Multiple graph lineages require reconciliation." }
    api.assertAccount(account, driveId)
    return GraphDriveLayout(root.id, lineages.single(), folders).also { it.validate() }
}

/** Local enrollment intent only; never an authoritative remote graph head. Persist before any folder create. */
internal class GraphNamespaceEnrollment(private val intentFile: File, private val api: GraphDriveApi) {
    suspend fun load(account: String, driveId: String): GraphDriveLayout? {
        api.assertAccount(account, driveId)
        if (!intentFile.exists()) return null
        val json = JSONObject(intentFile.readText())
        check(json.getInt("version") == 1 && json.getString("account") == account && json.getString("driveAccountId") == driveId)
        return GraphDriveLayout.parse(json.getJSONObject("layout"))
    }
    suspend fun enroll(account: String, driveId: String): GraphDriveLayout {
        api.assertAccount(account, driveId)
        var layout = load(account, driveId)
        if (layout == null) {
            check(api.roots(BackupGraphNamespace).isEmpty()) { "Existing graph requires verified adoption, not a new root." }
            val ids = api.reserveIds(5)
            check(ids.size == 5 && ids.distinct().size == 5)
            layout = GraphDriveLayout(ids.first(), UUID.randomUUID().toString(), BackupGraphDirectories.mapIndexed { i, name -> name to ids[i + 1] }.toMap())
            layout.validate()
            val bytes = JSONObject().put("version", 1).put("account", account).put("driveAccountId", driveId)
                .put("layout", JSONObject(layout.encode())).toString().toByteArray(Charsets.UTF_8)
            check(intentFile.parentFile!!.mkdirs() || intentFile.parentFile!!.isDirectory)
            val temporary = File(intentFile.parentFile, "${intentFile.name}.${UUID.randomUUID()}.tmp")
            FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
            check(!intentFile.exists() && temporary.renameTo(intentFile)) { "Namespace intent could not be persisted safely." }
        }
        val enrolled = layout
        val roots = api.roots(BackupGraphNamespace)
        check(roots.all { it.id == enrolled.rootId } && roots.size <= 1) { "Another graph root appeared; reconciliation is required." }
        ensureFolder(enrolled.rootId, BackupGraphNamespace, null)
        for (name in BackupGraphDirectories) {
            val children = api.children(enrolled.rootId).filter { it.name == name }
            check(children.all { it.id == enrolled.folders.getValue(name) } && children.size <= 1)
            ensureFolder(enrolled.folders.getValue(name), name, enrolled.rootId)
        }
        AccountBoundGraphStore(GraphWriterContext(account, driveId, enrolled.lineageId), api, enrolled).verifyLayout()
        return enrolled
    }
    private suspend fun ensureFolder(id: String, name: String, parent: String?) {
        val prior = api.metadata(id)
        if (prior == null) api.createFolder(id, name, parent)
        val verified = api.metadata(id) ?: error("Folder creation could not be verified; retry the same enrollment.")
        check(verified.id == id && verified.name == name && verified.mimeType == GraphFolderMime && !verified.trashed &&
            (parent == null || verified.parents == listOf(parent))) { "Intended namespace ID resolves to a different folder." }
    }
}
