package com.myvault.app.ui.navigation

import androidx.navigation.NavController

/** Replaces the active root while retaining Dashboard as the single graph base. */
internal fun NavController.navigateToVaultRoot(route: String) {
    navigate(route) {
        popUpTo(VaultDestination.Dashboard.route) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

/** Removes both Edit and its underlying Reader when a note is deleted from Edit. */
internal fun NavController.leaveDeletedNote() {
    val returned = if (previousBackStackEntry?.destination?.route == VaultDestination.Reading.route) {
        popBackStack(VaultDestination.Reading.route, inclusive = true)
    } else {
        popBackStack()
    }
    if (!returned) {
        navigateToVaultRoot(VaultDestination.Dashboard.route)
    }
}
