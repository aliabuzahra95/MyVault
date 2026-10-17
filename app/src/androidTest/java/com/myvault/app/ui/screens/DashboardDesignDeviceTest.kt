package com.myvault.app.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.repository.*
import com.myvault.app.ui.components.*
import com.myvault.app.ui.model.*
import com.myvault.app.ui.theme.*
import com.myvault.app.ui.viewmodel.HomeQuranContinue
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Pure synthetic screen state. No vault ViewModel, network, notes or attachments are accessed. */
class DashboardDesignDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private var opened: String? = null
    private val short = listOf("A question of theological terminology", "شرح العقيدة الواسطية ومسائل الأسماء والصفات والتوحيد", "Aqeedah", "Ibn Taymiyyah and Ibn al-Qayyim · مسائل الاعتقاد")
    private fun pins(count: Int) = (0 until count).map { i -> DashboardShortcut("$i",
        if (i == 2) DashboardShortcutKind.Folder else if (i == 1) DashboardShortcutKind.Document else DashboardShortcutKind.Note,
        short[i % 4], if (i == 1) "Library · Page 42" else if (i == 2) "Study · Folder" else "Study · Note") }
    private fun recent() = (0 until 5).map { i -> DashboardActivityItem(DashboardActivityKind.Library, "file-$i",
        listOf("حوادث لا أول لها", "مجموع الفتاوى", "درء تعارض العقل والنقل", "شرح العقيدة الطحاوية", "التدمرية")[i],
        "Library", 10L - i, pageIndex = 41 + i, pageCount = 500) }
    private fun render(count: Int, width: Int = 390, scale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                VaultTheme(mode = VaultThemeMode.Dark) {
                    Box(Modifier.fillMaxSize().background(VaultThemeTokens.colors.bg).statusBarsPadding().navigationBarsPadding()) {
                        DashboardOverviewScreen(pins(count), DashboardActivityState(
                            lastNote = DashboardActivityItem(DashboardActivityKind.Note, "study", short[0], "Study", 1),
                            recents = recent()), HomeQuranContinue("Aal-i-Imraan", 3, 43),
                            onMenuClick = {}, onOpenShortcut = { opened = it.key }, onUnpinShortcut = {},
                            onOpenQuran = { opened = it }, onOpenActivity = { opened = it.destinationId },
                            onViewAllLibrary = { opened = "library" }, modifier = Modifier.width(width.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
    }
    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot() ?: error("No rendered screenshot")
        val dir = File(rule.activity.getExternalFilesDir(null), "dashboard-design").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun emptyPinsLeaveOnlyContinueAndLibrary() {
        render(0)
        rule.onNodeWithTag("dashboard-pins").assertDoesNotExist()
        rule.onNodeWithText("QUR'AN").assertIsDisplayed()
        rule.onNodeWithText("STUDY").assertIsDisplayed()
        screenshot("no-pins")
    }
    @Test fun fourPinsAreVisibleInTwoRowsAndOpenExactIdentity() {
        render(4)
        val bounds = (0..3).map { i ->
            val key = pins(4)[i].key
            rule.onNodeWithTag("pin:$key").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(bounds[0].width, bounds[2].width, 1f)
        assertEquals(bounds[0].top, bounds[2].top, 1f)
        assertEquals(bounds[1].top, bounds[3].top, 1f)
        assertTrue(bounds[0].bottom <= bounds[1].top)
        rule.onNodeWithTag("pin:Folder:2").performClick()
        assertEquals("Folder:2", opened)
        screenshot("four-pins")
    }
    @Test fun tenPinsRemainTwoRowsAndSwipeWithoutGrowing() {
        render(10)
        val height = rule.onNodeWithTag("dashboard-pins").fetchSemanticsNode().boundsInRoot.height
        screenshot("ten-pins")
        rule.onNodeWithTag("dashboard-pins").performTouchInput { swipeLeft() }
        rule.onNodeWithTag("dashboard-pins").performTouchInput { swipeLeft() }
        assertEquals(height, rule.onNodeWithTag("dashboard-pins").fetchSemanticsNode().boundsInRoot.height, 1f)
        rule.onAllNodesWithText("View all")[0].performClick()
        rule.onAllNodesWithContentDescription("Unpin from Dashboard")[0].assertExists()
        screenshot("all-pins")
    }
    @Test fun fiveDistinctLibraryRowsAndSavedPositionAreVisible() {
        render(0)
        rule.onNodeWithTag("dashboard-scroll").performScrollToNode(hasTestTag("recent:file-4"))
        (0..4).forEach { rule.onNodeWithTag("recent:file-$it").assertIsDisplayed() }
        rule.onNodeWithText("Last read · Page 42").assertIsDisplayed()
        rule.onNodeWithTag("recent:file-2").performClick()
        assertEquals("file-2", opened)
        screenshot("five-library-documents")
    }
    @Test fun narrowScreenWithLargeFontsKeepsActionsVisible() {
        render(4, 320, 1.5f)
        (0..3).forEach { rule.onNodeWithTag("pin:${pins(4)[it].key}").assertIsDisplayed() }
        rule.onNodeWithText("QUR'AN").assertIsDisplayed()
        rule.onNodeWithText("STUDY").assertIsDisplayed()
        rule.onNodeWithText("Library · Page 42", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(getLayout(layouts))
                assertFalse("Pinned page metadata is clipped", layouts.single().hasVisualOverflow)
            }
        screenshot("narrow-large-font")
    }
    @Test fun quickLibraryAndOriginalLibraryInvokeSameDestination() {
        var visits = 0
        rule.setContent {
            VaultTheme(mode = VaultThemeMode.Dark) {
                VaultMobileWebShell(workspaceLabel = "Islamic Corpus", workspaceKey = "islamic_corpus",
                    accountEmail = "", onWorkspaceSelected = {},
                    items = listOf("Qur'an", "Memorise", "Study", "Library", "Courses")
                        .map { VaultMobileWebNavigationItem(it, Icons.AutoMirrored.Rounded.MenuBook) }, selectedIndex = -1,
                    explorerSections = listOf(
                        VaultMobileWebExplorerSection(2, nodes = listOf(
                            VaultMobileWebExplorerNode("fixture-folder", "Aqeedah", VaultMobileWebExplorerNodeType.Folder,
                                children = listOf(VaultMobileWebExplorerNode("fixture-note", "A study note", VaultMobileWebExplorerNodeType.Note))),
                        )),
                    ),
                    onItemSelected = { if (it == 3) visits++ }, onDashboardSelected = {}, onSearchSelected = {},
                    onAttachmentsSelected = {}, onFavouritesSelected = { visits++ }, onSettingsSelected = {},
                    onBackupSelected = {}, onThemeSelected = {}, contentStartsInMenuBar = true, menuVisible = false) { menu ->
                    FrozenDestinationHeader("Dashboard", "Continue where you left off", menu)
                }
            }
        }
        rule.onNodeWithContentDescription("Open navigation").performClick()
        rule.onNodeWithText("Quick Library").assertIsDisplayed()
        rule.onNodeWithContentDescription("Expand Study").performClick()
        rule.onNodeWithText("Aqeedah").assertIsDisplayed()
        rule.onNodeWithContentDescription("Collapse Study").performClick()
        rule.onNodeWithText("Aqeedah").assertDoesNotExist()
        screenshot("quick-library-drawer")
        rule.onNodeWithText("Quick Library").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Open navigation").performClick()
        rule.onNodeWithText("Library").performClick()
        rule.waitForIdle()
        assertEquals(2, visits)
    }
}
