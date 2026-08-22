package com.codex.starmapper.processing

import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.DsoShape
import com.codex.starmapper.domain.SkyPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSkyOverlayTest {

    // 0.01 deg/px, Referenzpixel (501,400), Bildmitte bei RA/Dec 0/0
    private fun solution() = WcsSolution(
        crPix1 = 501.0,
        crPix2 = 400.0,
        crVal1Degrees = 0.0,
        crVal2Degrees = 0.0,
        cd11 = 0.01,
        cd12 = 0.0,
        cd21 = 0.0,
        cd22 = 0.01,
    )

    private fun deepSky(
        id: String,
        type: String,
        raDegrees: Float = 0f,
        decDegrees: Float = 0f,
        magnitude: Float? = 8f,
        dimensions: String = "",
    ): DeepSkyObject {
        // Maj/Min aus `dimensions` ableiten wie DeepSkyAssetLoader.parseDim (der Domain-Typ tut das nicht selbst).
        val parts = dimensions.split('x', 'X', '×')
        val maj = parts.getOrNull(0)?.trim()?.toFloatOrNull()?.takeIf { it > 0f }
        val min = parts.getOrNull(1)?.trim()?.toFloatOrNull()?.takeIf { it > 0f } ?: maj
        return DeepSkyObject(
            id = id,
            name = id,
            type = type,
            point = SkyPoint(raDegrees, decDegrees),
            magnitude = magnitude,
            dimensions = dimensions,
            majorAxisArcmin = maj,
            minorAxisArcmin = min,
        )
    }

    @Test
    fun typeCodesMapToCategories() {
        assertEquals(DeepSkyCategory.Galaxy, DeepSkyCategory.fromType("s"))
        assertEquals(DeepSkyCategory.Galaxy, DeepSkyCategory.fromType("e"))
        assertEquals(DeepSkyCategory.Nebula, DeepSkyCategory.fromType("en"))
        assertEquals(DeepSkyCategory.Nebula, DeepSkyCategory.fromType("pn"))
        assertEquals(DeepSkyCategory.Cluster, DeepSkyCategory.fromType("oc"))
        assertEquals(DeepSkyCategory.Cluster, DeepSkyCategory.fromType("gc"))
        assertEquals(DeepSkyCategory.Other, DeepSkyCategory.fromType("pos"))
    }

    @Test
    fun markersUseTypeColorAndLineStyle() {
        // Ab 0.11.10: Form immer Kreis; Farbe + Linienstil je Typ. Objekte weit getrennt (dim 50),
        // damit weder Kreise noch Labels kollidieren.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(
                deepSky("M 31", type = "s", dimensions = "50"),
                deepSky("IC 1805", type = "en", raDegrees = 2f, dimensions = "50"),
                deepSky("NGC 7789", type = "oc", raDegrees = -2f, dimensions = "50"),
                deepSky("M 13", type = "gc", decDegrees = 2f, dimensions = "50"),
            ),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy, DeepSkyCategory.Nebula, DeepSkyCategory.Cluster),
            catalogMagRange = { 0f..10f },
        )

        assertEquals(4, overlays.size)
        val galaxy = overlays.first { it.text == "M 31" }
        val nebula = overlays.first { it.text == "IC 1805" }
        val openCluster = overlays.first { it.text == "NGC 7789" }
        val globular = overlays.first { it.text == "M 13" }
        assertEquals(OverlayKind.Ellipse, galaxy.kind)
        assertEquals(galaxy.size.width, galaxy.size.height, 0.01f) // Kreis
        // Linienstil je Typ: Galaxie gepunktet, Nebel durchgezogen, offener/Kugelhaufen gestrichelt.
        assertEquals(OverlayLineStyle.Dotted, galaxy.lineStyle)
        assertEquals(OverlayLineStyle.Solid, nebula.lineStyle)
        assertEquals(OverlayLineStyle.Dashed, openCluster.lineStyle)
        assertEquals(OverlayLineStyle.Dashed, globular.lineStyle)
        // Farbe je Typ: Galaxie Cyan, Nebel Hellgrün (App-Palette color_lime), offener Haufen Pink,
        // Kugelhaufen pastelliges Hellgrün -- zwei unterschiedliche Hex-Werte trotz ähnlichem Namen,
        // damit beide auf demselben Foto unterscheidbar bleiben (s. Kommentar an NEBULA_BRIGHTGREEN).
        assertEquals(0xFF3FDDF5L, galaxy.colorArgb)
        assertEquals(0xFF76FF03L, nebula.colorArgb)
        assertEquals(0xFFFF6FB5L, openCluster.colorArgb)
        assertEquals(0xFFA6F05AL, globular.colorArgb)
        assertTrue(galaxy.showName)
    }

    @Test
    fun smallMarkerFallsBackToSolidCircle() {
        // Winziges Objekt (keine dim -> kleiner Fallback-Kreis): gestrichelt/gepunktet zerfällt zum
        // Bogen -> stattdessen Vollkreis (Solid), obwohl die Galaxie-Kategorie normal gestrichelt wäre.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("winzig", type = "s", dimensions = "")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
        )
        val marker = overlays.single()
        assertTrue(marker.size.width < 61f) // unter der Muster-Schwelle
        assertEquals(OverlayLineStyle.Solid, marker.lineStyle)
    }

    @Test
    fun shapeMajOnlyDeterminesDiameter() {
        // maj-only Shape -> Kreis in korrigierter Größe: 170 arcmin = ~2.833 deg; 1 deg = 100 px -> ~283 px.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("IC 1396", type = "en", dimensions = "12x4")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..10f },
            shapes = mapOf("IC1396" to DsoShape(170f)),
        )
        val marker = overlays.single()
        assertEquals(283.3f, marker.size.width, 2f)
        assertEquals(marker.size.width, marker.size.height, 0.01f) // nur maj -> Kreis
    }

    @Test
    fun shapeWithMinAndPaBuildsOrientedEllipse() {
        // maj=30' -> 0.5 deg -> 50 px (große Achse, entlang X), min=10' -> ~16.7 px (kleine Achse, entlang Y).
        // Bei dieser TAN-Lösung ist Nord = oben, Ost = rechts. PA=0 (Hauptachse Nord) -> rotationDegrees ~ -90;
        // PA=90 (Hauptachse Ost) -> rotationDegrees ~ 0.
        val northAligned = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("M 31", type = "s", dimensions = "5x5")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
            shapes = mapOf("M31" to DsoShape(30f, 10f, 0f)),
        ).single()
        assertEquals(50f, northAligned.size.width, 1.5f)    // große Achse
        assertEquals(16.7f, northAligned.size.height, 1.5f) // kleine Achse
        assertTrue(northAligned.size.width > northAligned.size.height)
        assertEquals(-90f, northAligned.rotationDegrees, 2f)

        val eastAligned = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("M 31", type = "s", dimensions = "5x5")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
            shapes = mapOf("M31" to DsoShape(30f, 10f, 90f)),
        ).single()
        assertEquals(0f, eastAligned.rotationDegrees, 2f)
    }

    @Test
    fun filtersByPerCatalogMagnitudeCategoryAndBounds() {
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(
                deepSky("hell", type = "s", magnitude = 8f),
                deepSky("zu schwach", type = "s", magnitude = 14f),
                deepSky("falsche Kategorie", type = "oc", magnitude = 5f),
                deepSky("ausserhalb", type = "s", raDegrees = 45f, magnitude = 5f),
            ),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
        )

        assertEquals(listOf("hell"), overlays.map { it.text })
    }

    @Test
    fun magnitudeRangeLowerBoundExcludesTooBrightObjects() {
        // Neu seit dem Bereichsregler (Unter-/Obergrenze statt Einzel-Deckel): ein Objekt HELLER als
        // die Untergrenze fällt jetzt ebenfalls raus -- vorher gab es keine Untergrenze.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(
                deepSky("zu hell", type = "s", magnitude = -2f),
                deepSky("im Bereich", type = "s", magnitude = 5f),
            ),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
        )

        assertEquals(listOf("im Bereich"), overlays.map { it.text })
    }

    @Test
    fun objectsWithUnknownMagnitudeAreKept() {
        // Running-Man-Szenario: NGC 1977 hat keine katalogisierte Helligkeit.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(
                deepSky("NGC 1977", type = "bn", magnitude = null, dimensions = "42x26"),
            ),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..10f },
        )

        assertEquals(listOf("NGC 1977"), overlays.map { it.text })
    }

    @Test
    fun catalogFilterRestrictsToSelectedCatalogs() {
        val objects = listOf(
            deepSky("NGC 1977", type = "bn", magnitude = null),
            deepSky("IC 1805", type = "bn", magnitude = null),
            deepSky("Sh2-155", type = "bn", magnitude = null),
        )
        val onlyNgc = AstapOverlayMapper.createDeepSkyOverlays(
            objects = objects,
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..12f },
            catalogs = setOf(DeepSkyCatalogGroup.Ngc),
        )
        assertEquals(listOf("NGC 1977"), onlyNgc.map { it.text })

        val ngcAndSharpless = AstapOverlayMapper.createDeepSkyOverlays(
            objects = objects,
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..12f },
            catalogs = setOf(DeepSkyCatalogGroup.Ngc, DeepSkyCatalogGroup.Sharpless),
        )
        assertEquals(setOf("NGC 1977", "Sh2-155"), ngcAndSharpless.map { it.text }.toSet())
    }

    @Test
    fun pinnedIdsBypassAllFilters() {
        // Per Objekt-Suche gepinnte Objekte (id in pinnedIds) ignorieren Kategorie-, Katalog- UND
        // Helligkeits-Filter komplett -- der Sinn einer gezielten Suche ist ja, ein bestimmtes Objekt
        // unabhängig von den übrigen Reglern zu zeigen.
        val objects = listOf(
            deepSky("PGC 9999", type = "oc", magnitude = 20f), // falsche Kategorie + viel zu schwach
            deepSky("M 31", type = "s", magnitude = 5f), // würde regulär passieren
        )
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = objects,
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
            catalogs = setOf(DeepSkyCatalogGroup.Messier),
            pinnedIds = setOf("PGC 9999"),
        )
        assertEquals(setOf("PGC 9999", "M 31"), overlays.map { it.text }.toSet())
    }

    @Test
    fun pinnedIdsSurviveEmptyCategoriesAndCatalogs() {
        // Sogar wenn GAR KEINE Kategorie/Kein Katalog aktiv ist (sonst frueher Abbruch, leere Liste),
        // muss ein gepinntes Objekt trotzdem erscheinen.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("M 31", type = "s", magnitude = 5f)),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = emptySet(),
            catalogs = emptySet(),
            pinnedIds = setOf("M 31"),
        )
        assertEquals(listOf("M 31"), overlays.map { it.text })
    }

    @Test
    fun catalogGroupsCacheOverrideIsHonored() {
        // Der vorberechnete catalogGroups-Cache (Performance-Fix) muss tatsächlich verwendet werden,
        // nicht nur entgegengenommen: ein NGC-benanntes Objekt, im Cache aber als Barnard eingetragen,
        // muss sich wie Barnard verhalten (hier: vom Barnard-Katalogfilter erfasst).
        val relabeled = deepSky("NGC 1977", type = "bn", magnitude = null)
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(relabeled),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogs = setOf(DeepSkyCatalogGroup.Barnard),
            catalogGroups = mapOf(relabeled to DeepSkyCatalogGroup.Barnard),
        )
        assertEquals(listOf("NGC 1977"), overlays.map { it.text })
    }

    @Test
    fun markerDiameterFollowsMajorAxis() {
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("groß", type = "s", dimensions = "30x20")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..10f },
        )

        // 30 arcmin = 0.5 deg; 1 deg = 100 px -> 50 px Durchmesser (große Achse).
        assertEquals(50f, overlays.single().size.width, 1.5f)
        assertEquals(overlays.single().size.width, overlays.single().size.height, 0.01f)
    }

    @Test
    fun limitsToBrightestObjects() {
        val crowd = (0 until 120).map { index ->
            deepSky(
                id = "obj$index",
                type = "s",
                raDegrees = (index % 10) * 0.05f,
                decDegrees = (index / 10) * 0.05f,
                magnitude = index * 0.1f,
            )
        }
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = crowd,
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy),
            catalogMagRange = { 0f..16f },
            maxObjects = 75,
            maxPerCatalog = 200,
        )

        assertEquals(75, overlays.size)
        // die hellsten (kleinste Magnitude) werden behalten
        assertTrue(overlays.any { it.text == "obj0" })
    }

    @Test
    fun shownNameGetsCalloutAngle() {
        // Ein einzelnes Objekt bekommt den Namen in der ersten freien Richtung (rechts = 0°).
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(deepSky("M 42", type = "bn", dimensions = "90x60")),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..10f },
        )
        val m = overlays.single()
        assertTrue(m.showName)
        assertEquals(0f, m.labelAngleDeg, 0.01f)
    }

    @Test
    fun leaderGrowsToEscapeLargerOutline() {
        // Kleines Objekt innerhalb eines großen Nebel-Umrisses: die Führungslinie muss wachsen,
        // damit der Name außerhalb des großen Kreises liegt.
        val overlays = AstapOverlayMapper.createDeepSkyOverlays(
            objects = listOf(
                deepSky("BIGNEB", type = "bn", dimensions = "120", magnitude = 3f),
                deepSky("TINY", type = "s", raDegrees = 0.2f, dimensions = "1", magnitude = 8f),
            ),
            solution = solution(),
            imageWidth = 1000,
            imageHeight = 800,
            categories = setOf(DeepSkyCategory.Galaxy, DeepSkyCategory.Nebula),
            catalogMagRange = { 0f..10f },
            fullLabelPlacement = true,
        )
        val tiny = overlays.first { it.text == "TINY" }
        assertTrue(tiny.showName)
        // Führungslinie deutlich über der Basis-Länge -> aus dem großen Umriss herausgewachsen.
        assertTrue(tiny.labelLeaderPx > 40f)
    }
}
