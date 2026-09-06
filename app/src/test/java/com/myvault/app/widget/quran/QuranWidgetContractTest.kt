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
        assertTrue(store.contains("\"translation_\$id\""))
        assertTrue(store.contains("\"font_level_\$id\""))
        assertTrue(store.contains("\"tajweed_\$id\""))
        assertTrue(store.contains("\"search_\$id\""))
        assertTrue(store.contains("preferences.edit()"))
    }

    @Test
    fun `widget settings and search use the existing collection architecture`() {
        val manifest = String(Files.readAllBytes(projectRoot.resolve("app/src/main/AndroidManifest.xml")))
        val provider = source("widget/quran/QuranWidgetProvider.kt")
        val factory = source("widget/quran/QuranWidgetRemoteViewsService.kt")

        assertTrue(manifest.contains(".widget.quran.QuranWidgetSearchActivity"))
        assertTrue(provider.contains("QuranWidgetMode.Settings"))
        assertTrue(provider.contains("QuranWidgetContract.ACTION_SHOW_SETTINGS"))
        assertTrue(provider.contains("QuranWidgetContract.EXTRA_SEARCH_QUERY"))
        assertTrue(factory.contains("filteredWidgetSurahs(state.searchQuery)"))
        assertTrue(factory.contains("QuranWidgetDisplaySource"))
        assertTrue(factory.contains("quranWidgetArabicTextSize"))
    }

    @Test
    fun `widget rows expose translation and larger picker typography`() {
        val ayahLayout = resource("layout/widget_quran_ayah_large.xml")
        val surahLayout = resource("layout/widget_quran_surah_row.xml")

        assertTrue(ayahLayout.contains("@+id/quran_widget_translation"))
        assertTrue(ayahLayout.contains("android:fontFamily=\"@font/uthmani_hafs\""))
        assertTrue(ayahLayout.contains("android:includeFontPadding=\"false\""))
        assertTrue(surahLayout.contains("android:includeFontPadding=\"false\""))
        assertTrue(surahLayout.contains("android:textSize=\"15sp\""))
        assertTrue(surahLayout.contains("android:textSize=\"24sp\""))
        assertTrue(surahLayout.contains("android:minHeight=\"62dp\""))
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

    private fun resource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/res/$relativePath")))
}
