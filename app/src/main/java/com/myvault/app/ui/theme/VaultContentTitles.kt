package com.myvault.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontWeight

internal val LocalHighContrastTitles = staticCompositionLocalOf { false }

internal fun contentTitleFontWeight(highContrast: Boolean, normal: FontWeight): FontWeight =
    if (highContrast && normal < FontWeight.W600) FontWeight.W600 else normal
