package com.myvault.app.ui.screens

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.DrawerProfilePreferences
import com.myvault.app.ui.components.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DrawerProfilePersistenceTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun nameSurvivesProcessDeath() = runBlocking {
        val key = drawerAccountKey("cold-start-drawer-test@fixture.invalid")
        val phase = InstrumentationRegistry.getArguments().getString("drawerPersistencePhase")
        val store = DrawerProfilePreferences(context)
        if (phase != "read") store.setName(key, "علي Cold start")
        if (phase != "write") {
            try {
                assertEquals("علي Cold start", store.names.first()[key])
                assertEquals("علي Cold start", resolveDrawerIdentity("cold-start-drawer-test@fixture.invalid", store.names.first(), null).displayName)
            } finally { store.setName(key, "") }
        }
    }

    @Test fun namesPersistIndependentlyAndCanResetToFallback() = runBlocking {
        val store = DrawerProfilePreferences(context)
        val a = drawerAccountKey("${UUID.randomUUID()}@drawer-test.invalid")
        val b = drawerAccountKey("${UUID.randomUUID()}@drawer-test.invalid")
        try {
            store.setName(a, "  علي AH  ")
            store.setName(b, "Study")
            val reopened = DrawerProfilePreferences(context).names.first()
            assertEquals("علي AH", reopened[a])
            assertEquals("Study", reopened[b])
            assertTrue(File(context.filesDir, "datastore/drawer_profile_preferences.preferences_pb").length() > 0)
            store.setName(a, "")
            assertNull(store.names.first()[a])
            assertEquals("Study", store.names.first()[b])
        } finally { store.setName(a, ""); store.setName(b, "") }
    }

    @Test fun cachedPhotoSurvivesOfflineAndCannotLeakToAnotherAccount() {
        val a = DrawerIdentity(drawerAccountKey("${UUID.randomUUID()}@drawer-test.invalid"), "A", "https://127.0.0.1:1/photo")
        val b = a.copy(accountKey = drawerAccountKey("${UUID.randomUUID()}@drawer-test.invalid"))
        val original = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { original.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val cache = File(context.cacheDir, "drawer-profile-photos").apply { mkdirs() }
        val file = File(cache, drawerPhotoCacheKey(a))
        try {
            file.writeBytes(bytes)
            val loaded = loadDrawerPhoto(context, a)
            assertNotNull(loaded)
            assertTrue(loaded!!.width <= 128)
            assertNull(loadDrawerPhoto(context, b))
            assertNull(loadDrawerPhoto(context, a.copy(accountKey = "local")))
            assertNull(decodeDrawerPhoto(byteArrayOf(1, 2, 3)))
            assertNull(decodeDrawerPhoto(ByteArray(512 * 1024 + 1)))
        } finally { file.delete(); original.recycle() }
    }
}
