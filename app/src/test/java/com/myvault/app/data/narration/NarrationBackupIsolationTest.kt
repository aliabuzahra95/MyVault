package com.myvault.app.data.narration

import java.io.File
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationBackupIsolationTest {

    @Test
    fun testNotificationChannelsAreStrictlyDistinct() {
        val backupChannel = "myvault_drive_sync"
        assertTrue(backupNotificationSource().contains("ChannelId = \"$backupChannel\""))
        val narrationChannel = NarrationPlaybackService.CHANNEL_ID

        assertNotEquals(backupChannel, narrationChannel)
        assertTrue(backupChannel.isNotBlank())
        assertTrue(narrationChannel.isNotBlank())
        assertTrue(backupChannel == "myvault_drive_sync")
        assertTrue(narrationChannel == "myvault_narration")
    }

    @Test
    fun testNotificationIdsDoNotCollide() {
        val backupOngoingId = 2407
        assertTrue(backupNotificationSource().contains("NotificationId = $backupOngoingId"))
        val narrationNotificationId = NarrationPlaybackService.NOTIFICATION_ID

        assertNotEquals(backupOngoingId, narrationNotificationId)
        val allIds = setOf(backupOngoingId, narrationNotificationId)
        org.junit.Assert.assertEquals(2, allIds.size)
    }

    @Test
    fun testNarrationServiceDoesNotInterfereWithBackupChannel() {
        val narrationChannel = NarrationPlaybackService.CHANNEL_ID
        org.junit.Assert.assertFalse(narrationChannel.contains("backup", ignoreCase = true))
    }

    private fun backupNotificationSource(): String =
        File("src/main/java/com/myvault/app/data/sync/DriveSyncWorker.kt").readText()
}
