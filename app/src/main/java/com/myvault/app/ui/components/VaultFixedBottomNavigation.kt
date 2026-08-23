package com.myvault.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

data class VaultFixedBottomNavigationItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * The single persistent bottom navigation surface for every root workspace.
 * System navigation space is drawn as part of this surface so content ends above the bar.
 */
@Composable
fun VaultFixedBottomNavigation(
    items: List<VaultFixedBottomNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surface,
        contentColor = colors.textSecondary,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 1.dp,
                color = colors.border.copy(alpha = 0.82f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    VaultFixedBottomNavigationDestination(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onItemSelected(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultFixedBottomNavigationDestination(
    item: VaultFixedBottomNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary.copy(alpha = 0.76f),
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "fixedBottomNavContentColor",
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) colors.accentSoft else Color.Transparent,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "fixedBottomNavIndicatorColor",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(27.dp)
                .background(indicatorColor, VaultShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.W800 else FontWeight.W600,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
