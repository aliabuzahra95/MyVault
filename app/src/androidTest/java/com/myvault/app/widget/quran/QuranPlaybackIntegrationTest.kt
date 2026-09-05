package com.myvault.app.widget.quran

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.quran.audio.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuranPlaybackIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun waitFor(message: String, timeout: Long = 15_000, condition: () -> Boolean) {
        val until = android.os.SystemClock.elapsedRealtime() + timeout
        while (!condition() && android.os.SystemClock.elapsedRealtime() < until) Thread.sleep(50)
        assertTrue(message, condition())
    }

    @Test fun realFullTrackKeepsOneMediaPlayerThroughModesSeekAndBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = quranWidgetPlayback(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            main { controller.requestPlay(1, 1, AudioReciterUiModel(7, "Mishary al-Afasy")) }
            waitFor("Full track prepared: ${controller.state.value}", 90_000) { controller.state.value.isPlaying }
            assertTrue(controller.state.value.synchronized)
            val id = controller.state.value.recordingId
            val field = QuranPlaybackController::class.java.getDeclaredField("player").apply { isAccessible = true }
            val player = field.get(controller) as QuranAudioPlayer
            val media = QuranAudioPlayer::class.java.getDeclaredField("mediaPlayer").apply { isAccessible = true }
            val instance = media.get(player)
            waitFor("Beginning playing") { controller.state.value.positionMs >= 2000 }
            val before = controller.state.value.positionMs
            main { controller.setMode(QuranListeningMode.ContinueSurah) }
            assertTrue(controller.state.value.positionMs >= before)
            waitFor("Ayah 3 from real media position") { controller.state.value.verseKey == "1:3" }
            assertSame(instance, media.get(player))
            main { controller.pause() }
            val paused = controller.state.value.positionMs
            Thread.sleep(650)
            assertEquals(QuranPlaybackStatus.Paused, controller.state.value.status)
            assertTrue(kotlin.math.abs(controller.state.value.positionMs - paused) < 150)
            main { controller.setMode(QuranListeningMode.ThisAyah) }
            assertEquals(QuranPlaybackStatus.Paused, controller.state.value.status)
            main { controller.seek(25_000) }
            Thread.sleep(350)
            main { controller.resume() }
            waitFor("Single stops at ayah 5") { controller.state.value.status == QuranPlaybackStatus.Paused && controller.state.value.verseKey == "1:5" }
            assertTrue(kotlin.math.abs(controller.state.value.positionMs - 27_660L) < 250)
            main { controller.setMode(QuranListeningMode.ContinueSurah) }
            waitFor("Continue resumes next ayah") { controller.state.value.isPlaying && controller.state.value.verseKey == "1:6" }
            assertSame(instance, media.get(player))
            assertEquals(id, controller.state.value.recordingId)
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            waitFor("Background continues to last ayah") { controller.state.value.verseKey == "1:7" }
            main { controller.speed(1.5f); controller.seek(43_000) }
            waitFor("Surah ends without looping") { controller.state.value.status == QuranPlaybackStatus.Ended }
            Thread.sleep(500)
            assertFalse(player.isPlaying())
        } finally {
            main { controller.stop() }
            scenario.close()
        }
    }

    @Test fun unsupportedReciterIsNotSilentlyReplacedAndStalePreparationCannotRestart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = quranWidgetPlayback(context)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            main { controller.requestPlay(1, 1, AudioReciterUiModel(3, "Abdur-Rahman as-Sudais"), QuranListeningMode.ContinueSurah) }
            waitFor("Unsupported recitation is explicit") { controller.state.value.status == QuranPlaybackStatus.Error }
            assertTrue(controller.state.value.message.orEmpty().contains("unavailable"))
            main {
                controller.requestPlay(2, 255, AudioReciterUiModel(7, "Mishary al-Afasy"))
                controller.requestPlay(1, 4, AudioReciterUiModel(7, "Mishary al-Afasy"))
            }
            waitFor("Latest request wins", 90_000) { controller.state.value.isPlaying && controller.state.value.verseKey == "1:4" }
            Thread.sleep(500)
            assertEquals(1, controller.state.value.surah)
        } finally { main { controller.stop() }; scenario.close() }
    }
}
