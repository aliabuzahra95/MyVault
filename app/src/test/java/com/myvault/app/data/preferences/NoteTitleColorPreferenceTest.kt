package com.myvault.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTitleColorPreferenceTest {
    @Test
    fun `standard remains the fallback for existing and invalid preferences`() {
        assertEquals(NoteTitleColorPreference.Standard, NoteTitleColorPreference.fromStoredValue(null))
        assertEquals(NoteTitleColorPreference.Standard, NoteTitleColorPreference.fromStoredValue(""))
        assertEquals(NoteTitleColorPreference.Standard, NoteTitleColorPreference.fromStoredValue("unknown"))
    }

    @Test
    fun `every supported note title colour round trips through storage`() {
        NoteTitleColorPreference.entries.forEach { option ->
            assertEquals(option, NoteTitleColorPreference.fromStoredValue(option.storedValue))
        }
    }
}
