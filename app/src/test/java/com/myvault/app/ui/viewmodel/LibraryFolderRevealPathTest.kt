package com.myvault.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFolderRevealPathTest {
    @Test
    fun `nested folder path includes every ancestor and target`() {
        val parents = mapOf(
            "aqeedah" to null,
            "names" to "aqeedah",
            "pdfs" to "names",
        )

        assertEquals(listOf("aqeedah", "names", "pdfs"), libraryFolderPathIds("pdfs", parents))
        assertEquals(emptyList<String>(), libraryFolderPathIds("missing", parents))
    }

    @Test
    fun `corrupt parent cycle cannot loop forever`() {
        assertEquals(
            listOf("parent", "child"),
            libraryFolderPathIds("child", mapOf("child" to "parent", "parent" to "child")),
        )
    }
}
