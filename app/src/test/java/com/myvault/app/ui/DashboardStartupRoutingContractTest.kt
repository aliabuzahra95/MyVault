package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStartupRoutingContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")) }

    @Test
    fun `dashboard is the true initial route without a post-launch redirect`() {
        val navigation = String(
            Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/ui/navigation/VaultNavHost.kt")),
        )

        assertTrue(navigation.contains("startDestination = VaultDestination.Dashboard.route"))
        assertFalse(navigation.contains("startDestination = VaultDestination.Home.route"))
        assertFalse(navigation.contains("defaultLandingHandled"))
    }
}
