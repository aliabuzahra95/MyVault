package com.myvault.app.data.quran

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class QuranAyahSearchIndexTest {
    @Test
    fun `canonical ayah index preserves verse keys and strips trailing verse numbers`() {
        val source = JSONObject()
            .put("1:1", JSONObject().put("text", "بِسْمِ ٱللَّهِ\u00a0١"))
            .put("2:255", JSONObject().put("text", "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ\u00a0٢٥٥"))
            .put("invalid", JSONObject().put("text", ""))

        assertEquals(
            mapOf(
                "1:1" to "بِسْمِ ٱللَّهِ",
                "2:255" to "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ",
            ),
            source.toQuranAyahSearchIndex(),
        )
    }
}
