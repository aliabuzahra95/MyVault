package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AskAiSurfaceDecommissionContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `conversational Ask AI is not reachable from the navigation graph`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")

        assertFalse(navigation.contains("VaultDestination.AskAi"))
        assertFalse(navigation.contains("AskAiScreen("))
    }

    @Test
    fun `retired conversational presentation files stay removed`() {
        listOf(
            "ui/screens/AskAiScreen.kt",
            "ui/home/HomeInlineAiPanel.kt",
            "ui/home/HomeInlineAiBar.kt",
            "ui/home/HomeAiAttachmentPicker.kt",
            "ui/home/VaultAiComponents.kt",
        ).forEach { relativePath ->
            assertFalse(
                "$relativePath must not be restored with the retired chat UI",
                Files.exists(mainSource(relativePath)),
            )
        }
    }

    @Test
    fun `shared workspace screens do not mount conversational AI`() {
        listOf("HomeScreen.kt", "LibraryScreen.kt", "CoursesScreen.kt").forEach { screen ->
            val source = source("ui/screens/$screen")
            assertFalse("$screen must not mount the chat panel", source.contains("HomeInlineAiPanel("))
            assertFalse("$screen must not create a chat view model", source.contains("HomeInlineAiViewModel"))
        }
    }

    @Test
    fun `retired shared conversational runtime stays removed`() {
        listOf(
            "ai/home/HomeInlineAiClient.kt",
            "ai/home/HomeInlineAiPromptBuilder.kt",
            "ai/home/HomeInlineAiRepository.kt",
            "ai/home/HomeInlineAiState.kt",
            "ai/home/HomeInlineAiViewModel.kt",
        ).forEach { relativePath ->
            assertFalse(
                "$relativePath must not be restored with the retired conversational runtime",
                Files.exists(mainSource(relativePath)),
            )
        }
    }

    @Test
    fun `note surfaces retain formatting and remove chat launchers`() {
        val editor = source("ui/screens/EditorScreen.kt")
        val reading = source("ui/screens/ReadingScreen.kt")

        assertTrue(editor.contains("Structure & Format"))
        assertTrue(editor.contains("Run Structure Only"))
        assertTrue(editor.contains("Run Intelligent Structure"))
        assertFalse(editor.contains("fun AskAiSheet("))
        assertFalse(editor.contains("SelectedTextAiSheet("))
        assertFalse(editor.contains("Ask AI about selection"))
        assertFalse(reading.contains("onAskAiClick"))
    }

    @Test
    fun `retired conversational storage is removed by the compatibility migration`() {
        listOf(
            "ai/home/HomeChatHistoryEntity.kt",
            "ai/home/HomeChatHistoryDao.kt",
            "ai/home/HomeAiStorageModels.kt",
            "ai/home/LibraryAiFileCacheEntity.kt",
            "ai/home/LibraryPdfTextCacheEntity.kt",
            "data/local/entity/AiConversationEntity.kt",
            "data/local/entity/AiMessageEntity.kt",
            "data/local/dao/AiConversationDao.kt",
        ).forEach { relativePath ->
            assertFalse(
                "$relativePath must stay removed with the retired storage runtime",
                Files.exists(mainSource(relativePath)),
            )
        }

        val database = source("data/local/VaultDatabase.kt")
        val backup = source("data/repository/BackupRepository.kt")
        assertTrue(database.contains("version = 27"))
        assertTrue(database.contains("DROP TABLE IF EXISTS ai_messages"))
        assertTrue(database.contains("DROP TABLE IF EXISTS home_chat_history"))
        assertTrue(database.contains("DROP TABLE IF EXISTS library_ai_file_cache"))
        assertTrue(database.contains("DROP TABLE IF EXISTS library_pdf_text_cache"))
        assertFalse(backup.contains("ai_conversations.json"))
        assertFalse(backup.contains("ai_messages.json"))
        assertFalse(backup.contains("home_chat_history.json"))
    }

    @Test
    fun `retained formatting no longer invokes the legacy conversation repository`() {
        val module = source("di/NoteFormattingModule.kt")
        val nativeGenerator = source("data/formatting/NativeNoteFormattingGenerator.kt")
        val viewModel = source("ui/viewmodel/NoteViewModel.kt")

        assertTrue(module.contains("NativeNoteFormattingGenerator"))
        assertFalse(module.contains("LegacyNoteFormattingGenerator"))
        assertFalse(nativeGenerator.contains("NoteAiRepository"))
        assertTrue(viewModel.contains("NoteFormattingSessionStore"))
        assertFalse(viewModel.contains("NoteAiRepository"))
        assertFalse(viewModel.contains("AiConversationRepository"))
        assertFalse(Files.exists(mainSource("data/formatting/LegacyNoteFormattingGenerator.kt")))
        assertFalse(Files.exists(mainSource("data/repository/NoteAiRepository.kt")))
        assertFalse(Files.exists(mainSource("data/repository/AiPromptBuilder.kt")))
        assertFalse(Files.exists(mainSource("data/repository/AiConversationRepository.kt")))
    }

    @Test
    fun `retained Supabase function accepts formatting actions only`() {
        val function = projectSource("supabase/functions/myvault-ai/index.ts")

        assertTrue(function.contains("type FormattingAction = \"organise\" | \"format_note\""))
        assertTrue(function.contains("NoteFormattingSupabaseFunction"))
        assertFalse(function.contains("study_tutor"))
        assertFalse(function.contains("general_ask"))
        assertFalse(function.contains("quick_summary"))
        assertFalse(function.contains("deep_summary"))
        assertFalse(function.contains("deep_analysis"))
        assertFalse(function.contains("explain_note"))
        assertFalse(function.contains("wantsStream"))
    }

    @Test
    fun `dead chat helpers and legacy Home AI model fields stay removed`() {
        val navigation = source("ui/navigation/VaultNavHost.kt")
        val home = source("ui/screens/HomeScreen.kt")
        val library = source("ui/screens/LibraryScreen.kt")
        val courses = source("ui/screens/CoursesScreen.kt")
        val activityFeed = source("ui/viewmodel/PdfActivityFeedViewModel.kt")
        val gradle = projectSource("app/build.gradle.kts")

        assertFalse(navigation.contains("onShareAiAnswerClick"))
        assertFalse(home.contains("onShareAiAnswerClick"))
        assertFalse(library.contains("onShareAiAnswerClick"))
        assertFalse(courses.contains("onShareAiAnswerClick"))
        assertFalse(activityFeed.contains("askAiOnSelected"))
        assertFalse(Files.exists(mainSource("ui/components/RichMarkdownText.kt")))
        assertFalse(gradle.contains("HOME_AI_"))
        assertTrue(gradle.contains("NOTE_FORMATTING_KIMI_FAST_MODEL"))
    }

    @Test
    fun `pdf activity selection keeps study note action without Ask AI`() {
        val activityFeed = source("ui/screens/PdfActivityFeedScreen.kt")

        assertTrue(activityFeed.contains("Create Study Note"))
        assertFalse(activityFeed.contains("onNavigateToAskAi"))
        assertFalse(activityFeed.contains("onAskAiOnSelected"))
    }

    @Test
    fun `Quran AI Listen remains a protected reachable memorisation utility`() {
        val reader = source("ui/quran/QuranReaderSurface.kt")
        val ayahCard = source("ui/quran/QuranAyahCard.kt")
        val listenSheet = source("ui/quran/QuranMemorizationSheets.kt")

        assertTrue(reader.contains("QuranAiListenSheet("))
        assertTrue(reader.contains("onAiListenAttemptCompleted"))
        assertTrue(ayahCard.contains("AI Listen"))
        assertTrue(listenSheet.contains("QuranMemorizationRecorder"))
        assertTrue(listenSheet.contains("SpeechRecognitionProviderType"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(mainSource(relativePath)))

    private fun projectSource(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve(relativePath)))

    private fun mainSource(relativePath: String): Path =
        projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")
}
