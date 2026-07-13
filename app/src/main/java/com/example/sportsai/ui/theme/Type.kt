package com.example.sportsai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandFont = FontFamily.SansSerif

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Black,
        fontSize = 54.sp,
        lineHeight = 58.sp,
        letterSpacing = (-1.4).sp
    ),
    displayMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Black,
        fontSize = 45.sp,
        lineHeight = 49.sp,
        letterSpacing = (-1).sp
    ),
    displaySmall = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.15).sp
    ),
    titleLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 27.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.05.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.08.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.45.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.45.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BrandFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.75.sp
    )
)