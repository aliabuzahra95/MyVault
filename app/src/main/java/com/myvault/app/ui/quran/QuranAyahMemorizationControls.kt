package com.myvault.app.ui.quran

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myvault.app.data.quran.memorization.AyahMemorizationStatus
import com.myvault.app.ui.theme.VaultThemeTokens

@Composable
internal fun QuranMemorizationAttemptStatusIndicator(
    status: AyahMemorizationStatus,
    onClick: (() -> Unit)? = null,
) {
    if (status == AyahMemorizationStatus.NOT_ATTEMPTED) return

    val colors = VaultThemeTokens.colors
    val style = when (status) {
        AyahMemorizationStatus.PASSED -> QuranMemorizationStatusIndicatorStyle(
            background = Color(0x2631D07F),
            border = Color(0x9931D07F),
            foreground = Color(0xFF31D07F),
            contentDescription = "Latest recitation passed",
        )
        AyahMemorizationStatus.NEEDS_REVIEW -> QuranMemorizationStatusIndicatorStyle(
            background = Color(0x26FFA726),
            border = Color(0x99FFA726),
            foreground = Color(0xFFFFA726),
            contentDescription = "Latest recitation needs review",
        )
        AyahMemorizationStatus.INCORRECT -> QuranMemorizationStatusIndicatorStyle(
            background = Color(0x26FF5A5F),
            border = Color(0x99FF5A5F),
            foreground = Color(0xFFFF5A5F),
            contentDescription = "Latest recitation incorrect",
        )
        AyahMemorizationStatus.ATTEMPTED -> QuranMemorizationStatusIndicatorStyle(
            background = colors.elevated,
            border = colors.borderStrong,
            foreground = colors.textSecondary,
            contentDescription = "Latest recitation attempted",
        )
        AyahMemorizationStatus.UNKNOWN -> QuranMemorizationStatusIndicatorStyle(
            background = colors.elevated,
            border = colors.border.copy(alpha = 0.8f),
            foreground = colors.textMuted,
            contentDescription = "Latest recitation status unknown",
        )
        AyahMemorizationStatus.DIFFICULT -> QuranMemorizationStatusIndicatorStyle(
            background = colors.accentSoft,
            border = colors.accentBorder,
            foreground = colors.accent,
            contentDescription = "Marked difficult",
        )
        AyahMemorizationStatus.NOT_ATTEMPTED -> return
    }

    Box(
        modifier = Modifier
            .padding(end = 6.dp)
            .size(18.dp)
            .clip(CircleShape)
            .background(style.background)
            .border(1.dp, style.border, CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            AyahMemorizationStatus.PASSED -> Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = style.contentDescription,
                tint = style.foreground,
                modifier = Modifier.size(11.dp),
            )
            AyahMemorizationStatus.NEEDS_REVIEW,
            AyahMemorizationStatus.INCORRECT -> Text(
                text = "!",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, fontSize = 10.sp),
                color = style.foreground,
            )
            AyahMemorizationStatus.ATTEMPTED -> Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(style.foreground),
            )
            AyahMemorizationStatus.UNKNOWN -> Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W900, fontSize = 10.sp),
                color = style.foreground,
            )
            AyahMemorizationStatus.DIFFICULT -> Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = style.contentDescription,
                tint = style.foreground,
                modifier = Modifier.size(10.dp),
            )
            AyahMemorizationStatus.NOT_ATTEMPTED -> Unit
        }
    }
}

private data class QuranMemorizationStatusIndicatorStyle(
    val background: Color,
    val border: Color,
    val foreground: Color,
    val contentDescription: String,
)

@Composable
internal fun QuranMemorizationButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailingIcon: Boolean = false,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) colors.accentBorder else colors.border.copy(alpha = 0.78f), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, fontWeight = FontWeight.W800),
            color = if (selected) colors.accent else colors.textSecondary,
            maxLines = 1,
        )
        if (trailingIcon) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = if (selected) colors.accent else colors.textMuted,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
internal fun QuranMemorizationHideMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = VaultThemeTokens.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(shape)
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) colors.accentBorder else Color.Transparent, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (selected) FontWeight.W900 else FontWeight.W700),
            color = if (selected) colors.accent else colors.text,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
