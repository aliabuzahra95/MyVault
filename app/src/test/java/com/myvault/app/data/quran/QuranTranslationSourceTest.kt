package com.myvault.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class QuranTranslationSourceTest {
    @Test
    fun storedValuesRestoreKnownTranslationSources() {
        assertEquals(
            QuranTranslationSource.SahihInternational,
            QuranTranslationSource.fromStoredValue("sahih_international"),
        )
        assertEquals(
            QuranTranslationSource.Maududi,
            QuranTranslationSource.fromStoredValue("maududi"),
        )
    }

    @Test
    fun missingOrUnknownValuesSafelyUseSahihInternational() {
        assertEquals(
            QuranTranslationSource.SahihInternational,
            QuranTranslationSource.fromStoredValue(null),
        )
        assertEquals(
            QuranTranslationSource.SahihInternational,
            QuranTranslationSource.fromStoredValue("unsupported"),
        )
    }

    @Test
    fun bundledMaududiTranslationContainsEveryVerseAndSourceAttribution() {
        val asset = listOf(
            File("src/main/assets/Maududi_en_tanzil.txt"),
            File("app/src/main/assets/Maududi_en_tanzil.txt"),
        ).firstOrNull(File::isFile)
        assertTrue("The bundled Maududi translation asset is missing", asset != null)

        val rawText = checkNotNull(asset).readText()
        val translations = parseMaududiTranslationAsset(rawText)

        assertEquals(6_236, translations.size)
        assertEquals(
            "In the name of Allah, the Merciful, the Compassionate",
            translations["1:1"],
        )
        assertEquals(
            "whether he be from the jinn or humans.”",
            translations["114:6"],
        )
        assertTrue(rawText.contains("Source: Tanzil.net"))
        assertTrue(rawText.contains("ID: en.maududi"))
    }

    @Test
    fun bundledMaududiParserPreservesPipesInsideTranslationText() {
        val translations = parseMaududiTranslationAsset(
            """
            1|1|First | second
            # Source metadata
            """.trimIndent(),
        )

        assertEquals(mapOf("1:1" to "First | second"), translations)
    }

    @Test
    fun groupedTafsirResponseSkipsEmptyPlaceholderForSameResource() {
        val response = JSONObject().put(
            "verse",
            JSONObject().put(
                "tafsirs",
                JSONArray()
                    .put(JSONObject().put("resource_id", 169).put("text", ""))
                    .put(JSONObject().put("resource_id", 14).put("text", "Arabic source"))
                    .put(
                        JSONObject()
                            .put("resource_id", 169)
                            .put("text", "<h2>The populated grouped Ibn Kathir commentary</h2>"),
                    ),
            ),
        )

        assertEquals(
            "<h2>The populated grouped Ibn Kathir commentary</h2>",
            response.selectRemoteTafsirHtml(169),
        )
    }

    @Test
    fun groupedTafsirResponsePrefersMostCompleteMatchingEntry() {
        val response = JSONObject().put(
            "verse",
            JSONObject().put(
                "tafsirs",
                JSONArray()
                    .put(JSONObject().put("resource_id", 169).put("text", "Short"))
                    .put(JSONObject().put("resource_id", 169).put("text", "Longer grouped commentary")),
            ),
        )

        assertEquals("Longer grouped commentary", response.selectRemoteTafsirHtml(169))
    }
}
