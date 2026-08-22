package com.codex.starmapper.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AstroDarkScheme = darkColorScheme(
    primary = Color(0xFF9AD7FF),
    secondary = Color(0xFFCCC2FF),
    tertiary = Color(0xFFFFD28A),
    background = Color(0xFF090B12),
    surface = Color(0xFF111522),
    surfaceVariant = Color(0xFF252B3A),
)

private val AstroLightScheme = lightColorScheme(
    primary = Color(0xFF005B83),
    secondary = Color(0xFF5E5B7A),
    tertiary = Color(0xFF775900),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E5F0),
)

@Composable
fun StarMapperTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Bild-adaptiv: sobald ein Bild geladen ist (ImageTheme.seed != null), färbt sich das ganze Schema
    // aus der Bild-Seed-Farbe um (im Geist von Material You). Ohne Bild bleibt das bisherige Verhalten
    // (Android-Dynamic ab S, sonst das Astro-Schema).
    val imageSeed = ImageTheme.seed
    val colors = when {
        imageSeed != null -> imageColorScheme(imageSeed, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> AstroDarkScheme
        else -> AstroLightScheme
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
