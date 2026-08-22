package com.codex.starmapper.processing

import android.content.Context
import android.graphics.Typeface
import com.codex.starmapper.domain.OverlayFont
import java.util.concurrent.ConcurrentHashMap

/**
 * Schriftart-Auflösung für Editor-Vorschau UND Export (WYSIWYG). System-Familien (sans/serif/...)
 * brauchen keinen Context; die gebündelten Brush-/Schreibschrift-Fonts (im Ordner assets/fonts) werden
 * über einen einmalig gesetzten App-Context geladen und gecacht. Ist der Context (noch) nicht
 * gesetzt oder fehlt eine Datei, fällt es sauber auf Sans zurück.
 */
object OverlayFontCache {
    @Volatile
    private var appContext: Context? = null
    private val assetCache = ConcurrentHashMap<OverlayFont, Typeface>()

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    fun typeface(font: OverlayFont, bold: Boolean): Typeface {
        val asset = assetPathFor(font)
        if (asset != null) {
            val cached = assetCache[font]
            if (cached != null) return cached
            val ctx = appContext ?: return Typeface.SANS_SERIF
            val loaded: Typeface? = try {
                Typeface.createFromAsset(ctx.assets, asset)
            } catch (e: Exception) {
                null
            }
            if (loaded == null) return Typeface.SANS_SERIF
            assetCache[font] = loaded
            return loaded
        }
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return Typeface.create(systemFamilyFor(font), style)
    }

    /** Pfad im assets-Ordner für gebündelte Fonts; null = System-Familie (kein Asset). */
    private fun assetPathFor(font: OverlayFont): String? = when (font) {
        OverlayFont.Cherish -> "fonts/cherish.ttf"
        OverlayFont.Estonia -> "fonts/estonia.ttf"
        OverlayFont.Freehand -> "fonts/freehand.ttf"
        OverlayFont.Galada -> "fonts/galada.ttf"
        OverlayFont.KolkerBrush -> "fonts/kolker_brush.ttf"
        OverlayFont.LeagueScript -> "fonts/league_script.ttf"
        OverlayFont.Smooch -> "fonts/smooch.ttf"
        OverlayFont.Splash -> "fonts/splash.ttf"
        OverlayFont.TwinkleStar -> "fonts/twinkle_star.ttf"
        OverlayFont.WaterBrush -> "fonts/water_brush.ttf"
        OverlayFont.Waterfall -> "fonts/waterfall.ttf"
        OverlayFont.Whisper -> "fonts/whisper.ttf"
        else -> null
    }

    /** Benannte Android-Systemfamilie (fehlt sie, fällt Typeface.create auf Sans zurück). */
    private fun systemFamilyFor(font: OverlayFont): String = when (font) {
        OverlayFont.SansSerifLight -> "sans-serif-light"
        OverlayFont.SansSerifCondensed -> "sans-serif-condensed"
        OverlayFont.SansSerifBlack -> "sans-serif-black"
        OverlayFont.Serif -> "serif"
        OverlayFont.SerifMonospace -> "serif-monospace"
        OverlayFont.Monospace -> "monospace"
        OverlayFont.Cursive -> "cursive"
        OverlayFont.Casual -> "casual"
        else -> "sans-serif"
    }
}

/** Bequemer Aufruf an den Render-Stellen (Editor + Export). */
fun OverlayFont.toTypeface(bold: Boolean): Typeface = OverlayFontCache.typeface(this, bold)

/**
 * Optischer Größen-Ausgleich je Schriftart: bei gleicher px-Textgröße wirken die dekorativen
 * Schreibschrift-/Brush-Fonts (kleine x-Höhe) kleiner als Sans/Serif. Der Faktor multipliziert die
 * effektive Textgröße, damit ALLE Schriftarten optisch etwa gleich groß erscheinen (Namen/Beschriftung).
 * Sans = 1.0 als Referenz. Erste Schätzung, am Gerät feinjustierbar.
 */
fun OverlayFont.sizeScale(): Float = when (this) {
    // Gebündelte Schreibschrift-/Brush-Fonts: kleine x-Höhe -> hochskalieren.
    OverlayFont.Cherish -> 1.45f
    OverlayFont.Estonia -> 1.5f
    OverlayFont.Freehand -> 1.3f
    OverlayFont.Galada -> 1.4f
    OverlayFont.KolkerBrush -> 1.5f
    OverlayFont.LeagueScript -> 1.5f
    OverlayFont.Smooch -> 1.5f
    OverlayFont.Splash -> 1.35f
    OverlayFont.TwinkleStar -> 1.4f
    OverlayFont.WaterBrush -> 1.45f
    OverlayFont.Waterfall -> 1.45f
    OverlayFont.Whisper -> 1.5f
    // System-Familien: nahe an Sans.
    OverlayFont.Cursive -> 1.15f
    OverlayFont.Casual -> 1.05f
    else -> 1.0f
}
