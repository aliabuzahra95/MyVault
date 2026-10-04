package com.myvault.app.ui.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultContentTitlesTest {
    @Test
    fun `high contrast gives screen and drawer titles the same emphasis`() {
        assertEquals(FontWeight.W600, contentTitleFontWeight(true, FontWeight.W400))
        assertEquals(FontWeight.W600, contentTitleFontWeight(true, FontWeight.W500))
    }

    @Test
    fun `disabling high contrast preserves each existing title weight`() {
        assertEquals(FontWeight.W400, contentTitleFontWeight(false, FontWeight.W400))
        assertEquals(FontWeight.W500, contentTitleFontWeight(false, FontWeight.W500))
    }

    @Test
    fun `already emphasized pinned selected and folder titles stay emphasized`() {
        assertEquals(FontWeight(650), contentTitleFontWeight(true, FontWeight(650)))
        assertEquals(FontWeight.W700, contentTitleFontWeight(true, FontWeight.W700))
    }
}
