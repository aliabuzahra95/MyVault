package com.myvault.app.widget.note

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteWidgetContractTest {
    @Test
    fun manifestRegistersBothUserFacingWidgetsAndConfiguration() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".widget.note.NoteWidgetProvider"))
        assertTrue(manifest.contains(".widget.note.QuickNoteWidgetProvider"))
        assertTrue(manifest.contains(".widget.note.NoteWidgetConfigActivity"))
        assertTrue(File("src/main/res/xml/note_widget_info.xml").readText().contains("android:configure"))
        val quickInfo = File("src/main/res/xml/quick_note_widget_info.xml").readText()
        assertTrue(quickInfo.contains("widget_quick_note_wide"))
        assertTrue(quickInfo.contains("WidgetAppearanceActivity"))
        assertTrue(quickInfo.contains("reconfigurable|configuration_optional"))
        assertTrue(manifest.contains(".widget.WidgetAppearanceActivity"))
        assertTrue(manifest.contains("android:exported=\"true\""))
    }

    @Test
    fun quickNoteLayoutsAreCreationOnlyAndContainNoVisibleSettingsGear() {
        val layouts = listOf(
            "widget_quick_note_compact.xml",
            "widget_quick_note_wide.xml",
            "manual_light_widget_quick_note_compact.xml",
            "manual_dark_widget_quick_note_compact.xml",
            "manual_light_widget_quick_note_wide.xml",
            "manual_dark_widget_quick_note_wide.xml",
        ).map { File("src/main/res/layout/$it").readText() }
        val provider = File("src/main/java/com/myvault/app/widget/note/QuickNoteWidgetProvider.kt").readText()

        assertTrue(layouts.all { !it.contains("quick_note_widget_settings") })
        assertTrue(layouts.all { !it.contains("ic_widget_settings") })
        assertTrue(provider.contains("R.id.quick_note_widget_root"))
        assertTrue(!provider.contains("R.id.quick_note_widget_settings"))
        assertTrue(layouts[0].contains("layout_height=\"36dp\""))
        assertTrue(!layouts[0].contains("quick_note_widget_label"))
        assertTrue(layouts[1].contains("quick_note_widget_label"))
    }

    @Test
    fun noteViewerUsesScrollableCollectionAndExplicitEntryActions() {
        val provider = File("src/main/java/com/myvault/app/widget/note/NoteWidgetProvider.kt").readText()
        val service = File("src/main/java/com/myvault/app/widget/note/NoteWidgetRemoteViewsService.kt").readText()
        val activity = File("src/main/java/com/myvault/app/MainActivity.kt").readText()
        assertTrue(provider.contains("setRemoteAdapter"))
        assertTrue(service.contains("ACTION_OPEN_NOTE"))
        assertTrue(activity.contains("ACTION_QUICK_CREATE_NOTE"))
        assertTrue(activity.contains("noteRepository.createNote(folderId = null)"))
        assertTrue(activity.contains("pendingOpenNoteQuickFocus"))
    }
}
