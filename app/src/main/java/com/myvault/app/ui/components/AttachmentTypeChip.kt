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
fun AttachmentTypeChip(
    kind: AttachmentKind,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = if (selected) colors.accent else colors.surface,
        shape = VaultShapes.pill,
        border = BorderStroke(1.dp, if (selected) colors.accent else colors.border),
    ) {
        Text(
            text = kind.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) androidx.compose.ui.graphics.Color.White else colors.textSecondary,
        )
    }
}

@Preview(name = "AttachmentTypeChip Light")
@Composable
private fun AttachmentTypeChipLightPreview() {
    VaultComponentPreview(dark = false) {
        AttachmentTypeChip(kind = AttachmentKind.Pdf, selected = true)
    }
}

@Preview(name = "AttachmentTypeChip Dark")
@Composable
private fun AttachmentTypeChipDarkPreview() {
    VaultComponentPreview(dark = true) {
        AttachmentTypeChip(kind = AttachmentKind.Image, selected = false)
    }
}
