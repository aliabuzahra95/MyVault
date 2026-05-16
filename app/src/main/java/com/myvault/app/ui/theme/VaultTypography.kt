package com.myvault.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val InterFallback = FontFamily.SansSerif

val VaultTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = InterFallback,
        fontSize = 26.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFallback,
        fontSize = 24.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFallback,
        fontSize = 22.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = (-0.02).em,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFallback,
        fontSize = 16.sp,
        fontWeight = FontWeight.W700,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFallback,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.W400,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFallback,
        fontSize = 13.sp,
        fontWeight = FontWeight.W500,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFallback,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.W400,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFallback,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.W600,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFallback,
        fontSize = 11.sp,
        fontWeight = FontWeight.W500,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFallback,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.16.em,
    ),
)
