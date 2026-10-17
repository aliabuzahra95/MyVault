package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.BackupGraphBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe process and persistent cache for verified immutable graph commits, parsed manifests, and lineage membership.
 * Persists to disk (graph_commit_cache.json) for instant cold-start and process restart performance.
 */
internal object VerifiedCommitGraphCache {
    private val commitObjects = ConcurrentHashMap<String, GraphObject>()
    private val parsedCommits = ConcurrentHashMap<String, BackupGraphCommit>()
    private val commitIdToCloudFileId = ConcurrentHashMap<String, String>()
    private val lineageCommitFileIds = ConcurrentHashMap<String, MutableSet<String>>()

    private val diskLock = Any()
    @Volatile private var cacheFile: File? = null

    private fun lineageKey(accountScope: String, lineageId: String): String =
        "$accountScope\u0000$lineageId"

    fun init(filesDir: File) {
        initFile(File(filesDir, "graph_commit_cache.json"))
    }

    fun initFile(file: File) {
        synchronized(diskLock) {
            cacheFile = file
            loadFromDiskLocked()
        }
    }

    private fun loadFromDiskLocked() {
        val file = cacheFile ?: return
        if (!file.exists() || !file.isFile) return
        try {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return
            val json = JSONObject(text)
            val lineagesArray = json.optJSONArray("lineages")
            if (lineagesArray != null) {
                for (i in 0 until lineagesArray.length()) {
                    val entry = lineagesArray.getJSONObject(i)
                    val scope = entry.getString("accountScope")
                    val lineage = entry.getString("lineageId")
                    val fileIds = entry.getJSONArray("commitFileIds")
                    val key = lineageKey(scope, lineage)
                    val set = lineageCommitFileIds.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
                    for (j in 0 until fileIds.length()) {
                        set.add(fileIds.getString(j))
                    }
                }
            }
            val idMap = json.optJSONObject("commitIdToCloudFileId")
            if (idMap != null) {
                val keys = idMap.keys()
                while (keys.hasNext()) {
                    val commitId = keys.next()
                    commitIdToCloudFileId[commitId] = idMap.getString(commitId)
                }
            }
            val objectsMap = json.optJSONObject("objects")
            if (objectsMap != null) {
                val keys = objectsMap.keys()
                while (keys.hasNext()) {
                    val fileId = keys.next()
                    val objJson = objectsMap.getJSONObject(fileId)
                    val sha256 = objJson.getString("sha256")
                    val size = objJson.getLong("size")
                    val bytesBase64 = objJson.optString("bytes", "")
                    if (bytesBase64.isNotEmpty()) {
                        val bytes = Base64.getDecoder().decode(bytesBase64)
                        val ref = GraphObjectRef(fileId, sha256, size)
                        val graphObj = GraphObject(ref, bytes)
                        commitObjects[fileId] = graphObj
                        runCatching {
                            val parsed = BackupGraphProtocol.parse(graphObj)
                            parsedCommits[fileId] = parsed
                            commitIdToCloudFileId[parsed.commitId] = fileId
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore corrupted disk cache and allow clean recovery from Google Drive
        }
    }

    fun flushToDisk() {
        val target = cacheFile ?: return
        synchronized(diskLock) {
            try {
                val root = JSONObject()
                root.put("version", 1)

                val lineagesArray = JSONArray()
                lineageCommitFileIds.forEach { (key, fileIds) ->
                    val parts = key.split("\u0000")
                    if (parts.size == 2) {
                        val entry = JSONObject()
                        entry.put("accountScope", parts[0])
                        entry.put("lineageId", parts[1])
                        val arr = JSONArray()
                        fileIds.forEach { arr.put(it) }
                        entry.put("commitFileIds", arr)
                        lineagesArray.put(entry)
                    }
                }
                root.put("lineages", lineagesArray)

                val idMap = JSONObject()
                commitIdToCloudFileId.forEach { (commitId, fileId) ->
                    idMap.put(commitId, fileId)
                }
                root.put("commitIdToCloudFileId", idMap)

                val objectsMap = JSONObject()
                commitObjects.forEach { (fileId, obj) ->
                    val objJson = JSONObject()
                    objJson.put("sha256", obj.objectRef.sha256)
                    objJson.put("size", obj.objectRef.size)
                    objJson.put("bytes", Base64.getEncoder().encodeToString(obj.bytes))
                    objectsMap.put(fileId, objJson)
                }
                root.put("objects", objectsMap)

                val parent = target.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val temp = File(parent ?: target, "${target.name}.tmp")
                temp.writeText(root.toString(), Charsets.UTF_8)
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) {
                        temp.copyTo(target, overwrite = true)
                        temp.delete()
                    }
                }
            } catch (_: Exception) {
                // Non-fatal disk write failure; in-memory cache remains active
            }
        }
    }

    fun getCommitObject(cloudFileId: String): GraphObject? =
        commitObjects[cloudFileId]

    fun putCommitObject(cloudFileId: String, obj: GraphObject) {
        commitObjects[cloudFileId] = obj
    }

    fun getParsedCommit(cloudFileId: String): BackupGraphCommit? =
        parsedCommits[cloudFileId]

    fun putParsedCommit(cloudFileId: String, commit: BackupGraphCommit) {
        parsedCommits[cloudFileId] = commit
        commitIdToCloudFileId[commit.commitId] = cloudFileId
    }

    fun getKnownCommitFileIds(
        accountScope: String,
        lineageId: String,
        binding: BackupGraphBinding? = null,
    ): Set<String> {
        val key = lineageKey(accountScope, lineageId)
        val existing = lineageCommitFileIds[key]
        if (!existing.isNullOrEmpty()) {
            if (binding != null && existing.add(binding.commitFileId)) {
                flushToDisk()
            }
            return existing.toSet()
        }
        val set = ConcurrentHashMap.newKeySet<String>()
        if (binding != null) {
            set.add(binding.commitFileId)
        }
        lineageCommitFileIds[key] = set
        if (binding != null) {
            flushToDisk()
        }
        return set.toSet()
    }

    fun recordLineage(
        accountScope: String,
        lineageId: String,
        graph: BackupGraph,
        targetCommitId: String? = null,
    ) {
        val key = lineageKey(accountScope, lineageId)
        val set = lineageCommitFileIds.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }

        val target = targetCommitId ?: graph.tips.singleOrNull()
        if (target != null && graph.commits.containsKey(target)) {
            var current: String? = target
            while (current != null) {
                val commit = graph.commits[current] ?: break
                val fileId = commitIdToCloudFileId[commit.commitId]
                if (fileId != null) {
                    set.add(fileId)
                }
                val parent = commit.parents.singleOrNull()
                if (parent != null) {
                    set.add(parent.objectRef.cloudFileId)
                    current = parent.commitId
                } else {
                    current = null
                }
            }
        }
        flushToDisk()
    }

    fun addKnownCommitFileId(accountScope: String, lineageId: String, fileId: String) {
        val key = lineageKey(accountScope, lineageId)
        lineageCommitFileIds.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(fileId)
        flushToDisk()
    }

    fun clear(wipeDisk: Boolean = true) {
        synchronized(diskLock) {
            commitObjects.clear()
            parsedCommits.clear()
            commitIdToCloudFileId.clear()
            lineageCommitFileIds.clear()
            if (wipeDisk) {
                cacheFile?.delete()
            }
        }
    }
}
