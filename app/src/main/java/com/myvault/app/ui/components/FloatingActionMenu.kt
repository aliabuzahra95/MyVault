package com.myvault.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun FloatingActionMenu(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    actions: List<FloatingAction> = defaultFloatingActions,
    mainButtonSize: Dp = 56.dp,
    actionButtonSize: Dp = 44.dp,
    onToggle: () -> Unit = {},
    onActionClick: (FloatingAction) -> Unit = {},
) {
    val colors = VaultThemeTokens.colors
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "fabRotation",
    )

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = expanded,
            enter = scaleIn(
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                initialScale = 0.94f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 5 },
            ) + expandVertically(
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                expandFrom = Alignment.Bottom,
            ),
            exit = scaleOut(
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
                targetScale = 0.96f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f),
            ) + slideOutVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                targetOffsetY = { it / 5 },
            ) + shrinkVertically(
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Bottom,
            ),
        ) {
            Column(
                modifier = Modifier.padding(bottom = 68.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actions.forEach { action ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = VaultShapes.pill,
                            color = colors.elevated,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Text(
                                text = action.label,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                color = colors.text,
                                fontWeight = FontWeight.W600,
                            )
                        }
                        SmallFloatingActionButton(
                            onClick = { onActionClick(action) },
                            modifier = Modifier.size(actionButtonSize),
                            shape = VaultShapes.md,
                            containerColor = colors.elevated,
                            contentColor = colors.accent,
                        ) {
                            Icon(action.icon, contentDescription = action.label, modifier = Modifier.size(if (actionButtonSize < 42.dp) 14.dp else 15.dp))
                        }
                    }
                }
            }
        }
        SmallFloatingActionButton(
            onClick = onToggle,
            modifier = Modifier.size(mainButtonSize),
            shape = VaultShapes.lg,
            containerColor = colors.accent,
            contentColor = Color.White,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Create", modifier = Modifier.size(if (mainButtonSize < 52.dp) 21.dp else 24.dp).rotate(rotation))
        }
    }
}

data class FloatingAction(val label: String, val icon: ImageVector)

val defaultFloatingActions = listOf(
    FloatingAction("New Note", Icons.Rounded.Description),
    FloatingAction("New Folder", Icons.Rounded.CreateNewFolder),
)

@Preview(name = "FloatingActionMenu Light")
@Composable
private fun FloatingActionMenuLightPreview() {
    VaultComponentPreview(dark = false) {
        FloatingActionMenu(expanded = true)
    }
}

@Preview(name = "FloatingActionMenu Dark")
@Composable
private fun FloatingActionMenuDarkPreview() {
    VaultComponentPreview(dark = true) {
        FloatingActionMenu(expanded = true)
    }
}
