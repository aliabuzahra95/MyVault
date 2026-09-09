package com.myvault.app.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.*
import com.myvault.app.data.repository.*
import com.myvault.app.ui.model.*
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.viewmodel.HomeUiState
import com.myvault.app.ui.viewmodel.LibraryUiState
import com.myvault.app.ui.viewmodel.LibraryFolderItem
import com.myvault.app.ui.viewmodel.LibraryFileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All records and preferences belong to a disposable test database, not the user's vault. */
@RunWith(AndroidJUnit4::class)
class StudyOrganisationInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (node == null) emptyList() else
        listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }
    private fun find(label: String): AccessibilityNodeInfo {
        repeat(100) {
            if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
            nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }?.let { return it }
            SystemClock.sleep(50)
        }
        error("Missing $label")
    }
    private fun bounds(label: String) = Rect().also { find(label).getBoundsInScreen(it) }
    private fun awaitOrderSaved() {
        repeat(120) {
            if (nodes(automation.rootInActiveWindow).none { it.className?.toString() == "android.widget.ProgressBar" }) return
            SystemClock.sleep(50)
        }
        error("Order is still saving")
    }
    private fun touch(action: Int, x: Float, y: Float, start: Long) {
        MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0).also {
            it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(it, true))
            it.recycle()
        }
    }
    private fun tap(label: String, long: Boolean = false) {
        val r = bounds(label); val start = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, r.centerX().toFloat(), r.centerY().toFloat(), start)
        SystemClock.sleep(if (long) 800 else 50)
        touch(MotionEvent.ACTION_UP, r.centerX().toFloat(), r.centerY().toFloat(), start)
        SystemClock.sleep(500)
    }
    private fun drag(from: String, to: String, hold: Long = 0) {
        val a = bounds(from); val b = bounds(to); val start = SystemClock.uptimeMillis()
        val targetY = if (b.centerY() < a.centerY()) b.top + 2f else b.bottom - 2f
        touch(MotionEvent.ACTION_DOWN, a.centerX().toFloat(), a.centerY().toFloat(), start)
        SystemClock.sleep(16)
        repeat(30) { i ->
            touch(MotionEvent.ACTION_MOVE, a.centerX() + (b.centerX() - a.centerX()) * (i + 1) / 30f,
                a.centerY() + (targetY - a.centerY()) * (i + 1) / 30f, start)
            SystemClock.sleep(20)
        }
        if (hold > 0) repeat((hold / 50).toInt()) { touch(MotionEvent.ACTION_MOVE, b.centerX().toFloat(), b.centerY().toFloat(), start); SystemClock.sleep(50) }
        touch(MotionEvent.ACTION_UP, b.centerX().toFloat(), targetY, start)
        SystemClock.sleep(1000)
        awaitOrderSaved()
    }
    private fun screenshot(name: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("screencap -p /data/local/tmp/study-$name.png")).use { it.readBytes() }
    }
    private fun edgeDrag(from: String, bottom: Boolean) {
        val a = bounds(from)
        val window = Rect().also { automation.rootInActiveWindow.getBoundsInScreen(it) }
        val y = if (bottom) window.bottom - 100f else window.top + 100f
        val start = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, a.centerX().toFloat(), a.centerY().toFloat(), start)
        SystemClock.sleep(16)
        repeat(30) { i -> touch(MotionEvent.ACTION_MOVE, a.centerX().toFloat(), a.centerY() + (y - a.centerY()) * (i + 1) / 30, start); SystemClock.sleep(20) }
        repeat(240) { touch(MotionEvent.ACTION_MOVE, a.centerX().toFloat(), y, start); SystemClock.sleep(50) }
        screenshot(if (bottom) "edge-held-bottom" else "edge-held-top")
        touch(MotionEvent.ACTION_UP, a.centerX().toFloat(), y, start)
        SystemClock.sleep(1000)
        awaitOrderSaved()
    }
    private fun repository(db: VaultDatabase) = FolderRepository(db, db.folderDao(), db.folderStickyNoteDao(), db.noteDao(), db.attachmentDao(), db.blockDao(), db.tagDao(), db.noteTableDao(), db.noteVersionDao(), db.pdfAnnotationDao(), db.pdfReadingProgressDao(), db.sourceBacklinkDao(), db.knowledgeTagDao())

    @Test fun libraryMixedImmediateDragAndActions() = runBlocking {
        val name = "library-organise-disposable.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context,VaultDatabase::class.java,name).build()
        val folders = listOf(
            FolderEntity("fa",null,"Folder A",orderIndex=0,isFavourite=false,mode=FOLDER_MODE_LIBRARY,createdAt=1,updatedAt=10),
            FolderEntity("fb",null,"Folder B",orderIndex=1,isFavourite=false,mode=FOLDER_MODE_LIBRARY,createdAt=2,updatedAt=20),
            FolderEntity("nested","fa","Nested folder",orderIndex=0,isFavourite=false,mode=FOLDER_MODE_LIBRARY,createdAt=3,updatedAt=30))
        val files = (0 until 22).map { i -> AttachmentEntity("file-$i","",null,"PDF ${i.toString().padStart(2,'0')}","application/pdf",100,"/fixture",null,createdAt=i+40L,orderIndex=i+2) } +
            AttachmentEntity("nested-file","","fa","Nested PDF","application/pdf",100,"/fixture",null,createdAt=5,orderIndex=1)
        db.folderDao().upsertAll(folders); db.attachmentDao().upsertAll(files)
        var state by mutableStateOf(LibraryUiState())
        var clicked: String? = null
        suspend fun refresh() {
            val fs=db.folderDao().getAll(); val docs=db.attachmentDao().getAll()
            fun file(f:AttachmentEntity)=LibraryFileItem(f.id,f.fileName,"PDF","100 B","","application/pdf","",pageIndex=28,pageCount=54,
                highlightCount=4,annotationNoteCount=1,orderIndex=f.orderIndex!!,createdAt=f.createdAt,lastOpenedAt=100-f.createdAt)
            fun children(parent:String?): List<LibraryFolderItem> = fs.filter { it.parentId==parent }.map { f ->
                LibraryFolderItem(f.id,f.name,1,orderIndex=f.orderIndex,createdAt=f.createdAt,colorKey=f.colorKey,
                    files=docs.filter { it.libraryFolderId==f.id }.map(::file),children=children(f.id)) }
            state=state.copy(folders=children(null),files=docs.filter { it.libraryFolderId==null }.map(::file),allFolders=children(null))
        }
        refresh()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { VaultTheme { Box(Modifier.statusBarsPadding()) {
                    LibraryScreen(uiState=state,onFolderClick={},onAttachmentClick={clicked=it},onAnnotationClick={_,_->},onReferenceNoteClick={},
                        onRenameAnnotation={_,_->},onMoveAnnotation={_,_->},onDeleteAnnotationNote={},onDeleteAnnotation={},
                        onLinkAnnotationToStudyNote={_,_->},onCreateStudyNoteFromAnnotation={},onPrepareStudyNoteLinks={},
                        onCreateFolder={_,_->},onRenameFolder={_,_->},onUpdateFolderColor={_,_->},onMoveFolder={_,_->},onMoveFolderInOrder={_,_->},onDeleteFolder={},
                        onFolderExpandedChange={id,open->state=state.copy(expandedFolderIds=if(open) state.expandedFolderIds+id else state.expandedFolderIds-id)},
                        onViewModeChange={},onImportFiles={},onReplaceDuplicatePdf={},onSkipDuplicatePdf={},onDismissImportMessage={},
                        onRenameFile={_,_->},onMoveFile={_,_->},onSetFilePinned={_,_->},onDeleteFile={},onExportFile={_,_->},
                        onAddAttachmentTag={_,_->},onRemoveAttachmentTag={_,_->},onAddAnnotationTag={_,_->},onRemoveAnnotationTag={_,_->},
                        onThemeClick={},onQuickBackupClick={},onSettingsClick={},
                        onSortModeChange={state=state.copy(organisation=state.organisation.copy(sortMode=it))},
                        onReorder={ids->repository(db).reorderLibrarySiblings(ids);refresh();true})
                } } } }
                tap("Folder A",long=true)
                find("Change colour");find("Rename / Edit description");find("Sort / Organize")
                assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString() in listOf("Open","More actions") })
                assertTrue(bounds("Delete").top>bounds("Move").top)
                screenshot("library-actions")
                tap("Sort / Organize")
                assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString()=="Recently modified" })
                tap("Manual")
                tap("Reorder PDF 00")
                assertEquals(2,db.attachmentDao().getByIdIncludingDeleted("file-0")!!.orderIndex)
                drag("Reorder PDF 00","Reorder Folder A")
                assertEquals(0,db.attachmentDao().getByIdIncludingDeleted("file-0")!!.orderIndex)
                screenshot("library-mixed-drop")
                tap("Folder A")
                drag("Reorder Nested PDF","Reorder Nested folder")
                assertEquals(0,db.attachmentDao().getByIdIncludingDeleted("nested-file")!!.orderIndex)
                assertEquals("fa",db.attachmentDao().getByIdIncludingDeleted("nested-file")!!.libraryFolderId)
                screenshot("library-nested-drop")
                tap("Done")
                tap("PDF 00")
                assertEquals("file-0",clicked)
                val manual=libraryOrderTree(state.folders,state.files,StudySortMode.Manual,emptyMap())
                for(mode in librarySortModes.filter { it!=StudySortMode.Manual }) {
                    tap("PDF 00",long=true);tap("Sort / Organize");tap(mode.label)
                }
                tap("PDF 00",long=true);tap("Sort / Organize");tap("Manual")
                assertEquals(manual,libraryOrderTree(state.folders,state.files,StudySortMode.Manual,emptyMap()))
                tap("Folder A") // collapse nested children before the long-distance drag
                edgeDrag("Reorder PDF 00",bottom=true)
                assertTrue(db.attachmentDao().getByIdIncludingDeleted("file-0")!!.orderIndex!!>5)
                screenshot("library-autoscroll")
                assertEquals(folders.associate { it.id to it.updatedAt },db.folderDao().getAll().associate { it.id to it.updatedAt })
                val persisted=db.attachmentDao().getAll().associate { it.id to it.orderIndex }
                db.close();db=Room.databaseBuilder(context,VaultDatabase::class.java,name).build()
                assertEquals(persisted,db.attachmentDao().getAll().associate { it.id to it.orderIndex })
            }
        } finally { db.close();context.deleteDatabase(name) }
    }

    @Test fun realDragNestedSortActionsAndPersistence() = runBlocking {
        val name = "study-organisation-disposable-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, VaultDatabase::class.java, name).build()
        val localContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("study-disposable-$name", mode)
        }
        localContext.getSharedPreferences("dashboard_activity", 0).edit().clear().commit()
        fun activityRepository() = DashboardActivityRepository(localContext, db.noteDao(), db.folderDao(), db.attachmentDao(), db.courseDao(), db.pdfReadingProgressDao())
        val folders = listOf(
            FolderEntity("fa", null, "Folder A", orderIndex = 0, isFavourite = false, createdAt = 1, updatedAt = 10),
            FolderEntity("fb", null, "Folder B", orderIndex = 1, isFavourite = false, createdAt = 2, updatedAt = 20),
            FolderEntity("nested", "fa", "Nested folder", orderIndex = 0, isFavourite = false, createdAt = 3, updatedAt = 30),
        )
        val notes = (0 until 28).map { i -> NoteEntity("note-$i", null, title = "Note ${i.toString().padStart(2, '0')}", bodyPlainText = "Disposable test", isPinned = false, isFavourite = false, orderIndex = i + 2, createdAt = i + 100L, updatedAt = i + 200L) } +
            NoteEntity("nested-note", "fa", title = "Nested note", bodyPlainText = "Disposable", isPinned = false, isFavourite = false, orderIndex = 1, createdAt = 5, updatedAt = 50)
        db.folderDao().upsertAll(folders); db.noteDao().upsertAll(notes)
        val repo = repository(db)
        val activity = activityRepository()
        var state by mutableStateOf(HomeUiState(workspace = buildTree(folders, notes, emptyList(), emptyList())))
        suspend fun refresh() { state = state.copy(workspace = buildTree(db.folderDao().getAll(), db.noteDao().getAll(), emptyList(), emptyList())) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { screen ->
                    screen.setContent {
                        VaultTheme {
                            Box(Modifier.statusBarsPadding()) {
                            HomeScreen(uiState = state, onSearchClick = {}, onSettingsClick = {}, onFolderClick = {}, onNoteClick = {},
                                onStudySortModeChange = { state = state.copy(studyOrganisation = state.studyOrganisation.copy(sortMode = it)) },
                                onFolderExpandedChange = { key, expanded -> state = state.copy(expandedFolderIds = if (expanded) state.expandedFolderIds + key else state.expandedFolderIds - key) },
                                onStudyReorder = { ids -> repo.reorderStudySiblings(ids); refresh(); true },
                            )
                            }
                        }
                    }
                }
                tap("Folder A", long = true)
                find("Change colour"); find("Rename / Edit description"); find("Sort / Organize")
                val actionTexts = nodes(automation.rootInActiveWindow).mapNotNull { it.text?.toString() }
                assertFalse(actionTexts.contains("Open")); assertFalse(actionTexts.contains("More actions"))
                assertTrue(bounds("Delete").top > bounds("Move to Personal workspace").top)
                screenshot("folder-actions")
                tap("Sort / Organize")
                StudySortMode.entries.forEach { find(it.label) }
                screenshot("sort-options")
                tap("Manual")
                drag("Reorder Note 01", "Reorder Folder A")
                screenshot("first-drop")
                assertEquals("Drop before Folder A", 0, db.noteDao().getById("note-1")!!.orderIndex)
                drag("Reorder Folder B", "Reorder Folder A")
                assertEquals(1, db.folderDao().getAll().first { it.id == "fb" }.orderIndex)
                assertEquals(0, db.noteDao().getById("note-1")!!.orderIndex)
                tap("Folder A")
                drag("Reorder Nested note", "Reorder Nested folder")
                screenshot("nested-drop")
                assertEquals(0, db.noteDao().getById("nested-note")!!.orderIndex)
                assertEquals("fa", db.noteDao().getById("nested-note")!!.folderId)
                assertEquals(0, db.noteDao().getById("note-1")!!.orderIndex)
                screenshot("nested-manual")
                tap("Folder A")
                edgeDrag("Reorder Note 01", bottom = true)
                assertEquals("One held drag must reach the bottom", 29, db.noteDao().getById("note-1")!!.orderIndex)
                screenshot("autoscroll-bottom")
                edgeDrag("Reorder Note 01", bottom = false)
                assertEquals("One held drag must return to the top", 0, db.noteDao().getById("note-1")!!.orderIndex)
                tap("Done")
                tap("Note 01", long = true)
                find("Rename"); find("Sort / Organize")
                assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString() in listOf("Open", "More actions", "Change colour", "New subfolder") })
                screenshot("note-actions")
                tap("Sort / Organize"); tap("Manual"); tap("Done")
                assertFalse(nodes(automation.rootInActiveWindow).any { it.text?.toString() == "Sort / Organize" })
                tap("Note 01", long = true); tap("Sort / Organize"); tap("Alphabetical")
                assertTrue(bounds("Folder A").top < bounds("Note 01").top)
                tap("Folder A", long = true); tap("Sort / Organize"); tap("Recently modified")
                find("Note 27")
                tap("Note 27", long = true); tap("Sort / Organize"); tap("Manual")
                assertTrue(bounds("Reorder Note 01").top < bounds("Reorder Folder A").top)
                screenshot("manual-restored")
                tap("Done")
            }
            assertEquals(notes.associate { it.id to it.updatedAt }, db.noteDao().getAll().associate { it.id to it.updatedAt })
            assertEquals(folders.associate { it.id to it.updatedAt }, db.folderDao().getAll().associate { it.id to it.updatedAt })
            val rootBefore = db.noteDao().getAll().filter { it.folderId == null }.associate { it.id to it.orderIndex }
            assertTrue(runCatching { repo.reorderStudySiblings(listOf("nested-note", "note-1")) }.isFailure)
            assertTrue(runCatching { repo.reorderStudySiblings(listOf("note-1", "note-1")) }.isFailure)
            assertTrue(runCatching { repo.reorderStudySiblings(listOf("note-1", "missing")) }.isFailure)
            assertEquals(rootBefore, db.noteDao().getAll().filter { it.folderId == null }.associate { it.id to it.orderIndex })
            activity.recordNoteOpened("note-3"); activity.recordStudyFolderOpened("fb"); activity.setStudySortMode(StudySortMode.Opened)
            val reopenedActivity = activityRepository()
            assertEquals(StudySortMode.Opened, reopenedActivity.studyOrganisation.value.sortMode)
            assertTrue(reopenedActivity.studyOrganisation.value.openedAt.getValue("note-3") > 0)
            assertTrue(reopenedActivity.studyOrganisation.value.openedAt.getValue("fb") > 0)
            db.close()
            db = Room.databaseBuilder(context, VaultDatabase::class.java, name).build()
            assertEquals(rootBefore, db.noteDao().getAll().filter { it.folderId == null }.associate { it.id to it.orderIndex })
            assertEquals(0, db.noteDao().getById("nested-note")!!.orderIndex)
        } finally { db.close(); context.deleteDatabase(name); localContext.getSharedPreferences("dashboard_activity", 0).edit().clear().commit() }
    }
}
