package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.DarkVaultColors
import com.myvault.app.ui.theme.LightVaultColors
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun ThemePreviewCard(
    label: String,
    mode: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val previewBg = when (mode) {
        "light" -> LightVaultColors.bg
        "dark" -> DarkVaultColors.bg
        else -> Color.Transparent
    }
    val cardColor = when (mode) {
        "light" -> LightVaultColors.surface
        "dark" -> DarkVaultColors.surface
        else -> LightVaultColors.surface
    }

    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 88.dp, height = 96.dp),
        color = colors.surface,
        shape = VaultShapes.md,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.border),
    ) {
        Column(
            modifier = Modifier.padding(VaultSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 54.dp)
                    .background(
                        brush = if (mode == "auto") {
                            Brush.linearGradient(listOf(LightVaultColors.bg, DarkVaultColors.bg))
                        } else {
                            Brush.linearGradient(listOf(previewBg, previewBg))
                        },
                        shape = VaultShapes.sm,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(24.dp)
                        .background(cardColor, VaultShapes.sm),
                )
            }
            Spacer(modifier = Modifier.height(VaultSpacing.xs))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
        }
    }
}

@Preview(name = "ThemePreviewCard Light")
@Composable
private fun ThemePreviewCardLightPreview() {
    VaultComponentPreview(dark = false) {
        ThemePreviewCard(label = "Dark", mode = "dark", selected = true)
    }
}

@Preview(name = "ThemePreviewCard Dark")
@Composable
private fun ThemePreviewCardDarkPreview() {
    VaultComponentPreview(dark = true) {
        ThemePreviewCard(label = "Auto", mode = "auto", selected = false)
    }
}
