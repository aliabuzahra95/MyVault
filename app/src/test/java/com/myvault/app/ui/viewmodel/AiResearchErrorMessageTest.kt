package com.myvault.app.ui.viewmodel

import com.myvault.app.data.ai.ShamelaMcpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class AiResearchErrorMessageTest {
    @Test
    fun mapsAuthenticationFailureToReconnectAction() {
        assertEquals(
            "Shamela sign-in expired. Connect again.",
            ShamelaMcpException("raw server detail", 401).safeResearchMessage("fallback"),
        )
    }

    @Test
    fun mapsRateLimitWithoutExposingRawResponse() {
        assertEquals(
            "Shamela is temporarily rate limited. Try again shortly.",
            ShamelaMcpException("raw server detail", 429).safeResearchMessage("fallback"),
        )
    }

    @Test
    fun mapsOfflineAndTimeoutFailures() {
        assertEquals(
            "No network connection is available.",
            UnknownHostException().safeResearchMessage("fallback"),
        )
        assertEquals(
            "The request timed out. Try again.",
            SocketTimeoutException().safeResearchMessage("fallback"),
        )
    }

    @Test
    fun hidesUnexpectedInternalFailure() {
        assertEquals("Safe fallback", IllegalStateException("secret detail").safeResearchMessage("Safe fallback"))
    }
}
