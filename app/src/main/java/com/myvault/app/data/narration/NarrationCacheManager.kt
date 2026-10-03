package com.myvault.app.data.narration

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NarrationCacheManager private constructor(
    private val rootDirectory: () -> File,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        { File(context.filesDir, "note_narration_cache") },
    )

    internal constructor(rootDir: File) : this({ rootDir })

    private val rootDir: File by lazy { rootDirectory().apply { mkdirs() } }
    private val generationLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withChunkLock(cacheKey: String, action: suspend () -> T): T =
        generationLocks.computeIfAbsent(cacheKey) { Mutex() }.withLock { action() }

    fun contentHash(text: String): String = sha256(text.toByteArray())

    fun renditionChunkKey(noteId: String, chunkText: String, model: String, voice: String): String =
        cacheKey(noteId, contentHash("$ChunkCacheVersion:$chunkText"), model, voice, 1f)

    @Synchronized
    fun restoreTimeline(cacheKey: String, plans: List<NarrationChunkPlan>,
        noteId: String? = null, model: String = "", voice: String = ""): List<NarrationChunkPlan> {
        val durations = readDurations(cacheKey).toMutableMap()
        // Existing audio predates the duration ledger. Its persisted cues retain measured timing.
        if (noteId != null) {
            plans.filter { it.index !in durations }.forEach { plan ->
                val chunkKey = renditionChunkKey(noteId, plan.text, model, voice)
                val legacyFile = chunkFile(cacheKey, plan.index)
                val chunkFile = chunkFile(chunkKey, 0)
                val cues = when {
                    isValidChunk(legacyFile, MinValidAudioBytes) -> readChunkCues(cacheKey, plan.index)
                    isValidChunk(chunkFile, MinValidAudioBytes) -> readChunkCues(chunkKey, 0)
                    else -> emptyList()
                }
                cues.maxOfOrNull { it.endMs }?.takeIf { it > 0L }?.let { durations[plan.index] = it }
            }
        }
        return NarrationTimeline.rebuild(plans, durations)
    }

    @Synchronized
    fun recordDuration(cacheKey: String, chunkIndex: Int, file: File): Map<Int, Long> {
        val durations = readDurations(cacheKey).toMutableMap()
        val duration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                retriever.release()
            }
        }.getOrNull()
        if (duration != null && duration > 0L) {
            return recordKnownDuration(cacheKey, chunkIndex, duration)
        }
        return durations
    }

    @Synchronized
    internal fun recordKnownDuration(cacheKey: String, chunkIndex: Int, durationMs: Long): Map<Int, Long> {
        val durations = readDurations(cacheKey).toMutableMap()
        if (durationMs <= 0L) return durations
        durations[chunkIndex] = durationMs
        val target = File(sessionDir(cacheKey), "durations.json")
        val temp = File(target.parentFile, "durations.json.tmp")
        temp.writeText(JSONObject(durations.mapKeys { it.key.toString() }).toString())
        check(temp.renameTo(target)) { "Could not save narration timing." }
        return durations
    }

    private fun readDurations(cacheKey: String): Map<Int, Long> = runCatching {
        val json = JSONObject(File(sessionDir(cacheKey), "durations.json").readText())
        json.keys().asSequence().mapNotNull { key ->
            val index = key.toIntOrNull() ?: return@mapNotNull null
            json.optLong(key).takeIf { it > 0L }?.let { index to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    @Suppress("UNUSED_PARAMETER")
    fun cacheKey(noteId: String, contentHash: String, model: String, voice: String, speed: Float): String {
        // Synthesis bytes do not change with ExoPlayer playback speed. Keep the legacy 1.0
        // namespace so existing caches remain valid while speed changes never trigger TTS.
        val speedKey = "1_0"
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
            val totalChunks = json.optInt("totalChunks", 0)
            val complete = json.optBoolean("isComplete", false)
            val filesJson = json.optJSONArray("files") ?: return null
            if (!complete && totalChunks > 0 && filesJson.length() < totalChunks) return null
            val files = buildList {
                for (index in 0 until filesJson.length()) {
                    val file = File(dir, filesJson.getString(index))
                    if (!isValidChunk(file, MinValidAudioBytes)) {
                        logCache(false, noteId, index, model, voice)
                        return null
                    }
                    logCache(true, noteId, index, model, voice)
                    add(file)
                }
            }
            if (files.isEmpty()) return null
            val cues = json.optJSONArray("cues").toNarrationCues()
            if (!complete && totalChunks > 0 && files.size >= totalChunks) {
                File(dir, "manifest.json").writeText(json.put("isComplete", true).toString())
            }
            NarrationSession(cacheKey, noteId, noteTitle, model, voice, speed, contentHash, files, cues)
        }.getOrNull()
    }

    fun cachedChunkPrefix(
        cacheKey: String,
        totalChunks: Int,
        noteId: String,
        model: String,
        voice: String,
        extension: String = "mp3",
        minimumBytes: Long = MinValidAudioBytes,
        requiredSidecarSuffix: String? = null,
    ): List<File> {
        val dir = sessionDir(cacheKey)
        return buildList {
            for (index in 0 until totalChunks) {
                val stem = "chunk_${index.toString().padStart(3, '0')}"
                val file = File(dir, "$stem.$extension")
                val sidecarValid = requiredSidecarSuffix == null || File(dir, "$stem$requiredSidecarSuffix").exists()
                val hit = isValidChunk(file, minimumBytes) && sidecarValid
                logCache(hit, noteId, index, model, voice)
                if (!hit) break
                add(file)
            }
        }
    }

    fun cachedChunkOrNull(
        cacheKey: String,
        index: Int,
        noteId: String,
        model: String,
        voice: String,
        extension: String = "mp3",
        minimumBytes: Long = MinValidAudioBytes,
        requiredSidecarSuffix: String? = null,
    ): File? {
        val stem = "chunk_${index.toString().padStart(3, '0')}"
        val dir = sessionDir(cacheKey)
        val file = File(dir, "$stem.$extension")
        val sidecarValid = requiredSidecarSuffix == null || File(dir, "$stem$requiredSidecarSuffix").exists()
        val hit = isValidChunk(file, minimumBytes) && sidecarValid
        logCache(hit, noteId, index, model, voice)
        return file.takeIf { hit }
    }

    fun sessionDir(cacheKey: String): File = File(rootDir, cacheKey).apply { mkdirs() }

    fun chunkFile(cacheKey: String, index: Int): File = File(sessionDir(cacheKey), "chunk_${index.toString().padStart(3, '0')}.mp3")

    fun chunkCueFile(cacheKey: String, index: Int): File =
        File(sessionDir(cacheKey), "chunk_${index.toString().padStart(3, '0')}_cues.json")

    fun readChunkCues(cacheKey: String, index: Int): List<NarrationCue> = runCatching {
        JSONArray(chunkCueFile(cacheKey, index).readText()).toNarrationCues()
    }.getOrDefault(emptyList())

    fun writeChunkCues(cacheKey: String, index: Int, cues: List<NarrationCue>) {
        val json = JSONArray().apply {
            cues.forEach { cue ->
                put(
                    JSONObject()
                        .put("chunkIndex", cue.chunkIndex)
                        .put("startMs", cue.startMs)
                        .put("endMs", cue.endMs)
                        .put("textStart", cue.textStart)
                        .put("textEnd", cue.textEnd)
                        .put("text", cue.text)
                        .put("displayText", cue.displayText),
                )
            }
        }
        chunkCueFile(cacheKey, index).writeText(json.toString())
    }

    fun writeManifest(session: NarrationSession, isComplete: Boolean = true, totalChunks: Int = session.files.size) {
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
            .put("cues", JSONArray().apply {
                session.cues.forEach { cue ->
                    put(
                        JSONObject()
                            .put("chunkIndex", cue.chunkIndex)
                            .put("startMs", cue.startMs)
                            .put("endMs", cue.endMs)
                            .put("textStart", cue.textStart)
                            .put("textEnd", cue.textEnd)
                            .put("text", cue.text)
                            .put("displayText", cue.displayText),
                    )
                }
            })
            .put("isComplete", isComplete)
            .put("totalChunks", totalChunks)
            .put("updatedAt", System.currentTimeMillis())
        File(dir, "manifest.json").writeText(json.toString())
    }

    fun clearSession(cacheKey: String) {
        File(rootDir, cacheKey).deleteRecursively()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isValidChunk(file: File, minimumBytes: Long): Boolean =
        file.exists() && file.length() >= minimumBytes

    private fun logCache(hit: Boolean, noteId: String, index: Int, model: String, voice: String) {
        runCatching {
            Log.d(
                CacheLogTag,
                "CACHE ${if (hit) "HIT" else "MISS"} source=${noteId.take(96)} " +
                    "chunk=${index + 1} provider=${NarrationProvider.fromModel(model).storedValue} " +
                    "model=$model voice=$voice",
            )
        }
    }

    private companion object {
        const val ChunkCacheVersion = "chunk-v1"
    }
}

private fun JSONArray?.toNarrationCues(): List<NarrationCue> = buildList {
    val array = this@toNarrationCues ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        add(
            NarrationCue(
                chunkIndex = item.optInt("chunkIndex"),
                startMs = item.optLong("startMs"),
                endMs = item.optLong("endMs"),
                textStart = item.optInt("textStart"),
                textEnd = item.optInt("textEnd"),
                text = item.optString("text"),
                displayText = item.optString("displayText", item.optString("text")),
            ),
        )
    }
}

private fun String.safeFilePart(): String = replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)
private const val MinValidAudioBytes = 256L
private const val CacheLogTag = "MyVaultNarrationCache"
