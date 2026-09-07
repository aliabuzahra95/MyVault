package com.myvault.app.ui.screens

import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.MainActivity
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.PdfAnnotationEntity
import com.myvault.app.ui.theme.VaultTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production sheet with disposable in-memory rows, never a user database. */
@RunWith(AndroidJUnit4::class)
class PdfAnnotationSheetInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation

    private fun nodes(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
        if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { nodes(node.getChild(it)) }

    private fun find(label: String): AccessibilityNodeInfo {
        repeat(100) {
            nodes(automation.rootInActiveWindow).firstOrNull {
                it.text?.toString() == label || it.contentDescription?.toString() == label
            }?.let { return it }
            SystemClock.sleep(50)
        }
        error("Missing visible control: $label")
    }

    private fun bounds(label: String) = Rect().also { find(label).getBoundsInScreen(it) }
    private fun tap(label: String) {
        val rect = bounds(label)
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, rect.centerX().toFloat(), rect.centerY().toFloat(), 0)
            automation.injectInputEvent(event, true)
            event.recycle()
        }
        SystemClock.sleep(500)
    }

    private fun swipe(x: Float, from: Float, to: Float) {
        val down = SystemClock.uptimeMillis()
        for (step in 0..12) {
            val action = when (step) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, from + (to - from) * step / 12, 0)
            automation.injectInputEvent(event, true)
            event.recycle()
            SystemClock.sleep(10)
        }
        SystemClock.sleep(250)
    }

    private fun screenshot(name: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("screencap -p /data/local/tmp/pdf-$name.png"),
        ).use { it.readBytes() }
    }

    @Test fun handleIsTapOnlyAndRepeatedBottomFlingsKeepTheSheetStable() {
        val file = File(instrumentation.targetContext.cacheDir, "pdf-sheet-fixture.pdf")
        val pdf = PdfDocument()
        try {
            repeat(12) { number ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(600, 800, number).create())
                page.canvas.drawText("Disposable annotation page ${number + 1}", 60f, 100f, android.graphics.Paint().apply { textSize = 20f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val attachment = AttachmentEntity("sheet-test", "", fileName = "Sheet test.pdf", mimeType = "application/pdf", sizeBytes = file.length(), localPath = file.path, remoteUrl = null, createdAt = 1)
        val annotations = (0 until 45).map { index ->
            PdfAnnotationEntity("web-$index", attachment.id, null, 11, .1f, .1f, .9f, .3f, "yellow", if (index % 3 == 0) "Full note $index\nSecond paragraph\nThird paragraph" else null, selectedText = if (index == 44) "Final highlight" else null, createdAt = index.toLong(), updatedAt = index.toLong())
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    var filter by remember { mutableStateOf(PdfActivityFilter.All) }
                    var scope by remember { mutableStateOf(PdfAnnotationScope.AllPages) }
                    VaultTheme {
                        FrozenLocalPdfActivitySheet(attachment, annotations, emptyList(), emptyList(), 45, 15, 11, filter, { filter = it }, scope, { scope = it }, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
            val partial = bounds("Expand annotations").top
            screenshot("half")
            tap("Expand annotations")
            val expanded = bounds("Collapse annotations").top
            assertTrue("Tap must expand", expanded < partial - 100)
            screenshot("expanded")
            val header = bounds("Annotations")
            swipe(header.centerX().toFloat(), header.centerY().toFloat(), header.centerY() + 200f)
            assertEquals("Header swipe must not resize", expanded, bounds("Collapse annotations").top)
            val footer = bounds("View all activity")
            repeat(18) { swipe(footer.centerX().toFloat(), footer.top - 30f, header.bottom + 210f) }
            val lastRow = bounds("Final highlight")
            repeat(12) {
                assertEquals("Fling must not move sheet", expanded, bounds("Collapse annotations").top)
                assertEquals("Footer must remain fixed", footer, bounds("View all activity"))
                assertEquals("Last row must settle without jumping", lastRow, bounds("Final highlight"))
                SystemClock.sleep(50)
            }
            screenshot("bottom")
            for (label in listOf("Highlights", "Notes", "Study links", "All")) {
                tap(label)
                swipe(footer.centerX().toFloat(), footer.top - 30f, header.bottom + 210f)
                assertEquals("Filter swipe must not resize", expanded, bounds("Collapse annotations").top)
            }
            tap("Collapse annotations")
            assertEquals("Tap must return to half height", partial, bounds("Expand annotations").top)
        }
        file.delete()
    }
}
