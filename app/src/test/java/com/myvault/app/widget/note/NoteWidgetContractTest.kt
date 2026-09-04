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
        assertTrue(File("src/main/res/xml/quick_note_widget_info.xml").readText().contains("widget_quick_note_wide"))
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
