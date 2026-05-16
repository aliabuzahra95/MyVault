package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun PinnedNoteCard(
    note: VaultNoteCardData,
    modifier: Modifier = Modifier,
    previewLines: Int = 0,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 104.dp, height = 64.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(if (note.tableCount > 0) 76.dp else 86.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    maxLines = if (previewLines > 0) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (previewLines > 0 && note.preview.isNotBlank()) {
                    Text(
                        text = note.preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        maxLines = previewLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (note.tableCount > 0) {
                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        imageVector = Icons.Rounded.TableChart,
                        contentDescription = "Has table",
                        modifier = Modifier.size(12.dp),
                        tint = colors.textMuted,
                    )
                }
            }
        }
    }
}

@Preview(name = "PinnedNoteCard Light")
@Composable
private fun PinnedNoteCardLightPreview() {
    VaultComponentPreview(dark = false) {
        PinnedNoteCard(note = ComponentSamples.pinnedNote)
    }
}

@Preview(name = "PinnedNoteCard Dark")
@Composable
private fun PinnedNoteCardDarkPreview() {
    VaultComponentPreview(dark = true) {
        PinnedNoteCard(note = ComponentSamples.pinnedNote)
    }
}
