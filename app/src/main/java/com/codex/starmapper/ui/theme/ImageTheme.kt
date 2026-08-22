package com.codex.starmapper.ui.theme

import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

/**
 * Prozessweite, aus dem geladenen Bild abgeleitete Seed-Farbe fürs adaptive App-Theme.
 * null = kein Bild -> Standard-/Android-Dynamic-Theme. [seed] ist Compose-State: [StarMapperTheme]
 * liest es und komponiert bei Änderung neu (App färbt sich beim Bildladen passend um).
 */
object ImageTheme {
    // Direkt beschreibbar (kein separater Setter): ein eigener setSeed(Color?) würde wegen der
    // value class Color dieselbe gemanglete JVM-Signatur wie der generierte Property-Setter erhalten.
    var seed by mutableStateOf<Color?>(null)
}

/** Dominante/lebendige Farbe eines Bitmaps als Theme-Seed (null, wenn keine brauchbare Farbe). */
fun deriveImageSeed(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap).maximumColorCount(24).generate()
    val rgb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: return null
    return Color(rgb)
}

private fun hsv(hue: Float, sat: Float, value: Float): Color {
    val h = ((hue % 360f) + 360f) % 360f
    return Color(
        android.graphics.Color.HSVToColor(
            floatArrayOf(h, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)),
        ),
    )
}

/**
 * Vollständiges, aus dem Bild-Seed abgeleitetes Material-3-Schema (im Geist von Material You): der
 * Seed-Farbton trägt Primär/Sekundär/Tertiär UND die (leicht getönten) Flächen. Bewusst OHNE die
 * (teils internen) material-color-utilities gebaut – tonale Varianten robust via HSV.
 */
fun imageColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsvArr = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsvArr)
    val h = hsvArr[0]
    val baseSat = hsvArr[1].coerceIn(0.35f, 0.9f)
    return if (dark) {
        darkColorScheme(
            primary = hsv(h, baseSat * 0.8f, 0.85f),
            onPrimary = hsv(h, 0.9f, 0.16f),
            primaryContainer = hsv(h, baseSat * 0.7f, 0.32f),
            onPrimaryContainer = hsv(h, 0.4f, 0.95f),
            secondary = hsv(h + 30f, baseSat * 0.55f, 0.82f),
            onSecondary = hsv(h, 0.9f, 0.16f),
            tertiary = hsv(h - 45f, baseSat * 0.6f, 0.84f),
            onTertiary = hsv(h, 0.9f, 0.16f),
            background = hsv(h, 0.28f, 0.07f),
            onBackground = hsv(h, 0.10f, 0.95f),
            surface = hsv(h, 0.26f, 0.10f),
            onSurface = hsv(h, 0.08f, 0.95f),
            surfaceVariant = hsv(h, 0.24f, 0.20f),
            onSurfaceVariant = hsv(h, 0.12f, 0.82f),
            surfaceContainerHigh = hsv(h, 0.24f, 0.17f),
            surfaceContainerHighest = hsv(h, 0.24f, 0.21f),
            outline = hsv(h, 0.15f, 0.55f),
        )
    } else {
        lightColorScheme(
            primary = hsv(h, baseSat, 0.52f),
            onPrimary = Color.White,
            primaryContainer = hsv(h, baseSat * 0.4f, 0.90f),
            onPrimaryContainer = hsv(h, baseSat, 0.22f),
            secondary = hsv(h + 30f, baseSat * 0.55f, 0.48f),
            onSecondary = Color.White,
            tertiary = hsv(h - 45f, baseSat * 0.6f, 0.48f),
            onTertiary = Color.White,
            background = hsv(h, 0.10f, 0.98f),
            onBackground = hsv(h, 0.25f, 0.10f),
            surface = hsv(h, 0.06f, 0.99f),
            onSurface = hsv(h, 0.30f, 0.10f),
            surfaceVariant = hsv(h, 0.14f, 0.90f),
            onSurfaceVariant = hsv(h, 0.20f, 0.30f),
            surfaceContainerHigh = hsv(h, 0.12f, 0.93f),
            surfaceContainerHighest = hsv(h, 0.12f, 0.90f),
            outline = hsv(h, 0.15f, 0.50f),
        )
    }
}
