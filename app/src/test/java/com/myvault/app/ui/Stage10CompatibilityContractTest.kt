package com.myvault.app.ui

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage10CompatibilityContractTest {

    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/java/com/myvault/app/ui/screens/HomeScreen.kt")) }

    @Test
    fun `batch pin remains workspace wide and selection controls stay reachable`() {
        val home = source("ui/screens/HomeScreen.kt")

        assertTrue(home.contains("if (manageSelectionMode) {\n            SelectionManageBar("))
        assertTrue(home.contains("selectedNotes.forEach { onSetNotePinnedClick(it.id, true) }"))
        assertFalse(home.contains("selectedNotes.forEach { onSetNoteFolderPinnedClick(it.id, true) }"))
    }

    @Test
    fun `dormant memorisation repeat choices remain engine only`() {
        val models = source("data/quran/memorization/MemorizationModels.kt")
        val memoriseScreen = source("ui/screens/FrozenMemoriseScreen.kt")
        val memoriseSession = source("ui/screens/FrozenMemoriseSession.kt")

        assertTrue(models.contains("Three(label = \"3x\", repeatCount = 3)"))
        assertTrue(models.contains("UntilStopped(label = \"Until stopped\", repeatCount = null)"))
        assertFalse(memoriseScreen.contains("MemorizationRepeatMode"))
        assertFalse(memoriseSession.contains("MemorizationRepeatMode"))
    }

    private fun source(relativePath: String): String =
        String(Files.readAllBytes(projectRoot.resolve("app/src/main/java/com/myvault/app/$relativePath")))
}
