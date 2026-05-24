package com.myvault.app.data.narration

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationCacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val rootDir: File by lazy { File(context.filesDir, "note_narration_cache").apply { mkdirs() } }

    fun contentHash(text: String): String = sha256(text.toByteArray())

    fun cacheKey(noteId: String, contentHash: String, model: String, voice: String, speed: Float): String {
        val speedKey = speed.toString().replace('.', '_')
        return listOf(noteId.safeFilePart(), model.safeFilePart(), voice.safeFilePart(), speedKey, contentHash.take(16)).joinToString("_")
    }

    fun cachedSessionOrNull(
        cacheKey: String,
        noteId: String,
        noteTitle: String,
        model: String,
        voice: String,
        speed: Float,
        contentHash: String,
    ): NarrationSession? {
        val dir = File(rootDir, cacheKey)
        val manifest = File(dir, "manifest.json")
        if (!manifest.exists()) return null
        return runCatching {
            val json = JSONObject(manifest.readText())
            if (json.optString("contentHash") != contentHash) return null
            if (json.optString("model") != model) return null
            if (json.optString("voice") != voice) return null
            val filesJson = json.optJSONArray("files") ?: return null
            val files = buildList {
                for (index in 0 until filesJson.length()) {
                    val file = File(dir, filesJson.getString(index))
                    if (!file.exists() || file.length() <= 0L) return null
                    add(file)
                }
            }
            if (files.isEmpty()) return null
            NarrationSession(cacheKey, noteId, noteTitle, model, voice, speed, contentHash, files)
        }.getOrNull()
    }

    fun sessionDir(cacheKey: String): File = File(rootDir, cacheKey).apply { mkdirs() }

    fun chunkFile(cacheKey: String, index: Int): File = File(sessionDir(cacheKey), "chunk_${index.toString().padStart(3, '0')}.mp3")

    fun writeManifest(session: NarrationSession) {
        val dir = sessionDir(session.cacheKey)
        val files = JSONArray().apply {
            session.files.forEach { put(it.name) }
        }
        val json = JSONObject()
            .put("noteId", session.noteId)
            .put("noteTitle", session.noteTitle)
            .put("model", session.model)
            .put("voice", session.voice)
            .put("speed", session.speed.toDouble())
            .put("contentHash", session.contentHash)
            .put("files", files)
            .put("createdAt", System.currentTimeMillis())
        File(dir, "manifest.json").writeText(json.toString())
    }

    fun clearSession(cacheKey: String) {
        File(rootDir, cacheKey).deleteRecursively()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private fun String.safeFilePart(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
