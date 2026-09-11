package com.myvault.app.ui

import com.myvault.app.ui.components.*
import org.junit.Assert.*
import org.junit.Test

class DrawerIdentityTest {
    private val a = "a@example.test"
    private val b = "b@example.test"
    private val google = DrawerGoogleProfile(a, "Google Name", "https://example.test/photo")

    @Test fun customNameWinsAndAccountsRemainIsolated() {
        val names = mapOf(drawerAccountKey(a) to "Ali", drawerAccountKey(b) to "Study")
        assertEquals("Ali", resolveDrawerIdentity(a, names, google).displayName)
        assertEquals("Study", resolveDrawerIdentity(b, names, google).displayName)
        assertNull(resolveDrawerIdentity(b, names, google).photoUrl)
        assertEquals("Ali", resolveDrawerIdentity(a, names, google).displayName)
    }
    @Test fun googleNamePrecedesEmailAndFullEmailIsNotShown() {
        assertEquals("Google Name", resolveDrawerIdentity(a, emptyMap(), google).displayName)
        assertEquals("a", resolveDrawerIdentity(a, emptyMap(), null).displayName)
        assertEquals("MyVault", resolveDrawerIdentity("", emptyMap(), google).displayName)
    }
    @Test fun localIdentityCannotInheritGoogleNameOrPhoto() {
        val names = mapOf("local" to "My study", drawerAccountKey(a) to "Ali")
        assertEquals("My study", resolveDrawerIdentity("", names, google).displayName)
        assertNull(resolveDrawerIdentity("", names, google).photoUrl)
        assertEquals("Ali", resolveDrawerIdentity(a, names, google).displayName)
    }
    @Test fun keysNormalizeAccountCaseWithoutExposingEmail() {
        assertEquals(drawerAccountKey(a), drawerAccountKey(" A@EXAMPLE.TEST "))
        assertNotEquals(drawerAccountKey(a), drawerAccountKey(b))
        assertFalse(drawerAccountKey(a).contains("@"))
        assertNotEquals("local", drawerAccountKey("local"))
    }
    @Test fun normalUnicodeAndReasonablePunctuationAreAllowed() {
        assertTrue(validDrawerName("  علي AH - Study!  "))
        assertEquals("علي AH", normalizedDrawerName("  علي AH  "))
        assertTrue(validDrawerName(""))
        assertTrue(validDrawerName("a".repeat(60)))
        assertFalse(validDrawerName("a".repeat(61)))
        assertEquals("Ali AH", normalizedDrawerName("Ali\nAH"))
        assertEquals("عA", drawerInitials("علي AH"))
    }
    @Test fun invalidPersistedNameFallsBackWithoutBreakingHeader() {
        assertEquals("Google Name", resolveDrawerIdentity(a, mapOf(drawerAccountKey(a) to "a".repeat(61)), google).displayName)
    }
    @Test fun onlyValidHttpsPhotoLocationsAreAccepted() {
        listOf("http://example.test/photo", "file:///private/photo", "content://photo", "https://user:secret@example.test/photo", "broken").forEach {
            assertNull(drawerPhotoUrl(it))
        }
        assertEquals(google.photoUrl, drawerPhotoUrl(google.photoUrl))
        assertNull(resolveDrawerIdentity(a, emptyMap(), google.copy(photoUrl = null)).photoUrl)
    }
    @Test fun photoCacheIdentityIncludesAccountEvenWhenUrlIsShared() {
        assertNotEquals(drawerPhotoCacheKey(DrawerIdentity(drawerAccountKey(a), "Ali", google.photoUrl)),
            drawerPhotoCacheKey(DrawerIdentity(drawerAccountKey(b), "Study", google.photoUrl)))
    }
}
