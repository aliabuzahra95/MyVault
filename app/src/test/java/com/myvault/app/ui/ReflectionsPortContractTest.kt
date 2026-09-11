package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ReflectionsPortContractTest {
    private val root = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }
    private fun source(path: String) = String(Files.readAllBytes(root.resolve("app/src/main/java/com/myvault/app/$path")))
    private fun hash(path: String) = MessageDigest.getInstance("SHA-256").digest(source(path).toByteArray()).joinToString("") { "%02x".format(it) }

    @Test fun dashboardAndLegacyHubAreByteIdenticalToApprovedStartingPoint() {
        assertEquals("b9b1922964e0c6891457e70cf2ee1b10d3b75671ca5ca1f1cb4ec62912fc1f6c", hash("ui/screens/StageNineDestinationScreens.kt"))
        assertEquals("54c967a164e867276a3ed5fc0c5ea1ac0e97c8998f3a2bcfc40fc158af986b5b", hash("ui/screens/QuranReflectionsHubScreen.kt"))
        val dashboard = source("ui/navigation/VaultNavHost.kt").substringAfter("composable(VaultDestination.Dashboard.route) {").substringBefore("route = VaultDestination.LibraryFolder.route")
        assertTrue(dashboard.contains("navController.navigate(VaultDestination.QuranReflections.route)"))
        assertFalse(dashboard.contains("VaultDestination.Reflections.route"))
    }

    @Test fun dedicatedRouteReusesExistingRepositoryViewAndPreservesExactIdentity() {
        val nav = source("ui/navigation/VaultNavHost.kt")
        val port = nav.substringAfter("composable(VaultDestination.Reflections.route) {").substringBefore("composable(VaultDestination.QuranReflections.route)")
        assertTrue(port.contains("QuranReflectionsViewModel"))
        assertTrue(port.contains("pendingQuranVerseKey = reflection.verseKey"))
        assertTrue(port.contains("pendingReflectionNoteId = reflection.noteId"))
        assertTrue(port.contains("launchSingleTop = true"))
        assertFalse(port.contains("navigateToVaultRoot(VaultDestination.Knowledge.route)"))
        val drawer = source("ui/components/VaultMobileWebShell.kt")
        assertTrue(drawer.contains("listOf(\"Qur'an\", \"Memorise\", \"Study\", \"Library\", \"Courses\")"))
        assertTrue(drawer.contains("item.label == \"Qur'an\") {\n                                    DrawerNavigationRow(\n                                        label = \"Reflections\""))
    }
}
