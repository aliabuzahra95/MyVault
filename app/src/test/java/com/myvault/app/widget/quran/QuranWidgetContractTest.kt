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
        assertTrue(store.contains("\"reciter_id_\$id\""))
        assertTrue(store.contains("\"reciter_name_\$id\""))
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
        assertTrue(source("widget/quran/QuranWidgetSearchActivity.kt").contains("setSearchQuery"))
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
        assertTrue(ayahLayout.contains("android:fallbackLineSpacing=\"true\""))
        assertTrue(ayahLayout.contains("android:lineSpacingMultiplier=\"1.10\""))
        assertTrue(surahLayout.contains("android:includeFontPadding=\"false\""))
        assertTrue(surahLayout.contains("android:textSize=\"15sp\""))
        assertTrue(surahLayout.contains("android:textSize=\"24sp\""))
        assertTrue(surahLayout.contains("android:minHeight=\"62dp\""))
    }

    @Test
    fun `routine refresh keeps stable adapter identity and does not force a scroll`() {
        val provider = source("widget/quran/QuranWidgetProvider.kt")
        val factory = source("widget/quran/QuranWidgetRemoteViewsService.kt")

        assertTrue(provider.contains("myvault://quran-widget/\$appWidgetId/collection"))
        assertTrue(provider.contains("scrollToAyah: Int? = null"))
        assertTrue(provider.contains("if (state.mode == QuranWidgetMode.Reader && scrollToAyah != null)"))
        assertTrue(factory.contains("override fun hasStableIds(): Boolean = true"))
        assertTrue(factory.contains("it.surahNumber * 1_000L + it.ayahNumber"))
    }

    @Test
    fun `header has play immediately before far-right settings and no open control`() {
        listOf("compact", "medium", "large", "extra_large").forEach { size ->
            val layout = resource("layout/widget_quran_$size.xml")
            val play = layout.indexOf("@+id/quran_widget_play_surah")
            val settings = layout.indexOf("@+id/quran_widget_settings")
            assertTrue("Play must exist before Settings in $size", play >= 0 && play < settings)
            assertTrue("Settings must be the last header action in $size", settings >= 0)
            assertTrue(!layout.contains("quran_widget_open"))
            assertTrue(!layout.contains("quran_widget_audio_controls"))
            assertTrue(!layout.contains("quran_widget_continue"))
            assertTrue(!layout.contains("quran_widget_stop"))
        }
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
