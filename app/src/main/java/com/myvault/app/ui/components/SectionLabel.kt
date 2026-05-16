package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun SectionLabel(
    label: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    accentAction: Boolean = true,
    onActionClick: () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = VaultSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
        )
        if (actionLabel != null) {
            Surface(
                onClick = onActionClick,
                shape = VaultShapes.pill,
                color = colors.bg,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.heightIn(min = 28.dp),
            ) {
                Text(
                    text = actionLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (accentAction) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

@Preview(name = "SectionLabel Light")
@Composable
private fun SectionLabelLightPreview() {
    VaultComponentPreview(dark = false) {
        SectionLabel(label = "Pinned", actionLabel = "Manage")
    }
}

@Preview(name = "SectionLabel Dark")
@Composable
private fun SectionLabelDarkPreview() {
    VaultComponentPreview(dark = true) {
        SectionLabel(label = "Pinned", actionLabel = "Manage")
    }
}
