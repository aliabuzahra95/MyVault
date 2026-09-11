package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

class DrawerCleanupContractTest {
    private val root = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }
    private fun source(file: String) = String(Files.readAllBytes(root.resolve("app/src/main/java/com/myvault/app/$file")))

    @Test fun headerReplacesRedundantRowsAndHeadingsWithoutChangingRoutes() {
        val shell = source("ui/components/VaultMobileWebShell.kt")
        val header = shell.substringAfter("private fun DrawerProfileHeader(").substringBefore("private fun DrawerNavigationRow(")
        val rows = shell.substringAfter("DrawerProfileHeader(\n").substringBefore("val orderedItems")
        assertFalse(rows.contains("label = \"Search\""))
        assertFalse(rows.contains("label = \"Settings\""))
        assertFalse(shell.contains("DrawerSectionLabel"))
        assertFalse(shell.contains("label = \"Workspace Attachments\""))
        assertTrue(shell.indexOf("label = \"Favourites\"") in shell.indexOf("label = \"Dashboard\"") until shell.indexOf("val orderedItems"))
        assertFalse(header.contains("Close navigation"))
        assertTrue(header.contains("contentDescription = \"Global Search\""))
        assertTrue(shell.contains("onSearch = { closeDrawerThen(onSearchSelected) }"))
        val nav = source("ui/navigation/VaultNavHost.kt")
        assertTrue(nav.contains("navController.navigateToVaultRoot(VaultDestination.Search.route)"))
        assertTrue(nav.contains("composable(VaultDestination.Search.route)"))
    }
    @Test fun accountPresentationStaysOutsideBackupAndAuthentication() {
        val profile = source("data/preferences/DrawerProfilePreferences.kt")
        assertTrue(profile.contains("drawer_profile_preferences"))
        assertFalse(source("data/repository/BackupRepository.kt").contains("DrawerProfile"))
        assertFalse(source("data/preferences/VaultPreferences.kt").contains("drawerProfile"))
        val vm = source("ui/viewmodel/ShellPreferencesViewModel.kt")
        assertTrue(vm.contains("GoogleSignIn.getLastSignedInAccount(context)"))
        assertFalse(vm.contains("requestScopes"))
        assertTrue(vm.contains("check(drawerAccountKey(preferences.userPreferences.first().googleDriveAccountEmail) == accountKey)"))
    }
}
