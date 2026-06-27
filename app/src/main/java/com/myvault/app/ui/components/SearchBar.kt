package com.myvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    placeholder: String = "Search notes and folders...",
    active: Boolean = false,
    query: String = "",
    onQueryChange: (String) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val borderColor by animateColorAsState(
        targetValue = if (active) colors.accentBorder else colors.border,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "search-border",
    )
    val iconTint by animateColorAsState(
        targetValue = if (active) colors.accent else colors.textMuted,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "search-icon",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp),
        color = colors.elevated,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (active) 2.dp else 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VaultSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.height(15.dp),
                tint = iconTint,
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                singleLine = true,
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Preview(name = "SearchBar Light")
@Composable
private fun SearchBarLightPreview() {
    VaultComponentPreview(dark = false) {
        SearchBar()
    }
}

@Preview(name = "SearchBar Dark")
@Composable
private fun SearchBarDarkPreview() {
    VaultComponentPreview(dark = true) {
        SearchBar(active = true)
    }
}
