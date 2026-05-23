package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun SearchResultCard(
    result: SearchResultData,
    query: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Text(
                text = remember(result.title, query, colors) { highlightedText(result.title, query, colors.accent, colors.accentSoft) },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = remember(result.snippet, query, colors) { highlightedText(result.snippet, query, colors.accent, colors.accentSoft) },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xxs)) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.textMuted)
                Text(text = result.folder, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
    }
}

private fun highlightedText(
    text: String,
    query: String,
    accent: androidx.compose.ui.graphics.Color,
    accentSoft: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val start = text.indexOf(query, ignoreCase = true)
    if (start < 0) return AnnotatedString(text)
    val end = start + query.length
    return buildAnnotatedString {
        append(text)
        addStyle(
            style = SpanStyle(
                color = accent,
                background = accentSoft,
                fontWeight = FontWeight.W600,
            ),
            start = start,
            end = end,
        )
    }
}

@Preview(name = "SearchResultCard Light")
@Composable
private fun SearchResultCardLightPreview() {
    VaultComponentPreview(dark = false) {
        SearchResultCard(result = ComponentSamples.searchResult, query = "Attributes")
    }
}

@Preview(name = "SearchResultCard Dark")
@Composable
private fun SearchResultCardDarkPreview() {
    VaultComponentPreview(dark = true) {
        SearchResultCard(result = ComponentSamples.searchResult, query = "Attributes")
    }
}
