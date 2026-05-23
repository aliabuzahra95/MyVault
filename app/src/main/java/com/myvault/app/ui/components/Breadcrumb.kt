package com.myvault.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun Breadcrumb(
    items: List<String>,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xxs),
    ) {
        items.forEachIndexed { index, item ->
            Text(
                text = item,
                modifier = Modifier.widthIn(max = 140.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = if (index == items.lastIndex) colors.textSecondary else colors.textMuted,
            )
            if (index != items.lastIndex) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(18.dp),
                    tint = colors.textMuted,
                )
            }
        }
    }
}

@Preview(name = "Breadcrumb Light")
@Composable
private fun BreadcrumbLightPreview() {
    VaultComponentPreview(dark = false) {
        Breadcrumb(listOf("Islamic Studies", "Aqeedah"))
    }
}

@Preview(name = "Breadcrumb Dark")
@Composable
private fun BreadcrumbDarkPreview() {
    VaultComponentPreview(dark = true) {
        Breadcrumb(listOf("Islamic Studies", "Aqeedah"))
    }
}
