package com.myvault.app.widget.quran

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranWidgetContractTest {
    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/AndroidManifest.xml")) }

    @Test
    fun `widget is resizable and registered as a lazy collection`() {
        val manifest = String(Files.readAllBytes(projectRoot.resolve("app/src/main/AndroidManifest.xml")))
        val info = String(Files.readAllBytes(projectRoot.resolve("app/src/main/res/xml/quran_widget_info.xml")))
        val provider = source("widget/quran/QuranWidgetProvider.kt")

        assertTrue(manifest.contains(".widget.quran.QuranWidgetProvider"))
        assertTrue(manifest.contains("android.permission.BIND_REMOTEVIEWS"))
        assertTrue(info.contains("android:resizeMode=\"horizontal|vertical\""))
        assertTrue(info.contains("android:previewLayout=\"@layout/widget_quran_preview\""))
        assertTrue(provider.contains("setRemoteAdapter(R.id.quran_widget_collection"))
        assertTrue(provider.contains("QuranWidgetSizeBucket.ExtraLarge"))
    }

    @Test
    fun `each widget owns selection mode and anchor keys`() {
        val store = source("widget/quran/QuranWidgetStateStore.kt")
        assertTrue(store.contains("\"surah_\$id\""))
        assertTrue(store.contains("\"mode_\$id\""))
        assertTrue(store.contains("\"anchor_\$id\""))
        assertTrue(store.contains("preferences.edit()"))
    }

    @Test
    fun `widget exact location enters the existing Quran route`() {
        val activity = source("MainActivity.kt")
        val navigation = source("ui/navigation/VaultNavHost.kt")
        assertTrue(activity.contains("handleQuranWidgetIntent"))
        assertTrue(activity.contains("pendingWidgetQuranVerseKey = location.verseKey"))
        assertTrue(navigation.contains("pendingQuranVerseKey = verseKey"))
        assertTrue(navigation.contains("selectedIslamicRootMode = VaultRootMode.Quran.name"))
        assertTrue(navigation.contains("navigateToVaultRoot(VaultDestination.Knowledge.route)"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
