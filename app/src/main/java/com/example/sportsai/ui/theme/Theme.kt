package com.example.sportsai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val SportsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val DarkColorScheme = darkColorScheme(
    primary = CourtGreen,
    onPrimary = InkDark,
    primaryContainer = CourtGreenDark,
    onPrimaryContainer = OffWhite,
    secondary = SkyCyan,
    onSecondary = InkDark,
    tertiary = EnergyOrange,
    onTertiary = InkDark,
    background = FieldNavy,
    onBackground = OffWhite,
    surface = FieldNavy,
    onSurface = OffWhite,
    surfaceVariant = FieldNavySurface,
    onSurfaceVariant = Color(0xFFB9C6D2),
    surfaceContainer = FieldNavySurface,
    surfaceContainerHigh = Color(0xFF1D3049),
    outline = Color(0xFF3C5068)
)

private val LightColorScheme = lightColorScheme(
    primary = CourtGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF2DA),
    onPrimaryContainer = InkDark,
    secondary = TipBlue,
    onSecondary = Color.White,
    tertiary = EnergyOrange,
    onTertiary = Color.White,
    background = OffWhite,
    onBackground = InkDark,
    surface = OffWhite,
    onSurface = InkDark,
    surfaceVariant = Color(0xFFE2EAE4),
    onSurfaceVariant = Color(0xFF44514A),
    surfaceContainer = Color(0xFFEDF3EE),
    surfaceContainerHigh = Color(0xFFE4ECE6),
    outline = Color(0xFF74817A)
)

@Composable
fun SportsAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep branding consistent — dynamic (wallpaper) color disabled by default.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = SportsShapes,
        content = content
    )
}