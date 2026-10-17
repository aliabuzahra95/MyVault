package com.myvault.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

internal fun editorSelectionVelocity(y: Float, top: Float, bottom: Float, band: Float, maxSpeed: Float): Float {
    if (bottom <= top || band <= 0f) return 0f
    val edge = minOf(band, (bottom - top) / 3f)
    return when {
        y < top + edge -> -maxSpeed * ((top + edge - y) / edge).coerceIn(0f, 1f)
        y > bottom - edge -> maxSpeed * ((y - bottom + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

internal fun editorExtendedSelection(selection: TextRange, movingStart: Boolean, offset: Int): TextRange =
    if (movingStart) TextRange(offset, selection.end) else TextRange(selection.start, offset)

/** Native TextField still owns editing/selection; these handles add outer-viewport edge scrolling. */
@Composable
internal fun EditorSelectionHandles(
    value: TextFieldValue,
    layout: TextLayoutResult?,
    mapping: OffsetMapping,
    fieldOrigin: Offset,
    viewport: Rect,
    scroll: ScrollableState,
    color: Color,
    onDragActive: (Boolean) -> Unit,
    onSelection: (TextRange) -> Unit,
) {
    if (value.selection.collapsed || layout == null || viewport.height <= 2f) return
    val currentValue by rememberUpdatedState(value)
    val currentLayout by rememberUpdatedState(layout)
    val currentMapping by rememberUpdatedState(mapping)
    val currentSelection by rememberUpdatedState(onSelection)
    val currentDragActive by rememberUpdatedState(onDragActive)
    val currentFieldOrigin by rememberUpdatedState(fieldOrigin)
    val currentViewport by rememberUpdatedState(viewport)
    var draggingStart by remember { mutableStateOf<Boolean?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var contentOrigin by remember { mutableStateOf(Offset.Zero) }
    var popupOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragScroll by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val handleSize = with(density) { 36.dp.toPx() }
    val edgeBand = with(density) { 56.dp.toPx() }
    val maxSpeed = with(density) { 680.dp.toPx() }

    fun extend() {
        val start = draggingStart ?: return
        val y = pointer.y.coerceIn(currentViewport.top + 1f, currentViewport.bottom - 1f)
        val position = Offset(pointer.x - contentOrigin.x, y - contentOrigin.y + dragScroll)
        val offset = currentMapping.transformedToOriginal(currentLayout.getOffsetForPosition(position))
            .coerceIn(0, currentValue.text.length)
        val selection = editorExtendedSelection(currentValue.selection, start, offset)
        if (!selection.collapsed) currentSelection(selection)
    }

    LaunchedEffect(draggingStart) {
        if (draggingStart == null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (draggingStart != null) {
            val now = withFrameNanos { it }
            if (draggingStart == null) break
            val seconds = ((now - last) / 1_000_000_000f).coerceAtMost(0.032f)
            last = now
            val velocity = editorSelectionVelocity(pointer.y, currentViewport.top, currentViewport.bottom, edgeBand, maxSpeed)
            if (velocity != 0f) {
                dragScroll += scroll.scrollBy(velocity * seconds)
                extend()
            }
        }
    }
    DisposableEffect(Unit) { onDispose { currentDragActive(false) } }

    for (start in listOf(true, false)) {
        key(start) {
            val offset = if (start) value.selection.start else value.selection.end
            val cursor = layout.getCursorRect(mapping.originalToTransformed(offset))
            val anchor = fieldOrigin + Offset(cursor.left, cursor.bottom)
            val visible = anchor.y in viewport.top..viewport.bottom
            if (visible || draggingStart == start) {
                val origin = if (draggingStart == start) popupOrigin else anchor - Offset(handleSize / 2f, 0f)
                val latestOrigin by rememberUpdatedState(origin)
                val latestAnchor by rememberUpdatedState(anchor)
                val provider = remember(origin) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                            layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
                            IntOffset(origin.x.toInt(), origin.y.toInt())
                    }
                }
                Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                    Canvas(Modifier.size(36.dp).testTag(if (start) "EditorSelectionStart" else "EditorSelectionEnd")
                        .pointerInput(start) {
                            detectDragGestures(
                                onDragStart = {
                                    popupOrigin = latestOrigin
                                    contentOrigin = currentFieldOrigin
                                    dragScroll = 0f
                                    pointer = latestAnchor - Offset(0f, 1f)
                                    draggingStart = start
                                    currentDragActive(true)
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    pointer += delta
                                    extend()
                                },
                                onDragEnd = { draggingStart = null; currentDragActive(false) },
                                onDragCancel = { draggingStart = null; currentDragActive(false) },
                            )
                        }) {
                        val radius = size.width / 3f
                        drawCircle(color, radius, Offset(size.width / 2f, radius))
                        drawRect(color, Offset(size.width / 2f - radius / 2f, 0f),
                            androidx.compose.ui.geometry.Size(radius / 2f, radius))
                    }
                }
            }
        }
    }
}
