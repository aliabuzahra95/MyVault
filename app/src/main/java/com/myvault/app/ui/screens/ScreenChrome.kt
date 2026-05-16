package com.myvault.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun ScreenTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBackClick: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val colors = VaultThemeTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = VaultSpacing.screen),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            IconBtn(Icons.Rounded.ArrowBack, "Back", onClick = onBackClick)
            if (title != null) {
                Text(text = title, color = colors.text)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)) {
            actions()
        }
    }
}

@Composable
fun SearchMoreActions(
    onSearchClick: () -> Unit,
    onMoreClick: () -> Unit = {},
) {
    IconBtn(Icons.Rounded.Search, "Search", onClick = onSearchClick)
    IconBtn(Icons.Rounded.MoreHoriz, "More", onClick = onMoreClick)
}
