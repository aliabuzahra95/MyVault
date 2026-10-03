package com.myvault.app.data.narration

/** Azure-only rendering of the Director's existing units; no additional document parsing. */
internal object AzureNarrationSsml {
    const val CacheVersion = "lecture-v1-rate-minus8"
    const val Rate = "-8%"

    fun chunkUnits(units: List<NarrationUnit>, start: Int, end: Int): List<NarrationUnit> {
        var offset = 0
        return units.mapNotNull { unit ->
            val unitStart = offset
            val unitEnd = unitStart + unit.spokenText.length
            offset = unitEnd + 2 // The prepared units are joined with a blank line.
            val from = maxOf(start, unitStart)
            val to = minOf(end, unitEnd)
            if (from >= to) null else unit.copy(
                spokenText = unit.spokenText.substring(from - unitStart, to - unitStart).trim(),
                pauseBeforeMs = if (from == unitStart) unit.pauseBeforeMs else 0,
                pauseAfterMs = if (to == unitEnd) unit.pauseAfterMs else 0,
            )
        }.filter { it.spokenText.isNotBlank() }
    }

    fun build(units: List<NarrationUnit>, segmenter: BilingualTextSegmenter,
        englishVoice: String, arabicVoice: String): String = buildString {
        append("""<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="en-AU">""")
        var previousPause = 0L
        var previousLanguage: NarrationLanguage? = null
        val spokenUnits = units.filter { it.spokenText.isNotBlank() }
        spokenUnits.forEachIndexed { unitIndex, unit ->
            val segments = segmenter.segmentText(unit.spokenText, englishVoice = englishVoice, arabicVoice = arabicVoice)
            segments.forEachIndexed { segmentIndex, segment ->
                val structuralPause = if (segmentIndex == 0) {
                    maxOf(previousPause, unit.pauseBeforeMs)
                } else 0L
                val transitionPause = if (previousLanguage != null && previousLanguage != segment.language) 500L else 0L
                val pause = maxOf(structuralPause, transitionPause)
                append("<voice name=\"").append(BilingualTextSegmenter.xmlEscape(segment.voice))
                append("\"><lang xml:lang=\"").append(BilingualTextSegmenter.xmlEscape(segment.locale)).append("\">")
                if (pause > 0) append("<break time=\"").append(pause).append("ms\"/>")
                append("<prosody rate=\"").append(Rate).append("\">")
                append(BilingualTextSegmenter.xmlEscape(segment.text.trim()))
                append("</prosody>")
                if (unitIndex == spokenUnits.lastIndex && segmentIndex == segments.lastIndex && unit.pauseAfterMs > 0) {
                    append("<break time=\"").append(unit.pauseAfterMs).append("ms\"/>")
                }
                append("</lang></voice>")
                previousLanguage = segment.language
            }
            previousPause = unit.pauseAfterMs
        }
        append("</speak>")
    }
}
