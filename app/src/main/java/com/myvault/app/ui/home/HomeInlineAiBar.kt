package com.myvault.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.ai.home.HomeAiAttachableItem
import com.myvault.app.ai.home.HomeAiAttachableType
import com.myvault.app.ui.theme.VaultShapes
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
fun HomeInlineAiBar(
    input: String,
    suggestions: List<HomeAiAttachableItem>,
    isStreaming: Boolean,
    attachmentTrayOpen: Boolean,
    focusRequester: FocusRequester? = null,
    onInputChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSuggestionClick: (HomeAiAttachableItem) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaultThemeTokens.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, VaultShapes.lg, clip = false),
                color = colors.elevated,
                shape = VaultShapes.lg,
                border = BorderStroke(1.dp, colors.border),
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Suggestions",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900),
                        color = colors.textMuted,
                    )
                    suggestions.forEach { item ->
                        SuggestionRow(item = item, onClick = { onSuggestionClick(item) })
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = colors.elevated,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = onAttachClick,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = if (attachmentTrayOpen) "Close attachments" else "Attach",
                        modifier = Modifier.size(16.dp),
                        tint = if (attachmentTrayOpen) colors.accent else colors.textSecondary,
                    )
                }

                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .weight(1f)
                        .heightIn(min = 36.dp, max = 120.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
                    cursorBrush = SolidColor(colors.accent),
                    enabled = !isStreaming,
                    decorationBox = { innerTextField ->
                        if (input.isBlank()) {
                            Text("Ask AI...", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                        }
                        innerTextField()
                    },
                )

                if (isStreaming) {
                    Surface(
                        onClick = onStopClick,
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Stop, null, modifier = Modifier.size(16.dp), tint = colors.text)
                        }
                    }
                } else {
                    Surface(
                        onClick = onSendClick,
                        enabled = input.isNotBlank(),
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = if (input.isNotBlank()) colors.accent else colors.surface,
                        contentColor = if (input.isNotBlank()) Color.White else colors.textMuted,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.ArrowUpward, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(item: HomeAiAttachableItem, onClick: () -> Unit) {
    VaultAiSelectableRow(
        title = item.title,
        subtitle = null,
        sourceType = item.type,
        selected = false,
        enabled = true,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
    )
}

@Composable
fun SourceCapsule(type: HomeAiAttachableType) {
    val colors = VaultThemeTokens.colors
    val container = when (type) {
        HomeAiAttachableType.Study -> colors.accentSoft
        HomeAiAttachableType.Course -> colors.warningSoft
        HomeAiAttachableType.ConceptCard -> colors.accentSoft
        HomeAiAttachableType.Pdf -> colors.warningSoft
        HomeAiAttachableType.CourseContext -> colors.accentSoft
    }
    val content = when (type) {
        HomeAiAttachableType.Study -> colors.accent
        HomeAiAttachableType.Course -> colors.warning
        HomeAiAttachableType.ConceptCard -> colors.text
        HomeAiAttachableType.Pdf -> colors.warning
        HomeAiAttachableType.CourseContext -> colors.accent
    }
    Text(
        text = type.label,
        modifier = Modifier
            .background(container, VaultShapes.pill)
            .border(1.dp, content.copy(alpha = 0.28f), VaultShapes.pill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W900),
        color = content,
        maxLines = 1,
    )
}
