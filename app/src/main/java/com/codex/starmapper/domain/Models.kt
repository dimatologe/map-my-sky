package com.codex.starmapper.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

enum class EditorTool {
    Move,
    Constellation,
    AddStar,
    DeleteStar,
    Ellipse,
    Rectangle,
    Text,
    Erase,
    Mask,
    // Rechteck aufziehen, das den zu lösenden Bildausschnitt festlegt (Ausrichten-Menü).
    SolveRegion,
    // Pinsel-Radierer: blendet Beschriftungen unter dem Strich aus (Katalog-bearbeiten-Menü).
    EraseArea,
    // „Markieren"-Fadenkreuze (0.11.0): wie Formen platzierbar; erzeugen ein Ellipse-Overlay mit reticle.
    ReticleOpen,
    ReticleComet,
    // Freihand-Silhouette: Stift-Geste zeichnet einen eigenen Umriss (Objekt-platzieren-Menü).
    Draw,
}

enum class OverlayKind {
    Constellation,
    Ellipse,
    Rectangle,
    Text,
    Freehand,
}

/**
 * Kennzeichnet automatisch verwaltete Beschriftungs-Overlays (aus der Lösung erzeugt),
 * damit sie pro Ebene neu synchronisiert/aus- und eingeblendet werden können.
 * null = vom Nutzer manuell platziert.
 */
enum class AnnotationLayer {
    Constellation,
    DeepSky,
    Star,
}

/**
 * Frei sortierbare Zeichen-/Überlagerungs-Schicht (Einstellungen -> Katalog bearbeiten -> Schichten).
 * Fasst die 3 Overlay-Kategorien aus [AnnotationLayer] zusammen (DeepSky UND nutzerplatzierte Formen
 * mit layer==null werden zu EINER Schicht [Objects] zusammengelegt, s. OverlayGeometry.groupByDrawLayer)
 * sowie die beiden separaten Nicht-Overlay-Renderpässe Milchstraße/Gradnetz, die bislang fest VOR allen
 * Overlays gezeichnet wurden.
 *
 * Listen-Konvention (überall im Code, s. DEFAULT_DRAW_LAYER_ORDER): Index 0 = zuerst gezeichnet =
 * UNTERSTE Schicht, letzter Index = zuletzt gezeichnet = OBERSTE Schicht (Painter's Algorithm, dieselbe
 * Reihenfolge wie die bestehenden overlays.forEach-Zeichenschleifen).
 */
enum class DrawLayer {
    Constellation,
    Objects,
    Star,
    MilkyWay,
    Graticule,
}

/**
 * Standard-Reihenfolge, entspricht dem bisherigen fest verdrahteten De-facto-Verhalten: Milchstraße/
 * Gradnetz ganz unten, darüber Sternbilder/Objekte/Sterne in der Reihenfolge, die der "kombinierte
 * Sync-Aufruf" erzeugt (syncConstellationLayer -> syncDeepSkyLayer -> syncStarLayer hängen je ans
 * Listenende an -> Star zuoberst der 3, s. StarMapperApp.kt).
 */
val DEFAULT_DRAW_LAYER_ORDER: List<DrawLayer> = listOf(
    DrawLayer.MilkyWay,
    DrawLayer.Graticule,
    DrawLayer.Constellation,
    DrawLayer.Objects,
    DrawLayer.Star,
)

enum class Hemisphere {
    North,
    South,
    Both,
}

enum class ExportScale {
    Small,
    Medium,
    Original,
}

enum class OverlayLineStyle {
    Solid,
    Dashed,
    Dotted,
    HandDrawn,
    Brush,
    Marker,
    Chalk,
}

/**
 * Schriftart für Beschriftungen (Text-Overlays, Sternnamen, Sternbild-/Objektnamen). Auf Android-
 * Systemfamilien abgebildet (keine gebündelten Fonts nötig); nicht vorhandene Familien fallen
 * geräteseitig sauber auf Sans zurück. SansSerif = Standard (Altverhalten).
 */
enum class OverlayFont {
    SansSerif,
    SansSerifLight,
    SansSerifCondensed,
    SansSerifBlack,
    Serif,
    SerifMonospace,
    Monospace,
    Cursive,
    Casual,
    // Gebündelte Brush-/Schreibschrift-/Kalligraphie-Fonts (assets/fonts/*.ttf, OFL-Lizenz).
    Cherish,
    Estonia,
    Freehand,
    Galada,
    KolkerBrush,
    LeagueScript,
    Smooch,
    Splash,
    TwinkleStar,
    WaterBrush,
    Waterfall,
    Whisper,
}

/**
 * „Markieren": platzierbare Fadenkreuze. OpenCrosshair = Fadenkreuz mit FREIER Mitte (auf einen Stern
 * legen, ohne ihn zu verdecken). CometMarker = halbes Fadenkreuz (ein Strich nach rechts + einer nach
 * unten) zum Markieren von Kometen. Liegt als Zusatzfeld auf einem Ellipse-Overlay -> alle Transform-/
 * Bearbeiten-Mechaniken (Drehen/Größe/Farbe/Deckkraft/Dicke) gelten unverändert.
 */
enum class ReticleStyle {
    OpenCrosshair,
    CometMarker,
}

/**
 * Die 5 Farb-Gruppen aus AstapOverlayMapper.dsoStyleForType als eigener Schlüsseltyp für
 * nutzerdefinierte globale Standardfarben (Katalog bearbeiten -> Farben, Nutzerwunsch 2026-08-20).
 * NICHT identisch mit `processing.DeepSkyCategory` (4 Gruppen, dient den Sichtbarkeits-Filtern in
 * AnnotateSelections und fasst dort Kugelsternhaufen+Offene Sternhaufen zu EINER "Cluster"-Gruppe
 * zusammen) -- der Nutzer möchte diese beiden hier bewusst getrennt haben.
 */
enum class DsoColorGroup {
    Galaxy,
    Globular,
    OpenCluster,
    Nebula,
    Other,
    ;

    companion object {
        // Typcodes 1:1 aus AstapOverlayMapper.dsoStyleForType übernommen (dsos.20.json-Katalog).
        fun of(type: String): DsoColorGroup = when (type.lowercase()) {
            "g", "s", "s0", "sd", "i", "e", "gg" -> Galaxy
            "gc" -> Globular
            "oc" -> OpenCluster
            "en", "bn", "sfr", "rn", "pn", "snr", "dn" -> Nebula
            else -> Other
        }
    }
}

data class DetectedStar(
    val x: Float,
    val y: Float,
    val radius: Float,
    val score: Float,
) {
    val offset: Offset get() = Offset(x, y)
}

data class StarNode(
    val name: String,
    val x: Float,
    val y: Float,
    val raHours: Float,
    val decDegrees: Float,
)

data class SkyPoint(
    val raDegrees: Float,
    val decDegrees: Float,
)

data class CatalogStar(
    val id: String,
    val point: SkyPoint,
    val magnitude: Float,
    val bv: Float?,
    val name: String = "",
    // Anzeige-Label je Sprachkürzel (z.B. "de" -> "Beteigeuze"), nur belegte Sprachen enthalten --
    // fehlt eine, fällt [displayName] auf [name] zurück (die internationale/englische Form).
    val localizedNames: Map<String, String> = emptyMap(),
    // Eigenname (z.B. "Rigel"), leer wenn der Stern nur eine Katalogbezeichnung hat.
    val properName: String = "",
    // Eigenname je Sprachkürzel, analog [localizedNames] -- Rückfall auf [properName].
    val localizedProperNames: Map<String, String> = emptyMap(),
    // Sternbild-Kürzel (z.B. "Ori"), leer wenn unbekannt.
    val constellation: String = "",
) {
    /** Anzeige-Label je Sprache (BCP-47-Kürzel, s. AppLocale), sonst der internationale Standard. */
    fun displayName(lang: String): String =
        localizedNames[lang]?.takeIf { it.isNotBlank() } ?: name

    /** Eigenname je Sprache, sonst der internationale Standard. */
    fun properDisplayName(lang: String): String =
        localizedProperNames[lang]?.takeIf { it.isNotBlank() } ?: properName
}

data class DeepSkyObject(
    val id: String,
    val name: String,
    val type: String,
    val point: SkyPoint,
    val magnitude: Float?,
    val dimensions: String,
    // Maj/Min in Bogenminuten (aus `dimensions` geparst); für proportionale Ellipse. null = unbekannt.
    val majorAxisArcmin: Float? = null,
    val minorAxisArcmin: Float? = null,
    // Populärname (z.B. "Andromeda Galaxy"), leer wenn das Objekt nur eine Katalogbezeichnung trägt.
    val properName: String = "",
    // Populärname je Sprachkürzel, analog CatalogStar.localizedProperNames -- Rückfall auf [properName].
    val localizedProperNames: Map<String, String> = emptyMap(),
    // Herkunftshinweis für [properName] (z.B. "Stellarium"), leer wenn unabhängig verifiziert/ohne
    // spezielle Quellenangabe. Region-Info zeigt das an, damit unabhängig-bestätigte und nur von
    // EINER Quelle übernommene Namen unterscheidbar bleiben (Nutzerwunsch 2026-08-19).
    val properNameSource: String = "",
    // Bevorzugte Katalogbezeichnung, wenn [id] und [name] auseinanderfallen (z.B. id="NGC 224",
    // name="M 31" -> "M 31") -- s. DeepSkyAssetLoader.preferredCatalogDesignation für die Rangliste.
    // Bei id==name identisch zu beiden.
    val catalogDesignation: String = "",
    // Weitere Katalogbezeichnungen desselben Objekts, die NICHT [catalogDesignation] sind -- aus zwei
    // Quellen gespeist (s. DeepSkyAssetLoader.load/deduplicate): (1) id≠name am selben Katalogeintrag
    // (z.B. "NGC 224" neben "M 31"), (2) andere Katalogzeilen, die SIMBADs oidref als dasselbe
    // physische Objekt bestätigt (dso_crossid_duplicates.json), deren Zeile aber sonst verworfen wird.
    // Für die "auch bekannt als"-Anzeige in der Objekte-Info (Nutzerwunsch 2026-08-19).
    val alternateDesignations: List<String> = emptyList(),
) {
    /** Populärname je Sprache, sonst Englisch, sonst die Katalogbezeichnung [name]. */
    fun properDisplayName(lang: String): String =
        localizedProperNames[lang]?.takeIf { it.isNotBlank() }
            ?: properName.takeIf { it.isNotBlank() }
            ?: name
}

/**
 * App-eigene Form-Fakten (gemeinfreie Messwerte, RC3/PGC/Original-Kataloge): große Achse (Bogenmin),
 * optional kleine Achse (Bogenmin) + Positionswinkel (Grad, Nord→Ost). Mit [minArcmin]+[posAngleDeg] wird
 * das Objekt als orientierte Ellipse gezeichnet, sonst als Kreis in [majArcmin]-Größe.
 */
data class DsoShape(
    val majArcmin: Float,
    val minArcmin: Float? = null,
    val posAngleDeg: Float? = null,
)

data class MilkyWayPolygon(
    val rings: List<List<SkyPoint>>,
)

data class MilkyWayLayer(
    val level: Int,
    val polygons: List<MilkyWayPolygon>,
)

data class ConstellationPattern(
    val id: String,
    val name: String,
    val germanName: String,
    // Name je Sprachkürzel für die 5 neuen Sprachen (zh/es/ru/ar/ja) -- Deutsch bleibt eigenes Feld
    // [germanName] (vielfach referenziert), Englisch ist bereits [name]. Fehlt ein Kürzel, fällt
    // [ConstellationPattern.localizedName] (StarMapperApp.kt) auf [name] zurück.
    val localizedNames: Map<String, String> = emptyMap(),
    val hemisphere: Hemisphere,
    val stars: List<StarNode>,
    val edges: List<Pair<Int, Int>>,
    val mythStrokes: List<List<Offset>> = emptyList(),
) {
    val displayName: String get() = "$germanName ($name)"
}

data class AnnotationOverlay(
    val id: Long,
    val kind: OverlayKind,
    val center: Offset,
    val size: Size,
    val rotationDegrees: Float = 0f,
    val text: String = "",
    val constellation: ConstellationPattern? = null,
    val anchorOverrides: Map<Int, Offset> = emptyMap(),
    val colorArgb: Long = 0xFF8FD8FF,
    val strokeWidth: Float = 3f,
    val anchorRadiusRatio: Float = 0.045f,
    val lineStyle: OverlayLineStyle = OverlayLineStyle.Solid,
    val opacity: Float = 1f,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    val textBold: Boolean = true,
    // Schriftart für Text-Overlays UND Marker-/Sternnamen (0.11.0). Default = SansSerif (Altverhalten).
    val font: OverlayFont = OverlayFont.SansSerif,
    // „Markieren"-Fadenkreuz; null = normale Form. Liegt auf einem Ellipse-Overlay (Transforms gelten).
    val reticle: ReticleStyle? = null,
    val showName: Boolean = false,
    val nameTextSize: Float = 30f,
    // Namens-/Label-Farbe, unabhaengig von der Form-Farbe (colorArgb) waehlbar. Default Weiss
    // (guter Kontrast auf den meisten Astrofotos, wie die uebrigen Text-Overlay-Defaults).
    val nameColorArgb: Long = 0xFFFFFFFF,
    // Richtung Zentrum→Name (Grad, y-nach-unten; 0 = rechts). Nur DSO-Marker setzen != 0 für die
    // rotierende Callout-Beschriftung (Führungslinie + Name). Default 0 = Altverhalten (Name rechts).
    val labelAngleDeg: Float = 0f,
    // Länge der Führungslinie ab Kreisrand (Bild-px). 0 = Standardlänge (aus Größe abgeleitet). Wird vom
    // Mapper vergrößert, wenn der Name sonst in einem (größeren) Objektumriss läge.
    val labelLeaderPx: Float = 0f,
    // Ankerring-Sichtbarkeit pro Sternbild; null = globalem Schalter folgen.
    val showAnchors: Boolean? = null,
    val layer: AnnotationLayer? = null,
    // false = Sternmarker ohne Ring zeichnen (nur Name) – für Sternbildsterne, die schon
    // durch den Ankerring repräsentiert werden. Default true (normale Form-/Sternmarker).
    val markerRing: Boolean = true,
    // Star-Ebene: kleiner gefüllter Punkt auf dem Stern zeichnen? (UI-Toggle „Sternpunkte"). false =
    // nur der Name, kein Punkt. Gilt nur für ringlose Star-Overlays.
    val markerDot: Boolean = true,
    // Kreis/Ellipse + Rechteck gefüllt zeichnen (Füllung in colorArgb, halbtransparent) + Kontur.
    val filled: Boolean = false,
    // Gekrümmte Sternbildkanten: je sichtbarer Kante eine bereits ins Bild projizierte Polylinie
    // (Großkreis-Unterteilung, Bildkoordinaten). null = gerade Linien zwischen Ankern (Altverhalten).
    val edgePolylines: List<List<Offset>>? = null,
    // Freihand-Silhouette (OverlayKind.Freehand): Liste von SEGMENTEN, je ein Strich zwischen einem
    // Zweitfinger-Drücken und -Loslassen beim Zeichnen (0.x Zweifinger-Stift); eine Lücke zwischen
    // Strichen entsteht dadurch bewusst als Segmentgrenze, keine Verbindungslinie. Punkte je Segment
    // NORMALISIERT im lokalen, unrotierten Rahmen (Anteil von halber Breite/Höhe relativ zum Zentrum)
    // statt absoluter Bildkoordinaten -- dadurch funktionieren Verschieben/Skalieren/Drehen (die
    // generisch nur center/size/rotationDegrees anfassen) automatisch, ohne dass diese Funktionen den
    // Pfad kennen müssen. null bei allen anderen OverlayKind-Werten.
    val freehandSegments: List<List<Offset>>? = null,
    // Stabile Katalog-Id des Quellobjekts (z.B. DeepSkyObject.id). Nur für automatisch aus einem
    // Katalog erzeugte Overlays gesetzt (aktuell DSO-Marker); null bei allen anderen (u.a.
    // Sternbild-/Stern-Overlays sowie frei gezeichneten Formen), deren id selbst schon stabil ist.
    // Wird gebraucht, weil DSO-Overlays bei jeder Neusynchronisierung (Filter-/Reglerwechsel) mit
    // FRISCHER, index-abgeleiteter id neu aufgebaut werden -- ohne sourceId gäbe es keinen Weg, ein
    // manuell bearbeitetes Overlay über eine Neusynchronisierung hinweg wiederzuerkennen.
    val sourceId: String? = null,
)

// Nutzer-Override für Größe/Rotation eines einzelnen DSO-Overlays, keyed über AnnotationOverlay.sourceId.
// Überlebt Neusynchronisierungen der DeepSky-Ebene (s. AnnotateSelections.dsoSizeOverrides).
data class DsoSizeOverride(
    val size: Size,
    val rotationDegrees: Float,
)
