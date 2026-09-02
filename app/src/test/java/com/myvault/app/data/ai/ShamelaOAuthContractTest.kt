package com.myvault.app.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShamelaOAuthContractTest {
    @Test
    fun nativeClientUsesRegisteredRedirectAndMcpResource() {
        assertEquals("com.myvault.app:/oauth2redirect/shamela", ShamelaAuthRepository.RedirectEndpoint)
        assertEquals("https://shamela.link/mcp", ShamelaAuthRepository.ResourceEndpoint)
        assertTrue(ShamelaAuthRepository.Scope.contains("offline_access"))
    }
}
