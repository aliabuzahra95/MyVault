package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = colors.accentSoft,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, colors.accentBorder),
    ) {
        Text(
            text = "#$label",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.accent,
        )
    }
}

@Preview(name = "TagChip Light")
@Composable
private fun TagChipLightPreview() {
    VaultComponentPreview(dark = false) {
        TagChip(label = "aqeedah")
    }
}

@Preview(name = "TagChip Dark")
@Composable
private fun TagChipDarkPreview() {
    VaultComponentPreview(dark = true) {
        TagChip(label = "aqeedah")
    }
}
