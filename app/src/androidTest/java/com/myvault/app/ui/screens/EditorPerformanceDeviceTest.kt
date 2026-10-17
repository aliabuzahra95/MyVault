package com.myvault.app.ui.screens

import android.util.Log
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myvault.app.ui.theme.LightVaultColors
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

/** Disposable documents only; timings exclude network, Room and real user documents. */
@RunWith(AndroidJUnit4::class)
class EditorPerformanceDeviceTest {
    @Test fun mixedLargeDocumentHotPaths() {
        for (words in listOf(5_000, 20_000, 50_000)) {
            val text = (0 until words).joinToString(" ") {
                when (it % 8) { 0 -> "\nParagraph"; 3 -> "العربية"; 4 -> "المحفوظة"; else -> "word$it" }
            }
            val marks = listOf(VaultStyleMark(0, 10, VaultInlineStyle.Heading),
                VaultStyleMark(30, text.length / 2, VaultInlineStyle.Bold))
            val transform = VaultRichTextVisualTransformation(marks, colors = LightVaultColors)
            val input = AnnotatedString(text)
            val initial = TextFieldValue(text, TextRange(text.length / 2))
            val snapshot = EditorHistorySnapshot(TextFieldValue("Synthetic"), initial, marks, emptyList(), emptySet())
            val history = EditorHistory(listOf(snapshot))
            fun median(operation: () -> Unit): Double {
                repeat(3) { operation() }
                return List(9) { measureNanoTime(operation) / 1_000_000.0 }.sorted()[4]
            }
            val render = median { transform.filter(input) }
            val cursor = median {
                repeat(30) { i ->
                    val moved = initial.copy(selection = TextRange(text.length / 2 + i))
                    val display = transform.filter(moved.annotatedString)
                    display.offsetMapping.transformedToOriginal(display.offsetMapping.originalToTransformed(moved.selection.end))
                    history.record(snapshot.copy(body = moved))
                }
            }
            val deletion = median {
                val range = TextRange(text.length / 4, text.length * 3 / 4)
                val selected = initial.copy(selection = range)
                val changed = selected.copy(text = text.removeRange(range.start, range.end), selection = TextRange(range.start))
                val updatedMarks = handleVaultRichTextChange(selected, changed, marks, emptySet())
                val next = snapshot.copy(body = changed, styleMarks = updatedMarks)
                val undo = history.record(next).undo(next)!!
                assertEquals(text, undo.snapshot.body.text)
                assertEquals(marks, undo.snapshot.styleMarks)
                assertEquals(text.take(range.start) + text.drop(range.end), changed.text)
            }
            Log.i("EditorPerformance", "words=$words renderMs=$render cursor30Ms=$cursor deleteUndoMs=$deletion")
            println("words=$words renderMs=$render cursor30Ms=$cursor deleteUndoMs=$deletion")
        }
    }
}
