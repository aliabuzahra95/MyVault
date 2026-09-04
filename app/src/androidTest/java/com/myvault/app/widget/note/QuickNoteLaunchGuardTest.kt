package com.myvault.app.widget.note

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickNoteLaunchGuardTest {
    @Test
    fun rapidSecondLaunchIsRejectedButLaterLaunchIsAccepted() {
        val guard = QuickNoteLaunchGuard(ApplicationProvider.getApplicationContext())
        val base = android.os.SystemClock.elapsedRealtime() + 10_000
        assertTrue(guard.accept(base))
        assertFalse(guard.accept(base + 200))
        assertTrue(guard.accept(base + 1_500))
    }
}
