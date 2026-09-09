package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.formatting.*
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.repository.NoteRepository
import com.myvault.app.ui.theme.*
import com.myvault.app.ui.viewmodel.NoteUiState
import com.myvault.app.ui.viewmodel.QuranReflectionsUiState
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Fixtures never enter the user's vault. Live providers receive only the synthetic text below. */
@RunWith(AndroidJUnit4::class)
class NoteUxDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
        listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun find(label: String): AccessibilityNodeInfo {
        repeat(100) {
            nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }?.let { return it }
            SystemClock.sleep(50)
        }
        error("Missing $label")
    }
    private fun tap(label: String) {
        var node: AccessibilityNodeInfo? = find(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(600)
    }
    private fun screenshot(name: String) {
        SystemClock.sleep(500)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /data/local/tmp/note-ux-$name.png")).use { it.readBytes() }
    }

    @Test fun editingUsesBottomCardsAndReturnsWithoutLosingText() {
        val imageFile = File(context.cacheDir,"note-edit-card-fixture.png")
        val bitmap=Bitmap.createBitmap(900,600,Bitmap.Config.RGB_565)
        Canvas(bitmap).drawColor(android.graphics.Color.rgb(160,190,175))
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        var savedBody = "Start of note\n\n" + (1..30).joinToString("\n\n") { "Paragraph $it. Preserved English and Arabic: النص محفوظ" }
        val note=NoteEntity("editor-card-test",null,title="Attachment editing",bodyPlainText=savedBody,isPinned=false,isFavourite=false,createdAt=1,updatedAt=1)
        val images=listOf("Image A.png","Image B.png").mapIndexed { index,name -> AttachmentEntity("edit-image-$index",note.id,fileName=name,mimeType="image/png",sizeBytes=imageFile.length(),localPath=imageFile.path,remoteUrl=null,createdAt=index.toLong()) }
        val pdf=images.first().copy(id="edit-pdf",fileName="Reference.pdf",mimeType="application/pdf")
        val attachments=images+pdf
        var page by mutableStateOf("editor")
        var viewed: AttachmentEntity? by mutableStateOf(null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { VaultTheme(mode=VaultThemeMode.Light) {
                val state=NoteUiState(note=note.copy(bodyPlainText=savedBody),richText=VaultRichTextDocument(savedBody,emptyList()),attachments=attachments,attachmentCount=3)
                when(page) {
                    "viewer" -> AttachmentViewerScreen(viewed!!,onBackClick={page="editor"})
                    "reading" -> ReadingScreen(state,onBackClick={},onEditClick={page="editor"},onAttachmentClick={})
                    else -> EditorScreen(state,NoteFormattingUiState(),onBackClick={page="reading"},onMenuClick={},onTitleChange={},
                        onContentChange={body,_,_->savedBody=body},onRunFormattingTool={_,_,_,_,_->},onClearFormattingResult={},onAttachDocument={},
                        onAttachmentClick={id->viewed=attachments.single { it.id==id };page="viewer"})
                }
            } } }
            fun editable(): AccessibilityNodeInfo {
                repeat(80) {
                    nodes(automation.rootInActiveWindow).firstOrNull { it.isEditable && it.text?.startsWith("Start of note")==true }?.let { return it }
                    SystemClock.sleep(50)
                }
                error("Missing editable note body")
            }
            val typed=savedBody+"\n\nTyped at the bottom."
            editable().performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            assertTrue(editable().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,typed) }))
            SystemClock.sleep(700)
            assertEquals(typed,savedBody)
            screenshot("editor-keyboard-text")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
            repeat(18) {
                nodes(automation.rootInActiveWindow).filter { it.isScrollable }.forEach { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
                SystemClock.sleep(80)
            }
            find("Image A.png");find("Image B.png");find("Reference.pdf")
            screenshot("editor-bottom-cards")
            tap("Image A.png")
            assertEquals("edit-image-0",viewed?.id)
            tap("Back")
            SystemClock.sleep(600)
            assertEquals(typed,savedBody)
            scenario.onActivity { page="reading" }
            repeat(18) {
                nodes(automation.rootInActiveWindow).filter { it.isScrollable }.forEach { it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) };SystemClock.sleep(80)
            }
            screenshot("reading-images-return")
            assertEquals(listOf("edit-image-0","edit-image-1","edit-pdf"),attachments.map { it.id })
        }
    }

    @Test fun compactMenusImagesAndFormattingPreview() {
        val imageFile = File(context.cacheDir, "note-ux-fixture.png")
        val bitmap = Bitmap.createBitmap(2000, 1200, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(234, 237, 233))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(30, 70, 60); textSize = 100f }
        canvas.drawText("Research reference", 110f, 250f, paint)
        paint.textSize = 65f
        canvas.drawText("A preserved image attachment", 110f, 410f, paint)
        canvas.drawText("Study / Sources", 110f, 560f, paint)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val text = "Research notes\n\nA quotation remains exact [14].\nالدليل محفوظ كما هو"
        val note = NoteEntity("note-ux-fixture", null, title = "Research notes", bodyPlainText = text,
            isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
        val attachment = AttachmentEntity("note-ux-image", note.id, fileName = "Research-reference.png", mimeType = "image/png",
            sizeBytes = imageFile.length(), localPath = imageFile.path, remoteUrl = null, createdAt = 1)
        val state = NoteUiState(note = note, folderPath = listOf("Study", "Aqeedah"),
            richText = VaultRichTextDocument(text, emptyList()), attachments = listOf(attachment), attachmentCount = 1)
        val reflection = com.myvault.app.data.quran.QuranReflectionItem("reflection-fixture", "Reflection on Al-Fatiha 1:1", "Al-Fatiha", 1, 1, "1:1",
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "In the name of Allah, the Entirely Merciful, the Especially Merciful.",
            "A reminder to begin with Allah's name.", "A reminder to begin with Allah's name.", System.currentTimeMillis())
        var openedVerse: String? = null
        var page by mutableStateOf("reading")
        var dark by mutableStateOf(false)
        var width by mutableIntStateOf(390)
        var formatInitially by mutableStateOf(false)
        var selectedModel = NoteFormattingModel.Fast
        var formatting by mutableStateOf(NoteFormattingUiState())
        var preservedBody: String? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(context.resources.displayMetrics.widthPixels / width.toFloat())) {
                VaultTheme(mode = if (dark) VaultThemeMode.Dark else VaultThemeMode.Light) {
                    Box(Modifier.fillMaxSize()) {
                        when (page) {
                            "reading" -> ReadingScreen(uiState = state, onBackClick = {}, onEditClick = { page = "editor" },
                                onFormatClick = { formatInitially = true; page = "editor" },
                                onAttachmentClick = { assertEquals(attachment.id, it); page = "attachment" })
                            "attachment" -> AttachmentViewerScreen(attachment, onBackClick = { page = "reading" })
                            "reflections" -> QuranReflectionsHubScreen(QuranReflectionsUiState(listOf(reflection)),
                                onBackClick = { page = "reading" }, onReflectionClick = { openedVerse = it.verseKey })
                            else -> EditorScreen(uiState = state, formattingState = formatting, onBackClick = {}, onMenuClick = {},
                                onTitleChange = {}, onContentChange = { _, _, _ -> }, onAttachDocument = {},
                                onPreserveFormattingOriginal = { _, body, _, _ -> preservedBody = body },
                                onRunFormattingTool = { action, provider, model, _, body ->
                                    selectedModel = model
                                    formatting = NoteFormattingUiState(action = action, provider = provider, model = model,
                                        sourceBody = body, result = "<h2>Research notes</h2><p>A quotation remains exact [14].</p><blockquote>الدليل محفوظ كما هو</blockquote>")
                                }, onClearFormattingResult = { formatting = NoteFormattingUiState() },
                                onFormattingModelSelected = { formatting = formatting.copy(model = it) },
                                openFormattingInitially = formatInitially)
                        }
                    }
                }
                }
            } }
            find("Research-reference.png")
            screenshot("inline-light")
            tap("Research-reference.png")
            find("Save to device")
            screenshot("attachment-light")
            tap("Delete attachment")
            find("Delete attachment?")
            tap("Keep")
            tap("Back")
            for (size in listOf(360, 390, 412, 430)) {
                scenario.onActivity { width = size; dark = size >= 412 }
                tap("Note actions")
                for (label in listOf("Listen", "Pin note", "Favourite", "Note info", "Version history", "Export", "Structure & Format", "Delete note")) find(label)
                val labels = nodes(automation.rootInActiveWindow).mapNotNull { it.text?.toString() }
                assertFalse(labels.contains("Knowledge & references"))
                assertFalse(labels.contains("Attachments"))
                val deleteBounds = Rect().also { find("Delete note").getBoundsInScreen(it) }
                assertTrue(deleteBounds.bottom < context.resources.displayMetrics.heightPixels)
                screenshot("actions-$size")
                tap("Close")
            }
            tap("Note actions")
            tap("Structure & Format")
            find("Generate preview")
            screenshot("format-controls-dark")
            tap("Full")
            tap("Generate preview")
            assertEquals(NoteFormattingModel.Smart, selectedModel)
            find("Apply")
            screenshot("format-preview-dark")
            assertNull(preservedBody)
            tap("Apply")
            find("Replace note body?")
            tap("Replace")
            assertEquals(text, preservedBody)
            assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString() == "Generate preview" })
            scenario.onActivity { page = "reflections"; dark = false }
            find("Qur'an Reflections")
            screenshot("reflections-light")
            scenario.onActivity { dark = true }
            screenshot("reflections-dark")
            tap("Reflection on Al-Fatiha 1:1")
            assertEquals("1:1", openedVerse)
            scenario.onActivity { page = "attachment" }
            find("Save to device")
            screenshot("attachment-dark")
        }
    }

    @Test fun originalVersionAndNoteLinksSurviveFormatting() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).build()
        try {
            val repo = NoteRepository(db, db.noteDao(), db.folderDao(), db.blockDao(), db.attachmentDao(), db.tagDao(),
                db.noteTableDao(), db.noteVersionDao(), db.pdfAnnotationDao(), db.pdfReadingProgressDao(), db.sourceBacklinkDao(), db.knowledgeTagDao())
            val original = "Heading\n\nRead related note. الدليل محفوظ [14]"
            val note = NoteEntity("fixture", null, title = "Original", bodyPlainText = original, isPinned = false, isFavourite = false, createdAt = 1, updatedAt = 1)
            db.noteDao().upsertAll(listOf(note))
            repo.saveRichText(note.id, original, "[]")
            val recent = "Heading\n\nRead related note. الدليل محفوظ [14]\nA recent edit."
            repo.preserveFormattingOriginal(note.id, "Current title", recent, "[]", "[]")
            val snapshot = db.noteVersionDao().latestForNote(note.id)!!
            assertEquals(recent, snapshot.bodyPlainText)
            repo.saveRichText(note.id, "Changed presentation", "[]")
            repo.restoreVersion(note.id, snapshot.id)
            assertEquals(recent, db.noteDao().getById(note.id)!!.bodyPlainText)
            val link = VaultNoteLink(original.indexOf("related"), original.indexOf("related") + "related note".length, "target")
            val imported = parseRichImport("<h2>Heading</h2><p>Read related note. الدليل محفوظ [14]</p>", null).document
            assertTrue(FormattingTextContract.preservesText(original, imported.text))
            val remapped = remapFormattingNoteLinks(original, imported.text, listOf(link)).single()
            assertEquals("related note", imported.text.substring(remapped.start, remapped.end))
            assertEquals("target", remapped.noteId)
            val ordered = parseRichImport("<ol><li>First.</li><li>Second.</li></ol>", null).document
            assertTrue(FormattingTextContract.preservesText("1. First.\n2. Second.", ordered.text))
        } finally { db.close() }
    }

    @Test fun captureLiveProviderEvidence() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val rawOutputs = JSONArray()
        val repo = NoteFormattingRepository(NativeNoteFormattingGenerator(
            DefaultNoteFormattingProviderGateway(context, com.myvault.app.data.supabase.SupabaseSessionStore(context)),
            NoteFormattingTrace { _, stage, content -> if (stage == "02-raw-ai-response") rawOutputs.put(content) },
        ))
        val fixtures = listOf(
            "Water and purification\n\nResearch notes\nThe author distinguishes the description of water from its use.\n\nReferences\nConsult the source before drawing a conclusion [14].\nhttps://example.org/source?v=2&page=39",
            "Quotations\nقال: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ».\nHe wrote: \"Keep this quotation unchanged.\"\n\n- First observation\n- Second observation\n\nIbn Taymiyyah (728 AH), vol. 2, p. 39 [14].",
            List(120) { "Research point $it\nThe source wording and the distinction between a report and a conclusion remain important. Reference [${it + 1}]." }.joinToString("\n\n"),
            " Sources and method\n\n  This note    records a distinction.\nThe author's report is not automatically his conclusion.\n\nEvidence\n- Read the surrounding passage.\n- Keep the quotation exact: \"Do not shorten the source.\"\n- Compare reference [18], vol. 3, p. 91.\n\nقال: «النص محفوظ كما هو».\n\nNext reading\nhttps://example.org/book?volume=3&page=91",
        )
        val records = JSONArray()
        val folder = File(context.getExternalFilesDir(null), "note-ux-audit").apply { mkdirs() }
        for (provider in NoteFormattingProvider.entries) {
            if (args.getString("provider")?.let { it != provider.name } == true) continue
            var unavailable = false
            for ((index, source) in fixtures.withIndex()) for (action in listOf(NoteFormattingAction.StructureOnly, NoteFormattingAction.IntelligentStructure)) {
                if (unavailable) continue
                if (args.getString("fixture")?.let { it != index.toString() } == true) continue
                if (provider == NoteFormattingProvider.Kimi) kotlinx.coroutines.delay(if (index == 2) 61_000L else 21_000L)
                while (rawOutputs.length() > 0) rawOutputs.remove(0)
                val record = JSONObject().put("provider", provider.name).put("mode", action.name).put("fixture", index).put("source", source)
                val start = SystemClock.elapsedRealtime()
                try {
                    val result = repo.format(NoteFormattingRequest(action, provider, NoteFormattingModel.Fast, "Disposable formatting fixture", source))
                    val imported = parseRichImport(result.editorHtml, null).document
                    check(FormattingTextContract.preservesText(source, imported.text)) { "Editor import changed wording" }
                    record.put("status", "accepted").put("html", result.editorHtml).put("imported", imported.text)
                } catch (error: Exception) {
                    record.put("status", "rejected_or_unavailable").put("error", error.message)
                    unavailable = error.message.orEmpty().contains("sign in", true) || error.message.orEmpty().contains("configured", true) || error.message.orEmpty().contains("API key", true)
                }
                record.put("elapsedMs", SystemClock.elapsedRealtime() - start)
                record.put("rawOutputs", JSONArray(rawOutputs.toString()))
                records.put(record)
                val label = args.getString("auditRun", "retest").filter { it.isLetterOrDigit() || it == '-' }
                File(folder, "live-formatting-$label.json").writeText(records.toString(2))
            }
        }
        assertTrue(records.length() > 0)
    }
}
