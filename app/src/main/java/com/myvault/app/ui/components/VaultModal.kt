package com.myvault.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultSpacing
import com.myvault.app.ui.theme.VaultThemeTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultModal(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.Transparent,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = VaultSpacing.screen, vertical = VaultSpacing.sm),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = VaultShapes.xl,
                color = colors.elevated,
                border = BorderStroke(1.dp, colors.borderStrong),
                tonalElevation = 0.dp,
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(VaultSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 42.dp, height = 4.dp),
                        color = colors.borderStrong,
                        shape = VaultShapes.pill,
                    ) {}
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (icon != null) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                color = colors.accentSoft,
                                shape = VaultShapes.md,
                                border = BorderStroke(1.dp, colors.accentBorder),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, null, modifier = Modifier.size(18.dp), tint = colors.accent)
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800),
                                color = colors.text,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp),
                            color = colors.inset,
                            shape = VaultShapes.sm,
                            border = BorderStroke(1.dp, colors.border),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(18.dp), tint = colors.textMuted)
                            }
                        }
                    }
                    content()
                }
            }
        }
    }
}

@Composable
fun VaultFormModal(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    destructiveLabel: String? = null,
    onDestructive: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    VaultModal(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        content()
        VaultModalActions(
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            enabled = enabled,
            onDismiss = onDismiss,
            destructiveLabel = destructiveLabel,
            onDestructive = onDestructive,
        )
    }
}

@Composable
fun VaultConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    dismissLabel: String = "Keep",
    destructive: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    VaultModal(
        title = title,
        icon = icon,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = colors.textSecondary)
            }
            Button(
                onClick = onConfirm,
                shape = VaultShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) colors.warning else colors.accent,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                ),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.W700)
            }
        }
    }
}

@Composable
fun VaultActionModal(
    title: String,
    actions: List<VaultModalAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    VaultModal(title = title, onDismiss = onDismiss, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.heightIn(max = 430.dp),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
        ) {
            items(actions) { action ->
                Surface(
                    onClick = action.onClick,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (action.selected) colors.accentSoft else colors.surface,
                    shape = VaultShapes.md,
                    border = BorderStroke(1.dp, if (action.selected) colors.accentBorder else colors.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            color = if (action.destructive) colors.warningSoft else colors.accentSoft,
                            shape = VaultShapes.sm,
                            border = BorderStroke(1.dp, if (action.destructive) colors.warning.copy(alpha = 0.38f) else colors.accentBorder),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (action.destructive) colors.warning else colors.accent,
                                )
                            }
                        }
                        Text(
                            text = action.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                            color = if (action.destructive) colors.warning else colors.text,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = VaultThemeTokens.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.inset,
        shape = VaultShapes.md,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
                color = colors.textMuted,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                textStyle = TextStyle(
                    color = colors.text,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                    fontWeight = FontWeight.W600,
                ),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = visualTransformation,
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                    innerTextField()
                },
            )
        }
    }
}

data class VaultModalAction(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun VaultModalActions(
    confirmLabel: String,
    onConfirm: () -> Unit,
    enabled: Boolean,
    onDismiss: () -> Unit,
    destructiveLabel: String?,
    onDestructive: (() -> Unit)?,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (destructiveLabel != null && onDestructive != null) {
            TextButton(onClick = onDestructive) {
                Text(destructiveLabel, color = colors.warning, fontWeight = FontWeight.W700)
            }
        }
        TextButton(onClick = onDismiss) {
            Text("Cancel", color = colors.textSecondary)
        }
        Button(
            onClick = onConfirm,
            enabled = enabled,
            shape = VaultShapes.pill,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = androidx.compose.ui.graphics.Color.White,
                disabledContainerColor = colors.inset,
                disabledContentColor = colors.textMuted,
            ),
        ) {
            Text(confirmLabel, fontWeight = FontWeight.W700)
        }
    }
}
