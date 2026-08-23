package com.myvault.app.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAuthenticationTest {
    @Test
    fun `unauthorized response refreshes the token only once`() {
        assertTrue(shouldRefreshDriveToken(responseCode = 401, attempt = 0))
        assertFalse(shouldRefreshDriveToken(responseCode = 401, attempt = 1))
    }

    @Test
    fun `non authentication errors never refresh the token`() {
        assertFalse(shouldRefreshDriveToken(responseCode = 400, attempt = 0))
        assertFalse(shouldRefreshDriveToken(responseCode = 403, attempt = 0))
        assertFalse(shouldRefreshDriveToken(responseCode = 500, attempt = 0))
    }
}
