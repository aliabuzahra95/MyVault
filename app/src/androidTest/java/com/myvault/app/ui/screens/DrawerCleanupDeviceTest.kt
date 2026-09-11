package com.myvault.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.ui.components.*
import com.myvault.app.ui.theme.VaultTheme
import com.myvault.app.ui.theme.VaultThemeMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real drawer with disposable presentation state, never real Drive actions. */
@RunWith(AndroidJUnit4::class)
class DrawerCleanupDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private fun nodes(): List<AccessibilityNodeInfo> {
        if (Build.VERSION.SDK_INT >= 33) automation.clearCache()
        fun walk(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
        return walk(automation.rootInActiveWindow)
    }
    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(100) { nodes().firstOrNull { it.isVisibleToUser && predicate(it) }?.let { return it }; SystemClock.sleep(60) }
        error("Drawer control missing: " + nodes().mapNotNull { it.text ?: it.contentDescription })
    }
    private fun text(value: String) = awaitNode { it.text?.toString() == value }
    private fun description(value: String) = awaitNode { it.contentDescription?.toString() == value }
    private fun tap(node: AccessibilityNodeInfo) {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        assertTrue(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        SystemClock.sleep(600)
    }
    private fun open() = tap(description("Open navigation"))
    private fun setText(value: String) {
        assertTrue(awaitNode { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }))
        SystemClock.sleep(300)
    }
    private fun screenshot(name: String) {
        SystemClock.sleep(500)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "drawer-cleanup").apply { mkdirs() }
        val width = instrumentation.targetContext.resources.configuration.screenWidthDp
        File(directory, "$width-$name.png").outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun bounds(node: AccessibilityNodeInfo) = Rect().also { node.getBoundsInScreen(it) }
    private fun input(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        SystemClock.sleep(700)
    }
    private class Fixture {
        var mode by mutableStateOf(VaultThemeMode.Light)
        var identity by mutableStateOf(DrawerIdentity(displayName = "Ali AH"))
        var route by mutableStateOf("Dashboard fixture")
        var expanded by mutableStateOf(emptySet<String>())
        var backupTaps = 0
        val labels = listOf("Courses", "Study", "Library", "Qur'an", "Memorise")
        private val revision = VaultMobileWebExplorerNode("note", "Revision note", VaultMobileWebExplorerNodeType.Note)
        private val nested = VaultMobileWebExplorerNode("nested", "Nested folder", VaultMobileWebExplorerNodeType.Folder, children = listOf(revision))
        private val folder = VaultMobileWebExplorerNode("folder", "Aqeedah", VaultMobileWebExplorerNodeType.Folder, canAdd = true, children = listOf(nested))
        @Composable fun Content() = VaultTheme(mode) {
            VaultMobileWebShell(workspaceLabel = "Islamic Corpus", workspaceKey = "Islamic Corpus", accountEmail = "",
                onWorkspaceSelected = { route = it }, items = labels.map { VaultMobileWebNavigationItem(it, Icons.Outlined.Folder) },
                selectedIndex = -1, onItemSelected = { route = labels[it] }, onDashboardSelected = { route = "Dashboard fixture" },
                onSearchSelected = { route = "Global Search fixture" }, onAttachmentsSelected = { route = "Attachments fixture" },
                onFavouritesSelected = { route = "Favourites fixture" }, onSettingsSelected = { route = "Settings fixture" },
                onBackupSelected = { backupTaps++; route = "Backup callback fixture" }, onThemeSelected = { mode = VaultThemeMode.Dark },
                profile = identity, onSaveDisplayName = { key, name, complete ->
                    assertEquals(identity.accountKey, key)
                    identity = identity.copy(displayName = normalizedDrawerName(name).ifBlank { "MyVault" }); complete(true)
                }, onReflectionsSelected = { route = "Reflections fixture" },
                explorerSections = listOf(VaultMobileWebExplorerSection(1, listOf(folder), canAdd = true)),
                persistedExpandedExplorerKeys = expanded, onPersistExpandedExplorerKeys = { expanded = it },
                onExplorerNodeSelected = { _, node -> route = node.label },
                onExplorerAddSelected = { _, node -> route = "Add ${node?.label}" },
            ) { Text(route) }
        }
    }
    private fun fixture(block: (ActivityScenario<MainActivity>, Fixture) -> Unit) {
        val fixture = Fixture()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { fixture.Content() } }
            text("Dashboard fixture"); block(scenario, fixture)
        }
    }

    @Test fun headerFooterOrderThemesNamesAndWorkspaceRemainUsable() = fixture { scenario, state ->
        open()
        val order = listOf("Dashboard", "Favourites", "Qur'an", "Reflections", "Memorise", "Study", "Library", "Courses")
        val positions = order.map { bounds(text(it)).centerY() }
        assertEquals(positions.sorted(), positions)
        assertEquals(1, order.map { bounds(text(it)).left }.distinct().size)
        assertFalse(nodes().any { it.text?.toString() in listOf("APPLICATION", "KNOWLEDGE", "Search", "Workspace Attachments") })
        assertEquals(1, nodes().count { it.text?.toString() == "Settings" })
        description("Global Search"); description("Back up to Google Drive"); description("Change theme")
        screenshot("01-light")
        scenario.onActivity { state.mode = VaultThemeMode.Dark }; screenshot("02-dark")
        scenario.onActivity { state.mode = VaultThemeMode.Oled }; screenshot("03-oled")
        tap(description("Edit MyVault display name")); screenshot("04-display-name")
        setText("  علي AH  "); tap(text("Save")); text("علي AH")
        screenshot("05-arabic-name")
        tap(description("Global Search")); text("Global Search fixture"); open(); text("علي AH")
        scenario.onActivity { state.identity = state.identity.copy(displayName = "A very long custom account name for Islamic study and notes") }
        SystemClock.sleep(500)
        val search = bounds(description("Global Search"))
        val name = bounds(text(state.identity.displayName))
        assertTrue(name.right <= search.left)
        screenshot("06-long-name")
        tap(text("Favourites")); text("Favourites fixture"); open()
        tap(text("Settings")); text("Settings fixture"); open()
        tap(description("Change theme")); assertEquals(VaultThemeMode.Dark, state.mode); open()
        tap(description("Back up to Google Drive")); assertEquals(1, state.backupTaps); text("Backup callback fixture"); open()
        tap(description("Change workspace")); text("Switch workspace"); tap(text("Personal")); text("Personal")
    }

    @Test fun folderTitleChevronAndPlusStayIndependentAcrossNavigation() = fixture { _, state ->
        open(); tap(description("Expand Study")); text("Aqeedah")
        tap(text("Aqeedah")); text("Nested folder")
        tap(text("Nested folder")); text("Revision note")
        assertEquals("Dashboard fixture", state.route)
        tap(description("Collapse Aqeedah"))
        assertFalse(nodes().any { it.text?.toString() == "Nested folder" && it.isVisibleToUser })
        tap(description("Expand Aqeedah")); text("Revision note")
        val expansion = state.expanded
        tap(description("Add to Aqeedah")); text("Add Aqeedah"); assertEquals(expansion, state.expanded)
        open(); text("Revision note"); tap(text("Revision note")); text("Revision note")
        open(); text("Nested folder"); assertEquals(expansion, state.expanded)
        screenshot("07-expanded-tree")
    }

    @Test fun drawerClosesWithBackOutsideTapSwipeAndSelection() = fixture { _, _ ->
        open(); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(600)
        text("Dashboard fixture")
        assertFalse(nodes().any { it.contentDescription?.toString() == "Global Search" && it.isVisibleToUser })
        open()
        val width = instrumentation.targetContext.resources.displayMetrics.widthPixels
        input("input tap ${width - 15} 700")
        assertFalse(nodes().any { it.contentDescription?.toString() == "Global Search" && it.isVisibleToUser })
        open()
        input("input swipe ${width * 4 / 5} 700 5 700 350")
        assertFalse(nodes().any { it.contentDescription?.toString() == "Global Search" && it.isVisibleToUser })
        open(); tap(text("Reflections")); text("Reflections fixture")
        assertFalse(nodes().any { it.contentDescription?.toString() == "Global Search" && it.isVisibleToUser })
    }

    @Test fun searchHeaderWorksFromEveryDrawerContext() = fixture { scenario, state ->
        listOf("Dashboard", "Qur'an", "Reflections", "Memorise", "Study", "Library", "Courses", "PDF").forEach { route ->
            scenario.onActivity { state.route = "$route fixture" }; text("$route fixture")
            open(); tap(description("Global Search")); text("Global Search fixture")
        }
    }

    @Test fun productionRootsOpenExistingSearchAndFooterSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            listOf("Dashboard", "Qur'an", "Reflections", "Memorise", "Study", "Library", "Courses").forEach { destination ->
                open(); tap(text(destination)); description("Open navigation")
                open(); tap(description("Global Search")); text("Across MyVault")
            }
            screenshot("08-production-search")
            open(); tap(text("Settings")); text("Preferences & account")
            open()
            assertEquals(1, listOf("Dashboard", "Favourites", "Qur'an", "Reflections", "Memorise", "Study", "Library", "Courses")
                .map { bounds(text(it)).left }.distinct().size)
            screenshot("09-production-drawer")
        }
    }
}
