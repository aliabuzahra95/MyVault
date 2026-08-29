package com.myvault.app.data.preferences

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultPreferencesDriveAccountIsolationTest {
    @Test
    fun switchingAccountsRestoresOnlyThatAccountsSyncMetadata() = runBlocking {
        val preferences = VaultPreferences(ApplicationProvider.getApplicationContext())
        val suffix = System.nanoTime()
        val accountA = "drive-a-$suffix@example.com"
        val accountB = "drive-b-$suffix@example.com"
        val unknownAccount = "drive-unknown-$suffix@example.com"

        preferences.setGoogleDriveAccountEmail(accountA)
        preferences.markGoogleDriveSync(accountA, cloudManifestAt = 101L, syncedAt = 111L)
        assertEquals(111L, preferences.userPreferences.first().lastGoogleDriveSyncAt)
        assertEquals(101L, preferences.userPreferences.first().lastGoogleDriveManifestAt)

        preferences.setGoogleDriveAccountEmail(accountB)
        assertEquals(0L, preferences.userPreferences.first().lastGoogleDriveManifestAt)
        preferences.markGoogleDriveSync(accountB, cloudManifestAt = 202L, syncedAt = 222L)

        preferences.setGoogleDriveAccountEmail(accountA)
        assertEquals(111L, preferences.userPreferences.first().lastGoogleDriveSyncAt)
        assertEquals(101L, preferences.userPreferences.first().lastGoogleDriveManifestAt)

        preferences.setGoogleDriveAccountEmail(accountB)
        assertEquals(222L, preferences.userPreferences.first().lastGoogleDriveSyncAt)
        assertEquals(202L, preferences.userPreferences.first().lastGoogleDriveManifestAt)

        preferences.setGoogleDriveAccountEmail(unknownAccount)
        assertEquals(0L, preferences.userPreferences.first().lastGoogleDriveSyncAt)
        assertEquals(0L, preferences.userPreferences.first().lastGoogleDriveManifestAt)

        preferences.setGoogleDriveAccountEmail("")
    }
}
