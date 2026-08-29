package com.myvault.app.ui.navigation

import android.content.Context
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RootNavigationTest {
    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        onMain {
            val context = ApplicationProvider.getApplicationContext<Context>()
            navController = TestNavHostController(context).apply {
                navigatorProvider.addNavigator(ComposeNavigator())
                graph = createGraph(startDestination = VaultDestination.Dashboard.route) {
                    composable(VaultDestination.Dashboard.route) { }
                    composable(VaultDestination.Knowledge.route) { }
                    composable(VaultDestination.Search.route) { }
                    composable(VaultDestination.Settings.route) { }
                    composable(VaultDestination.Reading.route) { }
                    composable(VaultDestination.Editor.route) { }
                }
            }
        }
    }

    @Test
    fun dashboardOpensKnowledgeRoot() = onMain {
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)

        assertEquals(VaultDestination.Knowledge.route, navController.currentDestination?.route)
        assertEquals(VaultDestination.Dashboard.route, navController.previousBackStackEntry?.destination?.route)
    }

    @Test
    fun switchingRootsReplacesThePreviousRoot() = onMain {
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        navController.navigateToVaultRoot(VaultDestination.Search.route)
        navController.navigateToVaultRoot(VaultDestination.Settings.route)

        assertEquals(VaultDestination.Settings.route, navController.currentDestination?.route)
        assertEquals(VaultDestination.Dashboard.route, navController.previousBackStackEntry?.destination?.route)
    }

    @Test
    fun selectingTheSameRootDoesNotDuplicateIt() = onMain {
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)

        assertEquals(VaultDestination.Knowledge.route, navController.currentDestination?.route)
        assertEquals(VaultDestination.Dashboard.route, navController.previousBackStackEntry?.destination?.route)
    }

    @Test
    fun deletingFromEditorRemovesItsUnderlyingReader() = onMain {
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        navController.navigate("reading/note-id")
        navController.navigate("editor/note-id?quickFocus=false")

        navController.leaveDeletedNote()

        assertEquals(VaultDestination.Knowledge.route, navController.currentDestination?.route)
    }

    @Test
    fun deletingFromReaderReturnsToItsParent() = onMain {
        navController.navigateToVaultRoot(VaultDestination.Knowledge.route)
        navController.navigate("reading/note-id")

        navController.leaveDeletedNote()

        assertEquals(VaultDestination.Knowledge.route, navController.currentDestination?.route)
    }

    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
