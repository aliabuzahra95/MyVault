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

    @Test
    fun `remote consent response is recognized regardless of case`() {
        assertTrue("NeedRemoteConsent".isRemoteConsentMessage())
        assertTrue("Google auth failed: needremoteconsent".isRemoteConsentMessage())
    }

    @Test
    fun `ordinary authentication messages are not remote consent responses`() {
        assertFalse("Invalid credentials".isRemoteConsentMessage())
        assertFalse(null.isRemoteConsentMessage())
    }

    @Test
    fun `remote comparison uses only current account manifest version`() {
        assertFalse(hasNewerRemoteDriveVersion(remoteVersion = 90L, accountManifestVersion = 100L))
        assertTrue(hasNewerRemoteDriveVersion(remoteVersion = 90L, accountManifestVersion = 0L))
        assertFalse(hasNewerRemoteDriveVersion(remoteVersion = 0L, accountManifestVersion = 100L))
    }
}
