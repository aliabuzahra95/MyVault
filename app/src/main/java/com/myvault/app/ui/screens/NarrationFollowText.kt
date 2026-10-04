package com.myvault.app.ui.screens

import kotlin.math.abs
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.myvault.app.data.narration.NarrationPlaybackStatus

// Narrated text has normalized whitespace; map it back to the displayed source offsets.
internal fun narrationTextRange(text: String, sentence: String, hint: Int = 0,
    context: String = "", contextOffset: Int = 0): IntRange? {
    if (text.isEmpty() || sentence.isBlank()) return null
    fun pattern(value: String) = Regex(value.trim().split(Regex("[\\s\\u00A0]+"))
        .joinToString("[\\s\\u00A0\\u200E\\u200F\\u2066-\\u2069]+") { Regex.escape(it) })
    val contextRange = context.takeIf { it.isNotBlank() }?.let {
        pattern(it).findAll(text).minByOrNull { match -> abs(match.range.first - (hint - contextOffset)) }?.range
    }
    val sourceHint = contextRange?.let { range ->
        val prefix = context.take(contextOffset.coerceIn(0, context.length)).trim()
        if (prefix.isEmpty()) range.first else pattern(prefix).find(text, range.first)?.range?.last?.plus(1) ?: range.first
    } ?: hint
    val candidates = pattern(sentence).findAll(text).filter {
        contextRange == null || (it.range.first >= contextRange.first && it.range.last <= contextRange.last)
    }
    return candidates.minByOrNull { abs(it.range.first - sourceHint) }?.range
}

internal fun narrationCenteredScroll(top: Float, bottom: Float, viewport: Float, maxScroll: Int): Int =
    ((top + bottom) / 2f - viewport.coerceAtLeast(1f) / 2f).toInt().coerceIn(0, maxScroll)

internal fun narrationToolbarVisible(previous: Boolean, deltaY: Float, userInput: Boolean): Boolean =
    if (!userInput || deltaY == 0f) previous else deltaY > 0f

internal fun narrationShouldFollow(status: NarrationPlaybackStatus, touching: Boolean): Boolean =
    !touching && status in setOf(NarrationPlaybackStatus.Playing, NarrationPlaybackStatus.Generating,
        NarrationPlaybackStatus.Preparing)

internal class NarrationViewportGesture {
    var touching by mutableStateOf(false)
    var releasedAtMs by mutableLongStateOf(0L)
}

@Composable
internal fun rememberNarrationViewportGesture(sourceId: Any?): NarrationViewportGesture =
    remember(sourceId) { NarrationViewportGesture() }

// Observe unconsumed AND consumed finger motion, without intercepting selection or scrolling.
internal fun Modifier.narrationViewportGesture(gesture: NarrationViewportGesture,
    onManualScroll: (Float) -> Unit = {}): Modifier = pointerInput(gesture) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        gesture.touching = true
        var previousY = down.position.y
        var travelled = 0f
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null) {
                    val delta = change.position.y - previousY
                    previousY = change.position.y
                    travelled += abs(delta)
                    if (travelled > viewConfiguration.touchSlop && delta != 0f) onManualScroll(delta)
                }
            } while (event.changes.any { it.pressed })
        } finally {
            gesture.touching = false
            gesture.releasedAtMs = android.os.SystemClock.uptimeMillis()
        }
    }
}
