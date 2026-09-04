package com.myvault.app.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.R
import com.myvault.app.data.quran.QuranAyah
import com.myvault.app.data.quran.QuranReflectionItem
import com.myvault.app.data.quran.SurahInfo
import com.myvault.app.ui.components.IconBtn
import com.myvault.app.ui.theme.VaultThemeTokens

private val QuranReflectionUthmaniHafsFamily = FontFamily(
    Font(R.font.uthmani_hafs, weight = FontWeight.Normal),
)
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AyahActionsSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    isBookmarked: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCreateReflectionNote: () -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sectionShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 560.dp)
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp)
                    .padding(top = 6.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Ayah actions",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                    color = colors.text,
                )
                IconBtn(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close ayah actions",
                    onClick = onDismiss,
                )
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),
                        color = colors.text,
                    )
                    if (isBookmarked) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.accentSoft)
                                .border(1.dp, colors.accentBorder, RoundedCornerShape(9.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "Bookmarked",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.W600),
                                color = colors.accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ayah.arabicText,
                    style = TextStyle(
                        fontFamily = QuranReflectionUthmaniHafsFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        textDirection = TextDirection.Rtl,
                        lineHeight = 39.sp,
                    ),
                    color = colors.text,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
                    .clip(sectionShape)
                    .background(colors.surface)
                    .border(1.dp, colors.border.copy(alpha = 0.78f), sectionShape),
            ) {
                AyahActionRow(
                    label = "Copy ayah",
                    icon = Icons.Rounded.ContentCopy,
                    onClick = onCopy,
                )
                AyahActionRow(
                    label = if (isBookmarked) "Remove bookmark" else "Bookmark ayah",
                    icon = Icons.Rounded.Bookmark,
                    onClick = onToggleBookmark,
                )
                AyahActionRow(
                    label = "Add reflection/note",
                    icon = Icons.Rounded.Edit,
                    onClick = onCreateReflectionNote,
                    isLast = true,
                )
            }
        }
    }
}

@Composable
private fun AyahActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isLast: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 14.dp),
            color = colors.border.copy(alpha = 0.65f),
            thickness = 1.dp,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ReflectionEditorSheet(
    ayah: QuranAyah?,
    surah: SurahInfo,
    existingReflection: QuranReflectionItem?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    if (ayah == null) return

    val colors = VaultThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable(ayah.verseKey, existingReflection?.noteId) {
        mutableStateOf(existingReflection?.title ?: "Reflection on ${surah.name} ${surah.num}:${ayah.ayahNumber}")
    }
    var body by rememberSaveable(ayah.verseKey, existingReflection?.noteId) {
        mutableStateOf(existingReflection?.reflectionBody.orEmpty())
    }
    val fieldShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
        contentColor = colors.text,
        scrimColor = colors.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 2.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 620.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = if (existingReflection == null) "Add reflection" else "Edit reflection",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W900),
                        color = colors.text,
                    )
                    Text(
                        text = "${surah.name} ${surah.num}:${ayah.ayahNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                IconBtn(Icons.Rounded.Close, "Close reflection", onClick = onDismiss)
            }

            Surface(
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.78f)),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = ayah.arabicText,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            fontFamily = QuranReflectionUthmaniHafsFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            textDirection = TextDirection.Rtl,
                            lineHeight = 38.sp,
                        ),
                        color = colors.text,
                        textAlign = TextAlign.Right,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ayah.translation.isNotBlank()) {
                        Text(
                            text = ayah.translation,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            QuranTextInput(
                value = title,
                onValueChange = { title = it },
                placeholder = "Reflection title",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = fieldShape,
            )

            QuranTextInput(
                value = body,
                onValueChange = { body = it },
                placeholder = "Write your reflection...",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp),
                shape = fieldShape,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
                    color = colors.textMuted,
                )
                Text(
                    text = "Add later from the saved reflection note",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, colors.border.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                if (existingReflection != null) {
                    ReflectionSheetButton(label = "Delete", onClick = onDelete)
                }
                ReflectionSheetButton(label = "Close", onClick = onDismiss)
                ReflectionSheetButton(
                    label = "Save",
                    filled = true,
                    onClick = { onSave(title, body) },
                )
            }
        }
    }
}

@Composable
private fun QuranTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    val colors = VaultThemeTokens.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.text),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.border.copy(alpha = 0.78f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun ReflectionSheetButton(
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),
        color = if (filled) colors.accent else colors.textSecondary,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) colors.accentSoft else Color.Transparent)
            .border(1.dp, if (filled) colors.accentBorder else colors.border.copy(alpha = 0.72f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}
