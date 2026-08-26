package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun CompactWorkspaceHeader(
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
    searchOpen: Boolean = false,
    searchQuery: String = "",
    searchPlaceholder: String = "Search notes and folders...",
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (searchOpen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    modifier = Modifier.weight(1f),
                    placeholder = searchPlaceholder,
                    query = searchQuery,
                    active = true,
                    onQueryChange = onSearchQueryChange,
                    requestFocus = true,
                )
                CompactViewAction(
                    icon = Icons.Rounded.Close,
                    selected = false,
                    description = "Close search",
                    onClick = onSearchClose,
                )
            }
            return@BoxWithConstraints
        }
        val narrow = maxWidth < 300.dp
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactWorkspaceTitle(title = title, metadata = metadata)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompactWorkspaceTitle(
                    title = title,
                    metadata = metadata,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
private fun CompactWorkspaceTitle(
    title: String,
    metadata: String,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(modifier = modifier) {
        Text(
            text = title,
            color = colors.text,
            fontSize = 27.sp,
            fontWeight = FontWeight.W800,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = metadata,
            modifier = Modifier.padding(top = 4.dp),
            color = colors.textSecondary,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CompactActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier,
        color = colors.surface,
        shape = VaultShapes.sm,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun CompactViewAction(
    icon: ImageVector,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            color = if (selected) colors.elevated else Color.Transparent,
            contentColor = if (selected) colors.accent else colors.textMuted,
            shape = VaultShapes.sm,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, description, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
fun CompactPrimaryAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 46.dp,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(buttonSize),
        color = colors.accent,
        contentColor = Color.White,
        shape = VaultShapes.sm,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, modifier = Modifier.size(if (buttonSize < 46.dp) 18.dp else 20.dp))
        }
    }
}
