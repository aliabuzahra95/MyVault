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
            nodes(automation.rootInActiveWindow).firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }?.let { return it }
            SystemClock.sleep(50)
        }
        error("Missing $label")
    }
    private fun bounds(label: String) = Rect().also { find(label).getBoundsInScreen(it) }
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
        SystemClock.sleep(800)
        repeat(30) { i ->
            touch(MotionEvent.ACTION_MOVE, a.centerX() + (b.centerX() - a.centerX()) * (i + 1) / 30f,
                a.centerY() + (targetY - a.centerY()) * (i + 1) / 30f, start)
            SystemClock.sleep(20)
        }
        if (hold > 0) repeat((hold / 50).toInt()) { touch(MotionEvent.ACTION_MOVE, b.centerX().toFloat(), b.centerY().toFloat(), start); SystemClock.sleep(50) }
        touch(MotionEvent.ACTION_UP, b.centerX().toFloat(), targetY, start)
        SystemClock.sleep(1000)
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
        SystemClock.sleep(800)
        repeat(30) { i -> touch(MotionEvent.ACTION_MOVE, a.centerX().toFloat(), a.centerY() + (y - a.centerY()) * (i + 1) / 30, start); SystemClock.sleep(20) }
        repeat(120) { touch(MotionEvent.ACTION_MOVE, a.centerX().toFloat(), y, start); SystemClock.sleep(50) }
        touch(MotionEvent.ACTION_UP, a.centerX().toFloat(), y, start)
        SystemClock.sleep(1000)
    }
    private fun repository(db: VaultDatabase) = FolderRepository(db, db.folderDao(), db.folderStickyNoteDao(), db.noteDao(), db.attachmentDao(), db.blockDao(), db.tagDao(), db.noteTableDao(), db.noteVersionDao(), db.pdfAnnotationDao(), db.pdfReadingProgressDao(), db.sourceBacklinkDao(), db.knowledgeTagDao())

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
