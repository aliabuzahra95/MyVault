package com.myvault.app.ui.navigation

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestBackupLifecycleTest {
    @Test
    fun foregroundInvokesCheckerOnlyWhenEnabled() {
        var checks = 0

        dispatchLatestBackupLifecycleCheck(Lifecycle.Event.ON_RESUME, enabled = true) { checks++ }
        dispatchLatestBackupLifecycleCheck(Lifecycle.Event.ON_PAUSE, enabled = true) { checks++ }
        dispatchLatestBackupLifecycleCheck(Lifecycle.Event.ON_RESUME, enabled = false) { checks++ }

        assertEquals(1, checks)
    }
}
