package com.myvault.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveNotificationPermissionTest {
    @Test fun olderAndroidDoesNotNeedRuntimeNotificationConsent() {
        for (sdk in 29..32) assertFalse(requiresDriveNotificationPermission(sdk, false))
    }

    @Test fun newAndroidOnlyPromptsWhenPermissionIsMissing() {
        for (sdk in 33..36) {
            assertTrue(requiresDriveNotificationPermission(sdk, false))
            assertFalse(requiresDriveNotificationPermission(sdk, true))
        }
    }
}
