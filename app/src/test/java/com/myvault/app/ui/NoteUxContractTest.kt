package com.myvault.app.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NoteUxContractTest {
    private fun source(name: String) = File("src/main/java/com/myvault/app/$name").readText()

    @Test fun noteMenusKeepUsefulActionsAndDeleteLast() {
        for (screen in listOf("ReadingScreen", "EditorScreen")) {
            val source = source("ui/screens/$screen.kt")
            val menu = source.substringAfter("if (moreMenuOpen)").substringBefore("if (noteInfoOpen)")
                .substringBefore("if (listenModeOpen)")
            assertFalse(menu.contains("Knowledge & references"))
            assertFalse(menu.contains("\"Attachments\""))
            for (label in listOf("Listen", "Pin", "Favourite", "Note info", "Version history", "Export", "Structure & Format", "Delete note")) {
                assertTrue("$screen missing $label", menu.contains(label))
            }
            assertTrue(menu.lastIndexOf("Delete note") > menu.lastIndexOf("Structure & Format"))
            if (screen == "ReadingScreen") assertTrue(source.contains("NoteInlineAttachment("))
            else {
                assertFalse(source.contains("NoteInlineAttachment("))
                assertTrue(source.contains("AttachmentSheetRow("))
                assertTrue(source.indexOf("EditorAttachmentPreviewSection(") > source.indexOf("value = safeBodyValue"))
            }
        }
    }

    @Test fun qualitySelectionAndSafeApplyAreWired() {
        val vm = source("ui/viewmodel/NoteViewModel.kt")
        assertFalse(vm.contains("fastForRetainedFormattingAction"))
        assertTrue(vm.contains("sourceBody = body"))
        val editor = source("ui/screens/EditorScreen.kt")
        assertTrue(editor.contains("onPreserveFormattingOriginal(title.text"))
        assertTrue(editor.contains("formattingState.sourceBody != bodyValue.text"))
        assertTrue(editor.contains("remapFormattingNoteLinks"))
        val nav = source("ui/navigation/VaultNavHost.kt")
        assertTrue(nav.contains("onPreserveFormattingOriginal = viewModel::preserveFormattingOriginal"))
        assertTrue(nav.contains("onFormatClick ="))
    }

    @Test fun onlyRequestedDetailRoutesOwnTheirHeader() {
        val nav = source("ui/navigation/VaultNavHost.kt")
        for (setting in listOf("contentStartsInMenuBar =", "menuVisible =")) {
            val routes = nav.substringAfter(setting).substringBefore(")")
            assertTrue(routes.contains("VaultDestination.QuranReflections.route"))
            assertTrue(routes.contains("VaultDestination.AttachmentViewer.route"))
        }
    }
}
