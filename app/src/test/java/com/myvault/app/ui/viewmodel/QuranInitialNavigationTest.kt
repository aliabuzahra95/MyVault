package com.myvault.app.ui.viewmodel

import org.junit.Assert.*
import org.junit.Test

class QuranInitialNavigationTest {
    @Test fun explicitWidgetTargetWinsDelayedPreferences() {
        val navigation = QuranInitialNavigation()
        assertNull(navigation.request(4, 5))
        assertEquals(QuranInitialNavigation.Target(4, 5, true), navigation.initialize(16, 20))
    }

    @Test fun latestTargetWinsAndWarmRequestsAreImmediate() {
        val navigation = QuranInitialNavigation()
        navigation.request(4, 5)
        navigation.request(2, 255)
        assertEquals(QuranInitialNavigation.Target(2, 255, true), navigation.initialize(16, 20))
        assertEquals(QuranInitialNavigation.Target(4, 5, true), navigation.request(4, 5))
    }

    @Test fun ordinaryReaderEntryUsesSavedLocation() {
        assertEquals(QuranInitialNavigation.Target(16, 20, false), QuranInitialNavigation().initialize(16, 20))
    }
}
