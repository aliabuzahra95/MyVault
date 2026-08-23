package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun IconBtn(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Surface(
        onClick = onClick,
        modifier = modifier.size(38.dp),
        color = colors.elevated.copy(alpha = 0.96f),
        contentColor = if (active) colors.accent else colors.textSecondary,
        border = BorderStroke(
            width = 1.dp,
            color = if (active) colors.accent.copy(alpha = 0.48f) else colors.border,
        ),
        shape = VaultShapes.md,
        tonalElevation = 0.dp,
        shadowElevation = 0.5.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview(name = "IconBtn Light")
@Composable
private fun IconBtnLightPreview() {
    VaultComponentPreview(dark = false) {
        Box(contentAlignment = Alignment.Center) {
            IconBtn(icon = Icons.Rounded.Settings, contentDescription = null.toString())
        }
    }
}

@Preview(name = "IconBtn Dark")
@Composable
private fun IconBtnDarkPreview() {
    VaultComponentPreview(dark = true) {
        Box(contentAlignment = Alignment.Center) {
            IconBtn(icon = Icons.Rounded.WbSunny, contentDescription = null.toString(), active = true)
        }
    }
}
