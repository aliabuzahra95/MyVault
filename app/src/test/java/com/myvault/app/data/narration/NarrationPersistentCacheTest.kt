package com.myvault.app.data.narration

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class NarrationPersistentCacheTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun partialChunksSurviveManagerReconstructionAndCanStartBeforeCompletion() {
        val root = newRoot()
        val firstManager = NarrationCacheManager(root)
        val hash = firstManager.contentHash("A substantial unchanged source")
        val key = firstManager.cacheKey("note-1", hash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", 1f)
        val first = firstManager.chunkFile(key, 0).apply { writeBytes(ByteArray(1_024) { 1 }) }
        val second = firstManager.chunkFile(key, 1).apply { writeBytes(ByteArray(1_024) { 2 }) }
        firstManager.writeManifest(
            session = NarrationSession(
                key, "note-1", "Title", GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", 1f, hash,
                listOf(first, second),
            ),
            isComplete = false,
            totalChunks = 4,
        )

        val reconstructedManager = NarrationCacheManager(root)
        val playablePrefix = reconstructedManager.cachedChunkPrefix(
            key, 4, "note-1", GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", minimumBytes = 512L,
        )

        assertEquals(listOf(first.name, second.name), playablePrefix.map(File::getName))
        assertTrue(playablePrefix.size < 4)
    }

    @Test
    fun providerAndVoiceRenditionsRemainSeparateAndReusableOnRoundTrip() {
        val manager = NarrationCacheManager(newRoot())
        val hash = manager.contentHash("Same source")
        val geminiPuck = manager.cacheKey("note-2", hash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", 1f)
        val geminiKore = manager.cacheKey("note-2", hash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Kore", 1f)
        val openAi = manager.cacheKey("note-2", hash, NarrationConfig.MODEL, "cedar", 1f)

        assertNotEquals(geminiPuck, geminiKore)
        assertNotEquals(geminiPuck, openAi)

        val puckFile = manager.chunkFile(geminiPuck, 0).apply { writeBytes(ByteArray(1_024) { 3 }) }
        val openAiFile = manager.chunkFile(openAi, 0).apply { writeBytes(ByteArray(1_024) { 4 }) }

        assertEquals(puckFile, manager.cachedChunkOrNull(geminiPuck, 0, "note-2", GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", minimumBytes = 512L))
        assertEquals(openAiFile, manager.cachedChunkOrNull(openAi, 0, "note-2", NarrationConfig.MODEL, "cedar", minimumBytes = 512L))
        assertTrue(puckFile.exists())
    }

    @Test
    fun playbackSpeedDoesNotChangeSynthesisCacheIdentity() {
        val manager = NarrationCacheManager(newRoot())
        val hash = manager.contentHash("Speed-independent source")
        val keys = listOf(1f, 1.25f, 1.5f, 1.75f).map { speed ->
            manager.cacheKey("note-3", hash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", speed)
        }

        assertEquals(1, keys.distinct().size)
        assertTrue(keys.first().contains("_1_0_"))
    }

    @Test
    fun changedContentCannotReuseEarlierAudio() {
        val manager = NarrationCacheManager(newRoot())
        val oldHash = manager.contentHash("Original source")
        val newHash = manager.contentHash("Edited source")

        assertNotEquals(
            manager.cacheKey("note-4", oldHash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", 1f),
            manager.cacheKey("note-4", newHash, GeminiNarrationConfig.MODEL_FLASH_LITE, "Puck", 1f),
        )
    }

    private fun newRoot(): File = Files.createTempDirectory("myvault-narration-cache-").toFile().also(roots::add)
}
