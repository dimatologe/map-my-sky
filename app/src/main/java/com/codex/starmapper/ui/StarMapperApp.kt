@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.codex.starmapper.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.codex.starmapper.R
import com.codex.starmapper.solve.SolveController
import com.codex.starmapper.solve.SolveForegroundService
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ControlPoint
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.starmapper.data.ConstellationAssetLoader
import com.codex.starmapper.data.ConstellationCatalog
import com.codex.starmapper.data.DeepSkyAssetLoader
import com.codex.starmapper.data.DsoShapeLoader
import com.codex.starmapper.data.MilkyWayAssetLoader
import com.codex.starmapper.data.StarCatalogAssetLoader
import com.codex.starmapper.diagnostics.AppDiagnosticExporter
import com.codex.starmapper.diagnostics.AppDiagnosticSnapshot
import com.codex.starmapper.diagnostics.AppDiagnostics
import com.codex.starmapper.domain.AnnotationLayer
import com.codex.starmapper.domain.DEFAULT_DRAW_LAYER_ORDER
import com.codex.starmapper.domain.DrawLayer
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.domain.ConstellationPattern
import com.codex.starmapper.domain.DeepSkyObject
import com.codex.starmapper.domain.DetectedStar
import com.codex.starmapper.domain.DsoColorGroup
import com.codex.starmapper.domain.DsoShape
import com.codex.starmapper.domain.DsoSizeOverride
import com.codex.starmapper.domain.EditorTool
import com.codex.starmapper.domain.ExportScale
import com.codex.starmapper.domain.ExportImageFormat
import com.codex.starmapper.domain.ExportProjectionMode
import com.codex.starmapper.domain.Hemisphere
import com.codex.starmapper.domain.MilkyWayLayer
import com.codex.starmapper.domain.OverlayLineStyle
import com.codex.starmapper.domain.OverlayKind
import com.codex.starmapper.domain.OverlayFont
import com.codex.starmapper.domain.ReticleStyle
import com.codex.starmapper.domain.SkyPoint
import com.codex.starmapper.domain.TinySkyGeometry
import com.codex.starmapper.domain.constellationImagePoints
import com.codex.starmapper.domain.constellationNameAnchor
import com.codex.starmapper.domain.localizedDisplayName
import com.codex.starmapper.domain.distanceTo
import com.codex.starmapper.domain.trimmedLineEndpoints
import com.codex.starmapper.ui.SolutionExport.containsCorrectedProjection
import com.codex.starmapper.ui.SolutionExport.exportBlockReason
import com.codex.starmapper.ui.SolutionExport.hasHorizontalPeriod
import com.codex.starmapper.ui.SolutionExport.isExportable
import com.codex.starmapper.ui.SolutionExport.meshBaselineKindOrNull
import com.codex.starmapper.processing.ExportRenderer
import com.codex.starmapper.processing.ExportSourceDiagnostics
import com.codex.starmapper.processing.GpuSmokeTest
import com.codex.starmapper.processing.GraticuleGeometry
import com.codex.starmapper.processing.GraticuleRenderer
import com.codex.starmapper.processing.ImageEffects
import com.codex.starmapper.processing.MilkyWayGeometry
import com.codex.starmapper.processing.MilkyWayRenderer
import com.codex.starmapper.processing.StarDetector
import com.codex.starmapper.processing.AstapFovEstimator
import com.codex.starmapper.processing.AstapOverlayMapper
import com.codex.starmapper.processing.BestKnownCatalog
import com.codex.starmapper.processing.BestKnownEntry
import com.codex.starmapper.processing.ConstellationCompleteness
import com.codex.starmapper.processing.AstapCaptureSettings
import com.codex.starmapper.processing.AstapCaptureType
import com.codex.starmapper.processing.AstapEquipmentCatalog
import com.codex.starmapper.processing.AstapExifFieldOfView
import com.codex.starmapper.processing.AstapFieldOfView
import com.codex.starmapper.processing.AstapFieldOfViewCalculator
import com.codex.starmapper.processing.AstapFovCandidates
import com.codex.starmapper.processing.AstapFovSource
import com.codex.starmapper.processing.DeepSkyCatalogGroup
import com.codex.starmapper.processing.DeepSkyCategory
import com.codex.starmapper.processing.MosaicWcsSolution
import com.codex.starmapper.processing.TileWcs
import com.codex.starmapper.processing.NovaAstrometrySolver
import com.codex.starmapper.processing.NovaSolveException
import com.codex.starmapper.processing.LocalAstrometrySolver
import com.codex.starmapper.processing.LocalAstrometryNative
import com.codex.starmapper.processing.AstrometryIndexManager
import com.codex.starmapper.processing.AstrometryIndexDownloadManager
import com.codex.starmapper.processing.AstrometryIndexDownloader
import com.codex.starmapper.processing.AstrometryIndexPackage
import com.codex.starmapper.processing.OverlayGeometry
import com.codex.starmapper.processing.RegionInfoEntry
import com.codex.starmapper.processing.RegionInfoMatcher
import com.codex.starmapper.processing.WikidataFacts
import com.codex.starmapper.processing.WikidataFactsService
import com.codex.starmapper.processing.WikipediaSummary
import com.codex.starmapper.processing.WikipediaSummaryService
import com.codex.starmapper.processing.StrokeRenderer
import com.codex.starmapper.processing.FisheyeProjection
import com.codex.starmapper.processing.StereographicProjection
import com.codex.starmapper.processing.FisheyeRefiner
import com.codex.starmapper.processing.PanoramaDebug
import com.codex.starmapper.processing.PanoProjectionKind
import com.codex.starmapper.processing.PanoramaSolver
import com.codex.starmapper.processing.AtmosphericRefraction
import com.codex.starmapper.processing.PanoramaProjection
import com.codex.starmapper.processing.PanoramaWcsSolution
import com.codex.starmapper.processing.RefractedPanoramaWcsSolution
import com.codex.starmapper.processing.ResidualCorrection
import com.codex.starmapper.processing.RichCorrMesh
import com.codex.starmapper.processing.TileConsistency
import com.codex.starmapper.processing.TileDeWarp
import com.codex.starmapper.processing.raDecToVector
import com.codex.starmapper.processing.vectorToRaDec
import com.codex.starmapper.processing.OverlayFontCache
import com.codex.starmapper.processing.SolveMask
import com.codex.starmapper.processing.toTypeface
import com.codex.starmapper.processing.Vec3
import com.codex.starmapper.processing.WcsSolution
import com.codex.starmapper.processing.WcsSolutionLike
import com.codex.starmapper.processing.AstapTileOrientation
import com.codex.starmapper.processing.Tycho2Store
import com.codex.starmapper.processing.Tycho2DownloadManager
import com.codex.starmapper.processing.raDecBoundingBox
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

private enum class EditorPanel {
    Image,
    Automatic,
    Mask,
    Annotate,
    Constellation,
    Shapes,
    Text,
    Export,
    Settings,
}

private enum class AstapSolverChoice {
    // Lokaler, offline-fähiger astrometry.net-Solver (native libastrometry.so) — Standard.
    LocalAstrometry,
    // astrometry.net über nova.astrometry.net (online, braucht API-Schlüssel).
    NovaOnline,
    // ASTAP entfernt (Nutzerwunsch 2026-08-20, nie genutzt) -- nur noch die 2 astrometry.net-Wege oben.
}

private sealed interface AstapOperationState {
    data object Idle : AstapOperationState
    data object Solving : AstapOperationState
    data class SolvingOnline(val statusText: String) : AstapOperationState
    // Unit 6 (2026-09-02): eigener, von Idle/Failure klar unterschiedener Zustand für einen
    // expliziten Nutzer-Abbruch -- KEIN Fehler (keine "Solve fehlgeschlagen"-Meldung), UI kehrt
    // sauber in den Vor-Solve-Zustand zurück (SolveActionSection zeigt dafür nichts an, wie Idle).
    data object Cancelled : AstapOperationState
    data class Solved(
        val constellationCount: Int,
        val starCount: Int,
    ) : AstapOperationState
    data class Failure(val message: String) : AstapOperationState
}

private data class D3CatalogSettings(
    val showStars: Boolean = true,
    val starMagnitudeLimit: Float = 5.8f,
    // Nutzerwunsch 2026-08-20: nach einer frischen Lösung sollen NUR Sternbilder automatisch
    // eingeblendet sein, DSO-Objekte müssen der Nutzer bewusst im Katalog-Menü einschalten.
    val showDeepSkyObjects: Boolean = false,
    val deepSkyMagnitudeLimit: Float = 12f,
    val showMilkyWay: Boolean = true,
    val showBackgroundConstellations: Boolean = true,
    val showConstellationStarPoints: Boolean = true,
    val showConstellationNames: Boolean = true,
)

/**
 * Persistente Auswahl für das "Beschriften"-Menü. Liegt im StarMapperApp-Scope,
 * damit Haken und Magnitude erhalten bleiben, auch wenn das Sheet geschlossen wird.
 */
// Standard-Katalogauswahl (nur "beliebte" Kataloge; PGC/Abell/LDN/Sonstige aus) – geteilt von
// Initialisierung und Reset-Button.
// DSO-Grenzhelligkeit-Regler (pro Katalog): Bereich + Standard = Minimum (niedrigste Mag).
// 0 = Nutzervorgabe/verifizierte Katalog-Untergrenze (kein DSO im Bestand ist heller als mag 0).
// 20 = Sentinel-Obergrenze aus DeepSkyAssetLoader (mag.toFloatOrNull()?.takeIf { it in -30f..20f }),
// zugleich echtes Maximum im Bestand -> sinnvolle Gesamtspanne für den Bereichsregler.
private const val DSO_MAG_SLIDER_MIN = 0f
private const val DSO_MAG_SLIDER_MAX = 20f

// Absolute Obergrenze fürs global_refine-Gate (Anteil der langen Bilddiagonale): die beiden
// bestehenden Bedingungen sind rein relativ und lassen ein Modell durch, das für sich genommen
// bereits schlecht ist, solange es nicht schlechter als das ohnehin schwache Kachel-Modell wird
// (Gerätebeleg 2026-07-23: 23,6px "verbessert" gegenüber 26,3px wurde durchgewunken).
private const val MAX_GLOBAL_REFINE_RMS_FRACTION = 0.0035

// TileOwnAccuracy + MIN_TILE_OWN_RMS_MATCHES leben jetzt in TileConsistency.kt (dort auch für den
// neuen Überlappungs-Zuverlässigkeits-Mechanismus gebraucht, s. TileConsistency.overlapDisagreement/
// tileReliabilityWeights) -- private typealias unten hält die ~10 bestehenden unqualifizierten
// TileOwnAccuracy-Verwendungen in dieser Datei unverändert kompilierbar.
private typealias TileOwnAccuracy = TileConsistency.TileOwnAccuracy

private class AnnotateSelections {
    var constellationsEnabled by mutableStateOf(false)
    var showConstellationNames by mutableStateOf(true)
    var galaxies by mutableStateOf(true)
    var nebulae by mutableStateOf(true)
    var clusters by mutableStateOf(true)
    var others by mutableStateOf(false)
    // Standard: die bekanntesten/am dichtesten kuratierten Kataloge an (Messier/NGC/IC/Caldwell/
    // Sharpless) -- Nutzerentscheidung nach Recherche zu Stellarium/KStars/SkySafari & Co. (dort
    // dieselbe "Standard"-Stufe). Alle anderen 11 bleiben Opt-in, der Nutzer schaltet sie gezielt ein.
    val catalogs = mutableStateListOf(
        DeepSkyCatalogGroup.Messier,
        DeepSkyCatalogGroup.Ngc,
        DeepSkyCatalogGroup.Ic,
        DeepSkyCatalogGroup.Caldwell,
        DeepSkyCatalogGroup.Sharpless,
    )
    // Helligkeits-BEREICH (Unter-/Obergrenze) PRO Katalog. Fehlt ein Eintrag -> defaultMagRangeFor.
    val dsoCatalogMagRange = mutableStateMapOf<DeepSkyCatalogGroup, ClosedFloatingPointRange<Float>>()
    // Welchen Katalog steuert der Mag-Regler gerade? (Einfachauswahl unter den aktiven Katalogen.)
    var dsoSelectedCatalog by mutableStateOf(DeepSkyCatalogGroup.Messier)
    // Nutzerwunsch: EIN Regler kann wahlweise nur den gewählten Katalog ODER alle aktiven Kataloge
    // gleichzeitig steuern. false = Altverhalten (pro Katalog, s. dsoCatalogMagRange).
    var dsoMagRangeAppliesToAll by mutableStateOf(false)
    var dsoGlobalMagRange by mutableStateOf(DSO_MAG_SLIDER_MIN..DSO_MAG_SLIDER_MAX)
    // Deckkraft je Katalog (dieselbe Logik wie dsoMagRangeAppliesToAll/dsoCatalogMagRange, aber
    // EIGENER, unabhängiger Schalter -- Nutzer kann Helligkeit pro Katalog UND Deckkraft global
    // wollen, oder umgekehrt). Default 0.9 spiegelt den bisherigen festen Wert in
    // AstapOverlayMapper.createDeepSkyOverlays (dort gab es bislang keinen UI-Regler dafür).
    var dsoOpacityAppliesToAll by mutableStateOf(false)
    val dsoCatalogOpacity = mutableStateMapOf<DeepSkyCatalogGroup, Float>()
    var dsoGlobalOpacity by mutableFloatStateOf(0.9f)
    // Best Known V1 (Nutzer-Vorgabe 2026-09-01, s. processing/BestKnownCatalog.kt): reduziert den
    // sichtbaren DSO-Bestand auf eine kuratierte Liste (Messier/Caldwell/bekannte Nicht-Katalog-Ziele)
    // UND ersetzt NUR den Anzeigenamen (sofern in der Tabelle vorhanden) durch die international/
    // deutsch bekannte Bezeichnung -- schaltet KEINEN anderen Filter ab (Kategorie/Katalog/Helligkeit/
    // Größe bleiben unabhängig wirksam, s. AstapOverlayMapper.createDeepSkyOverlays bestKnownEnabled).
    var bestKnownEnabled by mutableStateOf(false)

    // Mindestgröße als Anteil (%) der kürzeren Bildseite -- jetzt WCS-basiert auf die ECHTE projizierte
    // Katalog-Winkelgröße geprüft (s. AstapOverlayMapper.createDeepSkyOverlays minNaturalSizePercent),
    // NICHT mehr auf die Render-/Anzeigegröße (das alte minRenderSizeFraction-Konzept bleibt im Code
    // bestehen, wird aber seit dieser Umstellung nirgends mehr mit einem von 0 verschiedenen Wert
    // aufgerufen, s. syncDeepSkyLayer). ECHTE Quantisierung auf exakte 0.1-%-Schritte bei JEDER Änderung
    // (nicht nur die Anzeige gerundet, s. quantizeDsoMinSizePercent) -- 0f = linker Reglar-Anschlag =
    // "Alle Größen" (Filter AUS, kompletter Katalog, Objekte ohne bekannte Winkelausdehnung eingeschlossen).
    // Default für neue/Standard-Einstellungen 0.2% (Best-Known-Vorgabe Abschnitt O) -- Persistenz-
    // Ladepfad s. StarMapperApp() `annotate`-Konstruktion, re-quantisiert dort ebenfalls beim Laden.
    var dsoMinSizePercent by mutableFloatStateOf(0.2f)

    // Performance-Fix 2026-09-02 (MethodTooLargeException-Nachtrag, s. Memory
    // project_methodtoolarge_editorpanelsheet): lebt bewusst HIER statt als eigener
    // StarMapperApp()-lokaler `remember{ mutableIntStateOf(0) }` -- AnnotateSelections wird an
    // EditorPanelSheet ohnehin bereits als EIN gebündelter `annotateSelections`-Parameter durchgereicht;
    // ein zusätzliches Feld hier kostet an dieser übervollen Aufrufstelle keinen neuen Capture-Slot,
    // ein zusätzlicher freistehender Parameter/Capture hatte dagegen bereits zweimal zuvor eine
    // MethodTooLargeException ausgelöst. Reiner Zähler: jeder DSO-Menü-Commit (Slider-Loslassen/
    // Schalter-Tap) erhöht ihn, ein reaktiver LaunchedEffect in StarMapperApp() reagiert darauf.
    var dsoResyncGeneration by mutableIntStateOf(0)
    // Per Objekt-Suche einzeln ausgewählte Objekte (Objekt-`id`): ignorieren Kategorie-/Katalog-/
    // Helligkeits-Regler komplett (s. AstapOverlayMapper.createDeepSkyOverlays pinnedIds). Bewusst
    // NICHT von resetDeepSky() geleert -- unabhängige, bewusste Einzel-Picks.
    val pinnedDsoIds = mutableStateSetOf<String>()
    // Manuell verändertes Größe/Rotation je DSO-Objekt (Objekt-`id`, s. AnnotationOverlay.sourceId):
    // überlebt eine Neusynchronisierung dieser Ebene (s. AstapOverlayMapper.createDeepSkyOverlays
    // sizeOverrides), die sonst bei JEDEM Filter-/Reglerwechsel die manuelle Bearbeitung verwirft.
    // Bewusst NICHT von resetDeepSky() geleert -- analog pinnedDsoIds eine unabhängige Einzel-Änderung.
    val dsoSizeOverrides = mutableStateMapOf<String, DsoSizeOverride>()
    // Schriftgröße der DSO-Objektnamen (UI-Slider), in die regenerierten Overlays gebacken.
    var deepSkyNameSize by mutableFloatStateOf(30f)
    // Punkt 5 (Nutzer-Vorgabe 2026-08-30): direkte Liniendicke-Steuerung für DSO-Konturen -- vorher gab
    // es dafür GAR KEINEN Regler (createDeepSkyOverlays bekam nie einen strokeWidth-Parameter übergeben,
    // lief immer mit dem Default 2.5). deepSkyStrokeWidthEnabled=false (Default) erhält exakt das
    // bisherige Verhalten (Default 2.5 in createDeepSkyOverlays) -- nur bei aktiviertem Schalter greift
    // deepSkyStrokeWidth. Bereich identisch zum abgesenkten Formen-/Sternbild-Reglerbereich (0.2f..40f).
    var deepSkyStrokeWidthEnabled by mutableStateOf(false)
    var deepSkyStrokeWidth by mutableFloatStateOf(2.5f)
    // Punkt 6 (Nutzer-Vorgabe 2026-08-30): koppelt Beschriftungs- an Objekt-Deckkraft, s.
    // AnnotationOverlay.nameOpacityLinked-KDoc. Default false = bisheriges Verhalten (Name immer voll
    // deckend) unverändert.
    var deepSkyNameOpacityLinked by mutableStateOf(false)
    // Schriftart der Deep-Sky-Objektnamen (0.11.0): in die regenerierten DSO-Overlays gebacken.
    var deepSkyFont by mutableStateOf(OverlayFont.SansSerif)
    // Objektnamen anzeigen? (Schalter). Überlappende Namen werden zusätzlich automatisch entstapelt.
    var deepSkyShowNames by mutableStateOf(true)
    // Nach dem Lösen NICHT automatisch einblenden (Nutzerwunsch): nur Sternbilder kommen automatisch,
    // Fixsterne/Sternnamen/Sternpunkte schaltet der Nutzer bewusst zu. Alle Stern-Ebenen daher default AUS.
    var starNamed by mutableStateOf(false)
    var starConstellation by mutableStateOf(false)
    var starAll by mutableStateOf(false)
    var starMagnitude by mutableFloatStateOf(5.5f)
    // Deckkraft der Sternnamen/-punkte (0.9.7): in den Overlay-Alpha gebacken; nur bei Slider-Release
    // angewendet -> keine Neuberechnung pro Tick, bleibt performant.
    var starNameOpacity by mutableFloatStateOf(0.9f)
    // Stern-Beschriftung global anpassbar (0.11.0): Farbe, Schriftart, Größe der Sternnamen/-punkte.
    // In die regenerierten Star-Overlays gebacken (wie starNameOpacity) -> kein Render-Sonderfall.
    var starNameColorArgb by mutableStateOf(0xFFEAF2FFL)
    var starFont by mutableStateOf(OverlayFont.SansSerif)
    var starNameSize by mutableFloatStateOf(26f)
    // Punkte auf den Sternen (Star-Ebene) zeigen? (Nutzerwunsch: ein/ausschaltbar.)
    var starShowDots by mutableStateOf(true)
    // Koordinatennetz (0.10.5): RA/Dec-Gradnetz, erst nach dem Lösen einblendbar. Kein Overlay,
    // sondern ein eigener Render-Pass (Editor + Export, 1:1). gridThickness = Bild-px-Anteil von minDim.
    var gridEnabled by mutableStateOf(false)
    var gridShowLabels by mutableStateOf(true)
    var gridThickness by mutableFloatStateOf(0.0012f)
    var gridDensity by mutableFloatStateOf(1f)
    var gridOpacity by mutableFloatStateOf(0.7f)
    var gridColorArgb by mutableStateOf(0xFF8FD8FFL)
    // Milchstraße (0.19.0): wie das Koordinatennetz ein eigener Render-Pass (kein Overlay), aus dem
    // Sternhimmel-Katalog (milkyWayLayers, 5 Dichtestufen) übers gelöste WCS ins Bild projiziert.
    var milkyWayEnabled by mutableStateOf(false)
    var milkyWayOpacity by mutableFloatStateOf(0.5f)
    // Bildeffekte (0.19.0): wirken aufs BASISBILD selbst (nicht auf Overlays/Sternbilder). Weichzeichnen
    // 0f=aus; Graustufen/Invertieren rein über ColorFilter beim Zeichnen (kein Bitmap-Umbau nötig).
    var imageBlurIntensity by mutableFloatStateOf(0f)
    var imageGrayscale by mutableStateOf(false)
    var imageInverted by mutableStateOf(false)

    fun deepSkyCategories(): Set<DeepSkyCategory> = buildSet {
        if (galaxies) add(DeepSkyCategory.Galaxy)
        if (nebulae) add(DeepSkyCategory.Nebula)
        if (clusters) add(DeepSkyCategory.Cluster)
        if (others) add(DeepSkyCategory.Other)
    }

    /** Aktueller Helligkeits-Bereich eines Katalogs. Bei [dsoMagRangeAppliesToAll] gilt EIN
     *  gemeinsamer Bereich ([dsoGlobalMagRange]) für alle Kataloge gleich; sonst gesetzter
     *  Katalog-Wert oder Default: die volle Reglerspanne 0..20. Kein katalogspezifischer Startwert
     *  nötig -- der Objekt-Zähldeckel (maxPerCatalog/maxObjects in createDeepSkyOverlays) verhindert
     *  bei großen Katalogen (PGC etc.) bereits eine Bildüberladung, unabhängig von der
     *  Helligkeitsgrenze (gezeigt werden ohnehin nur die z.B. 200 hellsten -- ein engerer
     *  Start-Deckel hätte dieselbe Auswahl ergeben). Bei kleinen Katalogen (Messier etc., weit unter
     *  dem Zähldeckel) blendet ein enger Start dagegen nur unnötig Objekte aus. Nutzer kann den
     *  Bereich jederzeit selbst enger ziehen. */
    fun dsoMagRangeOf(group: DeepSkyCatalogGroup): ClosedFloatingPointRange<Float> =
        if (dsoMagRangeAppliesToAll) dsoGlobalMagRange
        else dsoCatalogMagRange[group] ?: (DSO_MAG_SLIDER_MIN..DSO_MAG_SLIDER_MAX)

    /** Aktuelle Deckkraft eines Katalogs -- unabhängiger Schalter/Zustand von [dsoMagRangeOf], s.
     *  [dsoOpacityAppliesToAll]-Kommentar. */
    fun dsoOpacityOf(group: DeepSkyCatalogGroup): Float =
        if (dsoOpacityAppliesToAll) dsoGlobalOpacity else dsoCatalogOpacity[group] ?: 0.9f

    /** Merkt sich eine manuelle Größen-/Rotationsänderung eines DSO-Overlays (Resize-/Rotate-Geste),
     *  damit sie eine Neusynchronisierung der Ebene übersteht (s. [dsoSizeOverrides]). Kein Effekt für
     *  Overlays anderer Ebenen (kein sourceId). */
    fun recordDsoSizeOverride(overlay: AnnotationOverlay) {
        val id = overlay.sourceId ?: return
        if (overlay.layer != AnnotationLayer.DeepSky) return
        dsoSizeOverrides[id] = DsoSizeOverride(overlay.size, overlay.rotationDegrees, overlay.labelAngleDeg, overlay.labelLeaderPx)
    }

    /** Katalog-Auswahl/Filter der Deep-Sky-Ebene auf die Standardwerte zurücksetzen. Gepinnte
     *  Such-Objekte ([pinnedDsoIds]) bleiben bewusst erhalten -- unabhängige Einzel-Picks. */
    fun resetDeepSky() {
        galaxies = true
        nebulae = true
        clusters = true
        others = false
        catalogs.clear()
        catalogs.addAll(
            listOf(
                DeepSkyCatalogGroup.Messier,
                DeepSkyCatalogGroup.Ngc,
                DeepSkyCatalogGroup.Ic,
                DeepSkyCatalogGroup.Caldwell,
                DeepSkyCatalogGroup.Sharpless,
            ),
        )
        dsoCatalogMagRange.clear()
        dsoSelectedCatalog = DeepSkyCatalogGroup.Messier
        dsoMagRangeAppliesToAll = false
        dsoGlobalMagRange = DSO_MAG_SLIDER_MIN..DSO_MAG_SLIDER_MAX
        dsoOpacityAppliesToAll = false
        dsoCatalogOpacity.clear()
        dsoGlobalOpacity = 0.9f
        bestKnownEnabled = false
        dsoMinSizePercent = 0.2f
        deepSkyShowNames = true
        deepSkyNameSize = 30f
        deepSkyFont = OverlayFont.SansSerif
    }
}

// Obergrenze des DSO-Mindestgrößen-Reglers (Best-Known-Vorgabe Abschnitt N) -- unverändert der
// bisherige, bereits sinnvolle Regler-Deckel (0f..3f).
internal const val DSO_MIN_SIZE_PERCENT_MAX = 3f

/** Echte Quantisierung (NICHT nur Anzeige-Rundung) von [AnnotateSelections.dsoMinSizePercent] auf
 *  exakte 0.1-%-Schritte, s. Best-Known-Vorgabe Abschnitt N/O/P: zeigt die UI "0,6 %", muss der
 *  interne Zustand EXAKT 0.6f sein, nie 0.56f/0.59f/0.64f. 0f bleibt 0f ("Alle Größen", Filter AUS). */
internal fun quantizeDsoMinSizePercent(raw: Float): Float =
    (Math.round(raw * 10f) / 10f).coerceIn(0f, DSO_MIN_SIZE_PERCENT_MAX)

// Halte-Dauer, ab der ein Objekt/Marker zum Verschieben "gegriffen" wird (länger als der
// System-Long-Press, damit nichts versehentlich verschoben wird). Geteilt von Editor + Feinausrichtung.
internal const val MOVE_LONG_PRESS_MS = 350L
// Maximale Pause zwischen zwei qualifizierenden Stift-Tipps (Zeichnen-Modus), damit sie noch als
// Doppel-Tipp (Bleistift<->Radiergummi umschalten) zählen.
internal const val PEN_DOUBLE_TAP_MS = 300L

// Erlaubte Panorama-Modelle je Projektionswahl: "Auto" = alle (kleinster Restfehler gewinnt),
// sonst genau das gewählte (manueller Override). Geteilt von Kachel-Solve + Reprojektion.
private fun allowedProjectionKinds(choice: String): Set<PanoProjectionKind> = when (choice) {
    "Fisheye" -> setOf(PanoProjectionKind.Fisheye)
    "Stereographic" -> setOf(PanoProjectionKind.Stereographic)
    "Rectilinear" -> setOf(PanoProjectionKind.Rectilinear)
    "Equirectangular" -> setOf(PanoProjectionKind.Equirectangular)
    "Cylindrical" -> setOf(PanoProjectionKind.Cylindrical)
    "Mercator" -> setOf(PanoProjectionKind.Mercator)
    else -> setOf(
        PanoProjectionKind.Fisheye, PanoProjectionKind.Stereographic, PanoProjectionKind.Rectilinear,
        PanoProjectionKind.Equirectangular, PanoProjectionKind.Cylindrical, PanoProjectionKind.Mercator,
    )
}

// De-Warp-gekoppelte Refraktionskorrektur (nur bei extremer Rand-Verzeichnung, opt-in):
// fittet das globale Modell auf die SCHEINBAREN (refraktions-gehobenen) Anker und liefert eine
// Anzeige-Lösung, die wahre Katalogrichtungen vor der Projektion refraktiert. Ergebnis: der
// höhenabhängige Rand-Restfehler (RMS-Anstieg) fällt. Fit fehlgeschlagen -> unveränderter Fit.
private fun refractedDisplayFit(
    baseFit: PanoramaWcsSolution,
    anchors: List<Pair<Offset, Vec3>>,
    imgW: Int,
    imgH: Int,
    allowed: Set<PanoProjectionKind>,
    weights: List<Double>? = null,
    // Nutzer-Vorgabe 2026-08-28 (Runde 3, Punkt 1/2): durchgereicht an calibratePanorama, damit ein
    // De-Warp-Solve die horizontalPeriodPx=imageWidth-Nebenbedingung nicht stillschweigend verliert,
    // nur weil er über diesen (zweiten) Fit statt direkt über solveAllTiles()s Haupt-Fit läuft.
    enforceFullPanoramaPeriod: Boolean = false,
): WcsSolutionLike {
    val zenith = (baseFit.rotEquToPano.transpose() * Vec3(0.0, 0.0, 1.0)).normalized()
    val apparent = anchors.map { (px, dir) -> px to AtmosphericRefraction.apparentDirection(dir, zenith) }
    val corrected = FisheyeRefiner.calibratePanorama(apparent, imgW, imgH, allowed, weights, enforceFullPanoramaPeriod = enforceFullPanoramaPeriod) ?: return baseFit
    val base = corrected.solution
    // Stufe 2: glatte Rest-Korrektur über dem refraktionskorrigierten Modell. Stützstellen =
    // (vom Modell vorhergesagter Pixel, tatsächlicher Ankerpixel) -> macht die Lösung lokal genau.
    val samples = anchors.mapNotNull { (actual, dir) ->
        val app = AtmosphericRefraction.apparentDirection(dir, zenith)
        val pred = base.projection.directionToPixel(base.rotEquToPano * app) ?: return@mapNotNull null
        pred to actual
    }
    val residual = ResidualCorrection.fit(samples, imgW, imgH)
    // Rest-korrigierter RMS zum Vergleich mit dem geometrischen rms (solvetiles_done): fällt er,
    // sitzen die Overlays überall (Mitte + Rand) enger an den Sternen.
    val residualRms = if (residual != null && samples.isNotEmpty()) {
        var sum = 0.0
        for ((pred, actual) in samples) {
            val c = residual.correct(pred)
            val ex = (c.x - actual.x).toDouble(); val ey = (c.y - actual.y).toDouble()
            sum += ex * ex + ey * ey
        }
        kotlin.math.sqrt(sum / samples.size)
    } else {
        null
    }
    AppDiagnostics.record(
        "refraction_fit model=${corrected.kind} rms=${"%.1f".format(corrected.rms)} " +
            "residualRms=${residualRms?.let { "%.1f".format(it) } ?: "none"}",
    )
    return RefractedPanoramaWcsSolution(base, zenith, residual)
}

/**
 * Bildfeld der langen Kante in Grad aus einer gelösten WCS (für den FOV-Slider nach dem Solve).
 * TAN-Lösung: Pixelskala = sqrt(|det(CD)|) deg/px; ein Einzel-Kachel-Mosaik (Fisheye-Center-Crop)
 * hat dieselbe Skala wie das Vollbild. Panorama-/Mehrkachel-Lösungen -> null (dort separat gesetzt).
 */
private fun solvedFovLongDeg(wcs: WcsSolutionLike, imageWidth: Int, imageHeight: Int): Float? {
    val tan = when (wcs) {
        is WcsSolution -> wcs
        is MosaicWcsSolution -> wcs.tiles.singleOrNull()?.wcs as? WcsSolution
        else -> null
    } ?: return null
    val scaleDegPerPx = kotlin.math.sqrt(kotlin.math.abs(tan.cd11 * tan.cd22 - tan.cd12 * tan.cd21))
    if (!scaleDegPerPx.isFinite() || scaleDegPerPx <= 0.0) return null
    val fov = (maxOf(imageWidth, imageHeight) * scaleDegPerPx).toFloat()
    return if (fov.isFinite() && fov > 0f) fov else null
}

/**
 * Pendant zu [solvedFovLongDeg] für Panorama-/Mehrkachel-Lösungen (dort bewusst null): sondiert
 * die Bildmitten der langen Kante über [PanoramaProjection.pixelToDirection] und misst ihren
 * Winkelabstand -- projektionsart-unabhängig (keine Rotation nötig, der Winkel zwischen zwei
 * Richtungen im selben Frame ist derselbe wie im äquatorialen Frame).
 */
private fun panoramaFovLongDeg(fit: PanoramaWcsSolution, imageWidth: Int, imageHeight: Int): Float? {
    val landscape = imageWidth >= imageHeight
    val edge1x = if (landscape) 0.0 else imageWidth / 2.0
    val edge1y = if (landscape) imageHeight / 2.0 else 0.0
    val edge2x = if (landscape) imageWidth.toDouble() else imageWidth / 2.0
    val edge2y = if (landscape) imageHeight / 2.0 else imageHeight.toDouble()
    val d1 = fit.projection.pixelToDirection(edge1x, edge1y) ?: return null
    val d2 = fit.projection.pixelToDirection(edge2x, edge2y) ?: return null
    val deg = Math.toDegrees(acos(d1.dot(d2).coerceIn(-1.0, 1.0))).toFloat()
    return if (deg.isFinite() && deg > 0f) deg else null
}

/**
 * Baut aus einer TAN-Einzellösung eine Panorama-Kalibrierung: sampelt ein Gitter aus Bildpunkten,
 * projiziert sie per imageToSky in Himmelsrichtungen und fittet calibratePanorama. Dient (a) als
 * Feinjustier-Seed, der zur echten Optik passt (mit Rectilinear in [allowed] -> keine falsche
 * Fisheye-Näherung mehr) und (b) als Positions-Hinweis-Modell für Kachel-Solves ohne eigene Anker.
 */
private fun panoramaCalibrationFromWcs(
    wcs: WcsSolutionLike,
    imageWidth: Int,
    imageHeight: Int,
    allowed: Set<PanoProjectionKind>,
): FisheyeRefiner.PanoCalibration? {
    val tan = when (wcs) {
        is WcsSolution -> wcs
        is MosaicWcsSolution -> wcs.tiles.singleOrNull()?.wcs as? WcsSolution
        else -> null
    } ?: return null
    val refs = ArrayList<Pair<Offset, Vec3>>()
    val steps = 6
    for (iy in 0..steps) {
        for (ix in 0..steps) {
            val px = imageWidth.toDouble() * ix / steps
            val py = imageHeight.toDouble() * iy / steps
            val sky = tan.imageToSky(px, py, imageHeight)
            refs += Offset(px.toFloat(), py.toFloat()) to
                raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble())
        }
    }
    return FisheyeRefiner.calibratePanorama(refs, imageWidth, imageHeight, allowed)
}

/**
 * Schlägt RA/Dec eines Zielobjekts im Katalog nach (z.B. "M31", "NGC 7000", "Rigel"),
 * damit ASTAP gezielt statt am ganzen Himmel suchen kann. null = nicht gefunden.
 */
private fun resolveTargetObject(
    query: String,
    deepSkyObjects: List<DeepSkyObject>,
    catalogStars: List<CatalogStar>,
): SkyPoint? {
    val normalized = query.trim().lowercase().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    val compact = normalized.replace(" ", "")

    deepSkyObjects.firstOrNull { dso ->
        val name = dso.name.lowercase()
        val id = dso.id.lowercase()
        name == normalized || id == normalized ||
            name.replace(" ", "") == compact || id.replace(" ", "") == compact
    }?.let { return it.point }

    deepSkyObjects.firstOrNull {
        it.properName.isNotBlank() && it.properName.lowercase() == normalized
    }?.let { return it.point }

    catalogStars.firstOrNull {
        it.properName.isNotBlank() && it.properName.lowercase() == normalized
    }?.let { return it.point }

    // Lockerer Teiltreffer nur bei aussagekräftiger Eingabe.
    if (normalized.length >= 4) {
        deepSkyObjects.firstOrNull { it.name.lowercase().contains(normalized) }?.let { return it.point }
    }
    return null
}

/**
 * Objekt-Suche im DeepSky-Menü (mehrere Treffer statt nur einem, s. [resolveTargetObject] für den
 * verwandten Einzeltreffer-Fall "Zielobjekt"): exakte Treffer (Name ODER Katalogbezeichnung) zuerst,
 * dann Teiltreffer ab 2 Zeichen (kürzer als resolveTargetObject erlaubt, da hier gezielt aus einer
 * Ergebnisliste ausgewählt wird statt ein einzelner Treffer automatisch übernommen zu werden).
 */
private fun searchDeepSkyObjects(
    query: String,
    objects: List<DeepSkyObject>,
    limit: Int = 20,
): List<DeepSkyObject> {
    val normalized = query.trim().lowercase().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return emptyList()
    val compact = normalized.replace(" ", "")

    val exact = objects.filter { dso ->
        val name = dso.name.lowercase()
        val id = dso.id.lowercase()
        val proper = dso.properName.lowercase()
        name == normalized || id == normalized || (proper.isNotBlank() && proper == normalized) ||
            name.replace(" ", "") == compact || id.replace(" ", "") == compact
    }
    if (exact.size >= limit) return exact.take(limit)

    if (normalized.length < 2) return exact

    val partial = objects.asSequence()
        .filter { it !in exact }
        .filter { dso ->
            val name = dso.name.lowercase()
            val id = dso.id.lowercase()
            val proper = dso.properName.lowercase()
            name.contains(normalized) || id.contains(normalized) ||
                (proper.isNotBlank() && proper.contains(normalized)) ||
                name.replace(" ", "").contains(compact) || id.replace(" ", "").contains(compact)
        }
        .take(limit - exact.size)
        .toList()
    return exact + partial
}

/**
 * Manueller Kachel-Hinweis (Punkt "Hinweis" unter einer Solve-Kachel): sucht einen Katalogstern
 * per Eigenname (deutsch/englisch) oder Katalogbezeichnung (Bayer/Flamsteed/HIP über
 * [CatalogStar.displayName]), case-insensitive, exakter Treffer. Sucht zuerst im tiefen
 * Referenzkatalog (mehr Namen), dann im kleineren Sternhimmel-Katalog als Fallback.
 */
private fun findStarByName(query: String, vararg catalogs: List<CatalogStar>): CatalogStar? {
    val normalized = query.trim().lowercase().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    for (catalog in catalogs) {
        catalog.firstOrNull { star ->
            val candidates = listOf(star.properName, star.name) +
                star.localizedProperNames.values + star.localizedNames.values
            candidates.any { it.isNotBlank() && it.lowercase() == normalized }
        }?.let { return it }
    }
    return null
}

private inline fun <reified T : Enum<T>> enumPreference(value: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: fallback

/**
 * Parst die persistierte Schicht-Reihenfolge (s. serializeLayerDrawOrder). Robust: unbekannte Namen
 * werden verworfen, Duplikate entfernt, fehlende Schichten in DEFAULT_DRAW_LAYER_ORDER-Reihenfolge
 * hinten (= oberste Position) ergänzt -- eine Schicht verschwindet nie unsichtbar, ein leerer/korrupter
 * Wert fällt komplett auf den Default zurück statt zu crashen.
 */
private fun parseLayerDrawOrder(stored: String?): List<DrawLayer> {
    if (stored.isNullOrBlank()) return DEFAULT_DRAW_LAYER_ORDER
    val parsed = stored.split(',')
        .mapNotNull { name -> DrawLayer.entries.firstOrNull { it.name == name.trim() } }
        .distinct()
    if (parsed.isEmpty()) return DEFAULT_DRAW_LAYER_ORDER
    return parsed + DEFAULT_DRAW_LAYER_ORDER.filterNot { it in parsed }
}

private fun serializeLayerDrawOrder(order: List<DrawLayer>): String = order.joinToString(",") { it.name }

/**
 * Wandelt eine Overlay-/Kachel-Geometrie (Mittelpunkt, Größe, Rotation) zwischen zwei Projektionen um
 * (Original-Bild <-> Tiny-Sky-Ganzansicht, siehe [TileDeWarp.equirectangularNativeModel]/
 * [TileDeWarp.stereographicOverviewModel]).
 *
 * Position/Größe: die vier (ggf. rotierten) Ecken werden über eine Richtung von [fromProjection] nach
 * [toProjection] übertragen, danach die achsenparallele Boundingbox der umgerechneten Ecken genutzt
 * (unter einer nichtlinearen Umprojektion bleibt ein Rechteck kein exaktes Rechteck mehr).
 *
 * Rotation: NICHT einfach auf 0° zurückgesetzt, sondern lokal korrigiert. Die stereografische
 * Ganzansicht ist konform (winkeltreu) -> die "Verdrehung" zwischen den beiden Koordinatensystemen an
 * einem gegebenen Himmelspunkt ist wohldefiniert. Geschätzt über einen kleinen, NICHT rotierten
 * Referenzvektor ("oben" relativ zum Mittelpunkt in [fromProjection]): wohin bildet dieser in
 * [toProjection] ab, verglichen mit dessen eigenem "oben"? Dieser Verdrehungswinkel wird zur
 * übergebenen Rotation addiert.
 *
 * Gibt null zurück, wenn nicht alle vier Ecken + der Referenzvektor abbildbar sind (z. B. Objekt läuft
 * aus dem gültigen Bereich der Zielprojektion heraus).
 */
private fun convertOverlayGeometry(
    center: Offset,
    size: Size,
    rotationDegrees: Float,
    fromProjection: PanoramaProjection,
    toProjection: PanoramaProjection,
): Triple<Offset, Size, Float>? {
    val hw = size.width / 2f
    val hh = size.height / 2f
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = kotlin.math.cos(rad).toFloat()
    val sinR = kotlin.math.sin(rad).toFloat()
    val corners = listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(hw, hh), Offset(-hw, hh)).map {
        Offset(center.x + it.x * cosR - it.y * sinR, center.y + it.x * sinR + it.y * cosR)
    }

    // Mittelpunkt zuerst umrechnen -- wird ohnehin für die Verdrehung unten gebraucht, UND dient als
    // Referenz für die nahtstellen-sichere Umrechnung der 4 Ecken (Bild-Naht bei λ=±180°, s.
    // PanoramaProjection.directionToPixel(dir, reference)): ohne Referenz könnten Ecken knapp auf
    // verschiedenen Seiten der Naht auf x nahe 0 UND nahe der Bildbreite abgebildet werden, die
    // Bounding-Box würde dann fälschlich fast die volle Breite aufspannen statt die Nachbarschaft
    // über den Wrap zu erkennen.
    val twistEps = 4f
    val dirCenter = fromProjection.pixelToDirection(center.x.toDouble(), center.y.toDouble()) ?: return null
    val toCenterForTwist = toProjection.directionToPixel(dirCenter) ?: return null

    val converted = corners.mapNotNull { c ->
        fromProjection.pixelToDirection(c.x.toDouble(), c.y.toDouble())
            ?.let { toProjection.directionToPixel(it, toCenterForTwist) }
    }
    if (converted.size < 4) return null
    val minX = converted.minOf { it.x }
    val maxX = converted.maxOf { it.x }
    val minY = converted.minOf { it.y }
    val maxY = converted.maxOf { it.y }
    val newCenter = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
    val newSize = Size(maxX - minX, maxY - minY)

    val dirUp = fromProjection.pixelToDirection(center.x.toDouble(), (center.y - twistEps).toDouble()) ?: return null
    val toUpForTwist = toProjection.directionToPixel(dirUp, toCenterForTwist) ?: return null
    val twistRad = atan2(
        (toUpForTwist.x - toCenterForTwist.x).toDouble(),
        -(toUpForTwist.y - toCenterForTwist.y).toDouble(),
    )
    val newRotationDegrees = rotationDegrees + Math.toDegrees(twistRad).toFloat()
    return Triple(newCenter, newSize, newRotationDegrees)
}

/**
 * Wandelt eine Overlay-Geometrie wie [convertOverlayGeometry], aber über eine am MITTELPUNKT lokal
 * abgetastete 2x2-Jacobi-Matrix statt über 4 weit entfernte Ecken + separat geschätzte Rotation.
 *
 * Grund für eine zweite Funktion statt [convertOverlayGeometry] direkt zu ändern: dessen Ecken-Ansatz
 * ist für die (bereits validierte, unveränderte) native->Tiny-Sky-ANZEIGE ausreichend genau (Tiny Sky
 * ist konform/winkeltreu, ein einzelner Epsilon-Tastpunkt für die Rotation ist dort eine gute
 * Näherung). Für die Rückrichtung (Tiny-Sky->nativ, s. Verlassen-Button) ist das native Equirectangular
 * dagegen NUR am Äquator konform -- abseits davon (die meisten Bildpositionen) hat die Projektion
 * echte lokale Anisotropie/Scherung (dieselbe, die [AstapOverlayMapper.projectedEllipseAxes] für
 * DSO-Formen bereits korrekt berücksichtigt). Eine
 * EINZELNE "oben"-Tastung (wie in [convertOverlayGeometry]) kann eine solche Anisotropie nicht erfassen
 * -- das führte zu falsch orientierten/"gespiegelt" wirkenden Formen nach dem Verlassen von Tiny Sky
 * (Nutzerbefund 2026-08-24). Diese Funktion tastet stattdessen ZWEI Richtungen (x UND y) am Mittelpunkt
 * ab, komponiert das Ergebnis mit der Rechteck-Rotation zu einer einzigen 2x2-Abbildung A = J * R(rot),
 * und extrahiert Größe + Rotation exakt wie bei einer Ellipse per 2x2-SVD (identisches, bereits
 * verifiziertes Muster wie projectedEllipseAxes, nur mit einem Bild-zu-Bild- statt Himmel-zu-Bild-
 * Jacobi). Lokal-linear um GENAU den Mittelpunkt statt einer globalen Bounding-Box weit entfernter
 * Ecken -- für die meisten (nicht extrem großen) Objekte eine deutlich bessere Näherung.
 *
 * Seit Nachtrag 11 (Nutzer-Vorgabe: "einfache Umprojektion wie die Sternbilder, pixelgenau") NICHT mehr
 * die primäre Methode für Freihand/Ellipse/Rechteck -- die haben eine echte Kontur (Konturpunkte bzw.
 * Randlinie), für die [reprojectFreehandViaPoints]/[reprojectBoundaryViaPoints] viele Einzelpunkte statt
 * dieser einzelnen Mittelpunkt-Schätzung reprojizieren (genauer, da nicht auf lokale Linearität um EINEN
 * Punkt angewiesen). Bleibt für Sternbild/Reticle/Text (keine geeignete Kontur) sowie als Rückfall, falls
 * die punktbasierte Umrechnung zu wenige gültige Punkte liefert.
 */
private fun convertOverlayGeometryLocal(
    center: Offset,
    size: Size,
    rotationDegrees: Float,
    fromProjection: PanoramaProjection,
    toProjection: PanoramaProjection,
): Triple<Offset, Size, Float>? {
    val dirCenter = fromProjection.pixelToDirection(center.x.toDouble(), center.y.toDouble()) ?: return null
    val newCenter = toProjection.directionToPixel(dirCenter) ?: return null
    val epsPx = 4f
    val dirRight = fromProjection.pixelToDirection((center.x + epsPx).toDouble(), center.y.toDouble()) ?: return null
    val pRight = toProjection.directionToPixel(dirRight, newCenter) ?: return null
    val dirDown = fromProjection.pixelToDirection(center.x.toDouble(), (center.y + epsPx).toDouble()) ?: return null
    val pDown = toProjection.directionToPixel(dirDown, newCenter) ?: return null
    // J: Bild-px in toProjection PRO Bild-px in fromProjection (Spalten = x-/y-Schritt in fromProjection).
    val j00 = ((pRight.x - newCenter.x) / epsPx).toDouble()
    val j10 = ((pRight.y - newCenter.y) / epsPx).toDouble()
    val j01 = ((pDown.x - newCenter.x) / epsPx).toDouble()
    val j11 = ((pDown.y - newCenter.y) / epsPx).toDouble()
    if (!j00.isFinite() || !j10.isFinite() || !j01.isFinite() || !j11.isFinite()) return null
    // R(rotationDegrees): Spalten sind die gedrehten Einheitsvektoren der lokalen Rechteck-Achsen.
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(rad); val sinR = sin(rad)
    // A = J * R (2x2): bildet lokale (unrotierte) Halb-Extents direkt auf Bild-Versatz in toProjection ab.
    val a00 = j00 * cosR + j01 * sinR
    val a10 = j10 * cosR + j11 * sinR
    val a01 = j00 * -sinR + j01 * cosR
    val a11 = j10 * -sinR + j11 * cosR
    // Mit (hw,hh) vorskaliert -- A' = A * diag(hw,hh) -- bildet den Einheitskreis auf die tatsächliche
    // (Halb-)Größe des Rechtecks ab, exakt wie M in projectedEllipseAxes.
    val hw = size.width / 2.0
    val hh = size.height / 2.0
    val b00 = a00 * hw; val b10 = a10 * hw
    val b01 = a01 * hh; val b11 = a11 * hh
    val txx = b00 * b00 + b01 * b01
    val tyy = b10 * b10 + b11 * b11
    val txy = b00 * b10 + b01 * b11
    val mid = (txx + tyy) / 2.0
    val rad2 = sqrt(((txx - tyy) / 2.0).let { it * it } + txy * txy)
    val sigma1 = sqrt((mid + rad2).coerceAtLeast(0.0))
    val sigma2 = sqrt((mid - rad2).coerceAtLeast(0.0))
    if (!sigma1.isFinite() || !sigma2.isFinite()) return null
    val thetaMajor = 0.5 * atan2(2.0 * txy, txx - tyy)
    // Seitenverhältnis-Deckel, rein defensiv (Nutzerbefund 2026-08-24: DSO-Ellipsen mit identischem
    // unklammten Jacobi-Muster wurden nahe Bildpolen bis zu >1000:1 -- s.
    // AstapOverlayMapper.projectedEllipseAxes für die Diagnose-Belege). Bewusst SEHR großzügig (60:1,
    // deutlich über den 20:1 dort): eine Nutzer-Zeichnung (Pfeil, dünne Linie) darf legitim viel
    // dünner sein als ein Katalog-Objekt -- dieser Deckel soll nur echte numerische Entgleisungen nahe
    // einer Singularität abfangen, nicht normale dünne Formen antasten.
    val (majorPx, minorPx) = OverlayGeometry.clampAxisRatio((2.0 * sigma1).toFloat(), (2.0 * sigma2).toFloat(), 60f)
    val newSize = Size(majorPx, minorPx)
    val newRotationDegrees = Math.toDegrees(thetaMajor).toFloat()
    return Triple(newCenter, newSize, newRotationDegrees)
}

/**
 * Wie [convertOverlayGeometry] (native->Tiny-Sky-ANZEIGE), aber isotrop: erhält das Seitenverhältnis
 * von [size] exakt (keine durch die kombinierte Projektion verursachte zusätzliche Streckung) -- für
 * Overlays mit einer real bedeutsamen Kontur (DSO-Marker, Reticle/Kometenmarker, nutzerplatzierte
 * Ellipse/Rechteck/Freihand, Sternmarker), s. Nutzer-Vorgabe 2026-08-27 ("wieder normal ohne
 * Verzerrung, sowohl in Tiny Sky als auch im 2:1-Bild"). Am Mittelpunkt lokal abgetastete 2x2-Jacobi-
 * Matrix (identischer Aufbau wie [convertOverlayGeometryLocal]), aber statt der vollen anisotropen SVD
 * nur eine isotrope Skala (`sqrt(|det J|)`) + der reine Rotationsanteil (Polarzerlegung, `atan2(b-c,
 * a+d)`) -- identisches Prinzip wie [AstapOverlayMapper]s `projectedEllipseAxes`. Konstellation (nur
 * grobe Bounding-Box, echte Linienform kommt separat aus punktgenauer Anker-/Kantenlinien-Umrechnung)
 * und Text (kein Flächen-Seitenverhältnis-Konzept) bleiben bewusst auf [convertOverlayGeometry].
 */
private fun convertOverlayGeometryIsotropic(
    center: Offset,
    size: Size,
    rotationDegrees: Float,
    fromProjection: PanoramaProjection,
    toProjection: PanoramaProjection,
): Triple<Offset, Size, Float>? {
    val dirCenter = fromProjection.pixelToDirection(center.x.toDouble(), center.y.toDouble()) ?: return null
    val newCenter = toProjection.directionToPixel(dirCenter) ?: return null
    val epsPx = 4f
    val dirRight = fromProjection.pixelToDirection((center.x + epsPx).toDouble(), center.y.toDouble()) ?: return null
    val pRight = toProjection.directionToPixel(dirRight, newCenter) ?: return null
    val dirDown = fromProjection.pixelToDirection(center.x.toDouble(), (center.y + epsPx).toDouble()) ?: return null
    val pDown = toProjection.directionToPixel(dirDown, newCenter) ?: return null
    val j00 = ((pRight.x - newCenter.x) / epsPx).toDouble()
    val j10 = ((pRight.y - newCenter.y) / epsPx).toDouble()
    val j01 = ((pDown.x - newCenter.x) / epsPx).toDouble()
    val j11 = ((pDown.y - newCenter.y) / epsPx).toDouble()
    if (!j00.isFinite() || !j10.isFinite() || !j01.isFinite() || !j11.isFinite()) return null
    val isoScale = sqrt(abs(j00 * j11 - j10 * j01))
    if (!isoScale.isFinite() || isoScale <= 0.0) return null
    val newRotationDegrees = rotationDegrees + Math.toDegrees(atan2(j10 - j01, j00 + j11)).toFloat()
    val newSize = Size((size.width * isoScale).toFloat(), (size.height * isoScale).toFloat())
    return Triple(newCenter, newSize, newRotationDegrees)
}

/**
 * Exakte Einzelpunkt-Umrechnung (kein Bounding-Box-Näherung wie [convertOverlayGeometry]) --
 * für einzelne Anker-/Kantenlinien-Punkte verankerter Sternbilder, die in Tiny Sky NUR für die
 * Anzeige (nie zurückgeschrieben) korrekt positioniert dargestellt werden müssen.
 */
private fun convertProjectedPoint(
    point: Offset,
    fromProjection: PanoramaProjection,
    toProjection: PanoramaProjection,
    reference: Offset? = null,
): Offset? {
    val dir = fromProjection.pixelToDirection(point.x.toDouble(), point.y.toDouble()) ?: return null
    return toProjection.directionToPixel(dir, reference)
}

/**
 * Ergebnis von [reprojectFreehandViaPoints]: neue Bounding-Box + neu normalisierte Segmente (relativ
 * zu [center]/[size], `rotationDegrees=0` -- die Ausrichtung steckt bereits in den Punkten selbst).
 */
private data class FreehandReprojection(val center: Offset, val size: Size, val segments: List<List<Offset>>)

/**
 * Reprojiziert die TATSÄCHLICHEN Freihand-Konturpunkte EINZELN (Tiny-Sky-Pixel -> Himmelsrichtung ->
 * nativer Pixel) statt über eine einzige am Mittelpunkt abgetastete Jacobi-Schätzung -- exakt dasselbe
 * Prinzip wie bei Sternbild-Kanten (Nutzer-Vorgabe 2026-08-24: "einfache Umprojektion wie die
 * Sternbilder... pixelgenau"). Keine neue Information nötig -- die Punkte sind bereits Teil der
 * gezeichneten Linie, nur die Berechnung wechselt die Quelle (viele Einzelpunkte statt einer
 * Ableitungs-Schätzung an einer Stelle). Punkte, die sich nicht reprojizieren lassen (z.B. jenseits der
 * sichtbaren Himmel-Scheibe), fallen einzeln weg; Segmente, die dadurch auf <2 Punkte schrumpfen,
 * entfallen ganz. Rückgabe null, wenn zu wenige Punkte insgesamt übrig bleiben (Aufrufer fällt dann auf
 * [convertOverlayGeometryLocal] zurück).
 */
private fun reprojectFreehandViaPoints(
    overlay: AnnotationOverlay,
    tsg: TinySkyGeometry,
    overviewProj: PanoramaProjection,
    nativeProj: PanoramaProjection,
): FreehandReprojection? {
    val reference = convertProjectedPoint(tsg.center, overviewProj, nativeProj) ?: return null
    val tinySkySegments = OverlayGeometry.denormalizedFreehandPoints(
        overlay.copy(center = tsg.center, size = tsg.size, rotationDegrees = tsg.rotationDegrees),
    )
    val nativeSegments = tinySkySegments.map { segment ->
        segment.mapNotNull { pt -> convertProjectedPoint(pt, overviewProj, nativeProj, reference) }
    }.filter { it.size >= 2 }
    val allPoints = nativeSegments.flatten()
    if (allPoints.size < 2) return null
    val minX = allPoints.minOf { it.x }
    val maxX = allPoints.maxOf { it.x }
    val minY = allPoints.minOf { it.y }
    val maxY = allPoints.maxOf { it.y }
    val newCenter = Offset((minX + maxX) / 2f, (minY + maxY) / 2f)
    val newSize = Size((maxX - minX).coerceAtLeast(1f), (maxY - minY).coerceAtLeast(1f))
    val newSegments = OverlayGeometry.normalizeFreehandPoints(nativeSegments, newCenter, newSize)
    return FreehandReprojection(newCenter, newSize, newSegments)
}

// Ellipse: volle 360°, gleichmäßig verteilt. Rechteck: pro Kante (nicht nur die 4 Ecken) -- beides
// "Punkte auf dem Verlauf der Formlinien" (Nutzer-Vorgabe 2026-08-24), nicht nur an den Extrempunkten.
private const val ELLIPSE_BOUNDARY_SAMPLES = 32
private const val RECTANGLE_SAMPLES_PER_EDGE = 8

/** Punkte auf dem Rand einer Ellipse/eines Rechtecks (lokal, um (0,0), bereits um [rotationDegrees]
 *  gedreht) -- Translation um den Mittelpunkt macht der Aufrufer. */
private fun sampleShapeBoundaryPoints(size: Size, rotationDegrees: Float, kind: OverlayKind): List<Offset> {
    val hw = size.width / 2f
    val hh = size.height / 2f
    val local = when (kind) {
        OverlayKind.Ellipse -> (0 until ELLIPSE_BOUNDARY_SAMPLES).map { i ->
            val t = 2.0 * Math.PI * i / ELLIPSE_BOUNDARY_SAMPLES
            Offset((hw * cos(t)).toFloat(), (hh * sin(t)).toFloat())
        }
        OverlayKind.Rectangle -> {
            val corners = listOf(Offset(-hw, -hh), Offset(hw, -hh), Offset(hw, hh), Offset(-hw, hh))
            (0 until 4).flatMap { edge ->
                val a = corners[edge]
                val b = corners[(edge + 1) % 4]
                (0 until RECTANGLE_SAMPLES_PER_EDGE).map { j ->
                    val t = j.toFloat() / RECTANGLE_SAMPLES_PER_EDGE
                    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                }
            }
        }
        else -> emptyList()
    }
    val rad = Math.toRadians(rotationDegrees.toDouble())
    val cosR = cos(rad).toFloat()
    val sinR = sin(rad).toFloat()
    return local.map { p -> Offset(p.x * cosR - p.y * sinR, p.x * sinR + p.y * cosR) }
}

/**
 * Reprojiziert die Randkontur einer Ellipse/eines Rechtecks EINZELN (viele Punkte entlang des
 * Formverlaufs, s. [sampleShapeBoundaryPoints]) statt über eine einzige am Mittelpunkt abgetastete
 * Jacobi-Schätzung -- Nutzer-Vorgabe 2026-08-24: "Punkte auf dem Verlauf der Formlinien platzieren,
 * damit es möglichst genau wird". Rekonstruiert Mittelpunkt/Größe/Rotation aus der reprojizierten
 * Punktwolke per Hauptachsen-Analyse (Eigenzerlegung der Kovarianzmatrix -- Standardtechnik, liefert
 * die ORIENTIERUNG auch wenn die Punktwolke nicht mehr exakt elliptisch/rechteckig liegt; dieselbe
 * 2x2-Eigenwert-Formel wie u.a. in [convertOverlayGeometryLocal]/AstapOverlayMapper.projectedEllipseAxes).
 * Die tatsächliche Ausdehnung wird DANACH direkt entlang der gefundenen Achsen GEMESSEN (nicht aus einer
 * Varianz-Formel angenommen) -- robust auch bei realer Verzerrung der Punktwolke. Rückgabe null, wenn zu
 * wenige Punkte reprojiziert werden konnten (Aufrufer fällt dann auf [convertOverlayGeometryLocal] zurück).
 */
private fun reprojectBoundaryViaPoints(
    tsg: TinySkyGeometry,
    kind: OverlayKind,
    overviewProj: PanoramaProjection,
    nativeProj: PanoramaProjection,
): Triple<Offset, Size, Float>? {
    val reference = convertProjectedPoint(tsg.center, overviewProj, nativeProj) ?: return null
    val localBoundary = sampleShapeBoundaryPoints(tsg.size, tsg.rotationDegrees, kind)
    val reprojected = localBoundary.mapNotNull { local ->
        convertProjectedPoint(tsg.center + local, overviewProj, nativeProj, reference)
    }
    if (reprojected.size < 3) return null
    val n = reprojected.size
    val cx = (reprojected.sumOf { it.x.toDouble() } / n).toFloat()
    val cy = (reprojected.sumOf { it.y.toDouble() } / n).toFloat()
    var cxx = 0.0
    var cyy = 0.0
    var cxy = 0.0
    for (p in reprojected) {
        val dx = (p.x - cx).toDouble()
        val dy = (p.y - cy).toDouble()
        cxx += dx * dx
        cyy += dy * dy
        cxy += dx * dy
    }
    cxx /= n
    cyy /= n
    cxy /= n
    val theta = 0.5 * atan2(2.0 * cxy, cxx - cyy)
    val cosT = cos(theta)
    val sinT = sin(theta)
    var minU = Double.MAX_VALUE
    var maxU = -Double.MAX_VALUE
    var minV = Double.MAX_VALUE
    var maxV = -Double.MAX_VALUE
    for (p in reprojected) {
        val dx = (p.x - cx).toDouble()
        val dy = (p.y - cy).toDouble()
        val u = dx * cosT + dy * sinT
        val v = -dx * sinT + dy * cosT
        if (u < minU) minU = u
        if (u > maxU) maxU = u
        if (v < minV) minV = v
        if (v > maxV) maxV = v
    }
    val midU = (minU + maxU) / 2.0
    val midV = (minV + maxV) / 2.0
    val newCx = (cx + (midU * cosT - midV * sinT)).toFloat()
    val newCy = (cy + (midU * sinT + midV * cosT)).toFloat()
    val width = (maxU - minU).toFloat().coerceAtLeast(1f)
    val height = (maxV - minV).toFloat().coerceAtLeast(1f)
    if (!newCx.isFinite() || !newCy.isFinite() || !width.isFinite() || !height.isFinite()) return null
    return Triple(Offset(newCx, newCy), Size(width, height), Math.toDegrees(theta).toFloat())
}

private fun ConstellationPattern.localizedName(): String = localizedDisplayName(AppLocale.resolvedLanguageTag)

@Composable
private fun Hemisphere.localizedLabel(): String = when (this) {
    Hemisphere.North -> stringResource(R.string.hemisphere_north)
    Hemisphere.South -> stringResource(R.string.hemisphere_south)
    Hemisphere.Both -> stringResource(R.string.hemisphere_both)
}

@Composable
fun StarMapperApp() {
    val context = LocalContext.current
    // Gebündelte Schriftarten (assets/fonts) einmal mit App-Context registrieren (idempotent),
    // bevor Editor/Export sie zeichnen.
    OverlayFontCache.init(context)
    val preferences = remember(context) {
        context.getSharedPreferences("sternbild_mapper", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    val editorSession: EditorSessionViewModel = viewModel()
    var imageUri by editorSession.imageUriState
    val loadedImage = rememberEditorImageFromUri(imageUri)
    val bitmap = loadedImage?.bitmap
    // Logo-Seed (einmalig, Hintergrund): solange kein Bild geladen ist, färbt sich die App nach den
    // Logofarben statt nach Standard-/Android-Dynamic-Theme -> derselbe Mechanismus wie fürs Foto.
    var logoSeed by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(Unit) {
        logoSeed = withContext(Dispatchers.Default) {
            runCatching {
                // NICHT R.mipmap.ic_launcher: das ist seit 0.19.0 ein <adaptive-icon>-XML
                // (mipmap-anydpi-v26), das weder BitmapFactory noch painterResource laden können
                // (painterResource crasht sogar: "Only VectorDrawables and rasterized asset types").
                // app_logo ist eine eigene, garantiert einfache PNG-Ressource für UI-Zwecke (nicht
                // vom Adaptive-Icon-System berührt, auch nicht wenn dessen Foreground-PNG später
                // fürs Icon eingerückt/verändert wird).
                BitmapFactory.decodeResource(context.resources, R.drawable.app_logo)
                    ?.let { com.codex.starmapper.ui.theme.deriveImageSeed(it) }
            }.getOrNull()
        }
    }
    // Bild-adaptives Theme: aus dem geladenen Bild eine Seed-Farbe ableiten -> App-Farbgebung passt sich
    // an (ohne Bild -> Logo-Farbe, sobald verfügbar). Extraktion im Hintergrund (Palette), UI ruckelt nicht.
    LaunchedEffect(loadedImage, logoSeed) {
        val bmp = loadedImage?.bitmap
        if (bmp == null) {
            com.codex.starmapper.ui.theme.ImageTheme.seed = logoSeed
        } else {
            val seed = withContext(Dispatchers.Default) {
                com.codex.starmapper.ui.theme.deriveImageSeed(bmp)
            }
            com.codex.starmapper.ui.theme.ImageTheme.seed = seed
        }
    }
    val overlays = editorSession.overlays
    var astapCaptureSettings by remember {
        mutableStateOf(
            AstapCaptureSettings(
                // Nur noch Einzelbild (Single). Früher gespeichertes "Fisheye" oder "Mosaic" (ASTAP,
                // entfernt) -> Single migrieren (Fisheye/Panorama läuft jetzt über die Astrometrie-
                // Kalibrierung / Kacheln, enumPreference() fällt bei unbekanntem Wert automatisch zurück).
                captureType = enumPreference(
                    preferences.getString("astap_capture_type", null),
                    AstapCaptureType.Single,
                ).let { if (it == AstapCaptureType.Fisheye) AstapCaptureType.Single else it },
                fovSource = enumPreference(
                    preferences.getString("astap_fov_source", null),
                    AstapFovSource.AutomaticExif,
                ),
                cameraProfileId = preferences.getString(
                    "astap_camera_profile",
                    AstapEquipmentCatalog.FULL_FRAME_24_ID,
                ) ?: AstapEquipmentCatalog.FULL_FRAME_24_ID,
                focalLengthMm = preferences.getFloat("astap_focal_length", 40f),
                sensorMegapixels = preferences.getFloat("astap_sensor_megapixels", 24f),
                tileOrientation = enumPreference(
                    preferences.getString("astap_tile_orientation", null),
                    AstapTileOrientation.Landscape,
                ),
                manualVerticalFovDegrees = preferences.getFloat("astap_manual_fov", 60f),
            ),
        )
    }
    var astapExifFieldOfView by remember { mutableStateOf<AstapExifFieldOfView?>(null) }
    var astapOperationState by remember { mutableStateOf<AstapOperationState>(AstapOperationState.Idle) }
    var astapSolveJob by remember { mutableStateOf<Job?>(null) }
    // Unit 6 (2026-09-02): bei JEDEM expliziten Abbruch hochgezählt (s. resetSolveStateOnCancel).
    // Jede Solve-Coroutine (Einzelbild UND Kacheln) merkt sich beim Start ihre eigene Generation und
    // verwirft ihr eigenes Ergebnis, sobald sich die Generation inzwischen geändert hat -- verhindert,
    // dass ein Solve, der TROTZ Abbruch noch erfolgreich durchläuft (Race), danach trotzdem WCS/
    // Overlays/Solved-Status setzt oder die nächste Kachel auto-startet.
    var solveGeneration by remember { mutableIntStateOf(0) }
    val novaSolver = remember(context) { NovaAstrometrySolver(context) }
    val localSolver = remember(context) { LocalAstrometrySolver(context) }
    // Einmalig: ist der lokale Solver (native Lib + gebündelte Indizes, API28+) einsatzbereit?
    val localSolverAvailable = remember(context) { localSolver.isAvailable() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Ergebnis egal: der Solve läuft auch ohne sichtbare Notification weiter. */ }
    var astapSolverChoice by remember {
        mutableStateOf(
            enumPreference(preferences.getString("astap_solver_choice", null), AstapSolverChoice.LocalAstrometry),
        )
    }
    var novaApiKey by remember {
        mutableStateOf(preferences.getString("nova_api_key", "").orEmpty())
    }
    // Effektiver Solver für den Dispatch: „Lokal" gewählt, aber native Lib/Indizes fehlen
    // (z. B. Android < 9) -> transparent auf Online ausweichen, statt zu scheitern.
    val effectiveSolverChoice = if (
        astapSolverChoice == AstapSolverChoice.LocalAstrometry && !localSolverAvailable
    ) {
        AstapSolverChoice.NovaOnline
    } else {
        astapSolverChoice
    }
    var lastSolvedWcs by remember { mutableStateOf<WcsSolutionLike?>(null) }
    // Unveränderliche Original-Astrometrie (Plate-Solve-Ergebnis). Wird von der Feinausrichtung NIE
    // überschrieben, damit eine falsch angewendete Ausrichtung rückgängig gemacht werden kann und der
    // Ausrichten-Seed immer von der echten Lösung kommt (nicht von einem evtl. kaputten Fit).
    var originalSolvedWcs by remember { mutableStateOf<WcsSolutionLike?>(null) }
    // Einzelbild-Plate-Solve als vollwertige, PERSISTENTE Basis-Lösung: bleibt erhalten, auch wenn danach
    // Kacheln gelöst werden (sie ergänzen sie via Mosaik-Fallback, ersetzen sie NICHT). Speist außerdem den
    // Kachel-Positions-Hinweis und den Feinjustier-Seed. null = kein Einzelbild gelöst. Reset bei neuem Bild.
    var singleSolveWcs by remember(bitmap) { mutableStateOf<WcsSolutionLike?>(null) }
    var singleImageSolved by remember(bitmap) { mutableStateOf(false) }
    var lastPanoramaDebug by remember { mutableStateOf<PanoramaDebug?>(null) }
    // Anker-Korrespondenzen (Bildpunkt <-> Himmelsrichtung) der letzten Kachel-Lösung. Damit lässt sich
    // die PROJEKTION nach dem Solve OHNE erneutes Lösen umrechnen (nur calibratePanorama mit anderem Modell).
    var panoSolveAnchors by remember(bitmap) { mutableStateOf<List<Pair<Offset, Vec3>>>(emptyList()) }
    // Stimmgewicht je Anker (s. FisheyeRefiner.tileVoteWeights, 1:1 zu panoSolveAnchors): normiert
    // den Einfluss jeder Kachel im gemeinsamen Fit auf ihre Rasterdichte, nicht auf ihre Punktzahl.
    var panoSolveWeights by remember(bitmap) { mutableStateOf<List<Double>>(emptyList()) }
    // Vom Nutzer in der Feinjustierung bestätigte Referenzstern-Richtungen -- NUR für die
    // Sichtfeld-Kappung (anchorDirs unten), NIEMALS als Fit-Eingabe. panoSolveAnchors bleibt bewusst
    // rein Kachel-Daten: die Feinjustierung ist Goldstandard (Nutzer entscheidet visuell, wo der
    // echte Stern sitzt) -- würde sie mit den (ggf. schlechteren) Kachel-Ankern in denselben Topf
    // wandern, könnte ein künftiger Neu-Fit (Projektionswechsel/Nachschärfung) sie wieder verwässern,
    // statt "danach nahezu perfekt" zu bleiben (Nutzer-Korrektur 2026-07-24).
    var fisheyeConfirmedAnchorDirs by remember(bitmap) { mutableStateOf<List<Vec3>>(emptyList()) }
    // Pro-Kachel-WCS der letzten Lösung (per-Kachel-exakt, s. MosaicWcsSolution). Ohne das würde
    // reprojectPanorama() beim manuellen Projektionswechsel die Kachel-Präzision verwerfen und
    // überall nur das glatte globale Modell zeigen — auch innerhalb bereits exakt gelöster Kacheln.
    var lastSolvedTileWcs by remember(bitmap) { mutableStateOf<List<TileWcs>>(emptyList()) }
    // Teilmanuelle Fisheye-Feinausrichtung (Vollbild): Seed aus der mittengenauen Lösung,
    // dann zieht der Nutzer Rand-/Ecksterne auf ihre echten Sterne (Live-Fit).
    var fisheyeAlignActive by remember { mutableStateOf(false) }
    var fisheyeAlignSeed by remember { mutableStateOf<PanoramaWcsSolution?>(null) }
    var fisheyeAlignBright by remember { mutableStateOf<List<CatalogStar>>(emptyList()) }
    var fisheyeAlignBlobs by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Gespeicherter Feinjustier-Referenzzustand: Wieder-Öffnen ohne neues Solve zeigt EXAKT die
    // zuletzt eingestellten Sterne. null -> frisch aus dem Seed. Wird bei neuem Solve/neuen Kacheln geleert.
    var fisheyeAlignRefs by remember { mutableStateOf<List<AlignRefSnapshot>?>(null) }
    // Live-Vorschau der Referenzsterne beim Ziehen des Fisheye-FOV-Sliders: der (FOV-unabhängige)
    // Zentral-Fit wird einmal gecacht, pro FOV-Tick werden nur die Marker billig neu projiziert.
    // WICHTIG: NICHT an originalSolvedWcs koppeln — die Single-Solve-Completion setzt originalSolvedWcs
    // UND fisheyeBaseFit im selben Frame; ein originalSolvedWcs-Schlüssel würde den frisch berechneten
    // WCS-Seed sofort wieder auf null re-initialisieren (Feinjustierung fiele auf die falsche
    // Fisheye-Näherung zurück -> Referenzsterne in falscher Himmelsregion). Reset genügt über bitmap.
    var fisheyeBaseFit by remember(bitmap) { mutableStateOf<PanoramaWcsSolution?>(null) }
    // Referenzstern-Vorschau beim FOV-Ziehen: Name + Bildposition (Name wird neben dem Ring gezeigt).
    var fisheyePreviewPoints by remember(bitmap) { mutableStateOf<List<Pair<String, Offset>>>(emptyList()) }
    // Vom Nutzer gesetztes Gesamt-FOV der LANGEN Bildkante (Grad) -> daraus die korrekte
    // equidistante Fisheye-Brennweite für den Ausrichtungs-Seed (f = lange Kante / FOV_in_rad).
    var fisheyeFovLongDeg by remember { mutableFloatStateOf(preferences.getFloat("fisheye_fov_long_deg", 120f)) }
    // Zeichnen-Werkzeug (Zweifinger-Stift): Linkshänder spiegelt den Greif-Versatz + das Stift-Bild
    // auf die andere Seite der Spitze (Einstellungen-Panel). Default Rechtshänder (Altverhalten).
    var leftHandedDrawing by remember { mutableStateOf(preferences.getBoolean("left_handed_drawing", false)) }
    // Anzeige-Deckel für die "Alle Sterne bis Magnitude"-Funktion (s. AstapOverlayMapper.createStarOverlays
    // maxStars): global in den Einstellungen statt im Katalog-bearbeiten-Menü (Nutzerwunsch 2026-08-22),
    // da es kein Bild-spezifischer Anzeige-Wert ist, sondern eine Performance-Abwägung, die der Nutzer
    // einmalig selbst treffen will -- unabhängig von der (bildspezifischen) Magnitude-Grenze/den Chips.
    var starDisplayCap by remember { mutableStateOf(preferences.getInt("star_display_cap", 600)) }
    // Bilddateiformat für den "mit Hintergrund"-Export (Settings-Menü, Nutzerwunsch 2026-08-24): Png
    // (Altverhalten) oder Jpeg mit eingebetteten 360°-XMP-Metadaten, damit externe Galerie-Apps (z.B.
    // Samsung) das exportierte Bild als Panorama erkennen -- geht nur bei JPEG, s. ExportRenderer. Der
    // "nur Overlay"-Export bleibt UNABHÄNGIG von dieser Einstellung immer Png (Transparenz).
    var exportImageFormat by remember {
        mutableStateOf(enumPreference(preferences.getString("export_image_format", null), ExportImageFormat.Png))
    }
    // Projektionswahl für den globalen Kachel-Fit: "Auto" probiert alle Modelle und nimmt das mit dem
    // kleinsten Restfehler; sonst wird genau dieses Modell erzwungen (manueller Override).
    var panoProjectionChoice by remember {
        mutableStateOf(preferences.getString("pano_projection", "Auto") ?: "Auto")
    }
    // Das beim letzten Kachel-Lösen gewählte Modell -> die Feinausrichtung fittet mit DIESEM Kind.
    var alignProjectionKind by remember { mutableStateOf(PanoProjectionKind.Fisheye) }
    // Restfehler (Bild-Pixel) desselben Fits -> sichtbare "welche Projektion + wie gut"-Anzeige im
    // Astrometrie-Menü, damit ein Automodus-Ergebnis nicht mehr unsichtbar bleibt.
    var alignProjectionRms by remember { mutableStateOf<Double?>(null) }
    // Per Feinjustierung manuell verfeinerter Fit (aus echten, vom Nutzer gezogenen Referenzsternen) ->
    // erscheint als eigene, wählbare "Eigene"-Option in der Projektionsleiste (neben Auto/Fisheye/...),
    // solange er existiert. null = keine Feinjustierung angewendet bzw. seither zurückgesetzt.
    var customAlignFit by remember { mutableStateOf<PanoramaWcsSolution?>(null) }
    var customAlignRms by remember { mutableStateOf<Double?>(null) }
    var customAlignKind by remember { mutableStateOf<PanoProjectionKind?>(null) }
    // RichCorrMesh: aus den ECHTEN, dichten .corr-Sternmessungen (nicht den groben Kachel-Ankern)
    // gefittete Mesh-Korrektur -> im Projektionsmenü der Eintrag "Detail-Mesh" (seit 0.31.6; bis dahin
    // hieß dieser Eintrag irreführend "Mesh", obwohl "Mesh" in solvetiles_candidates schon immer den
    // ANDEREN Mechanismus meinte, s. sparseMeshFit unten). ANDERS als customAlignFit (nur per
    // Nutzeraktion gesetzt, überlebt mehrere Solves): automatisches Solve-Nebenprodukt, wird bei JEDEM
    // solveAllTiles()-Lauf frisch berechnet bzw. bei zu wenig Daten auf null zurückgesetzt (s. dortiger
    // Kommentar). Wird NIE automatisch angewendet -- ausschließlich über den manuellen Chip. Betrifft
    // NICHT die automatische Mesh-Konkurrenz bei "Auto" (das ist FisheyeRefiner.fitMesh über die groben
    // Kachel-Anker, unverändert Teil von pickCalibration).
    var richMeshFit by remember { mutableStateOf<PanoramaWcsSolution?>(null) }
    var richMeshRms by remember { mutableStateOf<Double?>(null) }
    // Nutzer-Auftrag 2026-09-03 ("Detail-Mesh" = RichCorrMesh): Ein technisch gültiger Fit, den die
    // Qualitäts-Gates (A8/Kreuzvalidierung) nur für die AUTOMATIK ablehnen, bleibt jetzt als manuell
    // wählbarer Kandidat erhalten (s. RichCorrMesh.fitCandidate). Diese beiden Felder sagen, ob der
    // vorhandene richMeshFit auch automatisch tauglich wäre bzw. warum nicht -- rein informativ für
    // UI/Diagnose, sie steuern KEINE automatische Auswahl (richMeshFit wird ohnehin nie automatisch
    // angewendet, s. solveAllTiles()).
    var richMeshAutoEligible by remember { mutableStateOf(false) }
    var richMeshRejectReason by remember { mutableStateOf<String?>(null) }
    // "Mesh" im UI = SparseAnchorMesh, also der 7. Kandidat aus solvetiles_candidates (FisheyeRefiner.
    // fitMesh über die groben Kachel-Anker). Bis 0.31.5 wurde dieser Fit VERWORFEN, sobald er die
    // BIC-Auswahl nicht gewann (reine lokale Variable in solveAllTiles) -- er war dadurch nie manuell
    // wählbar. Jetzt wird er festgehalten, ohne dass sich an pickCalibration/Auto etwas ändert.
    var sparseMeshFit by remember { mutableStateOf<PanoramaWcsSolution?>(null) }
    var sparseMeshRms by remember { mutableStateOf<Double?>(null) }
    // Ergebnis-RMS je starrem Projektionskandidat aus dem letzten Kachel-Solve (identisch zu dem, was
    // solvetiles_candidates protokolliert): Wert = erfolgreich gefittet, null = Fit gescheitert.
    // Leere Map = noch kein Solve gelaufen -> dann ist NICHTS ausgegraut (die Wahl wirkt dann als
    // Vorgabe für den nächsten Solve, exakt wie bisher).
    var projectionCandidateRms by remember { mutableStateOf<Map<PanoProjectionKind, Double?>>(emptyMap()) }
    // Verfügbarkeits-Übersicht der Projektionsauswahl -- an die Eingangsgrößen gekoppelt, feuert also nur
    // bei einer echten Änderung (nach einem Solve, nach Undo/Redo, nach Projektionswechsel), NICHT pro Frame.
    LaunchedEffect(
        projectionCandidateRms, sparseMeshFit, richMeshFit,
        richMeshAutoEligible, richMeshRejectReason, panoProjectionChoice, alignProjectionKind,
    ) {
        fun state(kind: PanoProjectionKind) =
            if (projectionCandidateRms.isEmpty() || projectionCandidateRms[kind] != null) "available" else "unavailable"
        AppDiagnostics.record(
            "projection_availability autoSelected=$alignProjectionKind choice=$panoProjectionChoice " +
                "rectilinear=${state(PanoProjectionKind.Rectilinear)} fisheye=${state(PanoProjectionKind.Fisheye)} " +
                "stereographic=${state(PanoProjectionKind.Stereographic)} " +
                "equirectangular=${state(PanoProjectionKind.Equirectangular)} " +
                "cylindrical=${state(PanoProjectionKind.Cylindrical)} mercator=${state(PanoProjectionKind.Mercator)} " +
                "mesh=${if (sparseMeshFit != null) "available" else "unavailable"} " +
                "detailMesh=${if (richMeshFit != null) "available" else "unavailable"} " +
                "detailMeshAutoEligible=$richMeshAutoEligible " +
                "detailMeshManualEligible=${richMeshFit != null} " +
                "detailMeshRejectReason=${richMeshRejectReason ?: "none"}",
        )
    }
    // Kompletter Kalibrierungs-Zustand direkt NACH dem letzten Solve (Kachel- oder Einzelbild) ->
    // präzise Rückfallbasis für "Original-Astrometrie wiederherstellen" (ersetzt die alleinige
    // originalSolvedWcs-Prüfung, da auch Projektionswahl/RMS/Kind mit zurückspringen müssen).
    var postSolveCalibration by remember { mutableStateOf<CalibrationSnapshot?>(null) }
    // Geteilter, abbrechbarer Job für alle asynchronen Kalibrierungs-Neuberechnungen (Kachel-Seed-
    // Refresh, Reprojektion, Feinjustier-Start/Vorschau). Ohne Cancel könnte ein noch laufender
    // Recompute einen per Undo/Redo gerade wiederhergestellten Zustand Millisekunden später wieder
    // überschreiben (Race zwischen synchronem Undo und asynchronem Re-Fit).
    var calibrationAsyncJob by remember { mutableStateOf<Job?>(null) }
    // ➕-Platzieren: Auswahl-Popup (Sternbild/Text/Form), danach Tap-to-place.
    var showPlacementMenu by remember { mutableStateOf(false) }
    // Versehentliches App-Beenden per Back verhindern: erst Bestätigung.
    var showExitConfirm by remember { mutableStateOf(false) }
    var maskBrushFraction by remember { mutableFloatStateOf(0.04f) }
    var maskEraseMode by remember { mutableStateOf(false) }
    // Pinselgröße des Beschriftungs-Radierers (Bruchteil der kurzen Bildkante).
    var eraseBrushFraction by remember { mutableFloatStateOf(0.06f) }
    // Pinsel-/Radierer-Härte (1 = harter Rand, 0 = weicher/gefederter Rand).
    var maskBrushHardness by remember { mutableFloatStateOf(1f) }
    var eraseBrushHardness by remember { mutableFloatStateOf(1f) }
    // Kachel-Ausrichtung: vom Nutzer platzierte Solve-Bereiche (Bild-px). Mehrere Kacheln werden
    // gelöst und zu einem globalen Fisheye-Fit kombiniert (vorausgerichtete Feinabstimmung).
    val solveTiles = remember(bitmap) { mutableStateListOf<SolveTile>() }
    var selectedSolveTileId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Manueller Kachel-Hinweis: Dialog offen für die Kachel mit dieser ID (Sternname eintippen).
    var manualHintDialogTileId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Manueller Positions-Hinweis für den EINZELBILD-Solve (Unit 6, 2026-09-02): wiederverwendet
    // denselben ManualHintDialog/findStarByName-Mechanismus wie der Kachel-Hinweis oben, statt einer
    // zweiten, unabhängigen Sternsuche -- absichtlich GETRENNT von targetSkyPoint/targetObjectQuery
    // (die bleiben die bestehende, unabhängige Text-Feld-Zielobjekt-Funktion in SolverConfigSection).
    var singleSolveHintPoint by remember(bitmap) { mutableStateOf<SkyPoint?>(null) }
    var showSingleSolveHintDialog by remember(bitmap) { mutableStateOf(false) }
    // Info-Popup (Status/De-Warp/Qualität) offen für die Kachel mit dieser ID.
    var tileInfoDialogId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Region-Info-Popup (Wissenswertes zu den aktuell beschrifteten Objekten) offen?
    var showRegionInfoDialog by remember(bitmap) { mutableStateOf(false) }
    // Median-Reprojektionsfehler je Kachel (TileConsistency.perTileError), Grundlage der
    // Qualitäts-Einstufung im Info-Popup. Frisch nach jedem solveAllTiles()-Durchlauf berechnet.
    var tileQualityById by remember(bitmap) { mutableStateOf<Map<Long, Double>>(emptyMap()) }
    // Echte, unabhängige Pro-Kachel-Solve-Genauigkeit (RMS in px + Trefferzahl) fürs Info-Popup:
    // bevorzugt aus den echten .corr-Solve-Treffern dieser Kachel (tileCorrRefsById unten), sonst
    // Kreuzmatch der Kachel-EIGENEN WCS gegen echte, unabhängig erkannte Sterne (nicht wie
    // tileQualityById oben eine gegenseitige Konsistenz-Prüfung zwischen den Kacheln). S.
    // solveAllTiles(). Eigenständiger State statt Feld auf SolveTile (kein Teil der Kachel-Geometrie
    // selbst) -- ABER in TileEditSnapshot MITGEFÜHRT (ownRms/corrRefs dort), damit ein Undo/Redo genau
    // die zum wiederhergestellten WCS-Stand passenden Werte zurückbringt statt sie zu verlieren
    // (Gerätebeleg 2026-07-27: ein einziges Undo hätte sonst 54/54 Kacheln ihre echten .corr-Werte
    // gekostet, s. TileEditSnapshot-Kommentar).
    var tileOwnRmsById by remember(bitmap) { mutableStateOf<Map<Long, TileOwnAccuracy>>(emptyMap()) }
    // Kachel-Überlappungs-Widerspruch (TileConsistency.overlapDisagreement) -- zweites Zuverlässigkeits-
    // Signal neben tileOwnRmsById, gleiches Undo/Redo-Bedürfnis (s. TileEditSnapshot-Kommentar), also
    // identisches remember(bitmap)-Muster.
    var tileOverlapRmsById by remember(bitmap) {
        mutableStateOf<Map<Long, TileConsistency.OverlapAccuracy>>(emptyMap())
    }
    // Echte, von astrometry.net selbst verifizierte Solve-Treffer je Kachel aus deren .corr-Datei
    // (s. LocalAstrometrySolver.LocalSolveResult) -- wie tileOwnRmsById in TileEditSnapshot mitgeführt
    // (Undo/Redo-sicher), aber ANDERS als tileOwnRmsById wird dieser State beim Lösen INKREMENTELL
    // gepflegt (ein Eintrag pro tatsächlich per LocalAstrometry gelöster Kachel im Roh-Crop-Zweig, s.
    // solveAllTiles()), nicht einmalig gesamt neu berechnet.
    var tileCorrRefsById by remember(bitmap) {
        mutableStateOf<Map<Long, List<Pair<Offset, Vec3>>>>(emptyMap())
    }
    // Vollständigkeits-Momentaufnahme JEDES Katalog-Sternbilds (alle 88, jede Hemisphäre) aus dem
    // letzten syncConstellationLayer()-Durchlauf -- Grundlage für den constellation_audit/caudit[]-
    // Diagnose-Block in solveAllTiles() (s. ConstellationCompleteness).
    var constellationCompletenessById by remember(bitmap) {
        mutableStateOf<Map<String, ConstellationCompleteness>>(emptyMap())
    }
    // 360°-Viewer "Tiny Sky": EIN Umschalter zwischen dem Original-2:1-Foto und einer EINMALIG
    // gerenderten stereografischen Ganzansicht (Zenit-zentriert, "kleiner Planet"). Kein Live-Rendering
    // mehr (das lief in vier vorigen Anläufen alles auf Performance-/UX-Probleme hinaus) — nach dem
    // Umschalten ist die Tiny-Sky-Bitmap ein stinknormales Foto aus Sicht von EditorCanvas, mit den
    // immer schon vorhandenen Pan-/Zoom-/Bearbeitungs-Gesten. Kacheln sind rein ansichts-lokal (eigener
    // Koordinatenraum je Ansicht, keine Umrechnung, s. TileSourceSpace/Plan Nachtrag 3). Overlays
    // (Sternbilder/Text/Formen) bleiben kanonisch im Original-Bild-Koordinatenraum gespeichert, werden
    // für die Anzeige in Tiny Sky nur abgeleitet umgerechnet (siehe displayOverlays unten) — bewusst
    // NICHT an calibrationActive gekoppelt, damit auch andere Werkzeuge (Sternbild/Text/Form hinzufügen,
    // außerhalb der Kalibrierung) in Tiny Sky nutzbar sind.
    var tinySkyActive by remember(bitmap) { mutableStateOf(false) }
    var tinySkyBitmap by remember(bitmap) { mutableStateOf<Bitmap?>(null) }
    // EXPLIZITES 360°-Workflow-Flag (Nutzer-Vorgabe 2026-09-04). Wird NUR gesetzt, wenn der Nutzer den
    // 360°-Viewer-Knopf gedrückt hat UND daraus eine gültige Tiny-Sky-Arbeitsansicht entstanden ist.
    //
    // Warum überhaupt: `seamAware` (Naht-Aufteilung der Sternbildkanten) hing bis 0.31.9 allein an
    // `projection.horizontalPeriodPx() != null`. Das ist zu breit -- ein ganz normal importiertes,
    // Mercator-GESTITCHTES Foto (untere Bildhälfte Vordergrund, Kacheln nur im Himmel) erfüllt diese
    // Bedingung ebenfalls, ist aber KEIN 360°-Panorama-Workflow. Ergebnis war der in der Golden-Forensik
    // beschriebene Fehler: Kantenstücke außerhalb [0, imageWidth] wurden um ±imageWidth zurück INS Bild
    // verschoben (Gerätebefund Diagnose 45: 69/69 Overlays seamAware=true auf einem 6392x4101-Foto).
    // Die Golden-APK (Diagnose 47) zeigt für exakt dasselbe Bild mit Mercator korrekte Sternbilder --
    // sie kannte splitPolylineAtSeam gar nicht.
    //
    // Bewusst per-Bild (`remember(bitmap)`) und monoton: einmal echter 360°-Workflow, bleibt es für
    // dieses Bild dabei -- auch nach Rückkehr in die 2:1-/native Ansicht, genau der vom Nutzer
    // beschriebene Fall. Bewusst NICHT Teil von CalibrationSnapshot: das ist kein Kalibrierungszustand,
    // sondern eine Aussage über die Bildherkunft; ein Undo einer Projektionswahl darf sie nicht ändern.
    var tinySkyWorkflowUsed by remember(bitmap) { mutableStateOf(false) }
    var tinySkyLoading by remember(bitmap) { mutableStateOf(false) }
    // Objekte/Formen/Zeichnungen/Text, die IN Tiny Sky entstehen oder dort bearbeitet werden, sollen sich
    // dort exakt wie in einem normalen (nativen) Bild verhalten -- keine laufende Projektionsrechnung
    // während des Zeichnens/Bearbeitens (Nutzer-Vorgabe 2026-08-24, Auslöser: Formen "landeten ganz
    // woanders, teils viel größer" nach dem Fertigstellen -- dieselbe Instabilitätsklasse, die für Kacheln
    // bereits durch "ansichts-lokal, keine Live-Umrechnung" behoben wurde, s. Plan Nachtrag 3). Dafür
    // bekommt ein Overlay bei jeder Erstellung/Bearbeitung IN Tiny Sky zusätzlich zu seiner normalen
    // (nativen) Geometrie eine mitlaufende TinySkyGeometry-Momentaufnahme -- diese ist die alleinige
    // Grundlage für Anzeige/Bearbeitung SOLANGE Tiny Sky aktiv ist (kein WCS-Bezug nötig). Beim Verlassen
    // von Tiny Sky (Umschalt-Button) wird daraus EINMALIG (nicht laufend) die native, projektionsverzerrte
    // Geometrie berechnet -- s. dortige Stelle. Die TinySkyGeometry bleibt danach erhalten (nicht
    // gelöscht), damit ein erneutes Betreten von Tiny Sky dieselbe Form unverändert wiederfindet und
    // weiter bearbeitbar bleibt. Native Bearbeitung eines Overlays mit vorhandener TinySkyGeometry
    // aktualisiert diese bewusst NICHT (siehe onUpdateOverlay) -- vermeidet dieselbe Instabilität in der
    // Gegenrichtung; betrifft nur den seltenen Fall, ein Objekt abwechselnd in beiden Ansichten zu
    // bearbeiten. Bereits vorhandene, aus dem Solve stammende Inhalte (Sternbilder/DSOs/Katalogsterne)
    // durchlaufen NIE diesen Pfad (sie werden nie über onCreateOverlay/onUpdateOverlay erzeugt) -- deren
    // bestehende, umgerechnete Tiny-Sky-Anzeige (s. displayOverlays) bleibt komplett unverändert.
    fun AnnotationOverlay.snapshotTinySkyGeometryIfActive(): AnnotationOverlay =
        if (tinySkyActive) {
            copy(
                tinySkyGeometry = TinySkyGeometry(
                    center, size, rotationDegrees, labelAngleDeg, labelLeaderPx, freehandSegments,
                ),
            )
        } else {
            this
        }
    var nextSolveTileId by remember(bitmap) { mutableLongStateOf(1L) }
    var solveTilesRunning by remember(bitmap) { mutableStateOf(false) }
    var solveTilesStatus by remember(bitmap) { mutableStateOf("") }
    // Live-Ticker: rollende Ereignis-Historie des Lösens (Zeitstempel, Info). Newest unten, gekappt
    // bei MAX. Zeitstempel bleibt fix, nur die Info-Zeile läuft (Marquee).
    val solveTicker = remember(bitmap) { mutableStateListOf<Pair<String, String>>() }
    // Langdruck auf eine Ticker-Zeile -> diese Meldung groß in der Bildmitte (Tap schließt).
    var tickerFocusMessage by remember { mutableStateOf<String?>(null) }
    // De-Warp: lebender Schalter. AN -> ab jetzt gelöste Kacheln werden vor dem Solve entzerrt
    // (aus bereits gelösten Kacheln). Umschalten wirkt auf die nächsten Kacheln; jede Kachel
    // merkt sich ihren Modus (SolveTile.dewarp). Nicht an bitmap gebunden -> bleibt über Bildwechsel.
    var dewarpEnabled by remember { mutableStateOf(false) }
    // Wurde die zuletzt angezeigte Lösung mit De-Warp-Kacheln gebaut? Dann greift die
    // Refraktionskorrektur (Rand-RMS) für die angezeigte Lösung + Reprojektion. Sonst normaler Weg.
    var dewarpUsedInLastSolve by remember(bitmap) { mutableStateOf(false) }
    // Vollbild-Ansicht "Astrometrie-Kalibrierung": Kacheln werden NUR hier gezeichnet/bearbeitet;
    // der Zustand (Kacheln + Lösungen) bleibt beim Verlassen erhalten (remember(bitmap)).
    var calibrationActive by remember(bitmap) { mutableStateOf(false) }
    // Kalibrierungs-Popup vorübergehend ausgeblendet (Bild frei), Modus bleibt aktiv. Zurück über
    // „Ausrichten". Beim Platzieren (selectedTool==SolveRegion) ist die Karte ebenfalls verborgen.
    var calibrationPopupHidden by remember(bitmap) { mutableStateOf(false) }
    // Aktives Segment der Astrometrie-Kalibrierung: 0=Kacheln, 1=Solver, 2=Projektion (bleibt erhalten).
    var calibrationSegment by remember { mutableStateOf(0) }
    // Slider in der Kalibrierungs-Karte gezogen -> Karte ausblenden (Bild sichtbar), wie bei den Popups.
    var calibrationSliderDragging by remember { mutableStateOf(false) }
    // Kacheln im Astrometrie-Modus sichtbar (bleiben sichtbar, solange man kalibriert). Per Toggle
    // (Kalibrier-Menü + Ticker) aus-/einblendbar, ohne sie zu löschen.
    var tilesVisible by remember { mutableStateOf(true) }
    // Beschriftungs-Radierer LIVE sichtbar machen: die billige Vorschau-Version bumpt pro
    // Pinselsegment, der teure Overlay-Cache-Redraw (der die Namen tatsächlich ausstanzt) aber
    // bewusst nur bei Strichende (Performance). Das fühlte sich wie „reagiert erst nach dem
    // Strich" an. Fix: den teuren Redraw zusätzlich zeitgedrosselt (alle ~100ms) WÄHREND des
    // Strichs mitziehen lassen, statt auf jedes einzelne Segment zu reagieren.
    LaunchedEffect(Unit) {
        var lastSynced = editorSession.annotationErasePreviewVersionState.intValue
        while (isActive) {
            delay(100)
            val current = editorSession.annotationErasePreviewVersionState.intValue
            if (current != lastSynced) {
                lastSynced = current
                editorSession.annotationEraseVersionState.intValue++
            }
        }
    }
    // Panorama-bewusster Feinjustier-Seed: bei Kachel-Solves die Referenzsterne aus den bereits
    // gelösten Kacheln positionieren (grob reicht — Nutzer schiebt in der Feinjustierung nach).
    // Wird nach jeder gelösten Kachel, beim Löschen/Leeren/Undo/Redo aufgerufen. Einzelbild-Pfad
    // bleibt unberührt (kein Kachel-Seed -> startFisheyeAlign nutzt den FOV-Regler wie bisher).
    fun refreshPanoramaSeed() {
        val src = bitmap ?: return
        val solved = solveTiles.filter { it.status == SolveTileStatus.Solved }
        // Kachelbestand hat sich geändert -> gespeicherte Feinjustier-Referenzen verwerfen (neu aufbauen).
        // NUR den in Bearbeitung befindlichen Seed -- bereits bestätigte Sterne (fisheyeConfirmedAnchorDirs)
        // bleiben unangetastet, die sind unabhängig vom Kachelbestand weiterhin korrekt.
        fisheyeAlignRefs = null
        if (solved.isEmpty()) {
            // Keine gelöste Kachel: wenn eine Einzellösung existiert, daraus seeden (NICHT auf den
            // Default-FOV-Seed zurückfallen -> genau das erzeugte den falschen 52°/67°-Seed). Sonst verwerfen.
            val base = singleSolveWcs
            if (base != null) {
                calibrationAsyncJob?.cancel()
                calibrationAsyncJob = scope.launch {
                    val calib = withContext(Dispatchers.Default) {
                        panoramaCalibrationFromWcs(
                            base, src.width, src.height, allowedProjectionKinds(panoProjectionChoice),
                        )
                    }
                    if (calib != null) {
                        // Gewickelt (equidistantSeedFrom) statt roh -> derselbe, garantiert nie
                        // zurückfaltende Mechanismus wie FOV-Vorschau/Feinjustierung (s. fisheyeEquidistantSeed).
                        fisheyeAlignSeed = FisheyeRefiner.equidistantSeedFrom(calib.solution, src.width, src.height, fisheyeFovLongDeg)
                        fisheyeBaseFit = calib.solution
                        alignProjectionKind = calib.kind
                        alignProjectionRms = calib.rms
                    } else {
                        fisheyeAlignSeed = null
                    }
                }
            } else {
                fisheyeAlignSeed = null
            }
            return
        }
        val perTile = solved.map { tileAnchorPairs(it, src.width, src.height) }
        val anchors = perTile.flatten()
        if (anchors.size < 3) return
        val weights = FisheyeRefiner.tileVoteWeights(perTile.map { it.size })
        calibrationAsyncJob?.cancel()
        calibrationAsyncJob = scope.launch {
            val calib = withContext(Dispatchers.Default) {
                FisheyeRefiner.calibratePanorama(
                    anchors, src.width, src.height, allowedProjectionKinds(panoProjectionChoice), weights,
                )
            } ?: return@launch
            // Gewickelt (equidistantSeedFrom) statt roh -> derselbe, garantiert nie zurückfaltende
            // Mechanismus wie FOV-Vorschau/Feinjustierung (s. fisheyeEquidistantSeed).
            fisheyeAlignSeed = FisheyeRefiner.equidistantSeedFrom(calib.solution, src.width, src.height, fisheyeFovLongDeg) // Referenzsterne landen ~richtig, Nutzer schiebt nach.
            fisheyeBaseFit = calib.solution
            alignProjectionKind = calib.kind
            alignProjectionRms = calib.rms
        }
    }
    var targetObjectQuery by remember { mutableStateOf("") }
    var selectedTool by editorSession.selectedToolState
    var selectedOverlayId by editorSession.selectedOverlayIdState
    var nextOverlayId by editorSession.nextOverlayIdState
    var selectedConstellation by editorSession.selectedConstellationState
    // X-Spiegelung standardmäßig AN (Nutzerwunsch): passt zum Sternhimmel-Blick (Foto = Blick nach oben).
    var selectedConstellationMirrorX by remember { mutableStateOf(true) }
    var selectedConstellationMirrorY by remember { mutableStateOf(false) }
    var constellationColorArgb by remember { mutableStateOf(preferences.getLong("color_constellation", 0xFFFFFFFF)) }
    // Globale DSO-Typ-Standardfarben (Katalog bearbeiten -> Farben, Nutzerwunsch 2026-08-20) --
    // überschreiben AstapOverlayMapper.dsoStyleForType()s eingebaute Farbe je Gruppe. Defaults =
    // dieselben eingebauten Töne (builtInColorFor), damit sich am Erscheinungsbild zunächst nichts ändert.
    var dsoGalaxyColorArgb by remember {
        mutableStateOf(preferences.getLong("color_dso_galaxy", AstapOverlayMapper.builtInColorFor(DsoColorGroup.Galaxy)))
    }
    var dsoGlobularColorArgb by remember {
        mutableStateOf(preferences.getLong("color_dso_globular", AstapOverlayMapper.builtInColorFor(DsoColorGroup.Globular)))
    }
    var dsoOpenClusterColorArgb by remember {
        mutableStateOf(preferences.getLong("color_dso_open_cluster", AstapOverlayMapper.builtInColorFor(DsoColorGroup.OpenCluster)))
    }
    var dsoNebulaColorArgb by remember {
        mutableStateOf(preferences.getLong("color_dso_nebula", AstapOverlayMapper.builtInColorFor(DsoColorGroup.Nebula)))
    }
    var dsoOtherColorArgb by remember {
        mutableStateOf(preferences.getLong("color_dso_other", AstapOverlayMapper.builtInColorFor(DsoColorGroup.Other)))
    }
    var constellationFont by remember { mutableStateOf(OverlayFont.SansSerif) }
    var constellationLineStyle by remember { mutableStateOf(OverlayLineStyle.Solid) }
    // Standard: dünnste Linie (Slider-Min 1) + kleinste Anker (Slider-Min 0.018). Auf der kleinsten
    // Ankerstufe bleiben die Sternbildlinien durchgehend verbunden (Trim erst beim Vergrößern, s. OverlayGeometry).
    var constellationStrokeWidth by remember { mutableFloatStateOf(1f) }
    var constellationAnchorRadiusRatio by remember { mutableFloatStateOf(0.018f) }
    // Bewusstes Platzieren: != null bedeutet "der nächste Tipp setzt EIN Objekt dieses Typs"
    // (Einmal-Modus). Wird nur über die "Hinzufügen"-Aktionen gesetzt, nicht durch Werkzeugwahl.
    var pendingPlacement by remember { mutableStateOf<EditorTool?>(null) }
    // Bestehendes Freihand-Overlay wird per Langdruck-Button "Weiterzeichnen" erneut mit dem Stift
    // bearbeitet (weitermalen ODER radieren) -- != null blendet dieses Overlay aus der normalen
    // Anzeige aus (die laufende Zeichnen-Sitzung zeigt seine Segmente stattdessen live), und der
    // Commit (onCreateFreehandOverlay) aktualisiert/löscht es, statt ein neues zu erzeugen.
    var editingFreehandSource by remember { mutableStateOf<AnnotationOverlay?>(null) }
    var constellationOpacity by remember { mutableFloatStateOf(1.0f) }
    var constellationShowName by remember { mutableStateOf(false) }
    // ~15% der Namensgrößen-Skala (14..240): 14 + 0.15*226 ≈ 48.
    var constellationNameTextSize by remember { mutableFloatStateOf(48f) }
    var shapeColorArgb by remember { mutableStateOf(preferences.getLong("color_shapes", 0xFFFFD28A)) }
    // Ausgekoppelt aus shapeColorArgb (Nutzerwunsch 2026-08-20): eigene globale Standardfarbe für neu
    // platzierte Fadenkreuz-/Kometenmarker-Reticles, unabhängig von normalen Formen. Default identisch
    // zum bisherigen shapeColorArgb-Wert, damit sich am Erscheinungsbild zunächst nichts ändert.
    var reticleColorArgb by remember { mutableStateOf(preferences.getLong("color_reticle", 0xFFFFD28A)) }
    // Standard so dünn wie im ShapesPanel-Regler (0.2f..40f, s. dort) überhaupt möglich (Nutzerwunsch
    // 2026-08-22; Reglerbereich 2026-08-30 deutlich erweitert, dieser Default bewusst unverändert
    // gelassen -- ein bestehendes Foto soll durch die reine Bereichserweiterung nicht plötzlich dünnere
    // Standardlinien zeigen).
    var shapeStrokeWidth by remember { mutableFloatStateOf(1.5f) }
    var shapeLineStyle by remember { mutableStateOf(OverlayLineStyle.Solid) }
    var shapeFilled by remember { mutableStateOf(false) }
    // Nutzer-Vorgabe 2026-08-30 (Punkt 2, "Kollisionsvermeidung automatisch neu berechnen"): jedes
    // Verschieben/Skalieren/Drehen/Löschen eines Overlays erhöht diesen Zähler; ein einzelner,
    // entprellter LaunchedEffect (s. unten bei den DSO-Farb-Debounces) löst daraufhin einmalig
    // syncDeepSkyLayer mit voller Kollisionssuche aus -- exakt dasselbe, bereits etablierte Entprell-
    // Prinzip wie beim Farbwähler, nur an ein Zähler- statt ein Werte-Signal gekoppelt (die auslösenden
    // Ereignisse sind diskrete Gesten-Enden, kein kontinuierlicher Wert).
    var dsoNeighborRecheckTick by remember { mutableIntStateOf(0) }
    var shapeFont by remember { mutableStateOf(OverlayFont.SansSerif) }
    var shapeOpacity by remember { mutableFloatStateOf(0.92f) }
    // Ziehbares Namens-Schwänzchen (Ellipse/Rechteck): eigene Regler-Zustände, seeden beim Auswählen
    // aus dem jeweiligen Overlay (analog zu den übrigen shape*-Variablen oben).
    var shapeShowName by remember { mutableStateOf(false) }
    var shapeNameTextSize by remember { mutableFloatStateOf(30f) }
    var shapeNameColorArgb by remember { mutableStateOf(preferences.getLong("color_shape_names", 0xFFFFFFFF)) }
    var shapeNameBold by remember { mutableStateOf(true) }
    var textColorArgb by remember { mutableStateOf(preferences.getLong("color_text", 0xFFFFFFFF)) }
    var textFont by remember { mutableStateOf(OverlayFont.SansSerif) }
    var textSize by remember { mutableFloatStateOf(42f) }
    var textBold by remember { mutableStateOf(true) }
    var textOpacity by remember { mutableFloatStateOf(1f) }
    var detectedStars by editorSession.detectedStarsState
    var showDetectedStars by editorSession.showDetectedStarsState
    var starDetectionSensitivity by remember { mutableFloatStateOf(0.58f) }
    var showConstellationAnchors by editorSession.showConstellationAnchorsState
    var isDetecting by remember { mutableStateOf(false) }
    var activePanel by remember { mutableStateOf<EditorPanel?>(null) }
    // Slider-Ziehen-Signal des aktiven Popups (hochgezogen, damit der Editor-Canvas die Pinsel-/
    // Radierer-Vorschau NUR während des Ziehens zeigt und beim Loslassen verschwindet). Reset, sobald
    // kein Popup offen ist.
    var sliderDragging by remember { mutableStateOf(false) }
    LaunchedEffect(activePanel, calibrationActive) {
        if (activePanel == null) sliderDragging = false
        // Maskier-/Radierer-Pinsel nur aktiv, solange sein Menü das GEWÄHLTE ist. Sobald das Menü auf
        // ein ANDERES Panel (Einstellungen/Export/…) ODER in die Astrometrie-Kalibrierung wechselt,
        // zurück auf „Bewegen" (sonst malt der Pinsel dort weiter -> versehentliche Streumaske).
        // Die Astrometrie setzt activePanel==null + calibrationActive; darum getrennt prüfen.
        // activePanel==null OHNE Kalibrierung bleibt erlaubt = bewusstes Malen bei minimiertem Popup.
        val brushActive = selectedTool == EditorTool.Mask || selectedTool == EditorTool.EraseArea
        if (brushActive && (calibrationActive || (activePanel != null && activePanel != EditorPanel.Mask))) {
            selectedTool = EditorTool.Move
        }
    }
    // Verschiebe-/Anker-Modus aktiv -> Bottom-Bar gesperrt (nur Zoom/Pan), bis „Fertig".
    var editorLocked by remember { mutableStateOf(false) }
    // Signal an EditorCanvas, den Verschiebe-/Anker-Modus zu beenden (ausgelöst von der Aktions-Bubble
    // „Fertig verschieben" – der frühere grüne Balken entfällt; jede Erhöhung beendet den Modus).
    var exitMoveModeRequest by remember { mutableStateOf(0) }
    // Analoges Signal für den Zeichnen-Modus („Fertig zeichnen" in der Aktions-Bubble): committet alle
    // in dieser Sitzung gesammelten Freihand-Segmente als EIN Overlay und beendet den Modus.
    var exitDrawModeRequest by remember { mutableStateOf(0) }
    var showSkyPicker by remember { mutableStateOf(false) }
    var pendingTextPosition by remember { mutableStateOf<Offset?>(null) }
    var editingTextOverlayId by remember { mutableStateOf<Long?>(null) }
    var referenceOverlayId by remember { mutableStateOf<Long?>(null) }
    var catalog by remember { mutableStateOf(ConstellationCatalog.featured) }
    var milkyWayLayers by remember { mutableStateOf<List<MilkyWayLayer>>(emptyList()) }
    var skyCatalogStars by remember { mutableStateOf<List<CatalogStar>>(emptyList()) }
    var referenceCatalogStars by remember { mutableStateOf<List<CatalogStar>>(emptyList()) }
    var deepSkyObjects by remember { mutableStateOf<List<DeepSkyObject>>(emptyList()) }
    // Vorberechnete Katalogzugehörigkeit je Objekt (einmalig beim Laden bestimmt) -- vermeidet
    // tausendfaches Regex-Matching (DeepSkyCatalogGroup.of()) bei jedem Regler-Tick in syncDeepSkyLayer.
    var deepSkyCatalogGroups by remember { mutableStateOf<Map<DeepSkyObject, DeepSkyCatalogGroup>>(emptyMap()) }
    // Vorberechnete Best-Known-Zuordnung je Objekt (einmalig beim Laden bestimmt, Performance-Fix
    // 2026-09-02) -- vermeidet, dass jeder Slider-Commit im Objekte-Menü erneut BestKnownCatalog.match()
    // über den kompletten Katalog laufen lässt, s. AstapOverlayMapper.createDeepSkyOverlays.
    var deepSkyBestKnownMatches by remember { mutableStateOf<Map<DeepSkyObject, BestKnownEntry?>>(emptyMap()) }
    // App-eigene Form-Fakten (normalisierte Bezeichnung -> DsoShape: maj + optional min/pa). Leer -> Kreis aus Katalog-dim.
    var dsoShapes by remember { mutableStateOf<Map<String, DsoShape>>(emptyMap()) }
    var d3CatalogSettings by remember {
        mutableStateOf(
            D3CatalogSettings(
                showStars = preferences.getBoolean("d3_show_stars", true),
                starMagnitudeLimit = preferences.getFloat("d3_star_magnitude_limit", 5.8f),
                showDeepSkyObjects = preferences.getBoolean("d3_show_deep_sky_objects", false),
                deepSkyMagnitudeLimit = preferences.getFloat("d3_deep_sky_magnitude_limit", 12f),
                showMilkyWay = preferences.getBoolean("d3_show_milky_way", true),
                showBackgroundConstellations = preferences.getBoolean("d3_show_background_constellations", true),
                showConstellationStarPoints = preferences.getBoolean("d3_show_constellation_star_points", true),
                showConstellationNames = preferences.getBoolean("d3_show_constellation_names", true),
            ),
        )
    }

    val astapEquipmentFieldOfView = AstapFieldOfViewCalculator.calculate(astapCaptureSettings)
    val astapFieldOfView = when {
        astapCaptureSettings.fovSource == AstapFovSource.Manual ->
            astapCaptureSettings.manualVerticalFovDegrees
        astapCaptureSettings.fovSource == AstapFovSource.AutomaticExif ->
            astapExifFieldOfView?.fieldOfView?.verticalDegrees
                ?: astapEquipmentFieldOfView.verticalDegrees
        else -> astapEquipmentFieldOfView.verticalDegrees
    }.coerceIn(0.05f, 179f)

    val annotate = remember {
        AnnotateSelections().apply {
            starNameColorArgb = preferences.getLong("color_star_names", starNameColorArgb)
            gridColorArgb = preferences.getLong("color_grid", gridColorArgb)
            bestKnownEnabled = preferences.getBoolean("dso_best_known_enabled", bestKnownEnabled)
            // Re-quantisieren beim Laden (Best-Known-Vorgabe Abschnitt V): verhindert, dass ein alter,
            // vor dieser Umstellung gespeicherter Float-Zwischenwert nach einem Neustart zurückkehrt.
            dsoMinSizePercent = quantizeDsoMinSizePercent(
                preferences.getFloat("dso_min_size_percent", dsoMinSizePercent),
            )
        }
    }

    // Zeichenreihenfolge der 5 Schichten (Katalog bearbeiten -> Schichten, Nutzerwunsch 2026-08-20).
    var layerDrawOrder by remember {
        mutableStateOf(parseLayerDrawOrder(preferences.getString("layer_draw_order", null)))
    }

    // Verwaltete Beschriftungs-Ebenen: jede Ebene wird beim Anwenden komplett ersetzt,
    // sodass abgewählte Kategorien/Kataloge wieder verschwinden.
    /**
     * Setzt die AKTIVE Lösung wieder auf die manuell gewählte Projektion, falls [panoProjectionChoice]
     * einen der drei gespeicherten Kandidaten benennt ("Mesh"/"DetailMesh"/"Custom").
     *
     * Hintergrund (Gerätebefund 2026-09-03, A1): `allowedProjectionKinds()` kennt für diese drei Werte
     * keinen eigenen Zweig und fällt auf ALLE sechs starren Modelle zurück. Jeder erneute Fit
     * (`solveAllTiles()`, `reprojectPanorama()`) hat dadurch den automatischen Sieger berechnet UND
     * angewendet, während die UI weiter "Mesh" anzeigte -- exakt die beobachtete Log-Folge
     * `choice=Mesh` -> `reproject model=Mercator` -> `autoSelected=Mercator choice=Mesh`.
     *
     * Der automatische Sieger wird weiterhin vollständig BERECHNET und protokolliert (nichts an der
     * Auto-Auswahl selbst geändert) -- er wird nur nicht mehr angezeigt, wenn der Nutzer bewusst etwas
     * anderes gewählt hat und dieser Kandidat gültig vorliegt. Ist der gewählte Kandidat NICHT (mehr)
     * vorhanden, bleibt der Auto-Sieger aktiv (ehrlicher als gar keine Lösung).
     *
     * Ruft bewusst KEINE sync*Layer()-Funktion auf -- die Aufrufer tun das ohnehin direkt danach; so
     * entsteht kein zweiter, paralleler Annotationspfad.
     */
    fun applyManualProjectionOverride(): Boolean {
        val fit = when (panoProjectionChoice) {
            "Mesh" -> sparseMeshFit
            "DetailMesh" -> richMeshFit
            "Custom" -> customAlignFit
            else -> null
        } ?: return false
        lastSolvedWcs = fit
        fisheyeBaseFit = fit
        fisheyeAlignSeed = fit
        alignProjectionKind = if (panoProjectionChoice == "Custom") {
            customAlignKind ?: alignProjectionKind
        } else {
            PanoProjectionKind.Mesh
        }
        alignProjectionRms = when (panoProjectionChoice) {
            "Mesh" -> sparseMeshRms
            "DetailMesh" -> richMeshRms
            else -> customAlignRms
        }
        return true
    }

    /**
     * REIN DIAGNOSTISCH (Nutzer-Auftrag 2026-09-03, Abschnitt 6): misst, wie weit das Bildrechteck unter
     * [solution] von der tatsächlich durch gelöste Kacheln gestützten Himmelsregion abweicht.
     *
     * Für ein Raster über das Bild: Pixel -> Richtung (im Pano-Rahmen), zurück in den äquatorialen Rahmen
     * gedreht, dann der minimale Winkelabstand zu [anchors] (= `panoSolveAnchors`, Bild-px <-> äquatoriale
     * Richtung, projektionsunabhängig aus den Kachel-WCS gewonnen).
     *
     * Greift NICHT in die Modellauswahl ein -- kein Kandidat wird abgelehnt, kein RMS/BIC verändert, kein
     * Culling. Die Zahlen dienen ausschließlich dem späteren Vergleich "gesundes Bild" vs. "entarteter
     * Mercator-Fall", bevor überhaupt über eine Schwelle entschieden wird.
     */
    fun logSupportDistance(
        model: String,
        solution: PanoramaWcsSolution?,
        anchors: List<Pair<Offset, Vec3>>,
        imgW: Int,
        imgH: Int,
    ) {
        if (solution == null || anchors.isEmpty() || imgW <= 0 || imgH <= 0) return
        val inv = solution.rotEquToPano.transpose()
        val anchorDirs = anchors.map { it.second.normalized() }
        val distances = ArrayList<Double>(81)
        for (iy in 0..8) {
            for (ix in 0..8) {
                val dirPano = solution.projection.pixelToDirection(
                    imgW.toDouble() * ix / 8.0,
                    imgH.toDouble() * iy / 8.0,
                ) ?: continue
                val dirEq = (inv * dirPano).normalized()
                var best = 180.0
                for (a in anchorDirs) {
                    val dot = (dirEq.x * a.x + dirEq.y * a.y + dirEq.z * a.z).coerceIn(-1.0, 1.0)
                    val ang = Math.toDegrees(acos(dot))
                    if (ang < best) best = ang
                }
                distances += best
            }
        }
        if (distances.isEmpty()) return
        val sorted = distances.sorted()
        fun q(p: Double) = sorted[((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)]
        AppDiagnostics.record(
            "support_distance model=$model sampleCount=${sorted.size} " +
                "medianDeg=${"%.1f".format(q(0.50))} p90Deg=${"%.1f".format(q(0.90))} " +
                "p95Deg=${"%.1f".format(q(0.95))} maxDeg=${"%.1f".format(sorted.last())}",
        )
    }

    fun syncConstellationLayer(
        recordUndo: Boolean = true,
        // Default bewusst GeometryChanged (die "sichere" Seite): eine künftig neu hinzukommende
        // Aufrufstelle verliert damit höchstens eine manuelle Ankerkorrektur, statt still wieder
        // veraltete Pixel aus der vorherigen Projektion einzuschleppen. Die vier reinen
        // Darstellungs-Aufrufstellen setzen VisualOnly ausdrücklich.
        reason: ConstellationSyncReason = ConstellationSyncReason.GeometryChanged,
    ) {
        val wcs = lastSolvedWcs
        val source = bitmap
        if (recordUndo) editorSession.recordHistory()
        // Kernpunkt des Fixes (Nutzer-Auftrag 2026-09-03, nachgewiesener Stale-Anchor-Fehler):
        // anchorOverrides enthält NICHT nur manuelle Korrekturen, sondern (seit
        // createConstellationOverlays) für JEDEN überlebenden Stern die FINAL PROJIZIERTE Position.
        // Beim Projektionswechsel (Mercator -> Mesh) wurden diese alten Pixel bisher unbedingt in das
        // frisch projizierte Overlay zurückkopiert, während `edgePolylines` bewusst frisch blieb ->
        // Linien neu, Anker/Name/Trefferfläche alt. Bei einer echten Geometrie-/Projektionsänderung
        // gewinnen deshalb ab jetzt die frischen Anker vollständig; manuelle Ankerkorrekturen gehen
        // dabei BEWUSST verloren (ausdrückliche Produktentscheidung für diesen sicheren Fix).
        val preservePriorAnchors = reason == ConstellationSyncReason.VisualOnly
        // Individuelle Sternbild-Anpassungen (Farbe/Linie/Deckkraft/etc.) vor dem Neuaufbau sichern,
        // nach constellation-ID gruppiert -> nach dem (Neu-)Solve nur die POSITION/Geometrie aus dem
        // frischen Fit übernehmen, individuelle Stil-Abweichungen aber je Sternbild erhalten. Ohne
        // das würde jeder Reproject/Nach-Solve ALLE 88 Sternbilder auf die aktuellen globalen
        // Stil-Variablen zurücksetzen (das bisherige Verhalten).
        val previousStyleById = overlays
            .filter { it.layer == AnnotationLayer.Constellation }
            .mapNotNull { ov -> ov.constellation?.id?.let { id -> id to ov } }
            .toMap()
        overlays.removeAll { it.layer == AnnotationLayer.Constellation }
        if (annotate.constellationsEnabled && wcs != null && source != null) {
            val completenessList = mutableListOf<ConstellationCompleteness>()
            AstapOverlayMapper.createConstellationOverlays(
                catalog = catalog,
                solution = wcs,
                imageWidth = source.width,
                imageHeight = source.height,
                foregroundMask = editorSession.solveMaskState.value,
                colorArgb = constellationColorArgb,
                strokeWidth = constellationStrokeWidth,
                anchorRadiusRatio = constellationAnchorRadiusRatio,
                lineStyle = constellationLineStyle,
                opacity = constellationOpacity,
                showNames = annotate.showConstellationNames,
                nameTextSize = constellationNameTextSize,
                font = constellationFont,
                completenessOut = completenessList,
                panoramaWorkflowActive = tinySkyWorkflowUsed,
            ).forEach { fresh ->
                val prior = fresh.constellation?.id?.let { previousStyleById[it] }
                val merged = if (prior != null) {
                    fresh.copy(
                        colorArgb = prior.colorArgb,
                        strokeWidth = prior.strokeWidth,
                        anchorRadiusRatio = prior.anchorRadiusRatio,
                        lineStyle = prior.lineStyle,
                        opacity = prior.opacity,
                        font = prior.font,
                        nameTextSize = prior.nameTextSize,
                        showAnchors = prior.showAnchors,
                        // anchorOverrides NUR bei einem reinen Darstellungs-Resync aus `prior` übernehmen
                        // (Sprachwechsel, Sichtbarkeits-/Namens-Schalter) -- dort ist die Geometrie
                        // unverändert, und eine manuelle Ankerkorrektur soll erhalten bleiben. Bei einer
                        // echten Projektions-/Geometrieänderung bleibt es bei `fresh`s neu projizierten
                        // Ankern (s. preservePriorAnchors oben).
                        anchorOverrides = if (preservePriorAnchors) prior.anchorOverrides else fresh.anchorOverrides,
                        // constellationNameOffset bleibt in BEIDEN Fällen erhalten: das ist ein relativer
                        // Pixel-Versatz auf den automatisch berechneten Namensanker, keine absolute
                        // Position -- er veraltet beim Projektionswechsel also nicht auf dieselbe Weise.
                        // Bewusst nicht mit angefasst (Scope).
                        constellationNameOffset = prior.constellationNameOffset,
                    )
                } else {
                    fresh
                }
                overlays += merged.copy(id = nextOverlayId++)
            }
            constellationCompletenessById = completenessList.associateBy { it.id }
        }
        AppDiagnostics.record("annotate_constellations enabled=${annotate.constellationsEnabled}")
        // Nutzer-Vorgabe 2026-09-04: nachvollziehbar machen, WARUM die Naht-Aufteilung aktiv/inaktiv ist.
        val seamPeriodPresent = ((wcs as? PanoramaWcsSolution)
            ?: ((wcs as? MosaicWcsSolution)?.fallback as? PanoramaWcsSolution))
            ?.projection?.horizontalPeriodPx() != null
        AppDiagnostics.record(
            "constellation_seam projectionKind=$alignProjectionKind " +
                "horizontalPeriodPx=${if (seamPeriodPresent) "present" else "absent"} " +
                "panoramaWorkflowActive=$tinySkyWorkflowUsed " +
                "seamAwareForConstellations=${tinySkyWorkflowUsed && seamPeriodPresent} " +
                "source=$reason reason=${
                    when {
                        !seamPeriodPresent -> "projection_not_periodic"
                        !tinySkyWorkflowUsed -> "no_360_workflow_for_this_image"
                        else -> "periodic_projection_from_360_workflow"
                    }
                }",
        )
        AppDiagnostics.record(
            "constellation_sync reason=$reason preservePriorAnchors=$preservePriorAnchors " +
                "projectionKind=$alignProjectionKind " +
                "overlayCount=${overlays.count { it.layer == AnnotationLayer.Constellation }}",
        )
        // Genau EIN Beispiel-Overlay je Sync (kein Pro-Frame-Log, keine Flut): zeigt, ob für dieses
        // Sternbild die alten oder die frisch projizierten Anker verwendet wurden.
        overlays.firstOrNull { it.layer == AnnotationLayer.Constellation }?.let { sample ->
            val priorCount = sample.constellation?.id?.let { previousStyleById[it]?.anchorOverrides?.size } ?: 0
            AppDiagnostics.record(
                "constellation_sync_sample id=${sample.constellation?.id} " +
                    "priorAnchorCount=$priorCount freshAnchorCount=${sample.anchorOverrides.size} " +
                    "usedPriorAnchors=${preservePriorAnchors && priorCount > 0} " +
                    "usedFreshAnchors=${!preservePriorAnchors || priorCount == 0}",
            )
        }
    }

    /**
     * Kompakter Vollstaendigkeits-Audit aus dem ZULETZT gelaufenen syncConstellationLayer()-Durchlauf
     * (Geraetebefund 2026-09-04): der ausfuehrliche Audit am Ende von solveAllTiles() beschreibt immer
     * den Zustand DIREKT NACH DEM SOLVE -- nach einem manuellen Projektionswechsel stand in der Diagnose
     * also weiterhin die Mercator-Bilanz, obwohl inzwischen Mesh aktiv war. Diese Kurzfassung wird nach
     * jedem manuellen Wechsel nachgezogen.
     *
     * Bewusst OHNE den `caudit[]`-Genauigkeitsteil: der braucht `allRealMatches` (Blob- + .corr-Treffer),
     * das nur innerhalb von solveAllTiles() existiert. Hier werden ausschliesslich die bereits
     * vorhandenen Vollstaendigkeitszahlen aus `constellationCompletenessById` gelesen -- kein neuer
     * Rechenweg, keine Aenderung an der Erzeugung.
     */
    fun logConstellationAudit(source: String) {
        val rows = catalog.mapNotNull { p -> constellationCompletenessById[p.id]?.let { p to it } }
        if (rows.isEmpty()) return
        AppDiagnostics.record(
            "constellation_audit total=${catalog.size} " +
                "drawn=${rows.count { it.second.survivingEdges > 0 }} " +
                "in_fov_empty=${rows.count { it.second.anyStarInFov && it.second.survivingEdges == 0 }} " +
                "not_in_fov=${rows.count { !it.second.anyStarInFov }} " +
                "accuracy_checked=0 projectionKind=$alignProjectionKind source=$source",
        )
    }

    // Vor solveAllTiles() deklariert (lokale Funktionen dürfen nur zuvor deklarierte referenzieren),
    // da solveAllTiles() nach jedem Kachel-Solve alle drei Layer (Konstellation/DSO/Stern) synct.
    // fullLabelPlacement 2026-08-30 von recordUndo ENTKOPPELT (Nutzerbefund "Beschriftungen liegen im
    // eigenen Objekt/schneiden die Kontur"): vorher lief bei JEDEM recordUndo=false-Aufruf (Solve-
    // Abschluss, Reprojektion, Solution-Import, entprellter Farbwechsel, Fisheye-Align) automatisch der
    // GÜNSTIGE Live-Drag-Pfad OHNE jede Kollisionsprüfung (labelAngle fest auf 0°) -- obwohl an all
    // diesen Stellen gar nicht gezogen wird und die teure Suche sich problemlos leisten ließe. Default
    // bleibt `true` (volle Suche), NUR der echte Live-Slider-Pfad (s. onApplyDeepSky) übergibt weiterhin
    // explizit `false` während des Ziehens.
    // Reiner Compute-Teil von syncDeepSkyLayer (Performance-Fix 2026-09-02, Objekte-Menü-Slider froren
    // ein): liest nur (deepSkyObjects/WCS/annotate/...), mutiert KEINEN Compose-State (overlays/
    // editorSession/AppDiagnostics/nextOverlayId) -- dadurch sicher auch von einem Hintergrund-
    // Dispatcher (Dispatchers.Default) aufrufbar, s. requestDeepSkySync() weiter unten. syncDeepSkyLayer()
    // selbst bleibt für seine bestehenden ~13 Aufrufstellen (Solve-Abschluss/Reprojektion/Sprachwechsel/
    // Solution-Import/Fisheye-Align/entprellter Farbwechsel usw.) unverändert synchron, delegiert nur
    // noch hierher statt die Logik zu duplizieren -- keine Verhaltensänderung an diesen Stellen.
    data class DeepSkyComputeResult(
        val markers: List<AnnotationOverlay>,
        val constellationNameObstacles: Int,
        val userOverlayObstacles: Int,
    )
    fun computeDeepSkyOverlays(fullLabelPlacement: Boolean): DeepSkyComputeResult {
        val wcs = lastSolvedWcs ?: return DeepSkyComputeResult(emptyList(), 0, 0)
        val source = bitmap ?: return DeepSkyComputeResult(emptyList(), 0, 0)
        // Sternbild-Namen als Hindernis für die DSO-Namensplatzierung (s. externalObstacleBoxes-
        // Kommentar dort) -- aus der zuletzt synchronisierten Sternbild-Ebene. Layer-gefiltert (Constellation
        // bzw. layer==null) -- unabhängig davon, ob eine ältere DSO-Generation noch in overlays steht.
        val constellationNameBoxes = overlays.mapNotNull {
            if (it.layer == AnnotationLayer.Constellation) constellationNameObstacleBox(it, source.width.toFloat(), source.height.toFloat()) else null
        }
        // Manuell platzierte Formen/Zeichnungen/Text/Fadenkreuze ebenfalls als Hindernis (Nutzer-Vorgabe
        // 2026-08-30) -- vorher wichen DSO-Auto-Labels NUR Sternbildnamen aus, nicht bereits vorhandenen
        // Nutzer-Overlays.
        val userOverlayBoxes = overlays.mapNotNull { OverlayGeometry.userOverlayObstacleBox(it) }
        val markers = AstapOverlayMapper.createDeepSkyOverlays(
            objects = deepSkyObjects,
            solution = wcs,
            imageWidth = source.width,
            imageHeight = source.height,
            foregroundMask = editorSession.solveMaskState.value,
            categories = annotate.deepSkyCategories(),
            catalogMagRange = { annotate.dsoMagRangeOf(it) },
            catalogs = annotate.catalogs.toSet(),
            // Punkt 5 (Nutzer-Vorgabe 2026-08-30): nur bei aktiviertem Schalter greift der neue Regler --
            // deaktiviert (Default) bleibt exakt der bisherige, feste Default (2.5) in createDeepSkyOverlays.
            strokeWidth = if (annotate.deepSkyStrokeWidthEnabled) annotate.deepSkyStrokeWidth else 2.5f,
            nameOpacityLinked = annotate.deepSkyNameOpacityLinked,
            nameTextSize = annotate.deepSkyNameSize,
            font = annotate.deepSkyFont,
            showNames = annotate.deepSkyShowNames,
            catalogGroups = deepSkyCatalogGroups,
            pinnedIds = annotate.pinnedDsoIds,
            sizeOverrides = annotate.dsoSizeOverrides,
            dsoColorOverrides = mapOf(
                DsoColorGroup.Galaxy to dsoGalaxyColorArgb,
                DsoColorGroup.Globular to dsoGlobularColorArgb,
                DsoColorGroup.OpenCluster to dsoOpenClusterColorArgb,
                DsoColorGroup.Nebula to dsoNebulaColorArgb,
                DsoColorGroup.Other to dsoOtherColorArgb,
            ),
            shapes = dsoShapes,
            opacityFor = { annotate.dsoOpacityOf(it) },
            bestKnownEnabled = annotate.bestKnownEnabled,
            bestKnownMatches = deepSkyBestKnownMatches,
            minNaturalSizePercent = annotate.dsoMinSizePercent.takeIf { it > 0f },
            externalObstacleBoxes = constellationNameBoxes + userOverlayBoxes,
            // Volle (teure) Namensplatzierung nur beim Loslassen; beim Live-Ziehen günstig -> kein Ruckeln.
            fullLabelPlacement = fullLabelPlacement,
            lang = AppLocale.resolvedLanguageTag,
        )
        return DeepSkyComputeResult(markers, constellationNameBoxes.size, userOverlayBoxes.size)
    }

    fun syncDeepSkyLayer(recordUndo: Boolean = true, fullLabelPlacement: Boolean = true) {
        if (lastSolvedWcs == null || bitmap == null) return
        // Bei Live-Updates (Slider zieht) KEIN Undo-Schritt pro Tick -> Stack nicht fluten.
        if (recordUndo) editorSession.recordHistory()
        val result = computeDeepSkyOverlays(fullLabelPlacement)
        overlays.removeAll { it.layer == AnnotationLayer.DeepSky }
        result.markers.forEach { overlays += it.copy(id = nextOverlayId++) }
        // Datei-I/O nur beim committeten Aufruf, nicht bei jedem Live-Drag-Tick (Regler-Performance).
        // Punkt 12: Stroke-/Opazitäts-Kopplungs-Status + Hindernis-Boxen-Zahl mitgeloggt, damit sich am
        // Diagnose-Dump ablesen lässt, ob Punkt 2/5/6 tatsächlich wie eingestellt wirken.
        if (recordUndo) {
            AppDiagnostics.record(
                "annotate_deepsky count=${result.markers.size} strokeWidthEnabled=${annotate.deepSkyStrokeWidthEnabled} " +
                    "strokeWidth=${if (annotate.deepSkyStrokeWidthEnabled) annotate.deepSkyStrokeWidth else 2.5f} " +
                    "nameOpacityLinked=${annotate.deepSkyNameOpacityLinked} " +
                    "constellationNameObstacles=${result.constellationNameObstacles} userOverlayObstacles=${result.userOverlayObstacles} " +
                    "fullLabelPlacement=$fullLabelPlacement",
            )
        }
    }

    // Performance-Fix 2026-09-02 (Objekte-Menü-Slider froren ein): entprellte, hintergrund-berechnete
    // DSO-Neusynchronisierung für den EINEN tatsächlich per Slider-Drag betroffenen Auslöser
    // (onApplyDeepSky, s. requestDeepSkySync-Aufrufstelle unten) -- ersetzt den bisherigen SYNCHRONEN
    // syncDeepSkyLayer()-Aufruf direkt im UI-Event. annotate.dsoResyncGeneration (Feld auf
    // AnnotateSelections, s. dortiger KDoc -- MethodTooLargeException-Vorsicht) ist ein reiner Zähler:
    // jeder Commit erhöht ihn, LaunchedEffect(...) bricht eine noch laufende VORHERIGE Coroutine
    // automatisch ab (Compose-Standardverhalten bei Key-Wechsel) und startet neu. Die zusätzliche
    // `if (... != myGeneration) return`-Prüfung NACH der Hintergrundrechnung deckt zusätzlich den Fall
    // ab, dass eine bereits laufende (nicht kooperativ abbrechbare, da createDeepSkyOverlays keine
    // Suspend-Punkte hat) Altberechnung fertig wird, NACHDEM schon ein neuerer Commit ausgelöst wurde --
    // "letzter Wert gewinnt" ist damit in jedem Interleaving garantiert, nicht nur im Normalfall. Die
    // eigentliche schwere Arbeit (createDeepSkyOverlays, CPU-gebunden) läuft auf Dispatchers.Default;
    // overlays/nextOverlayId werden erst NACH Abschluss, zurück auf Main, in einem einzigen billigen
    // Schritt aktualisiert.
    fun requestDeepSkySync() {
        editorSession.recordHistory()
        annotate.dsoResyncGeneration++
    }
    LaunchedEffect(annotate.dsoResyncGeneration) {
        if (annotate.dsoResyncGeneration <= 0) return@LaunchedEffect
        val myGeneration = annotate.dsoResyncGeneration
        val startNanos = System.nanoTime()
        // Immer volle (teure) Kollisions-Namensplatzierung -- dieser Pfad ist per Konstruktion nur der
        // COMMITTETE Zustand (Slider-Release oder Schalter-Tap), nie ein Live-Drag-Zwischenwert mehr
        // (der läuft für die betroffenen Regler jetzt komplett am Pipeline vorbei, s. DeepSkyAnnotationSection).
        val result = withContext(Dispatchers.Default) { computeDeepSkyOverlays(fullLabelPlacement = true) }
        if (annotate.dsoResyncGeneration != myGeneration) return@LaunchedEffect
        overlays.removeAll { it.layer == AnnotationLayer.DeepSky }
        result.markers.forEach { overlays += it.copy(id = nextOverlayId++) }
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        AppDiagnostics.record(
            "dso_ui_slider_commit visibleCount=${result.markers.size} durationMs=$durationMs " +
                "bestKnownEnabled=${annotate.bestKnownEnabled} minObjectSizePercent=${annotate.dsoMinSizePercent}",
        )
    }

    fun syncStarLayer(recordUndo: Boolean = true) {
        val wcs = lastSolvedWcs
        val source = bitmap
        if (wcs == null || source == null) return
        if (recordUndo) editorSession.recordHistory()
        overlays.removeAll { it.layer == AnnotationLayer.Star }
        val baseStars = referenceCatalogStars.ifEmpty { skyCatalogStars }
        // Tycho-2-Tiefkatalog (optional, s. Tycho2Store): nur wenn installiert UND der Regler über die
        // Hipparcos-Grenze (Mag. 8) hinausgeht. NUR beim Loslassen (recordUndo) abgefragt, nicht bei
        // jedem Live-Drag-Tick -- SQLite-Disk-I/O beim Ziehen würde ruckeln (anders als die reine
        // In-Memory-Filterung von baseStars oben, die weiterhin live mitläuft).
        val catalogStars = if (recordUndo && annotate.starAll && annotate.starMagnitude > 8f &&
            Tycho2Store.isInstalled(context)
        ) {
            val deepStars = wcs.raDecBoundingBox(source.width, source.height)?.let { box ->
                Tycho2Store.queryStars(context, box, annotate.starMagnitude)
            }.orEmpty()
            if (deepStars.isEmpty()) baseStars else baseStars + deepStars
        } else {
            baseStars
        }
        val markers = AstapOverlayMapper.createStarOverlays(
            catalogStars = catalogStars,
            constellationPatterns = catalog,
            solution = wcs,
            imageWidth = source.width,
            imageHeight = source.height,
            foregroundMask = editorSession.solveMaskState.value,
            includeNamed = annotate.starNamed,
            includeConstellation = annotate.starConstellation,
            allToMagnitude = if (annotate.starAll) annotate.starMagnitude else null,
            maxStars = starDisplayCap,
            nameOpacity = annotate.starNameOpacity,
            nameColorArgb = annotate.starNameColorArgb,
            font = annotate.starFont,
            nameTextSize = annotate.starNameSize,
            showDots = annotate.starShowDots,
            lang = AppLocale.resolvedLanguageTag,
        )
        markers.forEach { overlays += it.copy(id = nextOverlayId++) }
        AppDiagnostics.record("annotate_stars count=${markers.size}")
    }

    // Bündelt den kompletten Kalibrierungs-Zustand für EINEN Undo/Redo-Schritt bzw. den "direkt
    // nach dem Solve"-Wiederherstellungspunkt (s. CalibrationSnapshot-Klasse weiter unten im File).
    fun snapshotCalibration() = CalibrationSnapshot(
        lastSolvedWcs = lastSolvedWcs,
        originalSolvedWcs = originalSolvedWcs,
        singleSolveWcs = singleSolveWcs,
        singleImageSolved = singleImageSolved,
        panoSolveAnchors = panoSolveAnchors,
        panoSolveWeights = panoSolveWeights,
        fisheyeConfirmedAnchorDirs = fisheyeConfirmedAnchorDirs,
        lastSolvedTileWcs = lastSolvedTileWcs,
        dewarpUsedInLastSolve = dewarpUsedInLastSolve,
        fisheyeBaseFit = fisheyeBaseFit,
        fisheyeAlignSeed = fisheyeAlignSeed,
        fisheyeAlignRefs = fisheyeAlignRefs,
        alignProjectionKind = alignProjectionKind,
        alignProjectionRms = alignProjectionRms,
        panoProjectionChoice = panoProjectionChoice,
        constellationsEnabled = annotate.constellationsEnabled,
        customAlignFit = customAlignFit,
        customAlignRms = customAlignRms,
        customAlignKind = customAlignKind,
        richMeshFit = richMeshFit,
        richMeshRms = richMeshRms,
        richMeshAutoEligible = richMeshAutoEligible,
        richMeshRejectReason = richMeshRejectReason,
        sparseMeshFit = sparseMeshFit,
        sparseMeshRms = sparseMeshRms,
        projectionCandidateRms = projectionCandidateRms,
    )


    fun applyCalibrationSnapshot(s: CalibrationSnapshot) {
        calibrationAsyncJob?.cancel()
        lastSolvedWcs = s.lastSolvedWcs
        originalSolvedWcs = s.originalSolvedWcs
        singleSolveWcs = s.singleSolveWcs
        singleImageSolved = s.singleImageSolved
        panoSolveAnchors = s.panoSolveAnchors
        panoSolveWeights = s.panoSolveWeights
        fisheyeConfirmedAnchorDirs = s.fisheyeConfirmedAnchorDirs
        lastSolvedTileWcs = s.lastSolvedTileWcs
        dewarpUsedInLastSolve = s.dewarpUsedInLastSolve
        fisheyeBaseFit = s.fisheyeBaseFit
        fisheyeAlignSeed = s.fisheyeAlignSeed
        fisheyeAlignRefs = s.fisheyeAlignRefs
        alignProjectionKind = s.alignProjectionKind
        alignProjectionRms = s.alignProjectionRms
        panoProjectionChoice = s.panoProjectionChoice
        annotate.constellationsEnabled = s.constellationsEnabled
        customAlignFit = s.customAlignFit
        customAlignRms = s.customAlignRms
        customAlignKind = s.customAlignKind
        richMeshFit = s.richMeshFit
        richMeshRms = s.richMeshRms
        richMeshAutoEligible = s.richMeshAutoEligible
        richMeshRejectReason = s.richMeshRejectReason
        sparseMeshFit = s.sparseMeshFit
        sparseMeshRms = s.sparseMeshRms
        projectionCandidateRms = s.projectionCandidateRms
        // recordUndo=false: das Wiederherstellen selbst darf keinen neuen History-Eintrag erzeugen.
        syncConstellationLayer(recordUndo = false)
        syncDeepSkyLayer(recordUndo = false)
        syncStarLayer(recordUndo = false)
    }

    // Undo/Redo für Kalibrierungsänderungen OHNE Kachel-Beteiligung (Feinjustierung anwenden,
    // Original wiederherstellen, Projektion wechseln, "Eigene" wählen).
    val calibUndoStack = remember(bitmap) { mutableStateListOf<CalibrationSnapshot>() }
    val calibRedoStack = remember(bitmap) { mutableStateListOf<CalibrationSnapshot>() }
    fun snapshotCalibrationForUndo() {
        calibUndoStack.add(snapshotCalibration())
        while (calibUndoStack.size > 50) calibUndoStack.removeAt(0)
        calibRedoStack.clear()
        editorSession.recordExternalStep(EditorSessionViewModel.HistoryKind.Calibration)
    }
    fun calibUndo(): Boolean {
        if (calibUndoStack.isEmpty()) return false
        calibRedoStack.add(snapshotCalibration())
        applyCalibrationSnapshot(calibUndoStack.removeAt(calibUndoStack.lastIndex))
        return true
    }
    fun calibRedo(): Boolean {
        if (calibRedoStack.isEmpty()) return false
        calibUndoStack.add(snapshotCalibration())
        applyCalibrationSnapshot(calibRedoStack.removeAt(calibRedoStack.lastIndex))
        return true
    }

    // Undo/Redo für Kacheln (Layout-Edits UND Solves) -- führt IMMER die zugehörige Kalibrierung mit,
    // damit nach einem Kachel-Undo/Redo die Anzeige-Lösung nie von den Kacheln abweicht (vorher: nur
    // solveTiles wurde gesichert, lastSolvedWcs/originalSolvedWcs blieben verwaist stehen).
    val tileUndoStack = remember(bitmap) { mutableStateListOf<TileEditSnapshot>() }
    val tileRedoStack = remember(bitmap) { mutableStateListOf<TileEditSnapshot>() }
    fun snapshotTilesForUndo() {
        tileUndoStack.add(
            TileEditSnapshot(
                solveTiles.toList(), tileQualityById, snapshotCalibration(),
                tileOwnRmsById, tileCorrRefsById, tileOverlapRmsById,
            ),
        )
        while (tileUndoStack.size > 50) tileUndoStack.removeAt(0)
        tileRedoStack.clear()
        // Kachel-Schritt in den GLOBALEN Verlauf einreihen -> Undo/Redo ist menü-unabhängig.
        editorSession.recordExternalStep(EditorSessionViewModel.HistoryKind.Tile)
    }
    fun applyTileSnapshot(snap: TileEditSnapshot) {
        solveTiles.clear()
        solveTiles.addAll(snap.tiles)
        tileQualityById = snap.quality
        // Wiederherstellen statt leeren (s. TileEditSnapshot-Kommentar): snap.ownRms/snap.corrRefs
        // gehören exakt zum WCS-Stand DIESES Snapshots, damit bleibt "Eigene Solve-Genauigkeit" nach
        // einem Undo/Redo erhalten statt für jede bereits gelöste Kachel verloren zu gehen.
        tileOwnRmsById = snap.ownRms
        tileCorrRefsById = snap.corrRefs
        tileOverlapRmsById = snap.overlapRms
        selectedSolveTileId = null
        // Synchron + exakt (statt refreshPanoramaSeed(), das asynchron neu fitten und den gerade
        // wiederhergestellten Zustand Millisekunden später überschreiben könnte -- Race-Fix).
        applyCalibrationSnapshot(snap.calibration)
    }
    fun tileUndo(): Boolean {
        if (tileUndoStack.isEmpty()) return false
        tileRedoStack.add(
            TileEditSnapshot(
                solveTiles.toList(), tileQualityById, snapshotCalibration(),
                tileOwnRmsById, tileCorrRefsById, tileOverlapRmsById,
            ),
        )
        applyTileSnapshot(tileUndoStack.removeAt(tileUndoStack.lastIndex))
        return true
    }
    fun tileRedo(): Boolean {
        if (tileRedoStack.isEmpty()) return false
        tileUndoStack.add(
            TileEditSnapshot(
                solveTiles.toList(), tileQualityById, snapshotCalibration(),
                tileOwnRmsById, tileCorrRefsById, tileOverlapRmsById,
            ),
        )
        applyTileSnapshot(tileRedoStack.removeAt(tileRedoStack.lastIndex))
        return true
    }

    // Teilmanuelle Fisheye-Feinausrichtung: aus der (mittengenauen) Lösung einen vollbild-
    // tauglichen Fisheye-Seed ableiten (zentrale Referenzsterne -> calibrateFromReferences) und
    // die Vollbild-Ausrichtung öffnen. Rechenarbeit läuft off-main.
    // (1) Zentral-Fit (Achse/Rotation/Zentrum/Parität) aus der mittengenauen Lösung — UNABHÄNGIG vom
    // FOV. Schwer (LM-Fit) -> einmal berechnen + cachen. Kandidaten: bild-interne Katalogsterne in
    // der zentralen Zone (nicht die global hellsten, die meist außerhalb des Ausschnitts liegen).
    fun fisheyeCentralFit(wcs: WcsSolutionLike, source: Bitmap): PanoramaWcsSolution? {
        val imageW = source.width
        val imageH = source.height
        val imageCenter = Offset(imageW / 2f, imageH / 2f)
        val centralRadius = min(imageW, imageH) * 0.25f
        val centralRefs = skyCatalogStars.filter { it.magnitude <= 6.5f }.mapNotNull { star ->
            val p = wcs.skyToImage(star.point, imageH) ?: return@mapNotNull null
            if (p.x < 0f || p.x > imageW || p.y < 0f || p.y > imageH) return@mapNotNull null
            if ((p - imageCenter).getDistance() > centralRadius) return@mapNotNull null
            Triple(
                star.magnitude,
                p,
                raDecToVector(star.point.raDegrees.toDouble(), star.point.decDegrees.toDouble()),
            )
        }.sortedBy { it.first }.take(40).map { it.second to it.third }
        if (centralRefs.size < 3) return null
        return FisheyeRefiner.calibrateFromReferences(centralRefs, imageW, imageH)
    }

    // (2) Sauberes equidistantes Fisheye (k=0, faltet nie) aus Zentral-Fit + FOV — billig je Tick.
    // Dünner Wrapper um FisheyeRefiner.equidistantSeedFrom: die vorherige Fassung fragte Bildmitte +
    // Parität per directionToPixel(Vec3(0,0,1)) ab — das ist nur für die azimutale Modellfamilie
    // (Fisheye/Stereografisch/Rektilinear) tatsächlich "Blickrichtung"; bei der Zylinder-Familie
    // (Equirectangular/Zylindrisch/Mercator, z.B. Mercator als häufigem Mosaik-Gewinnermodell) liegt
    // die Bildmitte auf +X, +Z ist deren Pol-/Hochachse — dieselbe Frage lieferte dort still `null`
    // (cx/cy fielen auf die geometrische Bildmitte zurück) UND die unverändert übernommene Rotation
    // blieb zusätzlich falsch orientiert (Referenzsterne wie Polaris/Alioth landeten astronomisch
    // unmöglich, teils im fotografierten Vordergrund). equidistantSeedFrom ermittelt die echte
    // Blickrichtung projektionsartunabhängig über Pixel-Sonden + rotEquToPano.transpose() und baut
    // daraus eine frische Rotation, statt eine ggf. falsch konventionierte zu übernehmen.
    fun fisheyeEquidistantSeed(baseFit: PanoramaWcsSolution, source: Bitmap, fovLongDeg: Float): PanoramaWcsSolution =
        FisheyeRefiner.equidistantSeedFrom(baseFit, source.width, source.height, fovLongDeg)

    // (3) Gleichverteilte benannte Referenzsterne, per Seed projiziert (Raster 5×4, je Zelle hellster;
    // ±10% Randmarge; ~25). Liefert Stern + Bildpunkt -> Align nutzt die Sterne, Vorschau die Punkte.
    fun fisheyeGridMarkers(seed: PanoramaWcsSolution, source: Bitmap): List<Pair<CatalogStar, Offset>> {
        val imageW = source.width
        val imageH = source.height
        val markerMargin = min(imageW, imageH) * 0.10f
        // Rückseiten-/Faltungs-Cull: azimutale Modelle (Fisheye/Stereografisch) können durch die
        // Polynom-/Radialterme weit entfernte Sterne (θ>~91° zur optischen Achse, +Z im Pano-Frame)
        // zurück auf die Bildebene falten -> die landen fälschlich „im Bild" und wären als
        // Referenzstern unbrauchbar (z. B. Orion/Crux in einem Sommer-Milchstraßen-Feld). Gleicher
        // Cull wie bei den Sternbild-Overlays, damit Vorschau und Feinjustierung konsistent sind.
        val azimuthal = seed.projection is FisheyeProjection || seed.projection is StereographicProjection
        val backHemisphereCos = kotlin.math.cos(Math.toRadians(91.0))
        val inField = skyCatalogStars.filter { it.properName.isNotBlank() }.mapNotNull { star ->
            if (azimuthal) {
                val cam = seed.rotEquToPano *
                    raDecToVector(star.point.raDegrees.toDouble(), star.point.decDegrees.toDouble())
                if (cam.z < backHemisphereCos) return@mapNotNull null
            }
            val p = seed.skyToImage(star.point, imageH) ?: return@mapNotNull null
            if (p.x < -markerMargin || p.x > imageW + markerMargin ||
                p.y < -markerMargin || p.y > imageH + markerMargin
            ) {
                return@mapNotNull null
            }
            Triple(star, p, star.magnitude)
        }
        // Dichteres Raster + höheres Cap -> mehr (gut verteilte) Referenzsterne für Feinausrichtung/Fit.
        val cols = 7
        val rows = 5
        val cellW = imageW / cols.toFloat()
        val cellH = imageH / rows.toFloat()
        val brightestPerCell = HashMap<Int, Triple<CatalogStar, Offset, Float>>()
        for (cand in inField) {
            val gx = (cand.second.x / cellW).toInt().coerceIn(0, cols - 1)
            val gy = (cand.second.y / cellH).toInt().coerceIn(0, rows - 1)
            val key = gy * cols + gx
            val cur = brightestPerCell[key]
            if (cur == null || cand.third < cur.third) brightestPerCell[key] = cand
        }
        // Raster-Picks zuerst, dann mit weiteren hellen In-Field-Sternen auffüllen (ohne Duplikate).
        val ordered = brightestPerCell.values.sortedBy { it.third } + inField.sortedBy { it.third }
        val seen = HashSet<CatalogStar>()
        val result = ArrayList<Pair<CatalogStar, Offset>>()
        for (t in ordered) {
            if (result.size >= 45) break
            if (seen.add(t.first)) result += t.first to t.second
        }
        return result
    }

    // Live-Vorschau der Referenzstern-Platzierung beim FOV-Ziehen aktualisieren (Basis-Fit gecacht).
    fun updateFisheyePreview() {
        val src = bitmap ?: return
        val base = fisheyeBaseFit
        if (base != null) {
            // Billig: nur neu projizieren (Hauptthread ok). Name behalten -> Vorschau zeigt Sternnamen.
            fisheyePreviewPoints = fisheyeGridMarkers(fisheyeEquidistantSeed(base, src, fisheyeFovLongDeg), src)
                .map { it.first.properName to it.second }
        } else {
            val wcs = originalSolvedWcs ?: lastSolvedWcs ?: return
            calibrationAsyncJob?.cancel()
            calibrationAsyncJob = scope.launch {
                // Bevorzugt die WCS-treue Panorama-Kalibrierung (Rectilinear-fähig, korrekte Orientierung);
                // nur als letzter Ausweg die reine Fisheye-Näherung. So sitzen die Referenzsterne in der
                // ECHTEN Himmelsregion des Bildes statt (bei Rectilinear-Fotos) daneben.
                val computed = withContext(Dispatchers.Default) {
                    panoramaCalibrationFromWcs(
                        wcs, src.width, src.height, allowedProjectionKinds(panoProjectionChoice),
                    )?.solution ?: fisheyeCentralFit(wcs, src)
                } ?: return@launch
                fisheyeBaseFit = computed
                fisheyePreviewPoints = withContext(Dispatchers.Default) {
                    fisheyeGridMarkers(fisheyeEquidistantSeed(computed, src, fisheyeFovLongDeg), src)
                        .map { it.first.properName to it.second }
                }
            }
        }
    }

    fun startFisheyeAlign() {
        // IMMER von der Original-Astrometrie seeden, nicht von einem evtl. angewendeten (kaputten)
        // Fit -> Wiederöffnen nach Fehl-Apply zeigt wieder die ursprünglichen Referenzsterne.
        val wcs = originalSolvedWcs ?: lastSolvedWcs
        val source = bitmap
        if (wcs == null || source == null) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_solve_first_then_align),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        fisheyePreviewPoints = emptyList() // Vorschau aus — die Feinausrichtung übernimmt jetzt.
        val fovLongDeg = fisheyeFovLongDeg
        calibrationAsyncJob?.cancel()
        calibrationAsyncJob = scope.launch {
            val prepared = withContext(Dispatchers.Default) {
                // Wie in der Vorschau: erst die WCS-treue Panorama-Kalibrierung (korrekte Orientierung),
                // sonst die Fisheye-Näherung -> Referenzsterne landen in der richtigen Himmelsregion.
                val baseFit = fisheyeBaseFit
                    ?: panoramaCalibrationFromWcs(
                        wcs, source.width, source.height, allowedProjectionKinds(panoProjectionChoice),
                    )?.solution
                    ?: fisheyeCentralFit(wcs, source)
                val seed = baseFit?.let { fisheyeEquidistantSeed(it, source, fovLongDeg) }
                val markers = if (seed != null) fisheyeGridMarkers(seed, source).map { it.first } else emptyList()
                val blobs = StarDetector.detect(source, starDetectionSensitivity).map { it.offset }
                Triple(baseFit, seed, Pair(markers, blobs))
            }
            val (baseFit, seed, markersBlobs) = prepared
            val (markers, blobs) = markersBlobs
            if (baseFit != null) fisheyeBaseFit = baseFit
            if (seed == null || markers.size < 3) {
                Toast.makeText(
                    context,
                    context.getString(R.string.fisheye_align_need_stars),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            fisheyeAlignSeed = seed
            fisheyeAlignRefs = null // frischer Seed -> Referenzen neu aufbauen.
            fisheyeAlignBright = markers
            fisheyeAlignBlobs = blobs
            fisheyeAlignActive = true
            AppDiagnostics.record(
                "fisheye_align_started bright=${markers.size} blobs=${blobs.size}",
            )
        }
    }

    // KACHEL-AUSRICHTUNG: alle (noch nicht gelösten) Kacheln plate-solven, aus ihren WCS verteilte
    // Anker (Pixel↔Himmel) ableiten, daraus einen globalen Fisheye-Fit rechnen und die Feinausrichtung
    // damit VORAUSGERICHTET öffnen. Gelöste Kacheln liefern zusätzlich eine Mosaik-WCS für die
    // Beschriftung. Nutzt den aktuell gewählten Solver (ASTAP offline / Nova blind).
    // Ein Ereignis in den Live-Ticker schreiben (mit Zeitstempel), zusätzlich die neueste Zeile in
    // solveTilesStatus (Bubble) und die Sperrbildschirm-Notification. Läuft auf Main (Solve-Scope).
    fun tick(msg: String) {
        val stamp = android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()).toString()
        solveTicker.add(stamp to msg)
        if (solveTicker.size > MAX_TICKER_LINES) {
            solveTicker.removeRange(0, solveTicker.size - MAX_TICKER_LINES)
        }
        solveTilesStatus = msg
        SolveController.update(msg)
    }
    // Unit 6 (2026-09-02): EINZIGER Ort, der einen laufenden Solve (Einzelbild ODER Kacheln) sauber
    // abbricht -- genutzt von cancelAstapSolve() (App-Abbrechen-Button, s. unten) UND vom Notification-
    // Abbrechen-Zweig in solveAllTiles(). Vorher lief der Notification-Zweig über eine eigene, inline
    // kopierte Teilmenge dieser Logik (kein Kachel-Statusreset) -- zwei divergente Abbruch-Pfade mit
    // unterschiedlichem Ergebnis für denselben Nutzer-Klick, je nachdem ob er den App-Button oder die
    // Notification-Aktion antippte. Absichtlich VOR solveAllTiles() deklariert (nicht erst bei
    // cancelAstapSolve() weiter unten), damit solveAllTiles()s Notification-Callback sie unabhängig von
    // Kotlins Vorwärtsreferenz-Regeln für lokale Funktionen sicher aufrufen kann.
    fun resetSolveStateOnCancel() {
        val wasSolving = astapOperationState is AstapOperationState.Solving ||
            astapOperationState is AstapOperationState.SolvingOnline
        astapSolveJob?.cancel()
        astapSolveJob = null
        SolveController.finish()
        if (wasSolving) {
            // Generation ZUERST hochzählen -- jede noch laufende Solve-Coroutine (Einzelbild oder
            // Kacheln) erkennt sich damit ab jetzt als veraltet, bevor irgendein UI-Zustand unten
            // geschrieben wird (s. solveGeneration-Kommentar an der Deklaration).
            solveGeneration++
            AppDiagnostics.record("astap_solve_cancelled_explicitly")
            astapOperationState = AstapOperationState.Cancelled
            // Sofort zurücksetzen, nicht erst auf den (kooperativen -- bei Nova ggf. erst nach dem
            // laufenden HTTP-Aufruf greifenden) Coroutine-Abbruch warten: sonst blieb eine Kachel
            // sichtbar "blau" (Solving) und gesperrt, obwohl der Abbrechen-Button oben schon
            // verschwunden war (astapOperationState bereits Idle) -- wirkte wie "Abbrechen tut
            // nichts" (Nutzerbefund 2026-08-17). Der finally-Block in solveAllTiles() macht denselben
            // Reset ohnehin nochmal (generation-geschützt, s. dort), sobald die Coroutine tatsächlich
            // abgewickelt ist (harmlos).
            for (i in solveTiles.indices) {
                if (solveTiles[i].status == SolveTileStatus.Solving) {
                    solveTiles[i] = solveTiles[i].copy(status = SolveTileStatus.Pending)
                }
            }
            solveTilesRunning = false
            solveTilesStatus = ""
        }
    }
    fun solveAllTiles() {
        val source = bitmap
        val loaded = loadedImage
        if (source == null || loaded == null) {
            Toast.makeText(context, context.getString(R.string.toast_load_image_first), Toast.LENGTH_SHORT).show()
            return
        }
        // Absichtlich NUR auf "gar keine Kachel" prüfen (nicht mehr auf "keine offene Kachel") --
        // sonst bleibt nach dem Löschen einzelner Kacheln (Nutzerwunsch) kein erreichbarer Weg,
        // Anker/Mosaik/Qualitätswerte auf den neuen (kleineren) Kachelbestand neu zu berechnen, ohne
        // erst eine weitere Kachel hinzuzufügen. Sind alle verbleibenden Kacheln bereits gelöst,
        // überspringt der Lös-Loop unten einfach (openIds leer) und es läuft direkt in die
        // Anker+Mosaik-Neuberechnung.
        if (solveTiles.isEmpty()) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_add_regions_first),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (solveTilesRunning) return
        snapshotTilesForUndo() // Status/WCS-Änderungen der Kacheln rückgängig machbar.
        solveTilesRunning = true
        selectedSolveTileId = null
        calibrationPopupHidden = false // Beim Lösen Fortschritt/Ticker sichtbar lassen.
        solveTicker.clear() // Live-Ticker für diesen Lauf frisch starten.
        solveTilesStatus = context.getString(R.string.tiles_solving)
        // Foreground-Service + WakeLock: hält Prozess/CPU/Netz am Leben, auch bei gesperrtem Bildschirm
        // / App im Hintergrund (sonst kappt Android dem Online-Solve das Netz -> „Keine Internetverbindung").
        SolveController.begin(
            title = context.getString(R.string.tiles_solving),
            text = context.getString(R.string.status_preparing),
            // Abbrechen aus der Notification: identischer Reset wie der App-Abbrechen-Button (Unit 6,
            // 2026-09-02) -- vorher eine eigene, unvollständige Inline-Kopie ohne Kachel-Statusreset.
            onCancel = { resetSolveStateOnCancel() },
        )
        SolveForegroundService.start(context)
        // In astapSolveJob laufen lassen -> der vorhandene Abbrechen-Weg (cancelAstapSolve) greift.
        astapSolveJob = scope.launch {
            // Eigene Generation dieser Solve-Coroutine (Unit 6) -- s. solveGeneration-Deklaration.
            // Jeder spätere Abbruch (egal über welchen der beiden Wege oben) zählt solveGeneration
            // hoch; diese Coroutine erkennt das an den Prüfstellen unten und verwirft dann ihr Ergebnis.
            val myGeneration = solveGeneration
            fun isStaleSolve() = solveGeneration != myGeneration
            fun setTile(id: Long, transform: (SolveTile) -> SolveTile) {
                val i = solveTiles.indexOfFirst { it.id == id }
                if (i >= 0) solveTiles[i] = transform(solveTiles[i])
            }
            try {
                val mask = editorSession.solveMaskState.value
                val solveBitmap = withContext(Dispatchers.Default) {
                    if (mask != null) SolveMask.applyTo(source, mask) else source
                }
                val imgW = source.width
                val imgH = source.height
                // Tiny-Sky-Kacheln (s. TileSourceSpace, Plan Nachtrag 1): rein geometrische Umrechnungs-
                // Kette Tiny-Sky-Pixel <-> native Pixel (kein Solve involviert, s. TileDeWarp.kt) --
                // einmal pro Lauf berechnet, unabhängig davon, ob überhaupt eine Tiny-Sky-Kachel dabei
                // ist (billig, reine Geometrie).
                val tinySkyBitmapForSolve = tinySkyBitmap
                val tinySkyNativeProjectionForSolve = TileDeWarp.equirectangularNativeModel(imgW, imgH).projection
                // Volles Modell (nicht nur .projection) aufgehoben -- wird unten auch als De-Warp-
                // Korrekturgrundlage für Tiny-Sky-Kacheln gebraucht (s. dewarpModel weiter unten).
                val tinySkyOverviewModelForSolve = tinySkyBitmapForSolve?.let {
                    TileDeWarp.stereographicOverviewModel(it.width)
                }
                val tinySkyOverviewProjectionForSolve = tinySkyOverviewModelForSolve?.projection
                // Projektionswahl: Auto = alle Modelle, sonst genau das gewählte (manueller Override).
                val allowedKinds = allowedProjectionKinds(panoProjectionChoice)
                // Nur OFFENE Kacheln lösen (Pending/Failed/Suspect); gelöste werden übersprungen.
                val openIds = solveTiles.filter { it.status != SolveTileStatus.Solved }.map { it.id }
                tick(
                    if (openIds.isEmpty()) {
                        // Kein Lös-Loop nötig (alle Kacheln bereits gelöst) -- reiner Neu-Berechnen-Lauf
                        // nach z. B. Kachel-Löschen (s. onDeleteSolveTile).
                        context.getString(R.string.status_recomputing)
                    } else {
                        context.getString(R.string.astrometry_started_tiles, openIds.size)
                    },
                )
                openIds.forEachIndexed { _, id ->
                    // Unit 6: Abbruch bereits erkannt -> keine weitere Kachel mehr STARTEN (die zuletzt
                    // begonnene Kachel unwindet unabhängig davon über ihre eigene CancellationException).
                    if (isStaleSolve()) return@forEachIndexed
                    val tile = solveTiles.firstOrNull { it.id == id } ?: return@forEachIndexed
                    if (tile.status == SolveTileStatus.Solved) return@forEachIndexed
                    // Kachel-Nummer = gezeichnete Nummer im Bild (Platzier-Reihenfolge), damit Ticker
                    // und Bild ÜBEREINSTIMMEN (nicht die Position unter den offenen Kacheln).
                    val tileNo = solveTiles.indexOfFirst { it.id == id } + 1
                    // Kacheln bleiben im Raum, in dem sie erstellt wurden (s. TileSourceSpace, Plan
                    // Nachtrag 1) -- Zuschnitt-Quelle + deren Maße daher PRO KACHEL wählen, nicht wie
                    // bisher einmal fürs Ganze. Tiny-Sky-Kacheln schneiden unmaskiert direkt aus dem
                    // gecachten Tiny-Sky-Bitmap (die Masken-Funktion ist in Tiny Sky ohnehin schon
                    // unerreichbar); praktisch immer vorhanden, sobald überhaupt eine Tiny-Sky-Kachel
                    // existiert (das setzt voraus, dass das Bitmap beim Erstellen gebaut wurde und es
                    // für die Foto-Sitzung nie geleert wird) -- trotzdem defensiv statt mit !! abgesichert.
                    val tileIsTinySky = tile.sourceSpace == TileSourceSpace.TinySky
                    val cropSource = if (tileIsTinySky) tinySkyBitmapForSolve else solveBitmap
                    if (cropSource == null) {
                        setTile(id) { it.copy(status = SolveTileStatus.Failed) }
                        return@forEachIndexed
                    }
                    val cropW = cropSource.width
                    val cropH = cropSource.height
                    val bb = solveTileBoundingBox(tile)
                    val rx = bb.left.roundToInt().coerceIn(0, cropW - 1)
                    val ry = bb.top.roundToInt().coerceIn(0, cropH - 1)
                    val rw = bb.width.roundToInt().coerceIn(64, cropW - rx)
                    val rh = bb.height.roundToInt().coerceIn(64, cropH - ry)
                    // Für alles, was NATIVE Bildkoordinaten erwartet (Nächste-Kachel-Hinweis-Suche u.a.):
                    // bei einer Tiny-Sky-Kachel den Mittelpunkt EXAKT (Einzelpunkt-Umrechnung, nicht
                    // convertOverlayGeometry) nach nativen Koordinaten umrechnen. Fällt die Umrechnung
                    // aus (z.B. ganz am Scheibenrand), Bildmitte als harmloser Fallback -- betrifft dann
                    // nur die Positions-HINWEIS-Suche (Komfort), nicht den eigentlichen Solve/Fit.
                    val tileNativeCenter: Offset = if (tileIsTinySky) {
                        tinySkyOverviewProjectionForSolve!!
                            .pixelToDirection(tile.center.x.toDouble(), tile.center.y.toDouble())
                            ?.let { tinySkyNativeProjectionForSolve.directionToPixel(it) }
                            ?: Offset(imgW / 2f, imgH / 2f)
                    } else {
                        tile.center
                    }
                    setTile(id) { it.copy(status = SolveTileStatus.Solving) }
                    val status = context.getString(R.string.solving_tile_number, tileNo)
                    tick(status) // setzt solveTilesStatus + Notification + Ticker-Zeile.
                    astapOperationState = AstapOperationState.SolvingOnline(status)
                    // Grobes Modell aus bereits gelösten Kacheln — dient ZWEIERLEI:
                    //  (a) De-Warp DIESER Kachel (nur wenn pro Kachel gewünscht, tile.dewarpRequested),
                    //  (b) Positions-Hinweis für den ROHEN Solve (schneller/robuster als blind).
                    // EINMAL pro Kachel; nur bei >=3 Ankern (Seed: die 1. Kachel löst blind/normal).
                    val useDewarp = tile.dewarpRequested
                    // Anker der bereits gelösten Kacheln separat vorhalten (nicht nur für den Fit
                    // selbst, sondern auch für die Extrapolations-Sicherheitsprüfung in predictHint).
                    val priorTiles = solveTiles.filter { it.status == SolveTileStatus.Solved }
                        .map { tileAnchorPairs(it, imgW, imgH) }
                    val priorAnchors = priorTiles.flatten()
                    // Stimmgewicht je Kachel (s. FisheyeRefiner.tileVoteWeights): der Positions-Hinweis
                    // soll nicht von der zufälligen Rasterdichte einzelner bereits gelöster Kacheln
                    // dominiert werden.
                    val priorWeights = FisheyeRefiner.tileVoteWeights(priorTiles.map { it.size })
                    val priorFit = run {
                        if (priorAnchors.size >= 3) {
                            withContext(Dispatchers.Default) {
                                FisheyeRefiner.calibratePanorama(priorAnchors, imgW, imgH, allowedKinds, priorWeights)
                            }
                        } else {
                            // Keine gelösten Kacheln, aber eine Einzellösung? -> daraus ein Modell für den
                            // Positions-Hinweis bauen, damit die Kachel nicht blind am ganzen Himmel sucht.
                            singleSolveWcs?.let { base ->
                                withContext(Dispatchers.Default) {
                                    panoramaCalibrationFromWcs(base, imgW, imgH, allowedKinds)
                                }
                            }
                        }
                    }
                    // Gleiches RMS-Gate wie fitHint unten (0,06 * Bildkante): ohne dieses Gate wurde
                    // JEDES priorFit -- auch ein bei eng/kollinear platzierten Kacheln schlecht
                    // konditioniertes -- fuer den teuren De-Warp-Patch verwendet. Ein instabiles Modell
                    // zeigt den Patch auf die falsche Himmelsregion -> der Solve-Versuch dort schlaegt
                    // fehl -> Fallback auf den Roh-Crop-Pfad -> ZWEI sequenzielle Solve-Versuche statt
                    // einem (Nutzerbefund 2026-07-31: viele eng beieinander platzierte Kacheln loesen
                    // spuerbar langsamer als weit auseinander platzierte). Für Tiny-Sky-Kacheln entfällt
                    // dieses Gate: die Korrekturgrundlage ist dort nicht aus Ankern GEFITTET (die gibt es
                    // bei der allerersten Kachel noch gar nicht -- kein Henne-Ei-Problem wie beim nativen
                    // Fall), sondern von Anfang an EXAKT bekannt (reine Geometrie -- dieselbe stereo-
                    // grafische Formel, mit der die Tiny-Sky-Bitmap selbst gebaut wurde).
                    val dewarpModel: PanoramaWcsSolution? = if (!useDewarp) {
                        null
                    } else if (tileIsTinySky) {
                        tinySkyOverviewModelForSolve
                    } else {
                        priorFit?.takeIf { it.rms < 0.06 * maxOf(imgW, imgH) }?.solution
                    }
                    // Manueller Hinweis (Nutzer hat einen Sternnamen für DIESE Kachel eingetippt) hat
                    // Vorrang vor dem automatisch aus priorFit abgeleiteten Hinweis — gerade bei der
                    // ERSTEN Kachel oder weit entfernten Kacheln ist priorFit oft nicht vertrauenswürdig
                    // (siehe unten), während der manuelle Hinweis exakt und nutzergeprüft ist. Radius aus
                    // der geschätzten Bild-FOV auf die Kachelgröße skaliert (analog predictHint: *1.5,
                    // 12..45°) — die tatsächliche Kachel-FOV ist ohne Modell unbekannt, daher Schätzung.
                    val manualHint: TileDeWarp.Hint? = tile.manualHintStarName?.let { starName ->
                        findStarByName(starName, referenceCatalogStars, skyCatalogStars)?.let { star ->
                            // Bei Tiny-Sky-Kacheln ist "rh/imgH" bedeutungslos (verschiedene Bild-/
                            // Projektionsräume) -- Winkelhöhe stattdessen direkt aus der (konformen)
                            // Tiny-Sky-Projektion messen: Winkel zwischen Kachel-Mittelpunkt- und
                            // Kachel-Oberkanten-Richtung, verdoppelt (Kante-zu-Mitte ist die halbe Höhe).
                            val tileFovDeg = if (tileIsTinySky) {
                                val overviewProj = tinySkyOverviewProjectionForSolve!!
                                val centerDir = overviewProj.pixelToDirection(rx + rw / 2.0, ry + rh / 2.0)
                                val edgeDir = overviewProj.pixelToDirection(rx + rw / 2.0, ry.toDouble())
                                if (centerDir != null && edgeDir != null) {
                                    val cosHalf = centerDir.dot(edgeDir).coerceIn(-1.0, 1.0)
                                    (2.0 * Math.toDegrees(acos(cosHalf))).toFloat().coerceAtLeast(0.1f)
                                } else {
                                    12f
                                }
                            } else {
                                (astapFieldOfView * rh / imgH).coerceAtLeast(0.1f)
                            }
                            val radiusDeg = (tileFovDeg * 1.5).coerceIn(12.0, 45.0)
                            // Katalog liefert RA teils im Bereich [-180,180) statt [0,360) (2026-07-21
                            // an Deneb bewiesen: -49.64° statt 310.36°) -> normieren, sonst verwirft
                            // LocalAstrometrySolvers eigene Wertebereichs-Prüfung den Hinweis lautlos.
                            val raDeg = star.point.raDegrees.toDouble().let { if (it < 0.0) it + 360.0 else it }
                            TileDeWarp.Hint(
                                raDeg,
                                star.point.decDegrees.toDouble(),
                                radiusDeg,
                            )
                        }
                    }
                    // Positions-Hinweis nur, wenn der Fit vertrauenswürdig ist (RMS-Gate) — ein
                    // schlechtes Modell würde Nova sonst in die falsche Himmelsregion schicken.
                    // Projektion bleibt die WAHL des Nutzers (allowedKinds); kein Auto-Zwang.
                    // anchorDirs nur aus dem Kachel-Fit-Zweig (priorAnchors) mitgeben: der Einzellösungs-
                    // Zweig deckt bereits das GANZE Bild ab -> dort ist Extrapolation kein Risiko.
                    // predictHint(model, rx, ry, rw, rh) interpretiert rx/ry/rw/rh als Pixel im selben
                    // Raum wie `model` -- priorFit.solution ist aus NATIVEN Ankern gefittet (s. o.), bei
                    // einer Tiny-Sky-Kachel sind rx/ry/rw/rh aber der Tiny-Sky-Zuschnitt. Ohne Umrechnung
                    // würde predictHint Tiny-Sky-Pixelkoordinaten fälschlich als native interpretieren
                    // und einen auf eine falsche Himmelsregion zeigenden Hinweis liefern -- das war bisher
                    // der einzige automatische Hinweis-Pfad für Tiny-Sky-Kacheln (nearestTileHint ist für
                    // sie oben bewusst deaktiviert), erklärt also direkt schlechte Solve-Erfolgsquoten bei
                    // Tiny-Sky-Kacheln OHNE manuellen Hinweis (Nutzerbefund 2026-08-24). 4-Eck-Umrechnung
                    // + Bounding-Box -- dieselbe Näherung wie andernorts bei convertOverlayGeometry, für
                    // einen ohnehin großzügig radius-tolerant genutzten Hinweis ausreichend genau.
                    val fitHintRect: Rect = if (tileIsTinySky) {
                        val overviewProj = tinySkyOverviewProjectionForSolve!!
                        val nativeCorners = listOf(
                            rx.toDouble() to ry.toDouble(),
                            (rx + rw).toDouble() to ry.toDouble(),
                            (rx + rw).toDouble() to (ry + rh).toDouble(),
                            rx.toDouble() to (ry + rh).toDouble(),
                        ).mapNotNull { (sx, sy) ->
                            overviewProj.pixelToDirection(sx, sy)
                                ?.let { tinySkyNativeProjectionForSolve.directionToPixel(it) }
                        }
                        if (nativeCorners.size >= 3) {
                            Rect(
                                left = nativeCorners.minOf { it.x }, top = nativeCorners.minOf { it.y },
                                right = nativeCorners.maxOf { it.x }, bottom = nativeCorners.maxOf { it.y },
                            )
                        } else {
                            Rect(tileNativeCenter.x, tileNativeCenter.y, tileNativeCenter.x + 1f, tileNativeCenter.y + 1f)
                        }
                    } else {
                        Rect(rx.toFloat(), ry.toFloat(), (rx + rw).toFloat(), (ry + rh).toFloat())
                    }
                    val fitHint: TileDeWarp.Hint? = priorFit
                        ?.takeIf { it.rms < 0.06 * maxOf(imgW, imgH) }
                        ?.let {
                            TileDeWarp.predictHint(
                                it.solution,
                                fitHintRect.left.roundToInt(), fitHintRect.top.roundToInt(),
                                fitHintRect.width.roundToInt().coerceAtLeast(1),
                                fitHintRect.height.roundToInt().coerceAtLeast(1),
                                anchorDirs = if (priorAnchors.size >= 3) priorAnchors.map { a -> a.second } else emptyList(),
                            )
                        }
                    // Fallback, wenn das aus ALLEN bisherigen Kacheln gefittete Gesamtmodell keinen
                    // Hinweis liefert (RMS-Gate durchgefallen oder Extrapolations-Check zu weit weg) --
                    // typischerweise, wenn die bisherigen Kacheln geometrisch zu eng/kollinear liegen,
                    // um Verzeichnungsparameter robust zu schätzen (Nutzerbefund 2026-07-30: Kacheln
                    // 1-3 in einer Reihe, Kachel 4 nahe Kachel 1 bekam keinen Hinweis, obwohl Kachel 1
                    // selbst längst präzise gelöst war). Statt eines neuen, ebenso fit-abhängigen
                    // Modells: die räumlich NÄCHSTGELEGENE bereits gelöste Einzelkachel direkt (ihre
                    // bewiesene WCS, kein Fit-Risiko) für die Extrapolation nutzen.
                    //
                    // isVeryClose zusätzlich: true, wenn sich Nachbarkachel und aktuelle Kachel praktisch
                    // berühren/überlappen (Mittelpunktabstand < Summe der halben Diagonalen, wie zwei sich
                    // berührende Kreise) -- dann bekommt dieser Hinweis unten VORRANG vor fitHint, weil
                    // eine derart nahe, bereits BEWIESENE Kachel präziser ist als ein Fit über ALLE
                    // bisherigen Kacheln, der bei eng/kollinear platzierten Kacheln schlecht konditioniert
                    // sein kann (Nutzerbefund 2026-07-31: viele eng beieinander platzierte Kacheln lösen
                    // spürbar schlechter/langsamer als weit auseinander platzierte).
                    // Hint-Audit 2026-09-01: rein additive Erweiterung des Rückgabetyps (Pair -> kleine
                    // lokale Datenklasse) um Spender-Kachel-ID + die tatsächlich zur Auswahl genutzte
                    // Distanz -- KEINE Änderung an der Auswahl-/Vergleichslogik selbst (dieselben
                    // minByOrNull-Aufrufe, dieselben Distanz-Formeln, dieselbe isVeryClose-Schwelle wie
                    // zuvor), nur zusätzliche Felder für die neue solvetile_hint_detail-Diagnosezeile
                    // unten (s. dort für den Zweck).
                    data class NearestTileHintResult(
                        val hint: TileDeWarp.Hint?,
                        val isVeryClose: Boolean,
                        val donorTileId: Long? = null,
                        val distancePx: Float? = null,
                    )
                    val nearestTileResult: NearestTileHintResult by lazy {
                        if (tileIsTinySky) {
                            // Plan Nachtrag 12 reparierte nur das ZIEL-Rechteck (fitHintRect) für diesen
                            // Rückfall, aber die SPENDER-Suche blieb `it.wcs != null` -- und Tiny-Sky-
                            // Kacheln haben `wcs` PER DESIGN IMMER null (s. SolveTile.wcs-Kommentar,
                            // Plan Nachtrag 1). Selbst mit korrekt umgerechnetem Zielrechteck fand die
                            // Suche für eine Tiny-Sky-Kachel dadurch strukturell NIE einen Spender, egal
                            // wie viele Tiny-Sky-Nachbarn schon gelöst waren -- erklärt Nutzerbefund
                            // 2026-08-25 ("bekommen falsche Hinweise, suchen viel zu lange") trotz
                            // Nachtrag 12. Fix: eigene Spender-Suche NUR unter bereits gelösten TINY-
                            // SKY-Kacheln (`tinySkyLocalWcs`), komplett im TINY-SKY-PIXEL-Raum -- Spender
                            // UND Ziel sind Ausschnitte DESSELBEN Tiny-Sky-Bitmaps, daher KEINE Cross-
                            // Projektions-Umrechnung nötig (einfacher als der native Zweig unten).
                            val targetCx = rx + rw / 2f
                            val targetCy = ry + rh / 2f
                            val nearest = solveTiles.filter {
                                it.sourceSpace == TileSourceSpace.TinySky &&
                                    it.status == SolveTileStatus.Solved && it.tinySkyLocalWcs != null
                            }.minByOrNull { t ->
                                val dx = t.center.x - targetCx
                                val dy = t.center.y - targetCy
                                dx * dx + dy * dy
                            } ?: return@lazy NearestTileHintResult(null, false)
                            val nb = solveTileBoundingBox(nearest)
                            val hint = TileDeWarp.predictHintFromTile(
                                nearest.tinySkyLocalWcs!!,
                                nb.left.roundToInt(), nb.top.roundToInt(), nb.height.roundToInt(),
                                rx, ry, rw, rh,
                            )
                            val centerDistance = hypot(nearest.center.x - targetCx, nearest.center.y - targetCy)
                            val isVeryClose = centerDistance < hypot(rw / 2f, rh / 2f) + hypot(nb.width / 2f, nb.height / 2f)
                            NearestTileHintResult(hint, isVeryClose, nearest.id, centerDistance)
                        } else {
                            // Spender-Kacheln sind hier IMMER nativ (wcs != null -> per Design nie eine
                            // Tiny-Sky-Kachel, s. Plan Nachtrag 1). Dieser Zweig laeuft ausschliesslich
                            // fuer NATIVE Ziel-Kacheln (der Tiny-Sky-Fall wird oben separat behandelt),
                            // rx/ry/rw/rh sind hier also bereits native Bildkoordinaten.
                            // GOLDEN-RUECKBAU 2026-09-03: der NATIVE Zweig nutzt wieder buchstaeblich
                            // Golden's Zuschnitt-Mittelpunkt (rx+rw/2, ry+rh/2) statt tile.center.
                            // Beide fallen nur zusammen, solange die Kachel nirgends am Bildrand
                            // geklemmt wurde (rx/ry/rw/rh laufen durch coerceIn) -- bei einer
                            // randnahen Kachel waehlte die neuere Fassung dadurch potenziell einen
                            // anderen Spender bzw. bewertete isVeryClose anders als am 12.08.
                            // Der TinySky-Zweig oben bleibt unveraendert (dort ist tileNativeCenter
                            // die einzige sinnvolle Groesse, kein Golden-Aequivalent vorhanden).
                            val targetCx = rx + rw / 2f
                            val targetCy = ry + rh / 2f
                            val nearest = solveTiles.filter { it.status == SolveTileStatus.Solved && it.wcs != null }
                                .minByOrNull { t ->
                                    val dx = t.center.x - targetCx
                                    val dy = t.center.y - targetCy
                                    dx * dx + dy * dy
                                } ?: return@lazy NearestTileHintResult(null, false)
                            val targetRx = fitHintRect.left.roundToInt()
                            val targetRy = fitHintRect.top.roundToInt()
                            val targetRw = fitHintRect.width.roundToInt().coerceAtLeast(1)
                            val targetRh = fitHintRect.height.roundToInt().coerceAtLeast(1)
                            val nb = solveTileBoundingBox(nearest)
                            val hint = TileDeWarp.predictHintFromTile(
                                nearest.wcs!!,
                                nb.left.roundToInt(), nb.top.roundToInt(), nb.height.roundToInt(),
                                targetRx, targetRy, targetRw, targetRh,
                            )
                            val centerDistance = hypot(nearest.center.x - targetCx, nearest.center.y - targetCy)
                            // targetRw/targetRh (nicht das rohe rw/rh) -- für eine Tiny-Sky-Kachel sind
                            // rw/rh Tiny-Sky-Pixel, centerDistance ist aber bereits nativ (s.o.); ein
                            // Vergleich gegen die rohen Tiny-Sky-Maße wäre bedeutungslos vermischt.
                            val isVeryClose = centerDistance < hypot(targetRw / 2f, targetRh / 2f) + hypot(nb.width / 2f, nb.height / 2f)
                            NearestTileHintResult(hint, isVeryClose, nearest.id, centerDistance)
                        }
                    }
                    val nearestTileHint: TileDeWarp.Hint? by lazy { nearestTileResult.hint }
                    val nearestTileIsVeryClose: Boolean by lazy { nearestTileResult.isVeryClose }
                    val rawHint: TileDeWarp.Hint? = manualHint
                        ?: nearestTileHint.takeIf { nearestTileIsVeryClose }
                        ?: fitHint
                        ?: nearestTileHint
                    // Rein diagnostisch: Nutzer-Beobachtung (2026-07-20) "ohne De-Warp blind, mit
                    // De-Warp plötzlich Hinweis bekannt" bei benachbarten Kacheln — Code zeigt aber
                    // dass rawHint UNABHÄNGIG von useDewarp berechnet wird (nur vom RMS-Gate auf
                    // priorFit). Zahlen mitschreiben, um zu klären, ob es an genau diesem Gate lag
                    // (das zufällig zeitlich mit dem De-Warp-Umschalten zusammenfiel) oder an etwas,
                    // das dieser Code hier noch nicht abbildet.
                    AppDiagnostics.record(
                        "solvetile_hint_gate tile=$tileNo tinySky=$tileIsTinySky dewarpRequested=$useDewarp " +
                            "priorAnchors=${priorAnchors.size} priorFitRms=${priorFit?.rms?.let { "%.1f".format(it) } ?: "null"} " +
                            "gateThreshold=${"%.1f".format(0.06 * maxOf(imgW, imgH))} " +
                            "manualHint=${manualHint != null} fitHint=${fitHint != null} " +
                            "nearestTileVeryClose=$nearestTileIsVeryClose " +
                            "nearestTilePreferredOverFit=${manualHint == null && nearestTileIsVeryClose && nearestTileHint != null} " +
                            "nearestTileFallbackUsed=${manualHint == null && !nearestTileIsVeryClose && fitHint == null && nearestTileHint != null} " +
                            "rawHint=${rawHint != null}",
                    )
                    // Hint-Audit 2026-09-01 (Nutzerauftrag: geometrische Herkunft jedes Hints prüfbar
                    // machen): EINE kompakte Zeile, die exakt zeigt, WELCHE Quelle rawHint tatsächlich
                    // lieferte (dieselbe Fallkette wie oben, nur als String statt einzelner Booleans) und
                    // -- für TinySky-Kacheln besonders wichtig -- sowohl den crop-lokalen als auch den
                    // daraus tatsächlich berechneten NATIVEN Kachel-Mittelpunkt nebeneinander. Rein
                    // additiv, liest nur bereits vorhandene Werte, verändert nichts an rawHint selbst.
                    val hintSource = when {
                        manualHint != null -> "manual"
                        nearestTileIsVeryClose && nearestTileHint != null -> "nearest_very_close"
                        fitHint != null -> "fit"
                        nearestTileHint != null -> "nearest_fallback"
                        else -> "blind"
                    }
                    val localCenterPxText = "(${"%.1f".format(rx + rw / 2f)},${"%.1f".format(ry + rh / 2f)})"
                    val nativeCenterPxText = "(${"%.1f".format(tileNativeCenter.x)},${"%.1f".format(tileNativeCenter.y)})"
                    val sourceTileText = nearestTileResult.donorTileId?.toString() ?: "n/a"
                    val sourceTileDistanceText = nearestTileResult.distancePx?.let { "%.1f".format(it) } ?: "n/a"
                    val priorFitKindText = priorFit?.kind?.toString() ?: "n/a"
                    val predictedRaDecText = if (rawHint != null) {
                        "%.4f".format(rawHint.centerRaDegrees) + "/" + "%.4f".format(rawHint.centerDecDegrees)
                    } else {
                        "n/a"
                    }
                    val radiusDegText = rawHint?.radiusDegrees?.let { "%.4f".format(it) } ?: "n/a"
                    AppDiagnostics.record(
                        "solvetile_hint_detail tile=$tileNo tinySky=$tileIsTinySky source=$hintSource " +
                            "localCenterPx=$localCenterPxText nativeCenterPx=$nativeCenterPxText " +
                            "sourceTile=$sourceTileText sourceTileDistancePx=$sourceTileDistanceText " +
                            "priorFitKind=$priorFitKindText predictedRaDec=$predictedRaDecText radiusDeg=$radiusDegText",
                    )

                    var solvedWcs: WcsSolution? = null
                    var dewarpAnchors: List<Pair<Offset, Vec3>>? = null
                    // Separates Feld für Tiny-Sky-Punktumrechnung (NICHT dewarpAnchors wiederverwenden --
                    // sonst zeigt die Kachel-Info fälschlich "De-Warp verwendet" für eine ganz normale
                    // Tiny-Sky-Kachel, s. Plan Nachtrag 1). Wird nach einem erfolgreichen Tiny-Sky-Roh-
                    // Crop-Solve unten befüllt (Kachel-eigene Sternpositionen exakt nach nativen
                    // Koordinaten umgerechnet).
                    var tinySkyPointAnchors: List<Pair<Offset, Vec3>>? = null
                    // Echte, von astrometry.net selbst verifizierte Solve-Treffer dieser Kachel aus
                    // .corr (nur im Roh-Crop-Zweig via LocalAstrometry befüllt, sonst leer).
                    var tileCorrRefs: List<Pair<Offset, Vec3>> = emptyList()
                    // Nur für die corr_pipeline-Diagnose (C4, Nutzer-Vorgabe 2026-08-30) -- bleiben 0 für
                    // De-Warp/Nova-Kacheln (dort wird .corr gar nicht erst geparst), s. corr_pipeline-Log
                    // unten. NICHT identisch zu tileCorrRefs.size: parsedCorr/transformedToNative zeigen
                    // Zwischenschritte, bevor die Ausreißer-Validierung greift.
                    var corrParsedCount = 0
                    var corrTransformedCount = 0
                    var corrRejectedTransformCount = 0
                    var corrRejectedOutlierCount = 0
                    // Ist der De-Warp-Patch in einen Server-TIMEOUT gelaufen? Dann den teuren Roh-Crop-
                    // Fallback überspringen (derselbe langsame Server, schwierigeres Bild -> erneuter Timeout).
                    var dewarpPatchTimedOut = false
                    // Für das Kachel-Info-Popup: Gesamtdauer dieses Solve-Versuchs (De-Warp-Patch +
                    // ggf. Roh-Crop-Fallback zusammen — das erlebt der Nutzer als EIN Lösungsvorgang).
                    val tileSolveStartMs = System.currentTimeMillis()
                    if (dewarpModel != null) {
                        val patch = withContext(Dispatchers.Default) {
                            TileDeWarp.buildPatch(solveBitmap, rx, ry, rw, rh, dewarpModel)
                        }
                        if (patch != null) {
                            val patchWcs: WcsSolution? = try {
                                val w: WcsSolutionLike = when (effectiveSolverChoice) {
                                    AstapSolverChoice.LocalAstrometry -> localSolver.solve(
                                        bitmap = patch.bitmap,
                                        // Positions-Hinweis: Patch-Mittelpunkt aus dem Modell.
                                        centerRaDeg = patch.centerRaDegrees,
                                        centerDecDeg = patch.centerDecDegrees,
                                        radiusDeg = patch.fovDegrees.toDouble().coerceIn(5.0, 60.0),
                                    ) { s ->
                                        withContext(Dispatchers.Main.immediate) {
                                            astapOperationState = AstapOperationState.SolvingOnline("$status ≈ $s")
                                            tick("${context.getString(R.string.tile_label)} $tileNo ≈ $s")
                                        }
                                    }.wcs
                                    AstapSolverChoice.NovaOnline -> novaSolver.solve(
                                        bitmap = patch.bitmap,
                                        apiKey = novaApiKey,
                                        fovWidthLowerDeg = null,
                                        fovWidthUpperDeg = null,
                                        // Automatischer Positions-Hinweis: Patch-Mittelpunkt aus dem Modell.
                                        centerRaDeg = patch.centerRaDegrees,
                                        centerDecDeg = patch.centerDecDegrees,
                                        radiusDeg = patch.fovDegrees.toDouble().coerceIn(5.0, 60.0),
                                    ) { s ->
                                        withContext(Dispatchers.Main.immediate) {
                                            astapOperationState = AstapOperationState.SolvingOnline("$status ≈ $s")
                                            tick("${context.getString(R.string.tile_label)} $tileNo ≈ $s")
                                        }
                                    }.wcs
                                }
                                w as? WcsSolution
                            } catch (cancelled: CancellationException) {
                                patch.bitmap.recycle()
                                throw cancelled
                            } catch (error: Throwable) {
                                AppDiagnostics.record("dewarp_tile_failed off=$rx,$ry msg=${error.message}")
                                tick("${context.getString(R.string.tile_label)} $tileNo ✗ ${error.message ?: context.getString(R.string.status_failed_lower)}")
                                dewarpPatchTimedOut = (error as? NovaSolveException)?.timeout == true
                                null
                            }
                            if (patchWcs != null) {
                                // Kontrolle: hat der Solver Vorwärts-SIP geliefert? (fwd=0 -> imageToSky
                                // fiel auf reine CD+Gnomonik zurück -> Anker am Rand ungenau.)
                                AppDiagnostics.record(
                                    "dewarp_patch_sip fwd=${patchWcs.forwardSipX.size} inv=${patchWcs.inverseSipX.size}",
                                )
                                dewarpAnchors = TileDeWarp.anchorsFor(patch, patchWcs, imgW, imgH)
                                    .takeIf { it.size >= 3 }
                            }
                            patch.bitmap.recycle()
                        }
                    }

                    // Kein De-Warp-Ergebnis -> normaler Crop-Solve (auch der Seed-Fall der 1. Kachel).
                    // ABER nicht nach einem Patch-Timeout: der Roh-Crop würde am selben überlasteten
                    // Server erneut ~6-14 min bis zum Timeout hängen (verzerrter -> noch unwahrscheinlicher).
                    if (dewarpPatchTimedOut) {
                        AppDiagnostics.record("solvetile_skip_rawfallback off=$rx,$ry reason=dewarp_timeout")
                        tick(
                            "${context.getString(R.string.tile_label)} $tileNo ✗ " +
                                context.getString(R.string.tick_patch_timeout_skip),
                        )
                    }
                    if (dewarpAnchors == null && !dewarpPatchTimedOut) {
                        if (rawHint != null) {
                            tick("${context.getString(R.string.tile_label)} $tileNo: ${context.getString(R.string.label_position_known)}")
                        }
                        val crop = withContext(Dispatchers.Default) {
                            Bitmap.createBitmap(cropSource, rx, ry, rw, rh)
                        }
                        solvedWcs = try {
                            val resultWcs: WcsSolutionLike = when (effectiveSolverChoice) {
                                AstapSolverChoice.LocalAstrometry -> {
                                    val localResult = localSolver.solve(
                                        bitmap = crop,
                                        // Positions-Hinweis (nur bei vertrauenswürdigem Modell, sonst null = blind).
                                        centerRaDeg = rawHint?.centerRaDegrees,
                                        centerDecDeg = rawHint?.centerDecDegrees,
                                        radiusDeg = rawHint?.radiusDegrees,
                                    ) { s ->
                                        withContext(Dispatchers.Main.immediate) {
                                            astapOperationState = AstapOperationState.SolvingOnline("$status $s")
                                            tick("${context.getString(R.string.tile_label)} $tileNo: $s")
                                        }
                                    }
                                    // A2 (Nutzer-Vorgabe 2026-08-30, löst den Verwurf aus Plan Nachtrag 1
                                    // AUF statt ihn zu entfernen): .corr-Rohtreffer sind CROP-LOKALE
                                    // Pixel. Für native Kacheln macht das die spätere naive "lokal +
                                    // tileOffsetX/Y"-Addition (globalizeTileCorrRefs/overlapDisagreement)
                                    // korrekt. Für eine Tiny-Sky-Kachel wäre dieselbe Addition weiterhin
                                    // falsch (mischt Tiny-Sky- und native Pixel) -- deshalb jetzt exakt
                                    // dieselbe geometrische Kette wie TileDeWarp.tinySkyAnchorsFor, nur
                                    // pro echtem .corr-Treffer statt pro künstlichem 3x3-Raster. Ein
                                    // einzelner nicht transformierbarer Punkt wird EINZELN verworfen
                                    // (mapNotNull in transformTinySkyRefsToNative), nie die ganze Kachel.
                                    corrParsedCount = localResult.corrRefs.size
                                    // Untersuchungsauftrag 2026-08-31 (dritte Runde): rein additive Detail-
                                    // Diagnose für die ersten paar .corr-Referenzen DIESER Kachel, unabhängig
                                    // vom Fix unten -- reicht aus, um im nächsten Solve empirisch zu belegen,
                                    // in welchem Koordinatenraum localResult.wcs tatsächlich vorhersagt.
                                    if (tileIsTinySky) {
                                        logTinySkyCorrValidationDetail(
                                            label = "tile$tileNo", rawRefs = localResult.corrRefs,
                                            rx = rx, ry = ry, rh = rh,
                                            overviewProjection = tinySkyOverviewProjectionForSolve!!,
                                            nativeProjection = tinySkyNativeProjectionForSolve,
                                            wcs = localResult.wcs, budget = 4,
                                        )
                                    }
                                    // FIX (Untersuchungsauftrag 2026-08-31, dritte Runde): A4 MUSS im
                                    // selben Koordinatenraum laufen, in dem localResult.wcs tatsächlich
                                    // gültig ist -- das ist IMMER der Raum des gelösten Bitmaps selbst
                                    // (crop-lokal, s. LocalAstrometrySolver-KDoc: "@return die WCS-Lösung
                                    // ... auf den Pixelkoordinaten von [bitmap]"; ebenso liefert die .corr-
                                    // Datei crop-lokale field_x/field_y, s. LocalAstrometrySolver.corrRefs).
                                    // Für native Kacheln war das schon immer der Fall (transformed==corrRefs
                                    // dort, s.u. -- keine Verhaltensänderung). Für Tiny-Sky-Kacheln verglich
                                    // der bisherige Code fälschlich bereits NACH-nativ-transformierte
                                    // Positionen gegen die weiterhin crop-lokale wcs -- ein Koordinatenraum-
                                    // Mismatch in der Größenordnung des vollen Crop-Offsets (rx,ry), der
                                    // praktisch JEDEN Punkt als "Ausreißer" erscheinen ließ (s. neue
                                    // corr_pipeline_detail-Diagnose oben für den empirischen Beleg). Reihen-
                                    // folge jetzt: erst validieren (crop-lokal gegen crop-lokal, korrekt),
                                    // DANACH nur die überlebenden Punkte nach nativ transformieren (spart
                                    // zusätzlich unnötige Transform-Arbeit für ohnehin verworfene Punkte).
                                    // GOLDEN-RÜCKBAU (Nutzer-Vorgabe 2026-09-03): der AUTOMATISCHE native
                                    // Kachel-Solve übernimmt die .corr-Treffer wieder UNGEFILTERT --
                                    // buchstäblich `tileCorrRefs = localResult.corrRefs`, exakt wie im
                                    // bestätigten Golden-Build vom 12.08. RichCorrMesh.rejectResidualOutliers
                                    // war dort NICHT Teil dieses Datenpfads; da diese .corr-Liste ohnehin von
                                    // astrometry.net selbst verifiziert ist, gilt für den Golden-Pfad wieder
                                    // "keine zusätzliche Vorfilterung". Der Filter bleibt AUSSCHLIESSLICH für
                                    // Tiny-Sky-Kacheln aktiv: dort wurde er zusammen mit der nativen
                                    // Transformation als eigener, klar getrennter Pfad eingeführt (s. Kommentar
                                    // oben zum Koordinatenraum-Mismatch), für den es kein Golden-Verhalten gibt.
                                    val transformed = if (tileIsTinySky) {
                                        val validatedLocal = RichCorrMesh.rejectResidualOutliers(
                                            localResult.corrRefs, localResult.wcs, rh,
                                            diagLabel = "tile$tileNo",
                                            diagBudget = intArrayOf(4),
                                        )
                                        corrRejectedOutlierCount = validatedLocal.rejectedOutliers
                                        val nativeRefs = TileDeWarp.transformTinySkyRefsToNative(
                                            validatedLocal.accepted, rx, ry,
                                            // Non-null bewiesen: tileIsTinySky setzt cropSource != null
                                            // voraus (s. oben), also war tinySkyBitmapForSolve nie null --
                                            // identische Begründung wie beim tinySkyAnchorsFor-Aufruf unten.
                                            tinySkyOverviewProjectionForSolve!!, tinySkyNativeProjectionForSolve,
                                        )
                                        corrRejectedTransformCount = validatedLocal.accepted.size - nativeRefs.size
                                        nativeRefs
                                    } else {
                                        // Golden 12.08.: weder Ausreißer-Vorfilter noch Transformation.
                                        corrRejectedOutlierCount = 0
                                        corrRejectedTransformCount = 0
                                        localResult.corrRefs
                                    }
                                    corrTransformedCount = transformed.size
                                    tileCorrRefs = transformed
                                    localResult.wcs
                                }
                                AstapSolverChoice.NovaOnline -> novaSolver.solve(
                                    bitmap = crop,
                                    apiKey = novaApiKey,
                                    fovWidthLowerDeg = null,
                                    fovWidthUpperDeg = null,
                                    // Positions-Hinweis (nur bei vertrauenswürdigem Modell, sonst null =
                                    // blind): Nova sucht nahe der aus gelösten Kacheln geschätzten Stelle.
                                    centerRaDeg = rawHint?.centerRaDegrees,
                                    centerDecDeg = rawHint?.centerDecDegrees,
                                    radiusDeg = rawHint?.radiusDegrees,
                                ) { s ->
                                    withContext(Dispatchers.Main.immediate) {
                                        astapOperationState = AstapOperationState.SolvingOnline("$status $s")
                                        tick("${context.getString(R.string.tile_label)} $tileNo: $s")
                                    }
                                }.wcs
                            }
                            resultWcs as? WcsSolution
                        } catch (cancelled: CancellationException) {
                            crop.recycle()
                            throw cancelled
                        } catch (error: Throwable) {
                            AppDiagnostics.record(
                                "solvetile_failed off=$rx,$ry size=${rw}x$rh solver=${astapSolverChoice.name} msg=${error.message}",
                            )
                            tick("${context.getString(R.string.tile_label)} $tileNo ✗ ${error.message ?: context.getString(R.string.status_failed_lower)}")
                            null
                        }
                        crop.recycle()
                        // Tiny-Sky-Kachel erfolgreich roh gelöst -> Kachel-eigene Sternpositionen exakt
                        // (Einzelpunkt-Umrechnung, s. TileDeWarp.tinySkyAnchorsFor) nach nativen
                        // Koordinaten umrechnen, damit sie wie jede andere Kachel am globalen Fit
                        // teilnehmen können. wcs bleibt bewusst null (s. setTile unten) -- sie ist nur
                        // in Tiny-Sky-Pixeln gültig, nicht in nativen.
                        if (tileIsTinySky && solvedWcs != null) {
                            tinySkyPointAnchors = TileDeWarp.tinySkyAnchorsFor(
                                solvedWcs!!, rx, ry, rw, rh,
                                // Non-null bewiesen: tileIsTinySky setzt cropSource != null voraus
                                // (s. oben), also war tinySkyBitmapForSolve nie null.
                                tinySkyOverviewProjectionForSolve!!, tinySkyNativeProjectionForSolve,
                            ).takeIf { it.size >= 3 }
                        }
                    }

                    // Unit 6: diese Kachel wurde TROTZ eines inzwischen erfolgten Abbruchs fertig gelöst
                    // (Race, z. B. Abbruch genau beim letzten ensureActive()-Fenster) -> Ergebnis NICHT
                    // mehr übernehmen (kein Status-/WCS-/Anker-Schreiben, kein Auto-Start der nächsten
                    // Kachel darunter). Der Solve-Bitmap-Zuschnitt (crop) ist an dieser Stelle bereits
                    // rekonstruiert (s. oben) -> nichts mehr freizugeben.
                    if (isStaleSolve()) {
                        AppDiagnostics.record("solvetile_discarded_stale_generation tileId=$id")
                        return@forEachIndexed
                    }
                    val ok = dewarpAnchors != null || tinySkyPointAnchors != null || solvedWcs != null
                    // De-Warp-Patch-Solve bekommt IMMER einen Hinweis (Patch-Mittelpunkt aus dem
                    // Modell, s. oben); im Roh-Crop-Zweig entscheidet rawHint (manuell oder priorFit).
                    val usedHint = if (dewarpAnchors != null) true else rawHint != null
                    setTile(id) {
                        it.copy(
                            status = if (ok) SolveTileStatus.Solved else SolveTileStatus.Failed,
                            // Tiny-Sky-Kacheln: wcs bleibt null (nur in Tiny-Sky-Pixeln gültig, s. o.) --
                            // exakt wie De-Warp-Kacheln heute schon (anchors statt wcs). Native Kacheln
                            // unverändert.
                            wcs = if (tileIsTinySky) null else solvedWcs,
                            dewarp = dewarpAnchors != null,
                            anchors = dewarpAnchors ?: tinySkyPointAnchors,
                            // Nur für Tiny-Sky-Kacheln befüllt (s. Feld-KDoc) -- Nachbar-Hinweis-Quelle
                            // für andere, noch ungelöste Tiny-Sky-Kacheln, s. nearestTileResult unten.
                            tinySkyLocalWcs = if (tileIsTinySky) solvedWcs else null,
                            solvedWithHint = usedHint,
                            solveDurationMs = System.currentTimeMillis() - tileSolveStartMs,
                        )
                    }
                    // Echte .corr-Solve-Treffer dieser Kachel (nur Roh-Crop-Zweig via LocalAstrometry,
                    // s. oben) -- leer bei De-Warp/ASTAP/Nova oder fehlgeschlagenem Solve -> alten Eintrag
                    // dieser ID entfernen statt eine zu einer alten WCS-Version gehörende Liste stehen
                    // zu lassen.
                    tileCorrRefsById = if (tileCorrRefs.isNotEmpty()) {
                        tileCorrRefsById + (id to tileCorrRefs)
                    } else {
                        tileCorrRefsById - id
                    }
                    // C4 (Nutzer-Vorgabe 2026-08-30): pro Kachel sichtbar machen, an welcher Stufe die
                    // .corr-Zahlen sinken -- getrennt nach Native/TinySky/DeWarp/Nova (die letzten beiden
                    // über eigene Tags statt eines einzelnen "source"-Werts, da DeWarp inzwischen auch
                    // für TinySky-Kacheln möglich ist, s. [[project_tiny_sky_viewer]] Nachtrag 4).
                    AppDiagnostics.record(
                        "corr_pipeline tile=$id tinySky=$tileIsTinySky dewarp=${dewarpAnchors != null} " +
                            "solver=${effectiveSolverChoice.name} parsedCorr=$corrParsedCount " +
                            "transformedToNative=$corrTransformedCount rejectedTransform=$corrRejectedTransformCount " +
                            "rejectedOutlier=$corrRejectedOutlierCount acceptedControlPoints=${tileCorrRefs.size}",
                    )
                    if (ok) {
                        AppDiagnostics.record(
                            "solvetile_solved off=$rx,$ry size=${rw}x$rh dewarp=${dewarpAnchors != null}",
                        )
                        tick(
                            "${context.getString(R.string.tile_label)} $tileNo ✓ ${context.getString(R.string.status_solved_lower)}" +
                                if (dewarpAnchors != null) context.getString(R.string.label_dewarped_suffix) else "",
                        )
                        // Nach JEDER gelösten Kachel den Feinjustier-Seed nachziehen (Nutzerwunsch):
                        // Referenzsterne sitzen dann schon während des Batches ~richtig.
                        refreshPanoramaSeed()
                    }
                }

                // Unit 6 (2026-09-02): Abbruch während der Kachel-Schleife erkannt -> globalen Fit/
                // Mosaik NICHT mehr aufbauen (kein WCS-Übernehmen, keine Overlay-Synchronisierung, kein
                // Solved-Endzustand). Der (ebenfalls generation-geschützte) finally-Block unten räumt
                // Kachel-/Job-/Foreground-Service-Status trotzdem auf.
                if (isStaleSolve()) return@launch

                // Anker + Mosaik-WCS. WICHTIG: das Mosaik zeichnet JEDE Region aus der EIGENEN, exakt
                // gelösten Kachel-WCS (per-Kachel-genau) — lokal genauer als ein einzelnes globales Modell.
                // De-Warp-Kacheln haben keine Crop-WCS, aber EXAKTE Anker -> daraus eine lokale WCS fitten.
                // VORAB: Konsistenz-Prüfung — falsch gelöste Kacheln (stiller Nova-Falschtreffer, Anker
                // widersprechen allen anderen) werden geflaggt und von Fit + Mosaik ausgeschlossen.
                val tilesSnapshot = solveTiles.toList()
                // Wie tilesSnapshot oben: VOR dem withContext-Sprung auf einen Hintergrund-Dispatcher
                // einfrieren, nicht den Compose-State selbst von dort lesen -- sonst sieht die
                // Zuverlässigkeits-Berechnung unten (die DIREKT nach der Kachel-Löse-Schleife läuft,
                // ohne die längere Rechenzeit des Modell-Fits weiter unten dazwischen) einen noch
                // nicht synchronisierten, veralteten Stand (Gerätebeleg 2026-08-17: tile_reliability
                // zeigte durchweg 1,00 und tile_overlap_rms computed=0, obwohl dieselben Kacheln in der
                // SPÄTEREN tile_own_rms-Zeile -- über denselben tileCorrRefsById-Zustand -- echte,
                // unterschiedliche Trefferzahlen hatten).
                val corrRefsSnapshot = tileCorrRefsById
                val (
                    anchors, anchorWeights, solvedTileWcs, idToTileWcs, suspects, tileQuality, meshGroupSizes,
                    reliabilityTileIds, meshGroupReliability, tileOverlapAccuracy,
                ) = withContext(Dispatchers.Default) {
                    val perTile = tilesSnapshot
                        .filter { it.status == SolveTileStatus.Solved }
                        .map { it to tileAnchorPairs(it, imgW, imgH) }
                        .filter { it.second.isNotEmpty() }
                    val outliers = TileConsistency.flagOutliers(
                        perTile.map { (tile, list) -> tile.id to list }, imgW, imgH, allowedKinds,
                    )
                    val outlierIds = outliers.map { it.tileId }.toSet()
                    val good = perTile.filterNot { it.first.id in outlierIds }
                    val a = ArrayList<Pair<Offset, Vec3>>()
                    val t = ArrayList<TileWcs>()
                    val idw = ArrayList<Pair<Long, TileWcs>>()
                    for ((tile, tileAnchors) in good) {
                        a += tileAnchors
                        // Für diese Buchhaltung (Mosaik-Rückfall + Überlappungs-/Zuverlässigkeits-
                        // Diagnostik, s. Plan Nachtrag 1) wird eine NÄHERUNGSWEISE native Fläche
                        // gebraucht -- bei einer Tiny-Sky-Kachel sind tile.center/size selbst Tiny-
                        // Sky-Pixel. Anders als die kanonische Kachel-Geometrie (bewusst NICHT mehr
                        // umgerechnet) ist das hier unkritisch: reine interne Buchhaltung für einen
                        // seit "Kachelfrei" ohnehin seltenen Rückfallpfad, keine sichtbare/bearbeitbare
                        // Kachel-Darstellung. Schlägt die Umrechnung fehl (z.B. Kachel ganz am
                        // Scheibenrand), wird NUR diese eine Kachel hier übersprungen -- ihre anchors
                        // (oben, `a += tileAnchors`) sind bereits eingetragen und fließen UNABHÄNGIG
                        // davon vollständig in den eigentlichen globalen Fit ein.
                        val bb: ImageBounds = if (tile.sourceSpace == TileSourceSpace.TinySky) {
                            val overviewProj = tinySkyOverviewProjectionForSolve ?: continue
                            val converted = convertOverlayGeometry(
                                tile.center, tile.size, tile.rotationDegrees,
                                overviewProj, tinySkyNativeProjectionForSolve,
                            ) ?: continue
                            val (c, s, _) = converted
                            ImageBounds(c.x - s.width / 2f, c.y - s.height / 2f, c.x + s.width / 2f, c.y + s.height / 2f)
                        } else {
                            solveTileBoundingBox(tile)
                        }
                        val rx = bb.left.roundToInt().coerceIn(0, imgW - 1)
                        val ry = bb.top.roundToInt().coerceIn(0, imgH - 1)
                        val rw = bb.width.roundToInt().coerceIn(64, imgW - rx)
                        val rh = bb.height.roundToInt().coerceIn(64, imgH - ry)
                        val tileW: WcsSolutionLike? = tile.wcs ?: run {
                            val local = tileAnchors.map { (g, dir) -> Offset(g.x - rx, g.y - ry) to dir }
                            if (local.size >= 3) {
                                FisheyeRefiner.calibratePanorama(local, rw, rh, allowedKinds)?.solution
                            } else {
                                null
                            }
                        }
                        if (tileW != null) {
                            // FIX (Untersuchungsauftrag 2026-08-31, Stage-2-Koordinatenfehler): markiert
                            // fürs nachgelagerte FisheyeRefiner.globalizeTileCorrRefs, dass diese Kachels
                            // .corr-Positionen (tileCorrRefsById) bereits nativ sind, s. TileWcs-KDoc.
                            val tw = TileWcs(
                                tileW, rx, ry, rw, rh,
                                corrRefsAlreadyNative = tile.sourceSpace == TileSourceSpace.TinySky,
                            )
                            t += tw
                            idw += tile.id to tw
                        }
                    }
                    // Kachel-Zuverlässigkeit VOR dem eigentlichen Modell-Fit: eigene Solve-Genauigkeit
                    // (nur der .corr-basierte Teilausschnitt -- die Ganzbild-Blobs fürs Nachtragen der
                    // restlichen Kacheln gibt es an dieser Stelle noch nicht, s. tileOwnRmsById weiter
                    // unten) + Überlappungs-Widerspruch zu Nachbarkacheln, kombiniert zu EINEM
                    // Vertrauens-Gewicht je Kachel-Gruppe.
                    val tileIds = good.map { it.first.id }
                    val earlyOwnAccuracy = TileConsistency.corrBasedOwnAccuracy(idw, corrRefsSnapshot)
                    val overlapAccuracy = TileConsistency.overlapDisagreement(idw, corrRefsSnapshot)
                    val groupReliability = TileConsistency.tileReliabilityWeights(tileIds, earlyOwnAccuracy, overlapAccuracy)
                    // Stimmgewicht je Anker (s. FisheyeRefiner.tileVoteWeights): normiert den Einfluss
                    // jeder Kachel im globalen Fit auf ihre Rasterdichte UND jetzt zusätzlich auf ihre
                    // Zuverlässigkeit statt nur auf ihre Punktzahl. groupSizes: dieselbe Grundlage, für
                    // FisheyeRefiner.fitMesh()s Kreuzvalidierung.
                    val groupSizes = good.map { it.second.size }
                    val weights = FisheyeRefiner.tileVoteWeights(groupSizes, reliability = groupReliability)
                    // Qualitäts-Grundlage fürs Info-Popup: ALLE Kacheln (auch Ausreißer), damit eine
                    // als "Suspect" markierte Kachel im Popup nachvollziehbar schlecht abschneidet.
                    val quality = TileConsistency.perTileError(
                        perTile.map { (tile, list) -> tile.id to list }, imgW, imgH, allowedKinds,
                    )
                    TileFitAnchors(a, weights, t, idw, outliers, quality, groupSizes, tileIds, groupReliability, overlapAccuracy)
                }
                // Verdächtige Kacheln orange markieren + loggen (beim nächsten Lösen automatisch neu versucht).
                for (outlier in suspects) {
                    setTile(outlier.tileId) { it.copy(status = SolveTileStatus.Suspect) }
                    AppDiagnostics.record(
                        "solvetile_suspect id=${outlier.tileId} medianErr=${"%.0f".format(outlier.medianErrorPx)}",
                    )
                    tick(context.getString(R.string.tick_tile_suspect_excluded))
                }
                panoSolveAnchors = anchors // Anker behalten -> Projektion später ohne Neu-Lösen umrechenbar
                panoSolveWeights = anchorWeights
                lastSolvedTileWcs = solvedTileWcs // s. reprojectPanorama(): erhält Kachel-Präzision beim Modellwechsel
                tileQualityById = tileQuality
                tileOverlapRmsById = tileOverlapAccuracy
                AppDiagnostics.record(
                    "tile_reliability " + reliabilityTileIds.zip(meshGroupReliability).joinToString(" ") { (id, w) -> "$id=%.2f".format(w) },
                )
                AppDiagnostics.record(
                    "tile_overlap_rms computed=${tileOverlapAccuracy.size} " +
                        tileOverlapAccuracy.entries.joinToString(" ") { (id, acc) ->
                            "$id=${acc.rmsPx?.let { "%.1f".format(it) } ?: "n/a"}(n=${acc.matchCount})"
                        },
                )
                // Erstmal leeren (nicht die alten Werte eines vorigen Durchlaufs stehen lassen) --
                // die echte Berechnung unten braucht die Ganzbild-Blobs, die erst bei erfolgreichem
                // globalFit weiter unten einmalig erkannt werden.
                tileOwnRmsById = emptyMap()
                // Dasselbe Prinzip für die reiche Mesh-Korrektur (automatisches Solve-Nebenprodukt,
                // s. Kommentar an richMeshFit): bleibt ein vorheriger Solve-Lauf frühzeitig stehen
                // (z.B. anchors.size < 3 weiter unten), darf kein veralteter Fit vom vorigen Kachel-
                // Stand als "Mesh"-Chip anwählbar bleiben.
                richMeshFit = null
                richMeshRms = null
                richMeshAutoEligible = false
                richMeshRejectReason = null
                // Gleiches Prinzip für den SparseAnchorMesh-Kandidaten und die Kandidaten-RMS-Tabelle:
                // beide gehören zum GERADE laufenden Solve, ein Rest vom vorigen Kachel-Stand dürfte
                // weder wählbar bleiben noch die Verfügbarkeits-Anzeige verfälschen.
                sparseMeshFit = null
                sparseMeshRms = null
                projectionCandidateRms = emptyMap()
                if (solvedTileWcs.isNotEmpty()) {
                    // Vorläufiger Zwischenstand, BEVOR das globale Modell weiter unten gefittet ist --
                    // bleibt der Endzustand, falls es dazu gar nicht erst kommt (anchors.size < 3).
                    // Einzellösung (falls vorhanden) direkt anzeigen (kein Mosaik-Vorrang, s. Nutzer-
                    // Entscheidung 2026-07-30); ohne sie bleibt vorübergehend nur die Kachel-Mosaik-
                    // Ansicht übrig, bis das globale Modell gleich danach übernimmt.
                    val provisional: WcsSolutionLike = singleSolveWcs ?: MosaicWcsSolution(solvedTileWcs)
                    lastSolvedWcs = provisional
                    originalSolvedWcs = provisional
                }
                // Rein diagnostisch (s. Kommentar an calibratePanorama): sammelt das Ergebnis JEDES
                // versuchten Projektions-Kandidaten (nicht nur den Gewinner), um bei "Auto" zweifelsfrei
                // zu klären, ob z. B. Fisheye/Rectilinear für dieses Anker-Set schlicht scheitern (statt
                // nur knapp gegen Equirectangular/Cylindrical/Mercator zu verlieren) — Nutzer-Verdacht
                // (2026-07-20): Automodus wähle bei mehreren, weiter gestreuten Kacheln ein Modell mit
                // offensichtlich zu großer (Panorama-artiger) Himmelsabdeckung für ein normales Foto.
                val candidateResults = mutableListOf<Pair<PanoProjectionKind, Double?>>()
                // A4-Fußabdruck-Bewertung je Kandidat -- erst NACH dem withContext-Block protokolliert
                // (AppDiagnostics-Aufrufe gesammelt statt aus dem Rechen-Dispatcher heraus).
                val footprintResults = mutableListOf<String>()
                // Nach außen gehoben (sonst nur innerhalb der withContext-Lambda sichtbar) -- der reine
                // starre BIC-Gewinner VOR jeder Nachschärfung wird weiter unten als Baseline für die
                // reiche Mesh-Korrektur (richMeshFit) gebraucht, s. Kommentar dort. Ändert sonst nichts
                // an diesem Block; der automatische Mesh-Pfad direkt darunter bleibt unverändert.
                var rigidCalibration: FisheyeRefiner.PanoCalibration? = null
                // s. Zuweisung unten -- nach außen gehoben, damit der SparseAnchorMesh-Kandidat die
                // withContext-Lambda überlebt und in den UI-State geschrieben werden kann.
                var sparseMeshCandidate: FisheyeRefiner.PanoCalibration? = null
                val calibration = if (anchors.size >= 3) {
                    withContext(Dispatchers.Default) {
                        // enforceFullPanoramaPeriod=true: dieser Fit kann direkt zu lastSolvedWcs werden
                        // (s. [[project_gradnetz_randbeschriftung]] Runde-3-Aufrufstellen-Analyse) --
                        // erzwingt bei einem erkannten vollständigen 2:1-Panorama horizontalPeriodPx=
                        // imageWidth, wenn dessen RMS-Kosten dafür klein genug sind.
                        val rigid = FisheyeRefiner.calibratePanorama(
                            anchors, imgW, imgH, allowedKinds, anchorWeights,
                            onCandidate = { kind, rms -> candidateResults += kind to rms },
                            // A4: eine Zeile pro Kandidat, nur beim Kachel-Solve (nicht bei jedem
                            // Reproject) -- zeigt, welcher Fit wegen unplausiblem Bild-Fußabdruck von
                            // der AUTOMATIK ausgeschlossen wurde. Manuell bleibt er wählbar.
                            onFootprint = { kind, s ->
                                footprintResults += "projection_fov_sanity kind=$kind valid=${s.plausible} " +
                                    "angularSpanDeg=${"%.1f".format(s.angularSpanDeg)} " +
                                    "supportSpanDeg=${"%.1f".format(s.supportSpanDeg)} " +
                                    "supportAzimuthSpanDeg=${"%.1f".format(s.supportAzimuthSpanDeg)} " +
                                    "wrapDetected=${s.wrapDetected} reason=${s.reason}"
                            },
                            enforceFullPanoramaPeriod = true,
                        )
                        rigidCalibration = rigid
                        // Mesh (7. Kandidat) nimmt NICHT an calibratePanorama's eigener BIC-Auswahl
                        // teil (s. Kommentar an FisheyeRefiner.fitMesh) -- braucht deren Gewinner
                        // erst als baseline (Korrektur-Design, kein eigenständiger Ersatz mehr),
                        // wird also NACH rigid separat gebaut, dann per pickCalibration verglichen.
                        // Ohne rigid-Gewinner gibt es keine Baseline zum Korrigieren -> gar nicht erst
                        // versucht (auch kein "Mesh=fail" im Diagnose-Log, s. candidateResults unten).
                        val mesh = rigid?.let {
                            FisheyeRefiner.fitMesh(anchors, meshGroupSizes, it.solution, groupWeights = meshGroupReliability)
                        }
                        if (rigid != null) {
                            candidateResults += PanoProjectionKind.Mesh to mesh?.rms
                        }
                        // Nutzer-Auftrag 2026-09-03: denselben SparseAnchorMesh-Fit zusätzlich festhalten,
                        // damit er im Projektionsmenü als "Mesh" manuell wählbar bleibt, auch wenn
                        // pickCalibration ihn gleich darunter NICHT zum Sieger kürt. pickCalibration
                        // selbst und sein Ergebnis bleiben unverändert.
                        sparseMeshCandidate = mesh
                        pickCalibration(rigid, mesh, imgW, imgH)
                    }
                } else {
                    null
                }
                val globalFit = calibration?.solution
                if (calibration != null) {
                    alignProjectionKind = calibration.kind
                    alignProjectionRms = calibration.rms
                }
                // Kandidaten für die manuelle Projektionswahl festhalten (Nutzer-Auftrag 2026-09-03).
                // Rein additiv: weder calibration/globalFit noch irgendein Auto-Pfad liest diese Felder.
                sparseMeshFit = sparseMeshCandidate?.solution
                sparseMeshRms = sparseMeshCandidate?.rms
                projectionCandidateRms = candidateResults.toMap()
                val solvedCount = solveTiles.count { it.status == SolveTileStatus.Solved }
                val failedCount = solveTiles.count { it.status == SolveTileStatus.Failed }
                // De-Warp im Spiel? Dann greift die Refraktionskorrektur (Rand-RMS) für die Anzeige.
                dewarpUsedInLastSolve = solveTiles.any { it.status == SolveTileStatus.Solved && it.dewarp }
                AppDiagnostics.record(
                    "solvetiles_done solved=$solvedCount failed=$failedCount suspect=${suspects.size} " +
                        "anchors=${anchors.size} model=${calibration?.kind} " +
                        "rms=${calibration?.rms?.let { "%.1f".format(it) }}",
                )
                if (candidateResults.isNotEmpty()) {
                    AppDiagnostics.record(
                        "solvetiles_candidates " + candidateResults.joinToString(" ") { (kind, rms) ->
                            "$kind=${rms?.let { "%.1f".format(it) } ?: "fail"}"
                        },
                    )
                }
                footprintResults.forEach { AppDiagnostics.record(it) }
                tick(
                    context.getString(R.string.tiles_done_summary, solvedCount, failedCount, suspects.size) +
                        (calibration?.let { " · ${it.kind} rms ${"%.0f".format(it.rms)}" } ?: ""),
                )
                if (globalFit != null) {
                    fisheyeBaseFit = globalFit
                    annotate.constellationsEnabled = true
                    detectedStars = emptyList()
                    showDetectedStars = false
                    val prepared = withContext(Dispatchers.Default) {
                        val markers = fisheyeGridMarkers(globalFit, source).map { it.first }
                        val blobs = StarDetector.detect(source, starDetectionSensitivity).map { it.offset }
                        markers to blobs
                    }
                    // Ganzbild-Nachschärfung (literaturbelegter Weg, s. FisheyeRefiner.refine()-Kommentar:
                    // A&A 2025/2019): echte, über das GANZE Bild verteilte Sternerkennung + Katalog-Cross-
                    // Match ergänzt die rein aus den Kachel-WCS abgetasteten synthetischen Anker (9 pro
                    // Kachel, keine unabhängigen Messungen). Nur übernommen, wenn genug Treffer gefunden
                    // werden UND die neue Lösung noch zu den ursprünglichen Kachel-Ankern passt — sonst
                    // bleibt der reine Kachel-Fit unverändert bestehen (Fallback, kein Risiko).
                    var effectiveGlobalFit = globalFit
                    var effectiveAnchors = anchors
                    var effectiveWeights = anchorWeights
                    val originalRms = calibration!!.rms
                    val refineCatalogVecs = skyCatalogStars.ifEmpty { referenceCatalogStars }
                        .asSequence()
                        .filter { it.magnitude <= 5.0f }
                        .map { raDecToVector(it.point.raDegrees.toDouble(), it.point.decDegrees.toDouble()) }
                        .toList()
                    // Echte, unabhängige Pro-Kachel-Solve-Genauigkeit fürs Kachel-Info-Popup
                    // (Nutzerwunsch): BEVORZUGT die echten, von astrometry.net selbst verifizierten
                    // .corr-Solve-Treffer dieser Kachel (tileCorrRefsById, nur LocalAstrometry-Roh-Crop) --
                    // kein Kreuzmatch nötig, die Zuordnung steht schon fest. Rückfall (ASTAP/Nova/De-Warp
                    // oder fehlende/zu kurze .corr): Kreuzmatch der Kachel-EIGENEN WCS (nicht der globalen
                    // Panorama-Lösung) gegen dieselben echten Ganzbild-Blobs, auf die Kachel-eigene
                    // Bounding-Box gefiltert. Kein bestehender Wert lässt sich dafür wiederverwenden --
                    // tileAnchorPairs() sampelt seine Punkte NUR aus der WCS selbst zurück (trivial
                    // nahe Null), und keiner der drei Solver liefert einen Restfehler.
                    tileOwnRmsById = withContext(Dispatchers.Default) {
                        val result = HashMap<Long, TileOwnAccuracy>()
                        for ((tileId, tw) in idToTileWcs) {
                            val corrRefs = tileCorrRefsById[tileId]
                            if (corrRefs != null && corrRefs.isNotEmpty()) {
                                // Stabilisierungs-Pass (Untersuchungsauftrag 2026-08-31): identischer Fix
                                // wie TileConsistency.corrBasedOwnAccuracy -- tw.wcs sagt lokal (relativ
                                // zu tw.tileOffsetX/Y) vorher, corrRefs ist für eine Tiny-Sky-Kachel
                                // (tw.corrRefsAlreadyNative) bereits nativ. Ohne diese Rückumrechnung
                                // liefert tile_own_rms für TinySky-Kacheln Werte in Höhe des Kachel-
                                // Offsets selbst (hunderte bis tausende Pixel) statt der echten
                                // Solve-Genauigkeit.
                                val effectiveRefs = if (tw.corrRefsAlreadyNative) {
                                    corrRefs.map { (px, dir) -> Offset(px.x - tw.tileOffsetX, px.y - tw.tileOffsetY) to dir }
                                } else {
                                    corrRefs
                                }
                                result[tileId] = TileOwnAccuracy(
                                    rmsPx = if (corrRefs.size >= TileConsistency.MIN_TILE_OWN_RMS_MATCHES) {
                                        FisheyeRefiner.reprojectionRmsWcs(tw.wcs, effectiveRefs, tw.tileHeight)
                                    } else {
                                        null
                                    },
                                    matchCount = corrRefs.size,
                                )
                                continue
                            }
                            val localBlobs = prepared.second.mapNotNull { p ->
                                val lx = p.x - tw.tileOffsetX
                                val ly = p.y - tw.tileOffsetY
                                if (lx in 0f..tw.tileWidth.toFloat() && ly in 0f..tw.tileHeight.toFloat()) {
                                    Offset(lx, ly)
                                } else {
                                    null
                                }
                            }
                            val matches = FisheyeRefiner.crossMatchWcs(
                                tw.wcs, refineCatalogVecs, localBlobs, tw.tileHeight,
                                tol = 0.02 * hypot(tw.tileWidth.toDouble(), tw.tileHeight.toDouble()),
                            )
                            if (matches.isEmpty()) continue
                            val refs = matches.map { (dir, px) -> px to dir }
                            result[tileId] = TileOwnAccuracy(
                                rmsPx = if (matches.size >= TileConsistency.MIN_TILE_OWN_RMS_MATCHES) {
                                    FisheyeRefiner.reprojectionRmsWcs(tw.wcs, refs, tw.tileHeight)
                                } else {
                                    null
                                },
                                matchCount = matches.size,
                            )
                        }
                        result
                    }
                    AppDiagnostics.record(
                        "tile_own_rms computed=${tileOwnRmsById.size}/${idToTileWcs.size} " +
                            tileOwnRmsById.entries.joinToString(" ") { (id, acc) ->
                                val rmsText = acc.rmsPx?.let { "%.1f".format(it) } ?: "n/a"
                                "$id=$rmsText(n=${acc.matchCount})"
                            },
                    )
                    // refineMatches wird SPÄTER (constellation_accuracy-Diagnose weiter unten) noch
                    // einmal in seiner ursprünglichen Bedeutung (reine Ganzbild-Blob-Kreuzmatch)
                    // gebraucht -- darf hier nicht umgewidmet werden, daher eigener Pool corrGlobalRefs.
                    val (refineMatches, corrGlobalRefs) = withContext(Dispatchers.Default) {
                        val blobMatches = FisheyeRefiner.crossMatchSolution(
                            globalFit, refineCatalogVecs, prepared.second,
                            tol = 0.02 * hypot(imgW.toDouble(), imgH.toDouble()),
                        )
                        // Echte, von astrometry.net selbst verifizierte .corr-Solve-Treffer JEDER
                        // gelösten Kachel (kachel-lokal -> globale Bildkoordinaten), zusätzlich zur
                        // Ganzbild-Blob-Kreuzmatch oben -- typischerweise deutlich zahlreicher und
                        // ohne Abhängigkeit von einem hellen (mag<=5) Katalog-Ausschnitt.
                        blobMatches to FisheyeRefiner.globalizeTileCorrRefs(idToTileWcs, tileCorrRefsById)
                    }
                    val matchRefs = refineMatches.map { (dir, px) -> px to dir }
                    val allRealMatches = matchRefs + corrGlobalRefs
                    // Mindestens 20 Treffer (etwas über FisheyeRefiner.refine()s MIN_MATCHES=10 -- ein
                    // ganzes Mosaik bietet mehr Sterne als der alte kleine Zentralpatch), jetzt über
                    // Ganzbild-Blob + .corr ZUSAMMEN (ein .corr-reiches Mosaik kann diese Schwelle
                    // erreichen, auch wenn die Ganzbild-Blob-Kreuzmatch allein zu wenige findet).
                    if (allRealMatches.size >= 20) {
                        // Ehrlicher Vorher-Vergleich: wie weit läge das ALTE (reine Kachel-)Modell von
                        // genau diesen echten, unabhängig erkannten Sternen entfernt? Ohne diesen Wert
                        // lässt sich nicht direkt nachvollziehen, ob die Nachschärfung dort wirklich
                        // hilft -- der Sanity-Check unten prüft nur, ob sie die Kacheln nicht verschlimmert.
                        val oldModelMatchRms = FisheyeRefiner.reprojectionRms(globalFit, allRealMatches)
                        val refinedAnchors = anchors + allRealMatches
                        val refinedWeights = anchorWeights + List(allRealMatches.size) { 1.0 }
                        val refitStartMs = System.currentTimeMillis()
                        val refinedCalibration = withContext(Dispatchers.Default) {
                            FisheyeRefiner.calibratePanorama(refinedAnchors, imgW, imgH, allowedKinds, refinedWeights, enforceFullPanoramaPeriod = true)
                        }
                        val refitMs = System.currentTimeMillis() - refitStartMs
                        if (refinedCalibration != null) {
                            val sanityRms = FisheyeRefiner.reprojectionRms(refinedCalibration.solution, anchors)
                            val newModelMatchRms = FisheyeRefiner.reprojectionRms(refinedCalibration.solution, allRealMatches)
                            // Drei Bedingungen: (1) darf die an den Kacheln bereits bewiesene Genauigkeit
                            // nicht verschlimmern (>2.5x schlechter dort -> verwerfen), (2) MUSS auf den
                            // echten Ganzbild-Sternen tatsächlich genauer sein als das alte Kachel-Modell --
                            // sonst bringt die Nachschärfung nichts oder schadet sogar genau dort, wo sie
                            // helfen soll (Gerätebeleg 2026-07-22: Gate 1 allein hat einmal einen Fall
                            // durchgelassen, der der Nutzer visuell als schlechter empfand), UND (3) das neue
                            // Modell muss auch für sich genommen absolut brauchbar sein -- (1)+(2) sind rein
                            // RELATIV und lassen ein Modell durch, das nur "weniger schlecht" als ein bereits
                            // schwaches Kachel-Modell ist (Gerätebeleg 2026-07-23: 23,6px "verbessert" gegenüber
                            // 26,3px wurde durchgewunken, obwohl 23,6px selbst schon ein Spinnennetz-Kollaps war).
                            // Bewusst gegen newModelMatchRms geprüft, NICHT gegen refinedCalibration.rms (dessen
                            // eigene interne RMS über refinedAnchors = alte Kachel-Anker + echte Treffer
                            // GEMEINSAM läuft): ein paar verrauschte/widersprüchliche Kachel-Anker (niedrige
                            // tile_reliability) zogen diese gemischte Zahl über die Grenze, obwohl dieselbe
                            // Nachschärfung gegen die echten Sterne ALLEIN hervorragend war (Gerätebeleg
                            // 2026-08-17: 18-Kachel-Fall, newModelMatchRms=12,0 gegen refinedCalibration.rms=
                            // 30,3 bei Grenze 23,4 -- wurde bislang fälschlich verworfen, obwohl newModelMatchRms
                            // sogar besser war als der parallel getestete "gute" Vergleichslauf).
                            val maxAbsoluteRms = MAX_GLOBAL_REFINE_RMS_FRACTION * hypot(imgW.toDouble(), imgH.toDouble())
                            val accepted = sanityRms <= 2.5 * originalRms && newModelMatchRms < oldModelMatchRms &&
                                newModelMatchRms <= maxAbsoluteRms
                            AppDiagnostics.record(
                                "global_refine blobs=${prepared.second.size} catalog=${refineCatalogVecs.size} " +
                                    "matches=${refineMatches.size} corrMatches=${corrGlobalRefs.size} " +
                                    "totalMatches=${allRealMatches.size} accepted=$accepted kind=${refinedCalibration.kind} " +
                                    "rmsBefore=${"%.1f".format(originalRms)} rmsAfter=${"%.1f".format(refinedCalibration.rms)} " +
                                    "sanityRms=${"%.1f".format(sanityRms)} maxAbsoluteRms=${"%.1f".format(maxAbsoluteRms)} " +
                                    "oldMatchRms=${"%.1f".format(oldModelMatchRms)} newMatchRms=${"%.1f".format(newModelMatchRms)} " +
                                    "refitMs=$refitMs",
                            )
                            if (accepted) {
                                effectiveGlobalFit = refinedCalibration.solution
                                effectiveAnchors = refinedAnchors
                                effectiveWeights = refinedWeights
                                alignProjectionKind = refinedCalibration.kind
                                alignProjectionRms = refinedCalibration.rms
                            } else {
                                // Die gewählte Modellart selbst hält den echten Sternen nicht stand (nicht
                                // nur "zu wenige Treffer" -- s. Bedingungen oben) -- bei wenigen Kachel-
                                // Ankern kann die BIC-Komplexitätsstrafe (complexityPenalizedScore) eine
                                // Modellart mit weniger Parametern (z.B. Rectilinear/Cylindrical, die bei
                                // großem Blickwinkel divergieren) über die tatsächlich passende (z.B.
                                // Fisheye) stellen, obwohl deren roher RMS auf den wenigen Ankern kaum
                                // schlechter war (Gerätebeleg 2026-07-28: Fisheye=1,3 vs. Rectilinear=1,4
                                // bei 9 Ankern, Rectilinear gewinnt durch BIC, scheitert aber hier mit 37,8px
                                // gegen echte Sterne). Erneuter Versuch auf denselben (jetzt reicheren)
                                // Ankern OHNE genau diese Modellart -- dieselben 3 Gates entscheiden, ob der
                                // Ersatzkandidat wirklich hält.
                                val retryKinds = allowedKinds - refinedCalibration.kind
                                val retryCalibration = if (retryKinds.isNotEmpty()) {
                                    withContext(Dispatchers.Default) {
                                        FisheyeRefiner.calibratePanorama(refinedAnchors, imgW, imgH, retryKinds, refinedWeights, enforceFullPanoramaPeriod = true)
                                    }
                                } else {
                                    null
                                }
                                if (retryCalibration != null) {
                                    val retrySanityRms = FisheyeRefiner.reprojectionRms(retryCalibration.solution, anchors)
                                    val retryMatchRms = FisheyeRefiner.reprojectionRms(retryCalibration.solution, allRealMatches)
                                    val retryAccepted = retrySanityRms <= 2.5 * originalRms &&
                                        retryMatchRms < oldModelMatchRms && retryMatchRms <= maxAbsoluteRms
                                    AppDiagnostics.record(
                                        "global_refine_retry excludedKind=${refinedCalibration.kind} " +
                                            "kind=${retryCalibration.kind} accepted=$retryAccepted " +
                                            "rmsAfter=${"%.1f".format(retryCalibration.rms)} " +
                                            "sanityRms=${"%.1f".format(retrySanityRms)} " +
                                            "newMatchRms=${"%.1f".format(retryMatchRms)}",
                                    )
                                    if (retryAccepted) {
                                        effectiveGlobalFit = retryCalibration.solution
                                        effectiveAnchors = refinedAnchors
                                        effectiveWeights = refinedWeights
                                        alignProjectionKind = retryCalibration.kind
                                        alignProjectionRms = retryCalibration.rms
                                    }
                                }
                            }
                        } else {
                            AppDiagnostics.record(
                                "global_refine blobs=${prepared.second.size} catalog=${refineCatalogVecs.size} " +
                                    "matches=${refineMatches.size} corrMatches=${corrGlobalRefs.size} " +
                                    "totalMatches=${allRealMatches.size} accepted=false reason=fit_failed refitMs=$refitMs",
                            )
                        }
                    } else {
                        AppDiagnostics.record(
                            "global_refine blobs=${prepared.second.size} catalog=${refineCatalogVecs.size} " +
                                "matches=${refineMatches.size} corrMatches=${corrGlobalRefs.size} " +
                                "totalMatches=${allRealMatches.size} accepted=false reason=too_few_matches",
                        )
                    }
                    // A1/A9 (Nutzer-Vorgabe 2026-08-30): RichCorrMesh ersetzt hier FisheyeRefiner.fitMesh
                    // -- interpoliert jeden EINZELNEN validierten .corr-Kontrollstern exakt (statt
                    // Kachel-weiser IDW-Mittelung, s. RichCorrMesh-Klassenkommentar), aus den ECHTEN,
                    // dichten .corr-Sternmessungen (corrGlobalRefs), NICHT den 9 synthetischen Kachel-
                    // Ankern (anchors/meshGroupSizes), die der AUTOMATISCHE SparseAnchorMesh-Kandidat
                    // oben (Teil der pickCalibration-Konkurrenz bei "Auto") verwendet -- der bleibt
                    // vollständig unverändert (A1: "bestehende Sparse-Anchor-Mesh-Kandidat bleibt
                    // vorerst bestehen"). Ergebnis nur in richMeshFit/richMeshRms gecacht, für den
                    // eigenständigen "Mesh"-Projektions-Chip -- dieselben Kotlin-Feldnamen wie zuvor
                    // (UI/CalibrationSnapshot unverändert), NUR die dahinterliegende Fit-Logik ist neu.
                    // Baseline bewusst rigidCalibration (reiner starrer Gewinner, VOR der Nachschärfung
                    // oben) statt effectiveGlobalFit (das hier bereits selbst SparseAnchorMesh sein
                    // kann) -- sonst würde ein Korrektur-Layer um einen anderen verschachtelt.
                    val rigidBaseline = rigidCalibration
                    if (rigidBaseline != null) {
                        // Untersuchungsauftrag 2026-08-31 (vierte Runde): rein additive Beweis-Diagnose
                        // für den jetzt behobenen Stage-2-Koordinatenfehler (s. logStage2CorrGlobalizationDetail-
                        // KDoc) -- läuft VOR RichCorrMesh.validateAndGroup, liest denselben corrGlobalRefs-
                        // Quellzustand (tileCorrRefsById/idToTileWcs), verändert nichts.
                        logStage2CorrGlobalizationDetail(
                            idToTileWcs, tileCorrRefsById, tilesSnapshot, rigidBaseline.solution, imgH,
                            budgetPerTile = 3,
                        )
                        val (richGroupSizes, _) = FisheyeRefiner.corrRefGroupSizesAndWeights(
                            idToTileWcs, tileCorrRefsById, reliabilityTileIds, meshGroupReliability,
                        )
                        val richStartMs = System.currentTimeMillis()
                        val richCandidate = withContext(Dispatchers.Default) {
                            // A4, Stufe 2 (global): Ausreißer/Duplikate gegen die Panorama-Baseline
                            // gruppenweise verwerfen, BEVOR sie als Kontrollpunkte gelten (Stufe 1 lief
                            // bereits pro Kachel direkt beim Solve, s. corr_pipeline-Diagnose oben).
                            val (validatedRefs, validatedGroupSizes) = RichCorrMesh.validateAndGroup(
                                corrGlobalRefs, richGroupSizes, rigidBaseline.solution, imgH,
                            )
                            // fitCandidate statt fit (Nutzer-Auftrag 2026-09-03): identische Gates und
                            // identische Gate-Diagnose, aber ein Fit, den nur die QUALITÄTS-Gates
                            // (A8/Kreuzvalidierung) ablehnen, bleibt als manuell wählbarer "Detail-Mesh"-
                            // Kandidat erhalten statt verworfen zu werden. Dass richMeshFit jetzt auch in
                            // diesem Fall gesetzt ist, ändert am AUTOMATISCHEN Pfad nichts -- richMeshFit
                            // wird nirgends automatisch angewendet, ausschließlich über den manuellen
                            // Projektions-Chip (s. dortiger Zweig).
                            RichCorrMesh.fitCandidate(validatedRefs, validatedGroupSizes, rigidBaseline.solution, imgW, imgH)
                        }
                        val rich = richCandidate?.fit
                        richMeshFit = rich?.solution
                        richMeshRms = rich?.crossValidationRmsPx?.takeIf { it.isFinite() }
                        richMeshAutoEligible = richCandidate?.autoEligible == true
                        richMeshRejectReason = richCandidate?.rejectReason
                        // A9: SparseAnchorMesh (oben, aus candidateResults) und RichCorrMesh (hier)
                        // diagnostisch klar getrennt benannt -- ein guter sparseAnchorMeshRms-Wert ist
                        // KEIN Beweis, dass die echte .corr-Sternkorrektur funktioniert.
                        val sparseAnchorMeshRms = candidateResults.firstOrNull { it.first == PanoProjectionKind.Mesh }?.second
                        AppDiagnostics.record(
                            "rich_corr_mesh sparseAnchorMeshRms=${sparseAnchorMeshRms?.let { "%.1f".format(it) } ?: "fail"} " +
                                "richCorrMeshRefs=${corrGlobalRefs.size} " +
                                "richCorrMeshAcceptedControlPoints=${rich?.acceptedControlPoints ?: 0} " +
                                "richCorrMeshCoverage=${rich?.coverageFraction?.let { "%.2f".format(it) } ?: "n/a"} " +
                                "baselineRms=${rich?.baselineRmsPx?.let { "%.1f".format(it) } ?: "n/a"} " +
                                "richCorrMeshTrainRms=${rich?.trainRmsPx?.let { "%.1f".format(it) } ?: "n/a"} " +
                                "richCorrMeshCrossValidationRms=${rich?.crossValidationRmsPx?.let { "%.1f".format(it) } ?: "fail"} " +
                                "tookMs=${System.currentTimeMillis() - richStartMs}",
                        )
                    }
                    panoSolveAnchors = effectiveAnchors
                    panoSolveWeights = effectiveWeights
                    fisheyeBaseFit = effectiveGlobalFit
                    // Anzeige = das globale Modell direkt, OHNE Kachel-Mosaik-Vorrang (Nutzer-Entscheidung
                    // 2026-07-30 nach systematischem Gerätevergleich über mehrere Bilder: mit 1 oder
                    // mehreren Kacheln zeigten sich durchweg sichtbare Abstufungen/Kanten in der Mosaik-
                    // Darstellung, die beim manuellen "Kachelfrei (Test)"-Umschalten verschwanden ("Linien
                    // perfekt gezeichnet von Stern zu Stern"). Kacheln bleiben unverändert die Datenquelle
                    // für Solve/global_refine/Feinjustier-Seed (lastSolvedTileWcs, tileCorrRefsById) --
                    // nur die STANDARD-ANZEIGE nutzt jetzt immer das globale Modell, nicht mehr die Mosaik-
                    // Priorisierung einzelner Kacheln.
                    var diagnosticsWcs: WcsSolutionLike? = null
                    if (solvedTileWcs.isNotEmpty()) {
                        // Einzellösung hat Vorrang (deckt das ganze Bild ab und verfällt nicht); sonst der
                        // globale (ggf. refraktionskorrigierte, ggf. nachgeschärfte) Fit.
                        val display: WcsSolutionLike = singleSolveWcs ?: if (dewarpUsedInLastSolve) {
                            withContext(Dispatchers.Default) {
                                refractedDisplayFit(effectiveGlobalFit, effectiveAnchors, imgW, imgH, allowedKinds, effectiveWeights, enforceFullPanoramaPeriod = true)
                            }
                        } else {
                            effectiveGlobalFit
                        }
                        lastSolvedWcs = display
                        originalSolvedWcs = display
                        diagnosticsWcs = display
                    }
                    // A1 (Nutzer-Auftrag 2026-09-03): hat der Nutzer bewusst Mesh/Detail-Mesh/Eigene
                    // gewählt, muss NACH dem Solve auch tatsächlich dieser Kandidat aktiv sein -- der
                    // Auto-Sieger wurde oben vollständig berechnet und protokolliert, überschreibt die
                    // manuelle Wahl aber nicht mehr still. originalSolvedWcs bleibt bewusst der
                    // Auto-Stand: "Original-Astrometrie wiederherstellen" soll zum ungefilterten
                    // Solve-Ergebnis zurückführen, nicht zur manuellen Auswahl.
                    val manualOverride = applyManualProjectionOverride()
                    if (manualOverride) diagnosticsWcs = lastSolvedWcs
                    AppDiagnostics.record(
                        "projection_selection choice=$panoProjectionChoice activeModel=$alignProjectionKind " +
                            "rawWinner=${calibration?.kind} " +
                            "rawWinnerRms=${calibration?.rms?.let { "%.1f".format(it) } ?: "n/a"} " +
                            "meshRms=${sparseMeshRms?.let { "%.1f".format(it) } ?: "n/a"} " +
                            "detailMeshRms=${richMeshRms?.let { "%.1f".format(it) } ?: "n/a"} " +
                            "finalWinner=$alignProjectionKind " +
                            "meshMargin=${
                                rigidCalibration?.let {
                                    "%.2f".format(meshSubstitutionMargin(it.kind, imgW, imgH))
                                } ?: "n/a"
                            } " +
                            "manualOverrideApplied=$manualOverride source=solve",
                    )
                    // Rein diagnostisch (s. logSupportDistance-KDoc): der tatsächlich aktive Auto-Sieger
                    // und die beiden manuell wählbaren Mesh-Kandidaten. Die von calibratePanorama
                    // VERWORFENEN Kandidaten sind hier bewusst nicht dabei -- deren Lösungsobjekte gibt
                    // die Funktion nicht heraus, und FisheyeRefiner sollte in dieser Runde ausdrücklich
                    // nicht angefasst werden (s. Abschlussbericht).
                    calibration?.let { logSupportDistance(it.kind.toString(), it.solution, anchors, imgW, imgH) }
                    logSupportDistance("Mesh", sparseMeshFit, anchors, imgW, imgH)
                    logSupportDistance("DetailMesh", richMeshFit, anchors, imgW, imgH)
                    // recordUndo=false an allen 3: das Solve ist bereits EIN Tile-Undo-Schritt (s.
                    // snapshotTilesForUndo() am Funktionsanfang) -> keine zusätzlichen Overlay-Schritte,
                    // sonst kostet EIN Solve vier globale Undo-Klicks statt einem.
                    syncConstellationLayer(recordUndo = false)
                    syncDeepSkyLayer(recordUndo = false)
                    syncStarLayer(recordUndo = false)
                    // Vollständigkeits-/Genauigkeits-Audit für ALLE Katalog-Sternbilder (alle 88, jede
                    // Hemisphäre) -- nicht nur die paar, die visuell auffallen. constellationCompletenessById
                    // ist durch syncConstellationLayer() oben bereits frisch; evaluateConstellationAccuracy
                    // deckt hier zusätzlich mit allRealMatches (Blob+.corr zusammen) den gesamten Katalog ab,
                    // nicht nur einen willkürlichen Top-5-Ausschnitt.
                    diagnosticsWcs?.let { wcs ->
                        val accuracyById = AstapOverlayMapper.evaluateConstellationAccuracy(
                            catalog, allRealMatches.map { (px, dir) -> dir to px }, wcs, imgH,
                        ).associate { (pattern, acc) -> pattern.id to acc }
                        val rows = catalog.mapNotNull { p -> constellationCompletenessById[p.id]?.let { p to it } }
                        val notInFov = rows.count { !it.second.anyStarInFov }
                        val drawn = rows.count { it.second.survivingEdges > 0 }
                        val inFovButEmpty = rows.count { it.second.anyStarInFov && it.second.survivingEdges == 0 }
                        AppDiagnostics.record(
                            "constellation_audit total=${catalog.size} drawn=$drawn in_fov_empty=$inFovButEmpty " +
                                "not_in_fov=$notInFov accuracy_checked=${accuracyById.size}",
                        )
                        rows.filter { it.second.anyStarInFov }
                            .sortedByDescending { (_, c) -> accuracyById[c.id]?.rmsErrorPx ?: -1.0 }
                            .forEachIndexed { i, (_, c) ->
                                val acc = accuracyById[c.id]
                                AppDiagnostics.record(
                                    "caudit[$i]=id:${c.id},hem:${c.hemisphere},stars:${c.survivingStars}/${c.totalStars}," +
                                        "edges:${c.survivingEdges}/${c.totalEdges}," +
                                        (
                                            acc?.let { "accuracyPx:${"%.1f".format(it.rmsErrorPx)}(n=${it.matchedStars})" }
                                                ?: "accuracyPx:n/a"
                                            ),
                                )
                            }
                    }
                    // Seed für die Feinjustierung vorbereiten, aber NICHT mehr auto-öffnen:
                    // die App bleibt im Kachel-Menü, der Nutzer entscheidet den nächsten Schritt.
                    // Gewickelt (equidistantSeedFrom) statt roh -> derselbe, garantiert nie
                    // zurückfaltende Mechanismus wie FOV-Vorschau/Feinjustierung.
                    fisheyeAlignSeed = FisheyeRefiner.equidistantSeedFrom(effectiveGlobalFit, imgW, imgH, fisheyeFovLongDeg)
                    fisheyeAlignRefs = null // neues Solve -> Feinjustier-Referenzen frisch aufbauen.
                    fisheyeAlignBright = prepared.first
                    fisheyeAlignBlobs = prepared.second
                    // Frischer "direkt nach dem Solve"-Wiederherstellungspunkt -- ALLE Felder oben sind
                    // jetzt final für dieses Solve gesetzt (Reihenfolge wichtig: vor diesem Punkt wären
                    // fisheyeAlignSeed/-Refs noch der Stand vor dem Solve).
                    postSolveCalibration = snapshotCalibration()
                    // FOV-Slider automatisch auf das tatsächlich gelöste Bildfeld stellen (analog zur
                    // Einzelbildlösung) -- bisher blieb er nach einem Kachel-Solve auf einem u.U.
                    // veralteten/falschen Wert stehen, obwohl genau dieser Wert in
                    // equidistantSeedFrom()/der Feinjustierung einfließt.
                    panoramaFovLongDeg(effectiveGlobalFit, imgW, imgH)?.let { fovDeg ->
                        val clamped = fovDeg.coerceIn(1f, 360f)
                        fisheyeFovLongDeg = clamped
                        preferences.edit().putFloat("fisheye_fov_long_deg", clamped).apply()
                    }
                    astapOperationState = AstapOperationState.Solved(
                        constellationCount = overlays.count { it.layer == AnnotationLayer.Constellation },
                        starCount = 0,
                    )
                    val suspectSuffix = if (suspects.isNotEmpty()) {
                        context.getString(R.string.suspect_excluded_suffix, suspects.size)
                    } else {
                        ""
                    }
                    val msg = if (failedCount > 0) {
                        context.getString(
                            R.string.tiles_solved_with_failed,
                            solvedCount,
                            failedCount,
                            suspectSuffix,
                        )
                    } else {
                        context.getString(R.string.tiles_solved_no_failed, solvedCount, suspectSuffix)
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    if (solvedTileWcs.isNotEmpty()) {
                        annotate.constellationsEnabled = true
                        syncConstellationLayer(recordUndo = false)
                        syncDeepSkyLayer(recordUndo = false)
                        syncStarLayer(recordUndo = false)
                        postSolveCalibration = snapshotCalibration()
                        // Kein globaler Fit (zu wenige Anker) -- Bildfeld stattdessen aus der lokalen
                        // Pixelskala EINER gelösten Kachel ableiten, hochgerechnet auf die volle
                        // Bildkante (dieselbe Optik gilt fürs ganze Foto). Analog zur Einzelbildlösung.
                        solvedTileWcs.firstNotNullOfOrNull { solvedFovLongDeg(it.wcs, imgW, imgH) }?.let { fovDeg ->
                            val clamped = fovDeg.coerceIn(1f, 360f)
                            fisheyeFovLongDeg = clamped
                            preferences.edit().putFloat("fisheye_fov_long_deg", clamped).apply()
                        }
                    }
                    astapOperationState = AstapOperationState.Failure(
                        context.getString(R.string.too_few_anchors_global_fit),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppDiagnostics.record("solvetiles_error type=${error.javaClass.name} msg=${error.message}")
                // Unit 6: ein Fehler aus einer bereits abgebrochenen Kachel (z. B. ein Netzfehler, der
                // beim Cancel-Zeitpunkt schon "in Flight" war) darf den bereits gesetzten Cancelled-
                // Zustand NICHT nachträglich als "Solve fehlgeschlagen" überschreiben.
                if (!isStaleSolve()) {
                    astapOperationState = AstapOperationState.Failure(
                        error.message ?: context.getString(R.string.label_tile_solve_failed),
                    )
                }
            } finally {
                // Unit 6: nur die AKTUELLE (nicht durch einen zwischenzeitlichen Abbruch veraltete)
                // Solve-Coroutine darf hier aufräumen -- sonst könnte eine spät unwindende Alt-Coroutine
                // den Job/Foreground-Service-Status eines INZWISCHEN bereits neu gestarteten Solves
                // überschreiben (astapSolveJob=null/SolveController.finish() für den FALSCHEN Lauf).
                // resetSolveStateOnCancel() hat den Kachel-/Job-/Service-Reset für den Abbruch-Fall
                // bereits synchron erledigt (s. dort) -- für eine veraltete Coroutine ist hier daher
                // nichts mehr zu tun.
                if (!isStaleSolve()) {
                    // Auch bei Abbruch sauberer Zustand: laufende (blaue) Kachel zurück auf offen (gelb).
                    for (i in solveTiles.indices) {
                        if (solveTiles[i].status == SolveTileStatus.Solving) {
                            solveTiles[i] = solveTiles[i].copy(status = SolveTileStatus.Pending)
                        }
                    }
                    solveTilesRunning = false
                    solveTilesStatus = ""
                    astapSolveJob = null
                    // Solve beendet -> Foreground-Service darf sich stoppen. success nur bei echtem
                    // Solved-Endzustand (nicht bei Abbruch/Failure) -> Service postet dann zusaetzlich
                    // eine "fertig"-Notification statt nur sein Icon verschwinden zu lassen.
                    SolveController.finish(success = astapOperationState is AstapOperationState.Solved)
                }
            }
        }
    }

    // Projektion NACH dem Solve umrechnen (Nutzeridee): der Solve = die Anker bleiben, nur calibratePanorama
    // läuft mit dem neu gewählten Modell erneut -> neues globalFit wird als angezeigte Lösung (lastSolvedWcs)
    // UND als Feinjustier-Seed gesetzt. Kein erneutes Plate-Solve. Nach den Sync-Funktionen deklariert, weil
    // es sie aufruft (lokale Funktionen dürfen nur zuvor deklarierte referenzieren).
    // WICHTIG für Aufrufer: snapshotCalibrationForUndo() VOR dieser Funktion aufrufen (und VOR dem Setzen
    // von panoProjectionChoice) -- ein Snapshot innerhalb dieser Funktion würde bereits den neuen, gerade
    // gewählten panoProjectionChoice einfrieren statt den alten, Undo würde dann nicht zur vorherigen
    // Projektionswahl zurückkehren.
    fun reprojectPanorama() {
        val src = bitmap ?: return
        val anchors = panoSolveAnchors
        // Ohne Kachel-Anker (reine Einzelbildlösung) trotzdem funktionsfähig: aus der Einzellösung ein
        // Anker-Gitter sampeln (wie beim Feinjustier-Seed) -> die Projektions-Auswahl ist so kein
        // toter Button mehr, auch ohne dass jemals eine Kachel gelöst wurde.
        if (anchors.size < 3 && singleSolveWcs == null) return
        val allowed = allowedProjectionKinds(panoProjectionChoice)
        calibrationAsyncJob?.cancel()
        calibrationAsyncJob = scope.launch {
            val calib = withContext(Dispatchers.Default) {
                if (anchors.size >= 3) {
                    FisheyeRefiner.calibratePanorama(anchors, src.width, src.height, allowed, panoSolveWeights, enforceFullPanoramaPeriod = true)
                } else {
                    singleSolveWcs?.let { panoramaCalibrationFromWcs(it, src.width, src.height, allowed) }
                }
            } ?: return@launch
            val fit = calib.solution
            alignProjectionKind = calib.kind
            alignProjectionRms = calib.rms
            fisheyeBaseFit = fit
            // Gewickelt (equidistantSeedFrom) statt roh -> derselbe, garantiert nie
            // zurückfaltende Mechanismus wie FOV-Vorschau/Feinjustierung nach Kachel-Solve.
            fisheyeAlignSeed = FisheyeRefiner.equidistantSeedFrom(fit, src.width, src.height, fisheyeFovLongDeg)
            fisheyeAlignRefs = null // andere Projektion -> Referenzen neu aufbauen.
            // Anzeige = das neu gewählte globale Modell direkt, ohne Kachel-Mosaik-Vorrang (s.
            // Nutzer-Entscheidung 2026-07-30 in solveAllTiles()). Gelöste Kacheln (lastSolvedTileWcs)
            // bleiben unverändert als Solve-/Feinjustier-Datenquelle bestehen, nur die STANDARD-
            // ANZEIGE folgt jetzt bei jedem Projektionswechsel konsistent dem globalen Modell.
            // Ein ECHTER, das ganze Bild abdeckender Einzelbild-Solve (singleSolveWcs) bleibt weiterhin
            // vorrangig vor dem nur aus Ankern gefitteten Modell -- er ist keine Näherung, sondern
            // selbst eine vollständige astrometrische Lösung.
            lastSolvedWcs = singleSolveWcs ?: if (dewarpUsedInLastSolve && anchors.size >= 3) {
                withContext(Dispatchers.Default) {
                    refractedDisplayFit(fit, anchors, src.width, src.height, allowed, panoSolveWeights, enforceFullPanoramaPeriod = true)
                }
            } else {
                fit
            }
            // A1: eine aktive manuelle Wahl (Mesh/Detail-Mesh/Eigene) gewinnt gegen den gerade neu
            // berechneten Auto-Sieger -- sonst liefe die UI-Auswahl still vom tatsächlich aktiven WCS weg.
            val manualOverride = applyManualProjectionOverride()
            AppDiagnostics.record(
                "projection_selection choice=$panoProjectionChoice activeModel=$alignProjectionKind " +
                    "rawWinner=${calib.kind} rawWinnerRms=${"%.1f".format(calib.rms)} " +
                    "finalWinner=$alignProjectionKind manualOverrideApplied=$manualOverride source=reproject",
            )
            // recordUndo=false: der Aufrufer hat bereits VOR dem Reproject-Start snapshotCalibrationForUndo()
            // aufgerufen -> ein Modellwechsel ist EIN globaler Undo-Schritt, keine zusätzlichen Overlay-Schritte.
            syncConstellationLayer(recordUndo = false)
            logConstellationAudit("reproject")
            syncDeepSkyLayer(recordUndo = false)
            syncStarLayer(recordUndo = false)
            AppDiagnostics.record("reproject model=${calib.kind} refraction=$dewarpUsedInLastSolve")
            // Transparenz statt stillem Verschwinden: manche Modelle können das aktuelle Bildfeld
            // geometrisch nicht abbilden (z.B. Rectilinear/Gnomonik bei sehr weitem Feld) -> Sternbilder
            // blieben sonst kommentarlos weg. Hinweisen statt den Nutzer raten zu lassen.
            if (annotate.constellationsEnabled &&
                overlays.none { it.layer == AnnotationLayer.Constellation }
            ) {
                Toast.makeText(
                    context,
                    context.getString(R.string.projection_cant_represent_fov),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // Zielobjekt-Auflösung (optionaler ASTAP-Startpunkt): Name -> RA/Dec aus Katalog.
    val targetSkyPoint = remember(targetObjectQuery, deepSkyObjects, skyCatalogStars) {
        resolveTargetObject(targetObjectQuery, deepSkyObjects, skyCatalogStars)
    }
    val targetObjectResolved: Boolean? = when {
        targetObjectQuery.isBlank() -> null
        else -> targetSkyPoint != null
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (editorSession.replaceImage(uri)) {
                if (astapSolveJob?.isActive == true) {
                    AppDiagnostics.record("astap_solve_cancelled_image_changed")
                    astapSolveJob?.cancel()
                }
                astapSolveJob = null
                astapOperationState = AstapOperationState.Idle
                astapExifFieldOfView = null
                lastSolvedWcs = null
                originalSolvedWcs = null
                // Neues Bild -> Feinjustierungs-Seed des VORBILDS verwerfen. Sonst zeigt die Feinjustierung
                // die Referenzsterne der letzten Lösung (z. B. Sommer-Milchstrasse) auf einem ganz anderen
                // Himmelsausschnitt (Winter/Orion). fisheyeBaseFit reset schon via remember(originalSolvedWcs,bitmap).
                fisheyeAlignSeed = null
                // Kalibrierungs-Anzeigefelder des VORBILDS verwerfen (sonst zeigt die Kalibrierungskarte
                // kurz einen stehengebliebenen RMS/Kind vom vorherigen Bild, bevor neu gelöst wird).
                alignProjectionKind = PanoProjectionKind.Fisheye
                alignProjectionRms = null
                customAlignFit = null
                customAlignRms = null
                customAlignKind = null
                richMeshFit = null
                richMeshRms = null
                richMeshAutoEligible = false
                richMeshRejectReason = null
                sparseMeshFit = null
                sparseMeshRms = null
                projectionCandidateRms = emptyMap()
                postSolveCalibration = null
                calibrationAsyncJob?.cancel()
                calibrationAsyncJob = null
            }
            AppDiagnostics.record("image_selected mime=${context.contentResolver.getType(uri) ?: "unknown"}")
        } else {
            AppDiagnostics.record("image_picker_cancelled")
        }
    }
    val exportSolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = SolutionExport.encode(
                    imageWidth = bitmap?.width ?: 0,
                    imageHeight = bitmap?.height ?: 0,
                    lastSolvedWcs = lastSolvedWcs,
                    originalSolvedWcs = originalSolvedWcs,
                    singleSolveWcs = singleSolveWcs,
                    tiles = solveTiles.toList(),
                )
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("openOutputStream lieferte null")
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.toast_solution_exported), Toast.LENGTH_SHORT).show()
                // overlaysStored=0 ist bewusst FEST, kein Zähler -- s. SolutionExport-Klassenkommentar
                // "Architektur-Trennung": eine Solution enthält nie automatisch erzeugte Overlays.
                AppDiagnostics.record(
                    "solution_exported tiles=${solveTiles.size} overlaysStored=0 " +
                        "lastSolvedWcsExported=${lastSolvedWcs?.isExportable() == true} " +
                        "projectionKind=$alignProjectionKind " +
                        "meshExported=${lastSolvedWcs.containsCorrectedProjection()}",
                )
            }.onFailure { e ->
                Toast.makeText(context, context.getString(R.string.toast_solution_export_failed), Toast.LENGTH_LONG).show()
                AppDiagnostics.record("solution_export_failed error=${e.message}")
            }
        } else {
            AppDiagnostics.record("solution_export_file_picker_cancelled")
        }
        activePanel = null
    }
    // Import wirkt DIREKT auf das bereits geöffnete Foto -- kein zweiter Foto-Auswahl-Dialog (Nutzer-
    // Korrektur 2026-08-27: das Foto ist beim Aufruf von "Lösung laden" bereits ausgewählt/geladen,
    // typischerweise weil die App das beim Start ohnehin verlangt; ein zusätzlicher Auswahl-Schritt war
    // unnötig und verwirrend). Die JSON-Datei trägt ohnehin keine Bildpixel, nur Metadaten+Lösung --
    // bewusst kein Bild-Hash/keine automatische Foto-Erkennung, der Nutzer ist selbst dafür
    // verantwortlich, das richtige Foto geöffnet zu haben.
    val importSolutionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: error("openInputStream lieferte null")
                SolutionExport.decode(text)
            }.onSuccess { parsed ->
                lastSolvedWcs = parsed.lastSolvedWcs
                originalSolvedWcs = parsed.originalSolvedWcs
                singleSolveWcs = parsed.singleSolveWcs
                solveTiles.clear()
                solveTiles.addAll(parsed.tiles)
                // Architektur-Trennung (Nutzer-Vorgabe 2026-08-30): die Solution trägt KEINE Overlays
                // mehr -- alle aktivierten automatischen Annotationen werden hier mit der AKTUELLEN
                // Build-Logik/den aktuellen Katalogen frisch erzeugt, statt einen alten Overlay-Snapshot
                // wiederherzustellen (derselbe Aufruf-Dreiklang wie nach einem frischen Solve/einer
                // Kalibrierungs-Wiederherstellung, s. applyCalibrationSnapshot). recordUndo=false: das
                // Laden selbst erzeugt keinen Undo-Schritt. Bereits vorhandene MANUELLE Overlays
                // (layer==null) bleiben unangetastet -- jede Sync-Funktion räumt nur ihre eigene Kategorie.
                syncConstellationLayer(recordUndo = false)
                syncDeepSkyLayer(recordUndo = false)
                syncStarLayer(recordUndo = false)
                activePanel = null
                Toast.makeText(context, context.getString(R.string.toast_solution_loaded), Toast.LENGTH_SHORT).show()
                AppDiagnostics.record(
                    "solution_imported tiles=${parsed.tiles.size} " +
                        "legacyOverlayPayloadIgnored=${parsed.legacyOverlayPayloadIgnored} autoAnnotationsRecomputed=true " +
                        "meshRestored=${parsed.lastSolvedWcs.containsCorrectedProjection()} " +
                        "baselineKind=${parsed.lastSolvedWcs.meshBaselineKindOrNull() ?: "n/a"} " +
                        "horizontalPeriodPx=${
                            if (parsed.lastSolvedWcs.hasHorizontalPeriod()) "present" else "absent"
                        }",
                )
            }.onFailure { e ->
                Toast.makeText(context, context.getString(R.string.toast_solution_import_failed), Toast.LENGTH_LONG).show()
                AppDiagnostics.record("solution_import_failed error=${e.message}")
            }
        } else {
            AppDiagnostics.record("solution_import_file_picker_cancelled")
        }
    }

    fun updateSelectedOverlay(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        val id = selectedOverlayId ?: return
        val index = overlays.indexOfFirst { it.id == id }
        if (index >= 0) overlays[index] = transform(overlays[index])
    }

    // Sternbild-Stil auf NUR das ausgewählte Sternbild anwenden (Long-Press-Editor) -- transform
    // erhält ein bereits als Constellation bekanntes Overlay. Globale Anwendung auf ALLE Sternbilder
    // läuft seit dem Nutzerwunsch 2026-08-20 ausschließlich über das Katalog-bearbeiten-Menü
    // (s. [applyConstellationStyleToAll]), nicht mehr über einen Umschalter in diesem Dialog --
    // klare Trennung "Long-Press = nur dieses eine" vs. "Katalog bearbeiten = global".
    fun applyConstellationStyle(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        updateSelectedOverlay { if (it.kind == OverlayKind.Constellation) transform(it) else it }
    }

    // Gegenstück zu [applyConstellationStyle]: wirkt IMMER auf ALLE Sternbild-Overlays. Nur vom
    // Katalog-bearbeiten-Menü aus aufgerufen.
    fun applyConstellationStyleToAll(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        for (i in overlays.indices) {
            if (overlays[i].kind == OverlayKind.Constellation) overlays[i] = transform(overlays[i])
        }
    }

    // Analoge "auf alle anwenden"-Helfer fürs globale Farben-Menü (Nutzerbefund 2026-08-20: eine
    // global geänderte Farbe wirkte bislang NUR auf künftig neu platzierte Objekte, nicht auf bereits
    // platzierte -- "verändert sich die Farbe bei der Ansicht auf dem Bild nicht"). `layer == null`
    // grenzt gegen DSO-Marker ab (die haben layer=DeepSky UND ihr eigenes Farbsystem, s. dsoStyleForType/
    // dsoColorOverrides); `reticle == null` grenzt Formen gegen Kometenmarker ab (beide sind
    // OverlayKind.Ellipse, aber zwei getrennte globale Kategorien im Farben-Menü).
    fun applyShapeStyleToAll(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        for (i in overlays.indices) {
            val ov = overlays[i]
            val isShape = ov.layer == null && ov.reticle == null &&
                (ov.kind == OverlayKind.Ellipse || ov.kind == OverlayKind.Rectangle || ov.kind == OverlayKind.Freehand)
            if (isShape) overlays[i] = transform(ov)
        }
    }

    fun applyReticleStyleToAll(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        for (i in overlays.indices) {
            if (overlays[i].reticle != null) overlays[i] = transform(overlays[i])
        }
    }

    fun applyTextStyleToAll(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        for (i in overlays.indices) {
            if (overlays[i].kind == OverlayKind.Text) overlays[i] = transform(overlays[i])
        }
    }

    // Performance-Fix 2026-09-02 (Objekte-Menü-Slider froren ein): Gegenstück zu applyConstellation-/
    // Shape-/Reticle-/TextStyleToAll speziell für die Deep-Sky-Ebene -- reine Restyle-Anwendung auf
    // bereits vorhandene DSO-Overlays, OHNE die teure createDeepSkyOverlays-Pipeline (Katalogfilter/
    // Best-Known-Matching/WCS-Größenprojektion/Kollisions-Platzierung über bis zu ~91k Objekte) erneut
    // auszulösen. Nur für Parameter zulässig, die 1:1 (ohne abgeleitete Logik) als Feld auf
    // AnnotationOverlay landen -- s. AstapOverlayMapper.createDeepSkyOverlays: strokeWidth/nameTextSize/
    // font/nameOpacityLinked sind das (direkte Parameter-Durchreichung), opacity NICHT (kommt aus
    // opacityFor(group), s. applyDeepSkyOpacityLive unten) und showName ERST RECHT NICHT (echtes
    // Kollisionsergebnis, showThisName) -- deshalb bleiben Deckkraft-Gruppierung und der "Namen zeigen"-
    // Schalter bewusst auf dem vollen, jetzt aber hintergrund-berechneten Pfad (s. requestDeepSkySync).
    fun applyDeepSkyStyleToAll(transform: (AnnotationOverlay) -> AnnotationOverlay) {
        for (i in overlays.indices) {
            if (overlays[i].layer == AnnotationLayer.DeepSky) overlays[i] = transform(overlays[i])
        }
    }

    // Katalogzugehörigkeit je Objekt-Id (aus dem ohnehin schon vorberechneten deepSkyCatalogGroups
    // abgeleitet, s. dort) -- braucht applyDeepSkyOpacityLive, um beim NICHT-"gilt für alle Kataloge"-Fall
    // nur die Overlays DES GERADE GEWÄHLTEN Katalogs live umzufärben, ohne dafür deepSkyCatalogGroups
    // (Schlüssel: DeepSkyObject) gegen AnnotationOverlay.sourceId (ein reiner String) durchsuchen zu
    // müssen. Nur neu berechnet, wenn sich deepSkyCatalogGroups selbst ändert (Katalog-Laden) -- NICHT
    // bei jedem Slider-Tick.
    val deepSkyGroupById = remember(deepSkyCatalogGroups) {
        deepSkyCatalogGroups.entries.associate { (obj, group) -> obj.id to group }
    }
    // group=null -> "gilt für alle Kataloge" (uniform); sonst nur Overlays DIESES Katalogs (Overlays ohne
    // bekannte Gruppenzuordnung -- sollte praktisch nicht vorkommen -- bleiben unangetastet, kein Absturz).
    fun applyDeepSkyOpacityLive(value: Float, group: DeepSkyCatalogGroup?) {
        applyDeepSkyStyleToAll { ov ->
            val ovGroup = ov.sourceId?.let { deepSkyGroupById[it] }
            if (group != null && ovGroup != group) ov else ov.copy(opacity = value)
        }
    }

    // Performance-Fix 2026-09-02 (MethodTooLargeException-Nachtrag): reagiert REAKTIV auf die reinen
    // Zustandsschreibungen der Style-only-DSO-Regler (s. DeepSkyAnnotationSection) statt dass diese
    // Regler eigene Callback-Parameter durch EditorPanelSheet -> AnnotatePanel -> DeepSkyAnnotationSection
    // durchreichen -- genau dieses Muster (neue () -> Unit-Parameter an der bereits übervollen
    // EditorPanelSheet-Aufrufstelle) hatte hier schon zweimal zuvor einen MethodTooLargeException-Build-
    // fehler ausgelöst (s. Memory project_methodtoolarge_editorpanelsheet), ein dritter Versuch mit
    // genau demselben Muster brach den Build erneut. Jeder Effekt ist klein/eigenständig (kein
    // gemeinsamer Riesen-Lambda mehr) und feuert nur bei ECHTER Wertänderung -- inkl. einmalig beim
    // ersten Komponieren (dann auf einem noch leeren overlays, folgenlos billig).
    LaunchedEffect(annotate.dsoGlobalOpacity) {
        if (annotate.dsoOpacityAppliesToAll) applyDeepSkyOpacityLive(annotate.dsoGlobalOpacity, null)
    }
    LaunchedEffect(annotate.dsoSelectedCatalog, annotate.dsoOpacityOf(annotate.dsoSelectedCatalog)) {
        if (!annotate.dsoOpacityAppliesToAll) {
            applyDeepSkyOpacityLive(annotate.dsoOpacityOf(annotate.dsoSelectedCatalog), annotate.dsoSelectedCatalog)
        }
    }
    LaunchedEffect(annotate.deepSkyStrokeWidth, annotate.deepSkyStrokeWidthEnabled) {
        applyDeepSkyStyleToAll { ov ->
            ov.copy(strokeWidth = if (annotate.deepSkyStrokeWidthEnabled) annotate.deepSkyStrokeWidth else 2.5f)
        }
    }
    LaunchedEffect(annotate.deepSkyNameSize) {
        applyDeepSkyStyleToAll { ov -> ov.copy(nameTextSize = annotate.deepSkyNameSize) }
    }
    LaunchedEffect(annotate.deepSkyFont) {
        applyDeepSkyStyleToAll { ov -> ov.copy(font = annotate.deepSkyFont) }
    }
    LaunchedEffect(annotate.deepSkyNameOpacityLinked) {
        applyDeepSkyStyleToAll { ov -> ov.copy(nameOpacityLinked = annotate.deepSkyNameOpacityLinked) }
    }

    // Bewusstes Platzieren scharf schalten (Einmal-Modus): Sheet schließen, damit man ins
    // Bild tippen kann; der nächste Tipp setzt genau ein Objekt und schaltet wieder ab.
    fun armPlacement(tool: EditorTool) {
        // Tap-to-place scharf: der pink pulsierende Bildrand + zentrale Hinweis-Pille im Editor
        // signalisieren, dass jetzt die Position angetippt werden soll (ersetzt den alten Toast).
        pendingPlacement = tool
        activePanel = null
    }

    // Langdruck-Button "Weiterzeichnen" auf einem Freihand-Overlay: dessen aktuelle Segmente
    // (in absoluten Bild-Koordinaten, inkl. bereits angewandter Drehung) in die Zeichnen-Sitzung
    // laden und den Stift erneut aktivieren -- der Nutzer kann dann weitermalen ODER radieren.
    fun continueEditingFreehand(overlay: AnnotationOverlay) {
        editingFreehandSource = overlay
        editorSession.drawSegmentsState.value = OverlayGeometry.denormalizedFreehandPoints(overlay)
        armPlacement(EditorTool.Draw)
    }

    fun updateD3CatalogSettings(settings: D3CatalogSettings) {
        d3CatalogSettings = settings
        preferences.edit()
            .putBoolean("d3_show_stars", settings.showStars)
            .putFloat("d3_star_magnitude_limit", settings.starMagnitudeLimit)
            .putBoolean("d3_show_deep_sky_objects", settings.showDeepSkyObjects)
            .putFloat("d3_deep_sky_magnitude_limit", settings.deepSkyMagnitudeLimit)
            .putBoolean("d3_show_milky_way", settings.showMilkyWay)
            .putBoolean("d3_show_background_constellations", settings.showBackgroundConstellations)
            .putBoolean("d3_show_constellation_star_points", settings.showConstellationStarPoints)
            .putBoolean("d3_show_constellation_names", settings.showConstellationNames)
            .apply()
    }

    // War frueher "clearAstapPreview" -- die Vorschau-Overlays/-Sterne, die hier geleert wurden, waren
    // in der gesamten Codebasis nie befuellt (bereits toter Zustand, unabhaengig von ASTAP) und sind
    // mit der ASTAP-Entfernung 2026-08-20 ganz raus. Verbleibende, echte Aufgabe: den "gelöst"-Zustand
    // zuruecksetzen, wenn der Nutzer danach weiterarbeitet.
    fun clearSolvedIndicator() {
        if (astapOperationState is AstapOperationState.Solved) {
            astapOperationState = AstapOperationState.Idle
        }
    }

    // Unit 6 (2026-09-02): reiner Wrapper -- die eigentliche Logik ist gemeinsam mit dem Notification-
    // Abbrechen-Zweig in resetSolveStateOnCancel() (s. oben, vor solveAllTiles) zusammengefasst, damit
    // beide Wege exakt denselben, vollständigen Reset durchlaufen. Name/Aufrufstellen unverändert.
    fun cancelAstapSolve() {
        resetSolveStateOnCancel()
    }

    // Unit 6 (2026-09-02): NEUER Einstiegspunkt für beide "Einzelbild lösen"-Buttons -- zeigt ZUERST
    // den Positions-Hinweis-Dialog (wiederverwendet ManualHintDialog/findStarByName, dieselbe Mechanik
    // wie der Kachel-Hinweis, s. singleSolveHintPoint-Deklaration). Der Dialog selbst entscheidet beim
    // Schließen (egal ob per Stern-Auswahl, "Entfernen" oder "Abbrechen"), ob startSingleImageSolve()
    // MIT oder OHNE Hinweis (blind) aufgerufen wird -- ein Schließen des Dialogs bricht NIEMALS den
    // Solve selbst ab, das ist eine komplett eigene, spätere Aktion (Abbrechen-Button/-Notification).
    fun requestSingleImageSolve() {
        AppDiagnostics.record("single_solve_hint_dialog_opened")
        showSingleSolveHintDialog = true
    }

    // Einzelbild-Solve, aus dem Compose-Argument herausgehoben (0.14.1): so kann ihn auch die
    // Kalibrierung ("Einzelbild lösen") aufrufen. Body wortgleich; currentBitmap wie zuvor = bitmap.
    fun startSingleImageSolve() {
        val currentBitmap = bitmap
                    val sourceUri = imageUri
                    val sourceBitmap = currentBitmap
                    if (sourceUri == null || sourceBitmap == null) {
                        astapOperationState = AstapOperationState.Failure(
                            context.getString(R.string.toast_load_image_first),
                        )
                    } else if (astapSolverChoice == AstapSolverChoice.NovaOnline && novaApiKey.isBlank()) {
                        astapOperationState = AstapOperationState.Failure(
                            context.getString(R.string.nova_api_key_required_first),
                        )
                    } else {
                        // Fisheye löst einen ~25deg-Mittenpatch -> Weitfeld-DB, nicht das
                        // (für Fisheye bedeutungslose) Einzelbild-FOV.
                        run {
                            // Defensiver Abbruch eines etwaigen (unter normaler UI-Sperrung eigentlich
                            // unerreichbaren) noch laufenden Alt-Jobs -- Generation mit hochzählen (Unit
                            // 6), damit dieser Alt-Job sich beim Aufwachen als veraltet erkennt.
                            solveGeneration++
                            astapSolveJob?.cancel()
                            // Foreground-Service hält den Prozess auf Vordergrund-Priorität,
                            // damit Android dem (minutenlangen) Solve im Hintergrund nicht
                            // Netz/CPU kappt. POST_NOTIFICATIONS ab Android 13 anfragen.
                            if (
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            SolveController.begin(
                                title = context.getString(R.string.status_solving),
                                text = context.getString(R.string.status_preparing),
                                onCancel = { cancelAstapSolve() },
                            )
                            SolveForegroundService.start(context)
                            // Vor-Zustand sichern -> auch die Einzelbild-Lösung ist ein globaler
                            // Undo/Redo-Schritt (vorher komplett ungeschützt).
                            snapshotCalibrationForUndo()
                            astapSolveJob = scope.launch {
                                // Eigene Generation dieser Solve-Coroutine (Unit 6) -- s. solveGeneration.
                                val myGeneration = solveGeneration
                                fun isStaleSolve() = solveGeneration != myGeneration
                                // Liveticker/Log auch für das Einzelbild (wie bei den Kacheln).
                                solveTicker.clear()
                                tick(
                                    context.getString(
                                        R.string.single_image_solve_started,
                                        astapSolverChoice.name,
                                    ),
                                )
                                // FOV ist eine Winkelgröße: Sie muss aus den Originalmassen in
                                // nativen Sensorpixeln berechnet werden, auch wenn das Bitmap
                                // für die Anzeige herunterskaliert wurde. (sourceBitmap != null
                                // impliziert loadedImage != null - der Compiler castet smart.)
                                val originalImageWidth = loadedImage.originalWidth
                                val originalImageHeight = loadedImage.originalHeight
                                val fovCandidates = if (astapCaptureSettings.fovSource == AstapFovSource.Manual) {
                                    AstapFovCandidates(
                                        primaryDegrees = astapFieldOfView,
                                        alternativeDegrees = null,
                                    )
                                } else {
                                    AstapFieldOfViewCalculator.candidates(
                                        imageWidthPx = originalImageWidth,
                                        imageHeightPx = originalImageHeight,
                                        settings = astapCaptureSettings,
                                    )
                                }
                                try {
                                    // Maskierte Bereiche (Vordergrund) werden vor dem Solven
                                    // geschwärzt; Anzeige und Overlays nutzen das Original.
                                    val mask = editorSession.solveMaskState.value
                                    val solveBitmap = withContext(Dispatchers.Default) {
                                        if (mask != null) SolveMask.applyTo(sourceBitmap, mask) else sourceBitmap
                                    }
                                    val wcs: WcsSolutionLike
                                    val solvedStars: List<DetectedStar>
                                    if (astapCaptureSettings.captureType == AstapCaptureType.Fisheye &&
                                        (effectiveSolverChoice == AstapSolverChoice.NovaOnline ||
                                            effectiveSolverChoice == AstapSolverChoice.LocalAstrometry)
                                    ) {
                                        // CROP-DIRECT (datenbelegt): astrometry.net löst den zentralen
                                        // Bildausschnitt exakt als TAN+SIP (Gegenprobe bestätigt: Skala/
                                        // Parität korrekt). Statt ihn verlustbehaftet in ein Fisheye-Modell
                                        // zu wandeln (Plateau ~42'), nutzen wir die ECHTE Crop-WCS direkt,
                                        // nur um den Crop-Offset ins Vollbild versetzt (Einzel-Kachel-Mosaik).
                                        // -> Bildmitte sitzt exakt; Ränder außerhalb des Crops bleiben unbeschriftet.
                                        val startStatus = context.getString(R.string.fisheye_solving_center_crop)
                                        astapOperationState = AstapOperationState.SolvingOnline(startStatus)
                                        SolveController.update(startStatus)
                                        val frac = PanoramaSolver.CENTER_CROP_FRACTION
                                        val cw = (solveBitmap.width * frac).roundToInt().coerceIn(64, solveBitmap.width)
                                        val ch = (solveBitmap.height * frac).roundToInt().coerceIn(64, solveBitmap.height)
                                        val cropX = (solveBitmap.width - cw) / 2
                                        val cropY = (solveBitmap.height - ch) / 2
                                        val crop = withContext(Dispatchers.Default) {
                                            Bitmap.createBitmap(solveBitmap, cropX, cropY, cw, ch)
                                        }
                                        AppDiagnostics.record(
                                            "fisheye_cropdirect_started crop=${cw}x${ch} masked=${mask != null}",
                                        )
                                        val cropWcs: WcsSolution = if (
                                            effectiveSolverChoice == AstapSolverChoice.LocalAstrometry
                                        ) {
                                            localSolver.solve(bitmap = crop) { status ->
                                                withContext(Dispatchers.Main.immediate) {
                                                    astapOperationState = AstapOperationState.SolvingOnline(status)
                                                    SolveController.update(status)
                                                }
                                            }.wcs
                                        } else {
                                            novaSolver.solve(
                                                bitmap = crop,
                                                apiKey = novaApiKey,
                                                fovWidthLowerDeg = null,
                                                fovWidthUpperDeg = null,
                                            ) { status ->
                                                withContext(Dispatchers.Main.immediate) {
                                                    astapOperationState = AstapOperationState.SolvingOnline(status)
                                                    SolveController.update(status)
                                                }
                                            }.wcs
                                        }
                                        crop.recycle()
                                        AppDiagnostics.record(
                                            "fisheye_cropdirect_succeeded solver=${effectiveSolverChoice.name} crop=${cw}x${ch}",
                                        )
                                        lastPanoramaDebug = null
                                        wcs = MosaicWcsSolution(
                                            listOf(TileWcs(cropWcs, cropX, cropY, cw, ch)),
                                        )
                                        solvedStars = emptyList()
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.fisheye_center_solved_crop),
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    } else {
                                        // Nutzerwunsch 2026-08-20 (ASTAP entfernt): der bisherige
                                        // "Fisheye + ASTAP"-Patch-/Reproject-Weg ist raus -- Fisheye lief
                                        // für Nova/LocalAstrometry ohnehin schon über den Crop-Direct-Zweig
                                        // oben, ASTAP war die einzige verbleibende Nutzung dieses Pfads.
                                        val local = effectiveSolverChoice == AstapSolverChoice.LocalAstrometry
                                        val connectingStatus = if (local) {
                                            context.getString(R.string.status_solving_locally_short)
                                        } else {
                                            context.getString(R.string.status_connecting)
                                        }
                                        astapOperationState =
                                            AstapOperationState.SolvingOnline(connectingStatus)
                                        SolveController.update(connectingStatus)
                                        // Immer blind lösen (Skala) - exakt wie die Website. Ein Skala-Hinweis
                                        // (aus altem Manual-FOV oder Geräteprofil) ist bei Crops/Fisheye-
                                        // Ausschnitten meist falsch und blockiert das Lösen. Positions-Hinweis
                                        // (Zielobjekt) wird durchgereicht; ASTAP offline nutzt das FOV weiterhin.
                                        // Unit 6 (2026-09-02): zwei unabhängige Hinweis-Quellen kombiniert --
                                        // singleSolveHintPoint (neuer Hinweis-Dialog, wiederverwendet die
                                        // Kachel-Mechanik) hat Vorrang, targetSkyPoint (bestehendes
                                        // Zielobjekt-Textfeld in SolverConfigSection) bleibt als Rückfall
                                        // voll funktionsfähig, falls dort weiterhin genutzt.
                                        val effectiveHintPoint = singleSolveHintPoint ?: targetSkyPoint
                                        AppDiagnostics.record(
                                            "single_solve_started solver=${effectiveSolverChoice.name} " +
                                                "posHint=${effectiveHintPoint != null} masked=${mask != null}",
                                        )
                                        val singleWcs: WcsSolution = if (local) {
                                            localSolver.solve(
                                                bitmap = solveBitmap,
                                                // Manueller Positions-Hinweis: Hinweis-Dialog ODER eingetipptes
                                                // Zielobjekt (Name -> RA/Dec).
                                                centerRaDeg = effectiveHintPoint?.raDegrees?.toDouble(),
                                                centerDecDeg = effectiveHintPoint?.decDegrees?.toDouble(),
                                                radiusDeg = effectiveHintPoint?.let { astapFieldOfView.toDouble().coerceIn(5.0, 90.0) },
                                            ) { status ->
                                                withContext(Dispatchers.Main.immediate) {
                                                    astapOperationState =
                                                        AstapOperationState.SolvingOnline(status)
                                                    SolveController.update(status)
                                                }
                                            }.wcs
                                        } else {
                                            novaSolver.solve(
                                                bitmap = solveBitmap,
                                                apiKey = novaApiKey,
                                                fovWidthLowerDeg = null,
                                                fovWidthUpperDeg = null,
                                                // Manueller Positions-Hinweis: Hinweis-Dialog ODER eingetipptes
                                                // Zielobjekt (Name -> RA/Dec). Skala bleibt blind; nur die
                                                // Suche wird auf die Umgebung eingegrenzt.
                                                centerRaDeg = effectiveHintPoint?.raDegrees?.toDouble(),
                                                centerDecDeg = effectiveHintPoint?.decDegrees?.toDouble(),
                                                radiusDeg = effectiveHintPoint?.let { astapFieldOfView.toDouble().coerceIn(5.0, 90.0) },
                                            ) { status ->
                                                withContext(Dispatchers.Main.immediate) {
                                                    astapOperationState =
                                                        AstapOperationState.SolvingOnline(status)
                                                    SolveController.update(status)
                                                }
                                            }.wcs
                                        }
                                        AppDiagnostics.record(
                                            "single_solve_succeeded solver=${effectiveSolverChoice.name}",
                                        )
                                        wcs = singleWcs
                                        solvedStars = emptyList()
                                    }
                                    // Unit 6 (2026-09-02): dieser Solve lief TROTZ eines inzwischen erfolgten
                                    // Abbruchs erfolgreich durch (Race, z. B. Abbruch genau beim letzten
                                    // ensureActive()-Fenster) -> Ergebnis NICHT mehr übernehmen (kein WCS-
                                    // Setzen, keine Sternbild-Synchronisierung, kein Solved-Endzustand).
                                    if (isStaleSolve()) {
                                        AppDiagnostics.record("single_solve_discarded_stale_generation")
                                        return@launch
                                    }
                                    // Konsistent mit solveAllTiles()/reprojectPanorama() (Nutzer-Entscheidung
                                    // 2026-07-30): die Einzellösung wird direkt angezeigt, keine Kachel-
                                    // Priorisierung mehr. Bestehende Kacheln (lastSolvedTileWcs) bleiben
                                    // trotzdem unangetastet als Datenquelle bestehen.
                                    lastSolvedWcs = wcs
                                    originalSolvedWcs = wcs // Original-Astrometrie sichern (nie überschrieben).
                                    // Einzellösung als PERSISTENTE Basis merken: bleibt erhalten, wenn später
                                    // Kacheln gelöst werden (Mosaik-Fallback + Positions-Hinweis + Status).
                                    singleSolveWcs = wcs
                                    singleImageSolved = true
                                    // Feinjustier-Seed aus DER ECHTEN Lösung bauen (Rectilinear erlaubt) statt
                                    // einer falschen Fisheye-Näherung am geschätzten FOV -> Referenzsterne sitzen.
                                    val seedCalib = wcs?.let { w ->
                                        withContext(Dispatchers.Default) {
                                            panoramaCalibrationFromWcs(
                                                w, solveBitmap.width, solveBitmap.height,
                                                allowedProjectionKinds(panoProjectionChoice),
                                            )
                                        }
                                    }
                                    if (seedCalib != null) {
                                        // Gewickelt (equidistantSeedFrom) statt roh -> derselbe, garantiert nie
                                        // zurückfaltende Mechanismus wie FOV-Vorschau/Feinjustierung.
                                        fisheyeAlignSeed = FisheyeRefiner.equidistantSeedFrom(
                                            seedCalib.solution, solveBitmap.width, solveBitmap.height, fisheyeFovLongDeg,
                                        )
                                        fisheyeBaseFit = seedCalib.solution
                                        alignProjectionKind = seedCalib.kind
                                        alignProjectionRms = seedCalib.rms
                                    } else {
                                        fisheyeAlignSeed = null
                                    }
                                    // Frischer "direkt nach dem Solve"-Wiederherstellungspunkt (alle Felder
                                    // oben sind jetzt final für diese Einzellösung gesetzt).
                                    postSolveCalibration = snapshotCalibration()
                                    // FOV-Slider automatisch auf das gelöste Bildfeld stellen (Nutzerwunsch).
                                    solvedFovLongDeg(wcs, solveBitmap.width, solveBitmap.height)?.let { fovDeg ->
                                        val clamped = fovDeg.coerceIn(1f, 360f)
                                        fisheyeFovLongDeg = clamped
                                        preferences.edit().putFloat("fisheye_fov_long_deg", clamped).apply()
                                    }
                                    detectedStars = solvedStars
                                    showDetectedStars = solvedStars.isNotEmpty()
                                    // Sternbilder werden nach dem Lösen standardmäßig
                                    // gezeichnet (kein Apply/Discard mehr).
                                    annotate.constellationsEnabled = true
                                    // recordUndo=false: der Vor-Zustand wurde bereits synchron vor Solve-Start
                                    // gesichert (snapshotCalibrationForUndo() oben) -> kein zusätzlicher Schritt.
                                    syncConstellationLayer(recordUndo = false)
                                    val constellationCount = overlays.count {
                                        it.layer == AnnotationLayer.Constellation
                                    }
                                    astapOperationState = AstapOperationState.Solved(
                                        constellationCount = constellationCount,
                                        starCount = solvedStars.size,
                                    )
                                    tick(
                                        context.getString(R.string.single_image_solved_tick, constellationCount),
                                    )
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.solution_ready_adjust_annotate,
                                            context.getString(R.string.panel_annotate),
                                        ),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Throwable) {
                                    AppDiagnostics.record(
                                        "astap_solve_failed solver=${astapSolverChoice.name} " +
                                            "type=${error.javaClass.name} message=${error.message}",
                                    )
                                    // Unit 6: ein Fehler aus einem bereits abgebrochenen Solve (z. B. ein
                                    // Netzfehler, der beim Cancel-Zeitpunkt schon "in Flight" war) darf den
                                    // bereits gesetzten Cancelled-Zustand NICHT nachträglich als "Solve
                                    // fehlgeschlagen" überschreiben.
                                    if (!isStaleSolve()) {
                                        tick(
                                            context.getString(
                                                R.string.single_image_failed,
                                                error.message ?: context.getString(R.string.error_unknown_short),
                                            ),
                                        )
                                        astapOperationState = AstapOperationState.Failure(
                                            error.message ?: context.getString(R.string.error_no_solution),
                                        )
                                    }
                                } finally {
                                    // Unit 6: nur die AKTUELLE (nicht veraltete) Solve-Coroutine räumt
                                    // Job/Foreground-Service auf -- sonst könnte eine spät unwindende Alt-
                                    // Coroutine einen inzwischen neu gestarteten Solve fälschlich beenden.
                                    if (!isStaleSolve()) {
                                        astapSolveJob = null
                                        // Solve beendet -> Foreground-Service darf sich stoppen. success
                                        // nur bei echtem Solved-Endzustand, s. Kommentar in solveAllTiles().
                                        SolveController.finish(success = astapOperationState is AstapOperationState.Solved)
                                    }
                                }
                            }
                        }
                    }
    }

    fun shareFullAppDiagnostic() {
        val sourceBitmap = bitmap
        // Dieselbe Berechnung + dieselben Bedingungen wie die Live-Anzeige (graticuleGeometry/
        // milkyWayGeometry weiter unten in EditorCanvas) -- hier bewusst NEU berechnet statt die dortige
        // remember()-Instanz zu teilen (die liegt in einem anderen, verschachtelten Scope) -- beide
        // Renderer sind reine, seiteneffektfreie Funktionen (wie ExportRenderer sie auch nutzt), ein
        // zweiter Aufruf für die Diagnose ist unkritisch. Ermöglicht, Gradnetz UND Milchstraße pixelgenau
        // aus der Diagnose-Textdatei nachzurechnen, nicht nur Overlays (Nutzerwunsch 2026-08-27).
        val diagnosticWcs = lastSolvedWcs
        val graticuleForDiagnostic = if (!tinySkyActive && annotate.gridEnabled && diagnosticWcs != null && sourceBitmap != null) {
            GraticuleRenderer.compute(diagnosticWcs, sourceBitmap.width, sourceBitmap.height, annotate.gridDensity)
        } else {
            null
        }
        val milkyWayForDiagnostic = if (!tinySkyActive && annotate.milkyWayEnabled && diagnosticWcs != null && sourceBitmap != null) {
            MilkyWayRenderer.compute(milkyWayLayers, diagnosticWcs, sourceBitmap.width, sourceBitmap.height)
        } else {
            null
        }
        runCatching {
            AppDiagnosticExporter.createShareUri(
                context = context,
                snapshot = AppDiagnosticSnapshot(
                    imageWidth = sourceBitmap?.width,
                    imageHeight = sourceBitmap?.height,
                    imageConfig = sourceBitmap?.config?.name,
                    imageMimeType = imageUri?.let { context.contentResolver.getType(it) },
                    selectedTool = selectedTool.name,
                    activePanel = activePanel?.name,
                    selectedOverlayId = selectedOverlayId,
                    overlays = overlays.toList(),
                    detectedStars = detectedStars,
                    showDetectedStars = showDetectedStars,
                    showConstellationAnchors = showConstellationAnchors,
                    starDetectionSensitivity = starDetectionSensitivity,
                    selectedConstellationId = selectedConstellation.id,
                    constellationCatalogCount = catalog.size,
                    skyCatalogStarCount = skyCatalogStars.size,
                    referenceCatalogStarCount = referenceCatalogStars.size,
                    deepSkyObjectCount = deepSkyObjects.size,
                    milkyWayLayerCount = milkyWayLayers.size,
                    graticule = graticuleForDiagnostic,
                    milkyWay = milkyWayForDiagnostic,
                    d3Settings = mapOf(
                        "showStars" to d3CatalogSettings.showStars,
                        "starMagnitudeLimit" to d3CatalogSettings.starMagnitudeLimit,
                        "showDeepSkyObjects" to d3CatalogSettings.showDeepSkyObjects,
                        "deepSkyMagnitudeLimit" to d3CatalogSettings.deepSkyMagnitudeLimit,
                        "showMilkyWay" to d3CatalogSettings.showMilkyWay,
                        "showBackgroundConstellations" to d3CatalogSettings.showBackgroundConstellations,
                        "showConstellationStarPoints" to d3CatalogSettings.showConstellationStarPoints,
                        "showConstellationNames" to d3CatalogSettings.showConstellationNames,
                    ),
                ),
            )
        }.onSuccess { uri ->
            context.shareTextFile(uri)
        }.onFailure { error ->
            AppDiagnostics.record(
                "app_diagnostic_export_failed type=${error.javaClass.name} message=${error.message}",
            )
            Toast.makeText(
                context,
                error.message ?: context.getString(R.string.app_diagnostic_creation_failed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(loadedImage) {
        loadedImage?.let {
            AppDiagnostics.record(
                "bitmap_loaded dimensions=${it.bitmap.width}x${it.bitmap.height} " +
                    "original=${it.originalWidth}x${it.originalHeight} config=${it.bitmap.config}",
            )
        }
    }

    LaunchedEffect(imageUri, loadedImage) {
        val uri = imageUri ?: return@LaunchedEffect
        val sourceImage = loadedImage ?: return@LaunchedEffect
        astapExifFieldOfView = null
        astapExifFieldOfView = withContext(Dispatchers.IO) {
            AstapFovEstimator.estimateDetailed(
                context = context,
                uri = uri,
                imageWidth = sourceImage.originalWidth,
                imageHeight = sourceImage.originalHeight,
            )
        }
    }

    LaunchedEffect(astapSolverChoice, novaApiKey) {
        preferences.edit()
            .putString("astap_solver_choice", astapSolverChoice.name)
            .putString("nova_api_key", novaApiKey)
            .apply()
    }

    LaunchedEffect(astapCaptureSettings) {
        preferences.edit()
            .putString("astap_capture_type", astapCaptureSettings.captureType.name)
            .putString("astap_fov_source", astapCaptureSettings.fovSource.name)
            .putString("astap_camera_profile", astapCaptureSettings.cameraProfileId)
            .putFloat("astap_focal_length", astapCaptureSettings.focalLengthMm)
            .putFloat("astap_sensor_megapixels", astapCaptureSettings.sensorMegapixels)
            .putString("astap_tile_orientation", astapCaptureSettings.tileOrientation.name)
            .putFloat("astap_manual_fov", astapCaptureSettings.manualVerticalFovDegrees)
            .apply()
    }

    LaunchedEffect(layerDrawOrder) {
        preferences.edit().putString("layer_draw_order", serializeLayerDrawOrder(layerDrawOrder)).apply()
    }

    // Globale DSO-Typ-Standardfarben: Persistenz bleibt live/sofort (billige SharedPreferences-Writes),
    // aber der teure syncDeepSkyLayer()-Neuaufbau (Filtern/Projizieren/Kollisions-Platzierung über ALLE
    // DSOs) wird entprellt -- sonst würde jedes Pixel einer Zieh-Geste im Farbwähler-2D-Feld einen
    // vollen Neuaufbau UND einen eigenen Undo-Schritt auslösen. Compose bricht eine LaunchedEffect-
    // Coroutine automatisch ab, wenn ihr Key sich vor Ablauf der delay() erneut ändert -> bei schneller
    // Geste feuert syncDeepSkyLayer erst ~150ms nach dem letzten Zwischenschritt, genau einmal.
    LaunchedEffect(dsoGalaxyColorArgb) { preferences.edit().putLong("color_dso_galaxy", dsoGalaxyColorArgb).apply() }
    LaunchedEffect(dsoGlobularColorArgb) { preferences.edit().putLong("color_dso_globular", dsoGlobularColorArgb).apply() }
    LaunchedEffect(dsoOpenClusterColorArgb) { preferences.edit().putLong("color_dso_open_cluster", dsoOpenClusterColorArgb).apply() }
    LaunchedEffect(dsoNebulaColorArgb) { preferences.edit().putLong("color_dso_nebula", dsoNebulaColorArgb).apply() }
    LaunchedEffect(dsoOtherColorArgb) { preferences.edit().putLong("color_dso_other", dsoOtherColorArgb).apply() }
    LaunchedEffect(dsoGalaxyColorArgb) { delay(150); syncDeepSkyLayer(recordUndo = true) }
    LaunchedEffect(dsoGlobularColorArgb) { delay(150); syncDeepSkyLayer(recordUndo = true) }
    LaunchedEffect(dsoOpenClusterColorArgb) { delay(150); syncDeepSkyLayer(recordUndo = true) }
    LaunchedEffect(dsoNebulaColorArgb) { delay(150); syncDeepSkyLayer(recordUndo = true) }
    LaunchedEffect(dsoOtherColorArgb) { delay(150); syncDeepSkyLayer(recordUndo = true) }

    // Sprachwechsel (Nutzer-Vorgabe 2026-08-30, "Export verwendet weiterhin die alte Sprache"):
    // Sternbild-/DSO-/Stern-Namen werden beim Sync einmalig aus createConstellationOverlays/
    // createDeepSkyOverlays/createStarOverlays' `lang`-Parameter gebaut und in overlay.text/
    // overlay.constellation GEBACKEN. Der Editor zeigt Sternbildnamen zusätzlich LIVE über
    // ConstellationPattern.localizedName() (reagiert daher schon ohne diesen Hook sofort), aber
    // Export/SphericalExport zeichnen ausschließlich das gebackene overlay.text -- ohne erneuten Sync
    // bliebe das nach einem Sprachwechsel in der zuvor aktiven Sprache stehen, obwohl die Vorschau
    // (Sternbilder) bereits die neue zeigt. Bewusst OHNE Solve/Astrometrie -- reiner Resync aus der
    // bereits vorhandenen WCS, identisches Prinzip wie nach einem Solution-Import; Größen-/Positions-
    // Overrides überstehen das wie gewohnt. Alle drei Sync-Funktionen sind bei fehlender WCS bereits
    // No-Ops (kein Extra-Guard nötig).
    LaunchedEffect(AppLocale.current) {
        AppDiagnostics.record("language_resync lang=${AppLocale.resolvedLanguageTag} appLang=${AppLocale.current}")
        // VisualOnly: nur der gebackene Anzeigename ändert sich, die WCS ist dieselbe.
        syncConstellationLayer(recordUndo = false, reason = ConstellationSyncReason.VisualOnly)
        syncDeepSkyLayer(recordUndo = false)
        syncStarLayer(recordUndo = false)
    }

    // Punkt 2 (Nutzer-Vorgabe 2026-08-30): Verschieben/Skalieren/Drehen/Löschen eines Overlays kann die
    // Umgebung anderer, bereits automatisch platzierter DSO-Namen verändern (ein neu daneben liegendes
    // Objekt/eine neu daneben liegende Form kann ein zuvor freies Label jetzt verdecken) -- bumpen daher
    // dsoNeighborRecheckTick (s. onUpdateOverlay/onRemoveOverlay), entprellt wie der Farbwähler oben
    // (150ms), damit eine schnelle Zieh-Geste nicht Dutzende Neuaufbauten auslöst. tick==0 = Ausgangs-
    // zustand, noch nie ausgelöst -> nichts zu tun.
    LaunchedEffect(dsoNeighborRecheckTick) {
        if (dsoNeighborRecheckTick > 0) {
            delay(150)
            syncDeepSkyLayer(recordUndo = true, fullLabelPlacement = true)
        }
    }

    // Übrige globale Standardfarben (Katalog bearbeiten -> Farben): Persistenz bleibt live/sofort
    // (billige SharedPreferences-Writes). Sternnamen/Gradnetz sind reine Anzeige-Parameter (kein
    // Overlay wird mutiert) -> bleiben voll live. Sternbilder/Formen/Formen-Namen/Kometenmarker/Text
    // mutieren dagegen bei jeder Änderung ALLE passenden bestehenden Overlays (Nutzerbefund
    // 2026-08-20: eine global geänderte Farbe muss auch bereits platzierte Objekte umfärben, nicht nur
    // künftig neue) -- das ersetzt bei jedem Treffer die Objekt-Instanz (.copy) und macht damit den
    // Offscreen-Cache der betroffenen Schicht ungültig (neuer Rebuild). Ungebremst würde das bei jedem
    // Pixel einer Zieh-Geste im Farbwähler einen vollen Cache-Rebuild über potenziell viele Overlays
    // auslösen -> genau das vom Nutzer gemeldete Ruckeln nach dem manuellen Platzieren mehrerer Objekte.
    // Deshalb hier derselbe Entprellungs-Trick wie bei den DSO-Farben oben: State bleibt sofort/live
    // (Vorschau-Kachel), der teure "auf alle anwenden"-Durchlauf läuft über einen eigenen, entprellten
    // LaunchedEffect.
    LaunchedEffect(constellationColorArgb) { preferences.edit().putLong("color_constellation", constellationColorArgb).apply() }
    LaunchedEffect(annotate.starNameColorArgb) { preferences.edit().putLong("color_star_names", annotate.starNameColorArgb).apply() }
    LaunchedEffect(annotate.gridColorArgb) { preferences.edit().putLong("color_grid", annotate.gridColorArgb).apply() }
    LaunchedEffect(annotate.bestKnownEnabled) {
        preferences.edit().putBoolean("dso_best_known_enabled", annotate.bestKnownEnabled).apply()
    }
    // Performance-Fix 2026-09-02 (Punkt 8): entprellt statt bei jedem Drag-Tick zu schreiben -- der
    // Regler aktualisiert selections.dsoMinSizePercent live bei jedem onValueChange (s.
    // DeepSkyAnnotationSection), ein LaunchedEffect(delay(...)) bricht sich bei jedem neuen Tick selbst
    // ab (Compose-Standardverhalten) und schreibt erst ~300ms nach dem letzten Tick -- identisches
    // Prinzip wie die bereits bestehende entprellte DSO-Farb-Persistenz (s. dsoGalaxyColorArgb u.a. oben).
    LaunchedEffect(annotate.dsoMinSizePercent) {
        delay(300)
        preferences.edit().putFloat("dso_min_size_percent", annotate.dsoMinSizePercent).apply()
    }
    LaunchedEffect(shapeColorArgb) { preferences.edit().putLong("color_shapes", shapeColorArgb).apply() }
    LaunchedEffect(shapeNameColorArgb) { preferences.edit().putLong("color_shape_names", shapeNameColorArgb).apply() }
    LaunchedEffect(reticleColorArgb) { preferences.edit().putLong("color_reticle", reticleColorArgb).apply() }
    LaunchedEffect(textColorArgb) { preferences.edit().putLong("color_text", textColorArgb).apply() }
    LaunchedEffect(constellationColorArgb) {
        delay(150)
        applyConstellationStyleToAll { it.copy(colorArgb = constellationColorArgb) }
    }
    LaunchedEffect(shapeColorArgb) {
        delay(150)
        applyShapeStyleToAll { it.copy(colorArgb = shapeColorArgb) }
    }
    LaunchedEffect(shapeNameColorArgb) {
        delay(150)
        applyShapeStyleToAll { it.copy(nameColorArgb = shapeNameColorArgb) }
    }
    LaunchedEffect(reticleColorArgb) {
        delay(150)
        applyReticleStyleToAll { it.copy(colorArgb = reticleColorArgb) }
    }
    LaunchedEffect(textColorArgb) {
        delay(150)
        applyTextStyleToAll { it.copy(colorArgb = textColorArgb) }
    }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { ConstellationAssetLoader.load(context) }.getOrElse { ConstellationCatalog.featured }
        }
        val loadedMilkyWay = withContext(Dispatchers.IO) {
            runCatching { MilkyWayAssetLoader.load(context) }.getOrElse { emptyList() }
        }
        val loadedSkyStars = withContext(Dispatchers.IO) {
            runCatching { StarCatalogAssetLoader.load(context) }.getOrElse { emptyList() }
        }
        val loadedReferenceStars = withContext(Dispatchers.IO) {
            runCatching { StarCatalogAssetLoader.load(context, "catalog/stars.8.json") }.getOrElse { loadedSkyStars }
        }
        val loadedDeepSkyObjects = withContext(Dispatchers.IO) {
            runCatching { DeepSkyAssetLoader.load(context) }.getOrElse { emptyList() }
        }
        // Einmalig vorberechnet (statt bei jedem Regler-Tick in syncDeepSkyLayer neu per Regex) --
        // s. AstapOverlayMapper.createDeepSkyOverlays catalogGroups-Parameter.
        val loadedDeepSkyCatalogGroups = withContext(Dispatchers.IO) {
            loadedDeepSkyObjects.associateWith { DeepSkyCatalogGroup.of(it) }
        }
        // Performance-Fix 2026-09-02 (Objekte-Menü-Slider fror ein): analog zu loadedDeepSkyCatalogGroups
        // einmalig vorberechnet statt bei JEDER DSO-Neuberechnung (jeder Slider-Commit) erneut über den
        // kompletten ~91k-Objekte-Katalog zu laufen -- s. AstapOverlayMapper.createDeepSkyOverlays
        // bestKnownMatches-Parameter.
        val loadedDeepSkyBestKnownMatches = withContext(Dispatchers.IO) {
            loadedDeepSkyObjects.associateWith { BestKnownCatalog.match(it) }
        }
        val loadedShapes = withContext(Dispatchers.IO) {
            runCatching { DsoShapeLoader.load(context) }.getOrElse { emptyMap() }
        }
        catalog = loaded
        milkyWayLayers = loadedMilkyWay
        skyCatalogStars = loadedSkyStars
        referenceCatalogStars = loadedReferenceStars
        deepSkyObjects = loadedDeepSkyObjects
        deepSkyCatalogGroups = loadedDeepSkyCatalogGroups
        deepSkyBestKnownMatches = loadedDeepSkyBestKnownMatches
        dsoShapes = loadedShapes
        AppDiagnostics.record(
            "catalogs_loaded constellations=${loaded.size} milkyWayLayers=${loadedMilkyWay.size} " +
                "sphereStars=${loadedSkyStars.size} referenceStars=${loadedReferenceStars.size} " +
                "deepSky=${loadedDeepSkyObjects.size} shapes=${loadedShapes.size}",
        )
        val lastConstellationId = preferences.getString("last_constellation_id", null)
        selectedConstellation = loaded.firstOrNull { it.id == lastConstellationId }
            ?: loaded.firstOrNull { it.name == selectedConstellation.name }
            ?: loaded.first()
    }


    Scaffold(containerColor = Color.Transparent) { padding ->
      // Stark geblurter, formatfüllender Hintergrund aus dem geladenen Bild – edge-to-edge (auch hinter
      // Status-/Navigationsleiste, da ohne Inset-Padding). Ohne Bild: neutraler dunkler Grund.
      Box(Modifier.fillMaxSize()) {
        val bgBitmap = bitmap
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // blur() nutzt RenderEffect (API31+); auf älteren Geräten no-op -> der Scrim darunter
                // sorgt trotzdem für Lesbarkeit (leicht abgedunkeltes Bild statt reinem Schwarz).
                modifier = Modifier.fillMaxSize().blur(56.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF05060B)))
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            EmptyEditor(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPickImage = {
                    AppDiagnostics.record("image_picker_opened_from_empty_editor")
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onShareDiagnostic = ::shareFullAppDiagnostic,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                val solvedTileKeys = remember(lastSolvedWcs) {
                    (lastSolvedWcs as? MosaicWcsSolution)
                        ?.tiles
                        ?.map { it.tileOffsetX to it.tileOffsetY }
                        ?.toSet()
                        .orEmpty()
                }
                // Koordinatennetz-Geometrie EINMAL berechnen (Editor + Export teilen GraticuleRenderer).
                // Nur Lösung/Aktivierung/Dichte sind Keys -> Stärke/Farbe/Deckkraft/Zahlen ändern ohne
                // Neuberechnung.
                val graticuleGeometry = remember(
                    lastSolvedWcs, annotate.gridEnabled, annotate.gridDensity, currentBitmap, tinySkyActive,
                ) {
                    val wcs = lastSolvedWcs
                    // Solange Tiny Sky aktiv ist, sind die Bild-Pixelkoordinaten des Koordinatennetzes
                    // (in ORIGINAL-Bild-Raum berechnet) auf der Tiny-Sky-Ansicht falsch platziert ->
                    // ausblenden, bis wieder auf das Original zurückgeschaltet ist.
                    if (!tinySkyActive && annotate.gridEnabled && wcs != null) {
                        GraticuleRenderer.compute(wcs, currentBitmap.width, currentBitmap.height, annotate.gridDensity)
                    } else {
                        null
                    }
                }
                // Milchstraßen-Geometrie EINMAL berechnen (Editor + Export teilen MilkyWayRenderer).
                val milkyWayGeometry = remember(
                    lastSolvedWcs, annotate.milkyWayEnabled, milkyWayLayers, currentBitmap, tinySkyActive,
                ) {
                    val wcs = lastSolvedWcs
                    if (!tinySkyActive && annotate.milkyWayEnabled && wcs != null) {
                        MilkyWayRenderer.compute(milkyWayLayers, wcs, currentBitmap.width, currentBitmap.height)
                    } else {
                        null
                    }
                }
                // Tiny Sky (siehe tinySkyActive/tinySkyBitmap oben): Kacheln bleiben KANONISCH in ihrem
                // jeweils eigenen Ursprungsraum gespeichert (Native ODER TinySky, s. TileSourceSpace) --
                // keine Umrechnung, keine Ansicht-übergreifende Sichtbarkeit (Plan Nachtrag 3). Overlays
                // (Sternbilder/Text/Formen) bleiben dagegen KANONISCH im Original-Bild-Koordinatenraum
                // gespeichert — für die Anzeige in Tiny Sky werden sie nur ABGELEITET umgerechnet
                // (displayOverlays), Änderungen rechnen beim Speichern sofort zurück. Dadurch braucht es
                // für Overlays keinen gesonderten "Umrechnen-beim-Verlassen"-Schritt: die Daten sind
                // jederzeit schon korrekt im Original gespeichert, das Umschalten ändert nur die Anzeige.
                // Bewusst NICHT an calibrationActive gekoppelt (kein Auto-Exit) — der Nutzer soll Tiny
                // Sky verlassen der Kalibrierung nutzen können (andere Werkzeuge).
                val tinySkyNativeProjection = remember(currentBitmap) {
                    TileDeWarp.equirectangularNativeModel(currentBitmap.width, currentBitmap.height).projection
                }
                val tinySkyBitmapValue = tinySkyBitmap
                val tinySkyOverviewProjection = tinySkyBitmapValue?.let {
                    TileDeWarp.stereographicOverviewModel(it.width).projection
                }
                // Für EditorCanvas' Auto-Zoom auf die Himmel-Scheibe (s. dortiger Parameter) -- nur in
                // Tiny Sky ein Wert, sonst null (kein Auto-Zoom im normalen 2:1-Modus).
                val tinySkyDiscRadiusPx = if (tinySkyActive) {
                    tinySkyBitmapValue?.let { TileDeWarp.stereographicSkyDiscRadius(it.width) }
                } else {
                    null
                }
                // Periodische 360°-Naht in der Tiny-Sky-Ansicht (Nutzer-Auftrag 2026-09-03). NUR hier:
                // Tiny Sky aktiv UND das Quellbild ist wirklich ein 2:1-Panorama -- sonst null, dann wird
                // weder eine Linie gezeichnet noch eine Kachel geschnappt (normaler Editor, tinySky=false,
                // Export, Annotationen und Spherical360 sehen davon nichts). Toleranz 0.02 wie
                // FisheyeRefiner.FULL_PANORAMA_ASPECT_TOLERANCE (dort private, hier derselbe Wert).
                val tinySkySeam = remember(tinySkyActive, tinySkyBitmapValue, currentBitmap) {
                    val ts = tinySkyBitmapValue
                    val aspect = currentBitmap.width.toDouble() / currentBitmap.height.toDouble()
                    if (tinySkyActive && ts != null && kotlin.math.abs(aspect - 2.0) <= 0.02) {
                        TileDeWarp.tinySkySeamGeometry(ts.width, currentBitmap.width, currentBitmap.height)
                    } else {
                        null
                    }
                }
                LaunchedEffect(tinySkySeam) {
                    val seam = tinySkySeam ?: return@LaunchedEffect
                    AppDiagnostics.record(
                        "tiny_seam_geometry imageWidth=${currentBitmap.width} imageHeight=${currentBitmap.height} " +
                            "tinySkySize=${tinySkyBitmapValue?.width ?: 0} seamAxis=horizontal " +
                            "seamTinyY=${"%.2f".format(seam.y)} " +
                            "seamTinyX0=${"%.2f".format(seam.minX)} seamTinyX1=${"%.2f".format(seam.maxX)} " +
                            "periodPx=${"%.2f".format(seam.periodPx)} " +
                            "continuityMaxAngularError=${"%.3e".format(seam.maxAngularErrorDeg)} " +
                            "continuityMaxPixelError=${"%.3e".format(seam.maxPixelErrorPx)}",
                    )
                }
                val effectiveBitmap = if (tinySkyActive && tinySkyBitmapValue != null) tinySkyBitmapValue else currentBitmap
                // Kacheln sind rein ansichts-lokal (Nutzer-Einwand 2026-08-23, s. Plan Nachtrag 3): eine
                // Kachel ist nur sichtbar/anfassbar, wenn ihr sourceSpace zur AKTUELL aktiven Ansicht
                // passt -- exakt wie beim Wechsel zu einem komplett anderen Foto. Keine
                // Cross-Projektions-Umrechnung/-Näherung mehr im Zeichen-/Gesten-Pfad (das war die
                // eigentliche Ursache der wiederholten Kachel-Instabilität, nicht nur unzureichend
                // repariert). EditorCanvas bekommt die kanonische, stabile Liste direkt + activeTileSpace
                // zum Filtern; die Filterung selbst passiert dort (Zeichenschleife/solveTileAtScreen).
                val activeTileSpace = if (tinySkyActive && tinySkyBitmapValue != null) {
                    TileSourceSpace.TinySky
                } else {
                    TileSourceSpace.Native
                }
                // Overlays (Sternbilder/Text/Formen/Reticle): center/size/rotationDegrees werden für die
                // Anzeige immer umgerechnet (nie zurückgeschrieben). Verankerte Sternbilder (individuelle
                // Anker-Punkte/gekrümmte Linien, siehe anchorOverrides/edgePolylines) bekommen ZUSÄTZLICH
                // jeden Anker-/Kantenlinien-Punkt einzeln exakt umgerechnet (convertProjectedPoint) --
                // vorher blieben diese Punkte unkonvertiert in nativen Pixelwerten stehen und landeten
                // dadurch an einer für Tiny Sky bedeutungslosen Position (Ursache für "Sternbilder passen
                // im Tiny Sky nicht", s. Plan Nachtrag 1 Folgefehler). Bearbeitung bleibt weiterhin aktiv
                // blockiert (s. Callbacks unten) -- nur die reine Anzeige wird hier korrigiert.
                val displayOverlays = (
                    if (tinySkyActive && tinySkyBitmapValue != null && tinySkyOverviewProjection != null) {
                        val overviewProj = tinySkyOverviewProjection
                        // StereographicProjection.directionToPixel lehnt NUR die exakte Antipode ab
                        // (theta ~180°), nicht theta > TINY_SKY_MAX_THETA_DEG (100°) -- ein Stern nahe
                        // dem Horizont im Originalfoto (theta z.B. 150°) bekommt dadurch eine technisch
                        // gültige, aber SINNLOS weit von der sichtbaren Scheibe entfernte Pixelposition
                        // (r wächst unbeschränkt mit tan(theta/2)) statt als "außerhalb" erkannt zu
                        // werden. Eine Kantenlinie von einem korrekt nahe Zenit sitzenden zu einem so
                        // einem weit-weg-abgebildeten Punkt zeichnet einen langen Strich quer über/aus
                        // der Scheibe hinaus -- bei mehreren betroffenen Sternbildern (je nach Himmels-
                        // position in unterschiedliche Richtungen) sichtbar als "Striche Kreuz und quer"
                        // (Nutzerbefund 2026-08-24). Fix: nur Punkte behalten, die tatsächlich innerhalb
                        // der quadratischen Tiny-Sky-Bitmap landen -- alles andere ist ohnehin unsichtbar
                        // (Canvas würde es clippen), wird hier aber VOR dem Zeichnen ausgefiltert statt
                        // als lange Linie bis zum Clip-Rand gezeichnet.
                        fun convertVisiblePoint(pt: Offset, reference: Offset?): Offset? {
                            val converted = convertProjectedPoint(pt, tinySkyNativeProjection, overviewProj, reference) ?: return null
                            return converted.takeIf {
                                it.x >= 0f && it.x <= tinySkyBitmapValue.width && it.y >= 0f && it.y <= tinySkyBitmapValue.height
                            }
                        }
                        // Jede Text-Verformung/-Ausrichtung von Beschriftungen (Zenit-Wölbung UND das
                        // ältere, native nameWarp-Feld selbst) wurde auf Nutzer-Wunsch komplett entfernt
                        // (2026-08-25, s. Plan Nachtrag 15/17) -- Beschriftungen erscheinen jetzt überall
                        // (nativ UND Tiny Sky) unverändert waagerecht. Diese `overlays.map{}`-Umrechnung
                        // betrifft daher nur noch Geometrie (center/size/rotationDegrees/labelAngleDeg/
                        // Anker-/Kantenlinien), keine Text-Verformung mehr. Die entfernten Fassungen bleiben
                        // im Commit b1ba053/Tag pre-rollback-zenith-labels-2026-08-25 vollständig erhalten.
                        overlays.map { ov ->
                            val tsg = ov.tinySkyGeometry
                            if (tsg != null) {
                                // In Tiny Sky entstanden/bearbeitet -- Form/Position bleiben die
                                // mitlaufende Tiny-Sky-Geometrie, KEINE Projektionsrechnung dafür
                                // (Nutzer-Vorgabe: soll dort aussehen/sich verhalten wie in einem
                                // normalen Bild, s. Kommentar bei snapshotTinySkyGeometryIfActive).
                                // Anker-/Kantenlinien-Umrechnung entfällt weiterhin bewusst -- ein solches
                                // Overlay hat nie echte anchorOverrides/edgePolylines (nur per Solve oder
                                // gesperrter Anker-Ziehgeste erreichbar, s. blockedAnchored in onUpdateOverlay).
                                return@map ov.copy(
                                    center = tsg.center, size = tsg.size, rotationDegrees = tsg.rotationDegrees,
                                    labelAngleDeg = tsg.labelAngleDeg, labelLeaderPx = tsg.labelLeaderPx,
                                    // tsg.freehandSegments (nicht ov.freehandSegments) -- die kanonischen
                                    // Punkte wurden beim letzten Verlassen von Tiny Sky NEU auf natives
                                    // center/size normiert (s. reprojectFreehandViaPoints); kombiniert mit
                                    // der HIER wiederhergestellten Tiny-Sky-Geometrie wäre das ein
                                    // Koordinatenraum-Mix (Nutzerbefund 2026-08-25: Zeichnung verformt sich
                                    // beim erneuten Betreten von Tiny Sky). Fallback auf ov.freehandSegments
                                    // nur für ältere tsg-Schnappschüsse von vor diesem Feld.
                                    freehandSegments = tsg.freehandSegments ?: ov.freehandSegments,
                                )
                            }
                            // Isotrope Umrechnung für alles mit real bedeutsamer Kontur (DSO-Marker,
                            // Reticle, nutzerplatzierte Formen, Sternmarker) -- Nutzer-Vorgabe 2026-08-27
                            // "wieder normal ohne Verzerrung, sowohl in Tiny Sky als auch im 2:1-Bild".
                            // Konstellation (nur grobe Box, echte Form kommt separat aus den Anker-/
                            // Kantenlinien-Punkten unten) und Text (kein Flächen-Seitenverhältnis-Konzept)
                            // bleiben bewusst auf der alten, bounding-box-basierten Umrechnung.
                            val converted = if (ov.kind == OverlayKind.Constellation || ov.kind == OverlayKind.Text) {
                                convertOverlayGeometry(ov.center, ov.size, ov.rotationDegrees, tinySkyNativeProjection, overviewProj)
                            } else {
                                convertOverlayGeometryIsotropic(ov.center, ov.size, ov.rotationDegrees, tinySkyNativeProjection, overviewProj)
                            }
                            val (c, s, r) = converted ?: Triple(ov.center, ov.size, ov.rotationDegrees)
                            // Callout-Richtung (labelAngleDeg) ist bildabsolut -- derselbe lokale
                            // Verdrehungswinkel, den convertOverlayGeometry oben bereits für rotationDegrees
                            // berechnet hat (r - ov.rotationDegrees), muss auch hier angewendet werden,
                            // sonst zeigt die Führungslinie am Scheibenrand zunehmend in eine falsche
                            // absolute Richtung (Nachtrag 11, optionale Zusatzverbesserung).
                            val tinySkyLabelAngle = ov.labelAngleDeg + (r - ov.rotationDegrees)
                            if (ov.anchorOverrides.isEmpty() && ov.edgePolylines == null) {
                                ov.copy(center = c, size = s, rotationDegrees = r, labelAngleDeg = tinySkyLabelAngle)
                            } else {
                                // `c` (Overlay-Zentrum, oben schon umgerechnet) als Entfaltungs-Referenz
                                // für jeden Anker-/Kantenlinien-Punkt -- defensiv konsistent mit
                                // convertOverlayGeometrys eigener Eck-Umrechnung (die dieselbe Referenz
                                // nutzt); aktuell wirkungslos, da StereographicProjection keine
                                // periodische Ast-Wahl hat, aber schützt vor einer künftigen periodischen
                                // toProjection ohne weiteres Zutun hier.
                                val convertedAnchors = ov.anchorOverrides.mapNotNull { (idx, pt) ->
                                    convertVisiblePoint(pt, c)?.let { idx to it }
                                }.toMap()
                                val convertedEdges = ov.edgePolylines?.map { poly ->
                                    poly.mapNotNull { pt -> convertVisiblePoint(pt, c) }
                                }
                                ov.copy(
                                    center = c, size = s, rotationDegrees = r, labelAngleDeg = tinySkyLabelAngle,
                                    anchorOverrides = convertedAnchors, edgePolylines = convertedEdges,
                                )
                            }
                        }
                    } else {
                        overlays
                    }
                    ).let { list ->
                    // Wird gerade per "Weiterzeichnen" bearbeitet (Stift erneut aktiv) -> aus der
                    // normalen Anzeige ausblenden, sonst erscheint es doppelt (statisch + Live-Sitzung).
                    val editingId = editingFreehandSource?.id
                    if (editingId == null) list else list.filterNot { it.id == editingId }
                }
                EditorCanvas(
                    modifier = Modifier.fillMaxSize(),
                    bitmap = effectiveBitmap,
                    tinySkyAutoFitRadiusPx = tinySkyDiscRadiusPx,
                    overlays = displayOverlays,
                    // Solve-Vorschau-Overlays/-Sterne (editorSession.solvePreviewOverlays/-Stars) sind
                    // reine Lesevorschau (kein Editier-Callback) und in Tiny Sky bewusst NICHT
                    // umgerechnet (out of scope, s. Plan) -> ausblenden statt falsch zu zeichnen.
                    previewOverlays = if (tinySkyActive) emptyList() else editorSession.solvePreviewOverlays,
                    previewDetectedStars = if (tinySkyActive) emptyList() else editorSession.solvePreviewStarsState.value,
                    graticule = graticuleGeometry,
                    graticuleThickness = annotate.gridThickness,
                    graticuleColorArgb = annotate.gridColorArgb,
                    graticuleOpacity = annotate.gridOpacity,
                    graticuleShowLabels = annotate.gridShowLabels,
                    milkyWay = milkyWayGeometry,
                    milkyWayOpacity = annotate.milkyWayOpacity,
                    layerDrawOrder = layerDrawOrder,
                    imageBlurIntensity = annotate.imageBlurIntensity,
                    imageGrayscale = annotate.imageGrayscale,
                    imageInverted = annotate.imageInverted,
                    // FOV-Vorschaukreise NUR im Feinjustierungs-Segment der Kalibrierung (dort liegt der
                    // FOV-Seed-Slider) -> in Kacheln/Projektion bleiben sie aus (Nutzerwunsch).
                    previewMarkers = if (calibrationActive && calibrationSegment == 1 && !tinySkyActive) {
                        fisheyePreviewPoints
                    } else {
                        emptyList()
                    },
                    selectedTool = selectedTool,
                    selectedOverlayId = selectedOverlayId,
                    selectedConstellation = selectedConstellation,
                    // Referenzstern-Marker (Feinjustierung) bewusst nicht in Tiny Sky umgerechnet
                    // (out of scope, s. Plan) -> ausblenden statt falsch zu zeichnen.
                    detectedStars = if (tinySkyActive) emptyList() else detectedStars,
                    showDetectedStars = showDetectedStars,
                    showConstellationAnchors = showConstellationAnchors,
                    activePanel = activePanel,
                    maskBitmap = if (tinySkyActive) null else editorSession.solveMaskState.value,
                    maskVersion = editorSession.solveMaskVersionState.intValue,
                    maskBrushFraction = maskBrushFraction,
                    eraseBrushFraction = eraseBrushFraction,
                    maskBrushHardness = maskBrushHardness,
                    eraseBrushHardness = eraseBrushHardness,
                    brushPreviewActive = sliderDragging,
                    annotationEraseMask = if (tinySkyActive) null else editorSession.annotationEraseMaskState.value,
                    annotationEraseVersion = editorSession.annotationEraseVersionState.intValue,
                    annotationErasePreviewVersion = editorSession.annotationErasePreviewVersionState.intValue,
                    solvedTileKeys = solvedTileKeys,
                    onMaskStroke = { from, to ->
                        val mask = editorSession.solveMaskState.value
                            ?: SolveMask.create(currentBitmap.width, currentBitmap.height)
                                .also { editorSession.solveMaskState.value = it }
                        val radius = maskBrushFraction *
                            min(currentBitmap.width, currentBitmap.height)
                        SolveMask.paintLine(mask, from, to, radius, maskEraseMode, maskBrushHardness)
                        editorSession.solveMaskVersionState.intValue++
                    },
                    onAnnotationEraseStroke = { from, to ->
                        val mask = editorSession.annotationEraseMaskState.value
                            ?: SolveMask.create(currentBitmap.width, currentBitmap.height)
                                .also { editorSession.annotationEraseMaskState.value = it }
                        val radius = eraseBrushFraction *
                            min(currentBitmap.width, currentBitmap.height)
                        SolveMask.paintLine(mask, from, to, radius, erase = false, hardness = eraseBrushHardness)
                        // Pro Segment nur die billige Live-Vorschau invalidieren (kein Overlay-Cache-Redraw
                        // pro Segment -> flüssig). Die teure Commit-Version bumpt erst am Strichende.
                        editorSession.annotationErasePreviewVersionState.intValue++
                    },
                    solveTiles = solveTiles,
                    activeTileSpace = activeTileSpace,
                    tinySkySeam = tinySkySeam,
                    tileOwnRmsById = tileOwnRmsById,
                    tileOverlapRmsById = tileOverlapRmsById,
                    selectedSolveTileId = selectedSolveTileId,
                    tilesLocked = solveTilesRunning,
                    calibrationActive = calibrationActive,
                    tilesVisible = tilesVisible,
                    onTileEditBegin = { snapshotTilesForUndo() },
                    onAddSolveTile = { center, size ->
                        // Nutzer-Einwand (2026-08-23) zurecht: eine Kachel ist nur ein rechteckiger
                        // Bildausschnitt für den Sternfeld-Löser -- die braucht keine an die Projektion
                        // angepasste Geometrie. Das gezeichnete Rechteck wird daher UNVERÄNDERT
                        // übernommen, egal ob gerade Tiny Sky oder die native 2:1-Ansicht aktiv ist;
                        // sourceSpace merkt sich nur, aus welcher Ansicht/welchem Bild es stammt (für
                        // Zuschnitt-Quelle beim Lösen s. solveAllTiles(), für Bearbeitungs-Sperre in der
                        // "falschen" Ansicht s. onUpdateSolveTile). Keine convertOverlayGeometry-
                        // Umrechnung mehr beim Erzeugen -> kann auch nicht mehr fehlschlagen.
                        snapshotTilesForUndo()
                        val creatingInTinySky = tinySkyActive && tinySkyBitmapValue != null
                        // Neue Kachel erbt den aktuellen De-Warp-Default (dewarpEnabled) -- identisch in
                        // Tiny Sky wie im Normalmodus (Nutzer-Vorgabe: gleiche Kachellogik, keine
                        // Ausnahmen). Die Tiny-Sky-Bitmap ist als Korrekturgrundlage sogar EINFACHER als
                        // das native Bild: ihre Projektion ist von Anfang an exakt bekannt (reine
                        // Geometrie, kein Fit aus Ankern nötig), s. solveAllTiles()/dewarpModel.
                        solveTiles.add(
                            SolveTile(
                                id = nextSolveTileId++,
                                center = center,
                                size = size,
                                sourceSpace = if (creatingInTinySky) TileSourceSpace.TinySky else TileSourceSpace.Native,
                                dewarpRequested = dewarpEnabled,
                            ),
                        )
                        // EINE Kachel platziert -> zurück auf Move (Kachel ist nun beweg-/skalierbar
                        // wie gewohnt) und gleich ausgewählt. Popup bleibt verborgen bis „Fertig".
                        // Für die NÄCHSTE Kachel erneut „Kachel +".
                        selectedSolveTileId = solveTiles.last().id
                        selectedTool = EditorTool.Move
                    },
                    onSelectSolveTile = { id ->
                        selectedSolveTileId = id
                        // Kachel per Langdruck zum Bearbeiten gewählt -> Popup ausblenden (bis „Fertig").
                        if (id != null) calibrationPopupHidden = true
                    },
                    onUpdateSolveTile = { updated, _ ->
                        // Kacheln sind rein ansichts-lokal (Plan Nachtrag 3) -- eine Kachel der jeweils
                        // anderen Ansicht ist in EditorCanvas gar nicht mehr sichtbar/anfassbar (gefiltert
                        // über activeTileSpace), kann hier also nie ankommen. Reiner, unbedingter
                        // Durchreicher, exakt wie die Kachel-Bearbeitung im Normalmodus schon immer
                        // funktioniert hat -- keine Sperre, keine Projektionsrechnung mehr nötig.
                        val i = solveTiles.indexOfFirst { it.id == updated.id }
                        if (i >= 0) {
                            // Geometrie geändert -> Lösung passt nicht mehr: invalidieren (zurück auf
                            // offen, WCS + De-Warp-Anker verwerfen), damit Anker/Mosaik nie eine
                            // veraltete Lösung zur neuen Position paaren.
                            solveTiles[i] = updated.copy(
                                status = SolveTileStatus.Pending,
                                wcs = null,
                                dewarp = false,
                                anchors = null,
                            )
                        }
                    },
                    onDeleteSolveTile = { id ->
                        snapshotTilesForUndo()
                        solveTiles.removeAll { it.id == id }
                        if (selectedSolveTileId == id) selectedSolveTileId = null
                        tileCorrRefsById = tileCorrRefsById - id
                        refreshPanoramaSeed() // gelöste Kachel entfernt -> Seed nachziehen.
                    },
                    onToggleTileDewarp = { id ->
                        // Nur den De-Warp-WUNSCH kippen (Status/Lösung bleiben; wirkt erst beim
                        // nächsten Lösen dieser Kachel).
                        val i = solveTiles.indexOfFirst { it.id == id }
                        if (i >= 0) {
                            solveTiles[i] = solveTiles[i].copy(dewarpRequested = !solveTiles[i].dewarpRequested)
                        }
                    },
                    onPlaceNextTile = {
                        // Grüner Button unter der Kachel: direkt die nächste Kachel platzieren (wie „Kachel +").
                        selectedSolveTileId = null
                        calibrationPopupHidden = true
                        selectedTool = EditorTool.SolveRegion
                    },
                    onOpenManualHint = { id -> manualHintDialogTileId = id },
                    onOpenTileInfo = { id -> tileInfoDialogId = id },
                    onSelectOverlay = { selectedOverlayId = it },
                    onEditingOverlay = { id, kind ->
                        AppDiagnostics.record("overlay_editing_started id=$id kind=${kind.name}")
                        selectedOverlayId = id
                        selectedTool = editorToolForOverlay(kind)
                        activePanel = editorPanelForOverlay(kind)
                        // Stil-Regler im Popup mit dem realen Zustand des Overlays seeden,
                        // damit z.B. Fett/Dünn korrekt umschaltet (statt globalem Default).
                        overlays.firstOrNull { it.id == id }?.let { ov ->
                            when (kind) {
                                OverlayKind.Text -> {
                                    textColorArgb = ov.colorArgb
                                    textFont = ov.font
                                    textBold = ov.textBold
                                    textOpacity = ov.opacity
                                    textSize = ov.size.height
                                }
                                OverlayKind.Ellipse, OverlayKind.Rectangle, OverlayKind.Freehand -> {
                                    shapeColorArgb = ov.colorArgb
                                    shapeStrokeWidth = ov.strokeWidth
                                    shapeLineStyle = ov.lineStyle
                                    shapeOpacity = ov.opacity
                                    shapeFilled = ov.filled
                                    shapeFont = ov.font
                                    shapeShowName = ov.showName
                                    shapeNameTextSize = ov.nameTextSize
                                    shapeNameColorArgb = ov.nameColorArgb
                                    shapeNameBold = ov.textBold
                                }
                                OverlayKind.Constellation -> Unit
                            }
                        }
                    },
                    onEditOverlayText = { id -> editingTextOverlayId = id },
                    onExitEditing = {
                        AppDiagnostics.record("overlay_editing_exited")
                        activePanel = null
                    },
                    onEmptyTap = {
                        // In der Kalibrierung: Tipp auf leere Bildfläche blendet das Popup aus
                        // (kommt über „Ausrichten" zurück). Nicht beim Platzieren/Lösen.
                        if (calibrationActive && !calibrationPopupHidden && pendingPlacement == null &&
                            selectedTool != EditorTool.SolveRegion && !solveTilesRunning
                        ) {
                            calibrationPopupHidden = true
                        }
                    },
                    onAddDetectedStar = { star ->
                        detectedStars = detectedStars + star
                        showDetectedStars = true
                        AppDiagnostics.record("detected_star_added x=${star.x} y=${star.y}")
                    },
                    onRemoveDetectedStar = { index ->
                        if (index in detectedStars.indices) {
                            detectedStars = detectedStars.filterIndexed { starIndex, _ -> starIndex != index }
                            showDetectedStars = true
                            AppDiagnostics.record("detected_star_removed index=$index")
                        }
                    },
                    onRemoveOverlay = { id ->
                        AppDiagnostics.record("overlay_removed id=$id")
                        // Fällt auf einen eigenständigen recordHistory()-Schritt zurück, wenn KEINE Geste
                        // offen ist (z.B. der runde Papierkorb-Button in der Langdruck-Aktionsleiste --
                        // der ruft diesen Callback NACH Gestenende auf, recordInteractionMutation() wäre
                        // sonst ein stiller No-Op und die Löschung bliebe unaufzeichenbar/nicht rückgängig
                        // machbar). Gesten-interne Aufrufer (Fixed-Delete-Icon, Radierer) bleiben davon
                        // unberührt -- dort liefert recordInteractionMutation() weiterhin true.
                        if (!editorSession.recordInteractionMutation()) editorSession.recordHistory()
                        overlays.removeAll { it.id == id }
                        if (selectedOverlayId == id) selectedOverlayId = null
                        // Punkt 2: ein gelöschtes Objekt/eine gelöschte Form kann ein Hindernis gewesen
                        // sein, dem ein anderes DSO-Label bisher auswich -- entprellt, s. oben.
                        dsoNeighborRecheckTick++
                    },
                    onOpenConstellationReference = { id -> referenceOverlayId = id },
                    onTransformOverlay = { id, imagePan, zoomChange, rotationChangeDegrees ->
                        val index = overlays.indexOfFirst { it.id == id }
                        if (index >= 0) {
                            val stored = overlays[index]
                            if (tinySkyActive && (stored.anchorOverrides.isNotEmpty() || stored.edgePolylines != null)) {
                                Toast.makeText(context, context.getString(R.string.toast_edit_anchored_in_2to1), Toast.LENGTH_SHORT).show()
                            } else {
                                editorSession.recordInteractionMutation()
                                // In Tiny Sky kommen imagePan/zoomChange/rotationChangeDegrees aus einer
                                // Geste gegen die Tiny-Sky-Anzeige -> das gespeicherte (native) Overlay
                                // zuerst nach Tiny Sky umrechnen, dieselbe Transform-Mathematik darauf
                                // anwenden, das Ergebnis zurück nach Original umrechnen.
                                val old = if (tinySkyActive && tinySkyBitmapValue != null && tinySkyOverviewProjection != null) {
                                    convertOverlayGeometry(
                                        stored.center, stored.size, stored.rotationDegrees,
                                        tinySkyNativeProjection, tinySkyOverviewProjection,
                                    )?.let { (c, s, r) -> stored.copy(center = c, size = s, rotationDegrees = r) } ?: stored
                                } else {
                                    stored
                                }
                                val newCenter = old.center + imagePan
                                val rotationRad = rotationChangeDegrees * PI.toFloat() / 180f
                                fun transformPoint(p: Offset) =
                                    newCenter + rotateOffset(p - old.center, rotationRad) * zoomChange
                                val transformedAnchors = old.anchorOverrides.mapValues { (_, point) -> transformPoint(point) }
                                // edgePolylines (gelöste Fisheye-Sternbilder) mit-transformieren, sonst
                                // entkoppeln die Linien beim Resize/Rotate von Ankern/Name.
                                val transformedPolylines = old.edgePolylines?.map { poly -> poly.map { transformPoint(it) } }
                                val transformedDisplay = old.copy(
                                    center = newCenter,
                                    size = Size(
                                        width = (old.size.width * zoomChange).coerceAtLeast(24f),
                                        height = (old.size.height * zoomChange).coerceAtLeast(24f),
                                    ),
                                    rotationDegrees = old.rotationDegrees + rotationChangeDegrees,
                                    anchorOverrides = transformedAnchors,
                                    edgePolylines = transformedPolylines,
                                )
                                val finalOverlay = if (
                                    tinySkyActive && tinySkyBitmapValue != null && tinySkyOverviewProjection != null
                                ) {
                                    convertOverlayGeometry(
                                        transformedDisplay.center, transformedDisplay.size, transformedDisplay.rotationDegrees,
                                        tinySkyOverviewProjection, tinySkyNativeProjection,
                                    )?.let { (c, s, r) -> transformedDisplay.copy(center = c, size = s, rotationDegrees = r) }
                                        ?: transformedDisplay
                                } else {
                                    transformedDisplay
                                }
                                overlays[index] = finalOverlay
                                annotate.recordDsoSizeOverride(finalOverlay)
                            }
                        }
                    },
                    onUpdateOverlay = { updated, kind ->
                        val index = overlays.indexOfFirst { it.id == updated.id }
                        if (index >= 0) {
                            val stored = overlays[index]
                            // Verankerte Sternbilder (individuelle Anker-Punkte/gekrümmte Linien) bleiben in
                            // Tiny Sky grundsätzlich blockiert (bestehende Regel, unverändert) -- UND
                            // zusätzlich JEDER Anker-Zieh-Versuch selbst, auch beim allerersten Anker eines
                            // gerade erst in Tiny Sky platzierten (noch unverankerten) Sternbilds. Das ist
                            // unabhängig vom neuen tinySkyGeometry-Mechanismus unten: Anker-/Kantenlinien-
                            // Punkte werden davon nicht erfasst (nur center/size/rotationDegrees), ein
                            // Sternbild mit echten Ankern müsste sie also weiterhin verlieren/verzerren --
                            // deshalb bleibt dieser Fall bewusst gesperrt statt "normal" editierbar.
                            val blockedAnchored = tinySkyActive && (
                                stored.anchorOverrides.isNotEmpty() ||
                                    stored.edgePolylines != null ||
                                    kind == GeometryEditKind.AnchorPoint
                                )
                            // Sternbildnamen-Position (neu, s. ConstellationNameHandle) ist ausschließlich
                            // eine Bild-px-Größe im nativen 2:1-Raum -- anders als center/size/rotationDegrees
                            // hat sie kein eigenes tinySkyGeometry-Gegenstück, würde also beim Verlassen von
                            // Tiny Sky im falschen Koordinatenraum landen. Deshalb unabhängig vom
                            // Ankerungs-Status IMMER in Tiny Sky gesperrt, statt einen weiteren Tiny-Sky-
                            // Sonderfall einzuführen.
                            val blockedNamePosition = tinySkyActive && kind == GeometryEditKind.ConstellationName
                            if (blockedAnchored || blockedNamePosition) {
                                val messageRes = if (blockedNamePosition && !blockedAnchored) {
                                    R.string.toast_constellation_name_edit_in_2to1
                                } else {
                                    R.string.toast_edit_anchored_in_2to1
                                }
                                Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_SHORT).show()
                            } else {
                                // Reiner Durchreicher, exakt wie im Normalmodus -- KEINE Projektionsrechnung
                                // mehr während der Bearbeitung (Nutzer-Vorgabe: Objekte sollen sich in Tiny
                                // Sky verhalten wie in einem normalen Bild, s. Kommentar bei
                                // snapshotTinySkyGeometryIfActive). In Tiny Sky wird zusätzlich die
                                // mitlaufende Tiny-Sky-Geometrie aktualisiert -- einzige Grundlage der
                                // Umrechnung ist danach der einmalige Schritt beim Verlassen von Tiny Sky.
                                val finalOverlay = updated.snapshotTinySkyGeometryIfActive()
                                editorSession.recordInteractionMutation()
                                overlays[index] = finalOverlay
                                annotate.recordDsoSizeOverride(finalOverlay)
                                // Punkt 2: Verschieben/Skalieren/Drehen kann NACHBAR-Labels betreffen (s.
                                // dsoNeighborRecheckTick-Deklaration/LaunchedEffect oben) -- entprellt,
                                // feuert bei jedem Zieh-Frame, löst aber erst 150ms nach dem letzten aus.
                                dsoNeighborRecheckTick++
                            }
                        }
                    },
                    onCreateOverlay = createOverlay@{ tool, rawImagePoint ->
                        // Tipp-Punkt wird IMMER direkt übernommen, auch in Tiny Sky -- Objekte sollen dort
                        // entstehen wie in einem normalen Bild, keine Umrechnung beim Erstellen (s.
                        // snapshotTinySkyGeometryIfActive-Kommentar bei tinySkyActive). Die neuen Overlays
                        // unten bekommen ihre Tiny-Sky-Momentaufnahme über denselben Helper am Ende dieses
                        // Blocks.
                        val imagePoint = rawImagePoint
                        editorSession.recordInteractionMutation()
                        when (tool) {
                            EditorTool.Constellation -> {
                                val baseSize = min(currentBitmap.width, currentBitmap.height) * 0.34f
                                overlays += AnnotationOverlay(
                                    id = nextOverlayId++,
                                    kind = OverlayKind.Constellation,
                                    center = imagePoint,
                                    size = Size(baseSize, baseSize),
                                    constellation = selectedConstellation,
                                    colorArgb = constellationColorArgb,
                                    strokeWidth = constellationStrokeWidth,
                                    anchorRadiusRatio = constellationAnchorRadiusRatio,
                                    lineStyle = constellationLineStyle,
                                    opacity = constellationOpacity,
                                    mirrorX = selectedConstellationMirrorX,
                                    mirrorY = selectedConstellationMirrorY,
                                    showName = constellationShowName,
                                    nameTextSize = constellationNameTextSize,
                                    font = constellationFont,
                                ).snapshotTinySkyGeometryIfActive()
                                selectedOverlayId = overlays.last().id
                                AppDiagnostics.record(
                                    "overlay_created id=${overlays.last().id} kind=Constellation constellation=${selectedConstellation.id}",
                                )
                                // Punkt 3: neu eingefügte Form/Objekt kann bestehende Nachbar-Labels
                                // verdecken -> entprellte Neuberechnung anstoßen (dieselbe Logik wie beim
                                // Verschieben/Skalieren, s. dsoNeighborRecheckTick-Deklaration oben).
                                dsoNeighborRecheckTick++
                            }
                            EditorTool.ReticleOpen, EditorTool.ReticleComet -> {
                                // „Markieren": offenes bzw. halbes Fadenkreuz als Ellipse-Overlay mit
                                // reticle -> Drehen/Größe/Farbe/Deckkraft/Dicke wie bei Formen.
                                val base = min(currentBitmap.width, currentBitmap.height) * 0.16f
                                overlays += AnnotationOverlay(
                                    id = nextOverlayId++,
                                    kind = OverlayKind.Ellipse,
                                    center = imagePoint,
                                    size = Size(base, base),
                                    colorArgb = reticleColorArgb,
                                    strokeWidth = shapeStrokeWidth,
                                    opacity = shapeOpacity,
                                    reticle = if (tool == EditorTool.ReticleComet) {
                                        ReticleStyle.CometMarker
                                    } else {
                                        ReticleStyle.OpenCrosshair
                                    },
                                ).snapshotTinySkyGeometryIfActive()
                                selectedOverlayId = overlays.last().id
                                AppDiagnostics.record("overlay_created id=${overlays.last().id} kind=Reticle")
                                dsoNeighborRecheckTick++
                            }
                            EditorTool.Ellipse -> {
                                val base = min(currentBitmap.width, currentBitmap.height) * 0.18f
                                overlays += AnnotationOverlay(
                                    id = nextOverlayId++,
                                    kind = OverlayKind.Ellipse,
                                    center = imagePoint,
                                    size = Size(base * 1.45f, base),
                                    colorArgb = shapeColorArgb,
                                    strokeWidth = shapeStrokeWidth,
                                    lineStyle = shapeLineStyle,
                                    opacity = shapeOpacity,
                                    filled = shapeFilled,
                                    font = shapeFont,
                                ).snapshotTinySkyGeometryIfActive()
                                selectedOverlayId = overlays.last().id
                                AppDiagnostics.record("overlay_created id=${overlays.last().id} kind=Ellipse")
                                dsoNeighborRecheckTick++
                            }
                            EditorTool.Rectangle -> {
                                val base = min(currentBitmap.width, currentBitmap.height) * 0.18f
                                overlays += AnnotationOverlay(
                                    id = nextOverlayId++,
                                    kind = OverlayKind.Rectangle,
                                    center = imagePoint,
                                    size = Size(base * 1.45f, base),
                                    colorArgb = shapeColorArgb,
                                    strokeWidth = shapeStrokeWidth,
                                    lineStyle = shapeLineStyle,
                                    opacity = shapeOpacity,
                                    filled = shapeFilled,
                                    font = shapeFont,
                                ).snapshotTinySkyGeometryIfActive()
                                selectedOverlayId = overlays.last().id
                                AppDiagnostics.record("overlay_created id=${overlays.last().id} kind=Rectangle")
                                dsoNeighborRecheckTick++
                            }
                            EditorTool.Text -> pendingTextPosition = imagePoint
                            // Draw: entsteht NICHT über einen einzelnen Tap/imagePoint, sondern über
                            // die eigene Stift-Geste (s. onCreateFreehandOverlay in EditorCanvas).
                            EditorTool.Move, EditorTool.AddStar, EditorTool.DeleteStar,
                            EditorTool.Erase, EditorTool.Mask, EditorTool.Draw,
                            EditorTool.SolveRegion, EditorTool.EraseArea -> Unit
                        }
                        // Einmal-Modus: nach dem Setzen wieder abschalten.
                        pendingPlacement = null
                    },
                    onCreateFreehandOverlay = { segments ->
                        // Zeichnen-Modus IMMER verlassen (auch bei zu kurzem/leerem Versuch, s.
                        // Kommentar bei „Fertig zeichnen" in EditorCanvas). Die Zwischenschritte der
                        // Zeichnen-Sitzung (pro-Strich-Undo) sind nach dem Commit bedeutungslos.
                        pendingPlacement = null
                        editorSession.clearDrawHistory()
                        val allPoints = segments.flatten()
                        fun bounds(): Pair<Offset, Size> {
                            val minX = allPoints.minOf { it.x }
                            val minY = allPoints.minOf { it.y }
                            val maxX = allPoints.maxOf { it.x }
                            val maxY = allPoints.maxOf { it.y }
                            return Offset((minX + maxX) / 2f, (minY + maxY) / 2f) to
                                Size((maxX - minX).coerceAtLeast(24f), (maxY - minY).coerceAtLeast(24f))
                        }
                        // recordInteractionMutation() bräuchte ein VORAB per beginInteraction()
                        // gemerktes pendingSnapshot -- das wird für den Zeichnen-Gestenpfad nie gesetzt
                        // (handleDrawMode kehrt schon vorher aus awaitEachGesture zurück, bevor das
                        // generische onInteractionStart() erreicht würde). recordHistory() ist die
                        // eigenständige Variante (kein Vorlauf nötig), exakt wie beim Namens-Text-Dialog
                        // (TextOverlayDialog.onConfirm) für denselben Fall genutzt.
                        val editSource = editingFreehandSource
                        if (editSource != null) {
                            // „Weiterzeichnen" auf einem bestehenden Freihand-Overlay committet: zu
                            // wenige Punkte (alles weggradiert) heißt "Objekt löschen", NIE
                            // stillschweigend nichts tun -- genau das war der gemeldete Bug (nach dem
                            // Verlassen der Zeichnen-Sitzung nur noch Komplett-Löschen möglich).
                            editorSession.recordHistory()
                            if (allPoints.size >= 3) {
                                val (center, size) = bounds()
                                val updated = editSource.copy(
                                    center = center,
                                    size = size,
                                    rotationDegrees = 0f,
                                    freehandSegments = OverlayGeometry.normalizeFreehandPoints(segments, center, size),
                                ).snapshotTinySkyGeometryIfActive()
                                val index = overlays.indexOfFirst { it.id == editSource.id }
                                if (index >= 0) overlays[index] = updated else overlays += updated
                                selectedOverlayId = editSource.id
                                AppDiagnostics.record(
                                    "overlay_edited id=${editSource.id} kind=Freehand " +
                                        "segments=${segments.size} points=${allPoints.size}",
                                )
                                dsoNeighborRecheckTick++
                            } else {
                                overlays.removeAll { it.id == editSource.id }
                                if (selectedOverlayId == editSource.id) selectedOverlayId = null
                                AppDiagnostics.record(
                                    "overlay_removed id=${editSource.id} kind=Freehand reason=erased_empty",
                                )
                                dsoNeighborRecheckTick++
                            }
                            editingFreehandSource = null
                        } else if (allPoints.size >= 3) {
                            editorSession.recordHistory()
                            val (center, size) = bounds()
                            overlays += AnnotationOverlay(
                                id = nextOverlayId++,
                                kind = OverlayKind.Freehand,
                                center = center,
                                size = size,
                                colorArgb = shapeColorArgb,
                                strokeWidth = shapeStrokeWidth,
                                lineStyle = shapeLineStyle,
                                opacity = shapeOpacity,
                                filled = shapeFilled,
                                font = shapeFont,
                                freehandSegments = OverlayGeometry.normalizeFreehandPoints(segments, center, size),
                            ).snapshotTinySkyGeometryIfActive()
                            selectedOverlayId = overlays.last().id
                            AppDiagnostics.record(
                                "overlay_created id=${overlays.last().id} kind=Freehand " +
                                    "segments=${segments.size} points=${allPoints.size}",
                            )
                            // Punkt 3: neu eingefügte Freihandform kann bestehende Nachbar-Labels
                            // verdecken -> entprellte Neuberechnung anstoßen.
                            dsoNeighborRecheckTick++
                        }
                    },
                    onContinueDrawing = ::continueEditingFreehand,
                    shapeColorArgb = shapeColorArgb,
                    shapeStrokeWidth = shapeStrokeWidth,
                    shapeLineStyle = shapeLineStyle,
                    pendingPlacement = pendingPlacement,
                    onInteractionStart = {
                        editorSession.beginInteraction()
                        // Vor dem Strich den jeweiligen Maskenzustand sichern (Vor/Zurück-Schritt):
                        // Beschriftungs-Radierer bzw. Vordergrund-Maskenpinsel.
                        if (selectedTool == EditorTool.EraseArea) editorSession.snapshotEraseMask()
                        if (selectedTool == EditorTool.Mask) editorSession.snapshotSolveMask()
                    },
                    onInteractionEnd = {
                        editorSession.endInteraction()
                        // Strichende: jetzt EINMAL die Commit-Version bumpen -> der Overlay-Cache stanzt
                        // die radierten Beschriftungen genau einmal aus (statt pro Segment).
                        if (selectedTool == EditorTool.EraseArea) {
                            editorSession.annotationEraseVersionState.intValue++
                        }
                    },
                    onModeActiveChange = { editorLocked = it },
                    exitMoveModeRequest = exitMoveModeRequest,
                    exitDrawModeRequest = exitDrawModeRequest,
                    leftHandedDrawing = leftHandedDrawing,
                    drawSegments = editorSession.drawSegmentsState.value,
                    onDrawStrokeBegin = { editorSession.snapshotDrawSegments() },
                    onDrawSegmentsCommitted = { editorSession.drawSegmentsState.value = it },
                )
                if (pendingPlacement != null && pendingPlacement != EditorTool.Draw) {
                    PlacementHintOverlay(
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Langgedrückte Ticker-Meldung groß in der Bildmitte (Tap irgendwo schließt).
                // Als eigenes Dialog-Fenster -> IMMER im Vordergrund (über Kalibrier-Karte + Aktionsleiste).
                tickerFocusMessage?.let { msg ->
                    Dialog(
                        onDismissRequest = { tickerFocusMessage = null },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { tickerFocusMessage = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .padding(32.dp)
                                    .widthIn(max = 520.dp),
                            ) {
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(24.dp),
                                )
                            }
                        }
                    }
                }
                // Globale Aktions-Box oben-mittig (für alle Funktionen): links Vor/Zurück (kontext-
                // abhängig – Kacheln in der Kalibrierung, sonst Overlays), Mitte = Info der laufenden
                // Aktion (driftet auf) + Abbrechen. Ersetzt die alte Undo-Pille + das Solve-Banner.
                val globalActionText = when {
                    solveTilesRunning && !calibrationActive ->
                        solveTilesStatus.ifBlank { context.getString(R.string.tiles_solving) }
                    astapOperationState is AstapOperationState.Solving ||
                        astapOperationState is AstapOperationState.SolvingOnline ->
                        context.getString(R.string.status_solving)
                    else -> null
                }
                // Laufende Infos (abwechselnd) + kontextuelle Aktionen für die Bubble bündeln.
                val barInfos = buildList { globalActionText?.let { add(it) } }
                val barActions = buildList {
                    if (globalActionText != null) {
                        add(BarAction(context.getString(R.string.action_cancel)) { cancelAstapSolve() })
                    }
                    // Verschiebe-/Anker-Modus: „Fertig verschieben" beendet ihn (ersetzt grünen Balken).
                    if (editorLocked) {
                        add(BarAction(context.getString(R.string.action_done_moving)) { exitMoveModeRequest++ })
                    }
                    // Zeichnen-Modus (Zweifinger-Stift): bleibt aktiv über beliebig viele Strich-
                    // Segmente hinweg, bis „Fertig zeichnen" alle Segmente als EIN Overlay committet.
                    if (pendingPlacement == EditorTool.Draw) {
                        add(BarAction(context.getString(R.string.action_done_drawing)) { exitDrawModeRequest++ })
                    }
                    // Popup verborgen (Kachel platzieren/bearbeiten): „Fertig" beendet das Platzieren/
                    // Bearbeiten -> Popup kommt zurück, Auswahl + Werkzeug zurückgesetzt.
                    if (calibrationActive && calibrationPopupHidden && !solveTilesRunning) {
                        add(BarAction(context.getString(R.string.action_done)) {
                            calibrationPopupHidden = false
                            selectedTool = EditorTool.Move
                            selectedSolveTileId = null
                        })
                    } else if (calibrationActive && !solveTilesRunning) {
                        // Kalibrierungs-Modus verlassen (ersetzt das entfernte X der Karte).
                        add(BarAction(context.getString(R.string.action_done)) {
                            calibrationActive = false
                            calibrationPopupHidden = false
                            selectedSolveTileId = null
                        })
                    }
                }
                GlobalActionBar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        // Näher an die Android-Statusleiste (statusBarsPadding verhindert Überlappung).
                        .statusBarsPadding()
                        .padding(top = 2.dp, start = 8.dp, end = 8.dp),
                    onUndo = {
                        // GLOBAL: nimmt den zuletzt gemachten Schritt zurück – egal in welchem Menü
                        // (Overlays, Masken/Radierer, Kacheln, Kalibrierung) er passiert ist. Nicht mehr an
                        // das aktuelle Werkzeug/den Kalibriermodus gekoppelt.
                        AppDiagnostics.record("edit_undo_global")
                        selectedOverlayId = null
                        editorSession.globalUndo(onTile = { tileUndo() }, onCalibration = { calibUndo() })
                    },
                    onRedo = {
                        AppDiagnostics.record("edit_redo_global")
                        selectedOverlayId = null
                        editorSession.globalRedo(onTileRedo = { tileRedo() }, onCalibrationRedo = { calibRedo() })
                    },
                    // Während eines laufenden Solves (Kacheln oder Einzelbild) gesperrt: ein Undo/Redo mitten im
                    // Solve würde mit dem noch schreibenden Coroutine um denselben Zustand konkurrieren.
                    enabled = !solveTilesRunning && astapSolveJob == null,
                    infoTexts = barInfos,
                    actions = barActions,
                )
                // Hauptleiste bleibt auch in der Astrometrie sichtbar (hinter dem gedimmten Kalibrier-Popup),
                // damit sie sich wie die übrigen Menüs (Einstellungen/Katalog) verhält und nicht „verschwindet".
                // Nur im Verschiebe-/Anker-Modus (editorLocked) ODER während einer ECHTEN Kachel-
                // Platzierung/-Bearbeitung ist sie aus (volle Bildfläche für Kachel-Gesten). Ein bloßer
                // Peek-Tipp aufs Bild/die gedimmte Leiste setzt zwar auch calibrationPopupHidden=true
                // (Popup minimiert), darf die Leiste aber NICHT mitverstecken — sonst gibt es keinen
                // erreichbaren Weg mehr zurück (der Kommentar an onEmptyTap verspricht „kommt über
                // Ausrichten zurück", was nur stimmt, wenn genau dieser Button sichtbar bleibt).
                val tileEditingActive = selectedSolveTileId != null ||
                    selectedTool == EditorTool.SolveRegion || solveTilesRunning
                if (!editorLocked && !tileEditingActive) {
                    EditorBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        activePanel = activePanel,
                        onPickImage = {
                            activePanel = null
                            // Bewusster Wechsel weg von der Astrometrie -> Kalibrierung sauber beenden
                            // (Kacheln + Popup verschwinden; tilesVisible bleibt für die nächste Session erhalten).
                            calibrationActive = false
                            calibrationPopupHidden = false
                            selectedSolveTileId = null
                            AppDiagnostics.record("image_picker_opened_from_toolbar")
                            imagePicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onPanelSelected = { panel ->
                            AppDiagnostics.record("panel_selected panel=${panel.name}")
                            activePanel = if (activePanel == panel) null else panel
                            calibrationActive = false
                            calibrationPopupHidden = false
                            selectedSolveTileId = null
                        },
                        onOpenCalibration = {
                            // „Ausrichten"-Button öffnet direkt die Astrometrie-Kalibrierung (kein Zwischenmenü).
                            activePanel = null
                            calibrationActive = true
                            calibrationPopupHidden = false
                            AppDiagnostics.record("calibration_opened_from_toolbar")
                        },
                        onAddObject = {
                            activePanel = null
                            calibrationActive = false
                            calibrationPopupHidden = false
                            selectedSolveTileId = null
                            showPlacementMenu = true
                        },
                        onOpenRegionInfo = {
                            activePanel = null
                            AppDiagnostics.record("region_info_opened")
                            showRegionInfoDialog = true
                        },
                    )
                }
                // ASTROMETRIE-KALIBRIERUNG (Vollbild-Modus): Top-Leiste (Titel/Schließen/Undo/Redo)
                // + Bottom-Leiste (Projektion + Aktionen). Kacheln sind nur in diesem Modus sichtbar
                // und erst nach Lang-Druck bearbeitbar.
                BackHandler(enabled = calibrationActive) {
                    when {
                        // Platzieren/Bearbeiten (Popup verborgen) -> wie „Fertig": Popup zurück, Auswahl/Werkzeug reset.
                        calibrationPopupHidden -> {
                            calibrationPopupHidden = false
                            selectedTool = EditorTool.Move
                            selectedSolveTileId = null
                        }
                        // Sonst: Kalibrierung verlassen.
                        else -> calibrationActive = false
                    }
                }
                // Sicherheit gegen versehentliches Beenden: Back schließt zuerst offene Menüs;
                // ist nichts offen -> Bestätigungsabfrage statt sofort die App zu schließen.
                BackHandler(enabled = !calibrationActive) {
                    when {
                        activePanel != null -> activePanel = null
                        showPlacementMenu -> showPlacementMenu = false
                        else -> showExitConfirm = true
                    }
                }
                if (showExitConfirm) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showExitConfirm = false },
                        title = { Text(context.getString(R.string.dialog_quit_app_title)) },
                        text = { Text(context.getString(R.string.dialog_quit_app_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showExitConfirm = false
                                (context as? android.app.Activity)?.finish()
                            }) { Text(context.getString(R.string.action_quit)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExitConfirm = false }) { Text(context.getString(R.string.action_cancel)) }
                        },
                    )
                }
                // Karte verborgen, solange eine Kachel platziert/bearbeitet wird (calibrationPopupHidden).
                // Zurück ins Menü ausschließlich über „Fertig" oben.
                if (calibrationActive && !calibrationPopupHidden) {
                    // Wie die anderen Menüs (Maskieren etc.): dasselbe Dialog-Popup (unten verankert,
                    // Scrim, gleiche Öffnen-Animation, Slider-Hide via AppBottomPopup). Kachel-Platzieren
                    // blendet es über calibrationPopupHidden aus -> Bild dann frei bedienbar.
                    AppBottomPopup(
                        onDismiss = {
                            // Tippen aufs Bild (Dialog-Scrim) minimiert NUR das Popup — wie „Kachel +"
                            // platzieren. Die Astrometrie-Sitzung (Kacheln bleiben sichtbar) endet
                            // ausschließlich über „Fertig" oder einen bewussten Menüwechsel.
                            calibrationPopupHidden = true
                        },
                        // Zurück-Taste bleibt exklusiv bei der eigenen BackHandler-Logik unten
                        // (unterscheidet korrekt zwischen „minimieren" und „verlassen").
                        dismissOnBackPress = false,
                        sliderDragging = calibrationSliderDragging,
                        onSliderDraggingChange = { calibrationSliderDragging = it },
                    ) {
                        // Höhen-Deckel + Scroll wie die übrigen Menüs -> die Karte bleibt kompakt unten
                        // und wächst nicht bis zur Bildmitte.
                        Column(
                            modifier = Modifier
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Kein X: Modus verlassen über „Fertig" in der Aktions-Bubble oder Android-Back.
                            HideWhileSliderDrag {
                                MenuHeader(title = context.getString(R.string.label_astrometry_calibration))
                            }
                            // Sternbilder direkt in der Kalibrierung ein-/ausblendbar (nach einem Solve),
                            // ohne ins Katalog-Menü wechseln zu müssen. syncConstellationLayer() blendet je
                            // nach Flag ein (baut Overlays) oder aus (entfernt sie).
            HideWhileSliderDrag {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = annotate.constellationsEnabled,
                                        onClick = {
                                            annotate.constellationsEnabled = !annotate.constellationsEnabled
                                            // VisualOnly: reiner Ein-/Ausblenden-Schalter, keine neue Lösung.
                                            syncConstellationLayer(reason = ConstellationSyncReason.VisualOnly)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (annotate.constellationsEnabled) Icons.Default.Visibility
                                                else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                            )
                                        },
                                        label = { Text(context.getString(R.string.label_constellations)) },
                                    )
                                    // Kacheln im Astrometrie-Modus ein-/ausblenden (löscht sie nicht).
                                    FilterChip(
                                        selected = tilesVisible,
                                        onClick = { tilesVisible = !tilesVisible },
                                        leadingIcon = {
                                            Icon(
                                                if (tilesVisible) Icons.Default.Visibility
                                                else Icons.Default.VisibilityOff,
                                                contentDescription = null,
                                            )
                                        },
                                        label = { Text(context.getString(R.string.label_tiles)) },
                                    )
                                }
                            }
                            val solvedTiles = solveTiles.count { it.status == SolveTileStatus.Solved }
                            val failedTiles = solveTiles.count { it.status == SolveTileStatus.Failed }
                            val suspectTiles = solveTiles.count { it.status == SolveTileStatus.Suspect }
                            // Läuft ein Kachel- ODER Einzelbild-Solve? -> Fortschritt + Live-Ticker (der
                            // Ticker loggt jetzt auch das Einzelbild).
                            if (solveTilesRunning || astapSolveJob != null) {
                                // Fortschritt + Live-Ticker ersetzen die Segment-Inhalte (keine Kollision).
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                                        TextButton(onClick = { cancelAstapSolve() }) {
                                            Text(context.getString(R.string.action_cancel))
                                        }
                                    }
                                    // Live-Ticker: laufende Ereignisse (Warteschlange, Timeouts,
                                    // Verbindungsabbrüche, gelöst/rot/verdächtig). Newest unten.
                                    SolveTicker(
                                        solveTicker,
                                        Modifier.fillMaxWidth(),
                                        onShowMessage = { tickerFocusMessage = it },
                                    )
                                }
                            } else {
                                val segments = listOf(
                                    context.getString(R.string.label_solver),
                                    context.getString(R.string.label_fine_tune),
                                    context.getString(R.string.label_projection),
                                )
                                HideWhileSliderDrag {
                                    AppSegmentTabs(
                                        options = segments,
                                        selectedIndex = calibrationSegment,
                                        onSelect = { calibrationSegment = it },
                                    )
                                }
                                when (calibrationSegment) {
                                    0 -> {
                                        var showSolveLog by remember { mutableStateOf(false) }
                                        // Reihenfolge (Nutzerwunsch): Kachel+ · Kacheln lösen · Einzelbild lösen · Log · Leeren.
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            OutlinedButton(onClick = {
                                                selectedSolveTileId = null
                                                calibrationPopupHidden = true // Popup weg -> Bild frei zum Aufziehen.
                                                selectedTool = EditorTool.SolveRegion
                                            }) { Text(context.getString(R.string.action_tile_plus)) }
                                            OutlinedButton(
                                                onClick = {
                                                    // Kacheln sind ansichts-lokal (Plan Nachtrag 3) --
                                                    // eine gerade ausgewählte Kachel der ALTEN Ansicht
                                                    // darf nach dem Wechsel nicht als "ausgewählt" (mit
                                                    // Aktions-Buttons) hängen bleiben, obwohl sie dort
                                                    // jetzt unsichtbar/ungefiltert ist.
                                                    selectedSolveTileId = null
                                                    val src = bitmap
                                                    when {
                                                        tinySkyActive -> {
                                                            // Verlassen von Tiny Sky: JETZT (einmalig, nicht
                                                            // laufend) für jedes Overlay mit einer mitlaufenden
                                                            // Tiny-Sky-Geometrie (s. snapshotTinySkyGeometryIfActive)
                                                            // die native, projektionsverzerrte Geometrie berechnen
                                                            // -- Nutzer-Vorgabe: die Verformung soll erst beim
                                                            // Verlassen des Modus stattfinden. tinySkyGeometry
                                                            // selbst bleibt erhalten (nicht gelöscht), damit ein
                                                            // erneutes Betreten dieselbe Form unverändert bearbeitbar
                                                            // wiederfindet. Schlägt die Umrechnung für ein Objekt
                                                            // fehl (z.B. Randbereich außerhalb der Himmel-Scheibe),
                                                            // bleibt dessen native Geometrie beim letzten bekannten
                                                            // Stand -- kein Objektverlust, nur ggf. vorübergehend
                                                            // falsch positioniert bis zur nächsten Bearbeitung.
                                                            if (src != null) {
                                                                val tsBitmap = tinySkyBitmap
                                                                if (tsBitmap != null) {
                                                                    val nativeProj = TileDeWarp.equirectangularNativeModel(src.width, src.height).projection
                                                                    val overviewProj = TileDeWarp.stereographicOverviewModel(tsBitmap.width).projection
                                                                    for (i in overlays.indices) {
                                                                        val tsg = overlays[i].tinySkyGeometry ?: continue
                                                                        val ov = overlays[i]
                                                                        // Nutzer-Vorgabe 2026-08-24: "einfache Umprojektion wie die Sternbilder...
                                                                        // pixelgenau" -- Formen mit einer echten Kontur (Freihand-Konturpunkte
                                                                        // bzw. Ellipse-/Rechteck-Randlinie) werden über VIELE einzeln reprojizierte
                                                                        // Punkte umgerechnet statt über eine einzelne Jacobi-Schätzung am
                                                                        // Mittelpunkt (s. reprojectFreehandViaPoints/reprojectBoundaryViaPoints).
                                                                        // Liefert das nichts (zu wenige Punkte überlebt) oder ist es eine andere
                                                                        // Overlay-Art (Sternbild/Reticle/Text), bleibt die bisherige Jacobi-
                                                                        // basierte Umrechnung als Rückfall.
                                                                        val freehandResult = if (ov.kind == OverlayKind.Freehand) {
                                                                            reprojectFreehandViaPoints(ov, tsg, overviewProj, nativeProj)
                                                                        } else {
                                                                            null
                                                                        }
                                                                        val converted = when {
                                                                            freehandResult != null -> Triple(freehandResult.center, freehandResult.size, 0f)
                                                                            ov.kind == OverlayKind.Ellipse || ov.kind == OverlayKind.Rectangle ->
                                                                                reprojectBoundaryViaPoints(tsg, ov.kind, overviewProj, nativeProj)
                                                                            else -> convertOverlayGeometryLocal(
                                                                                tsg.center, tsg.size, tsg.rotationDegrees, overviewProj, nativeProj,
                                                                            )
                                                                        }
                                                                        if (converted != null) {
                                                                            val (c, s, r) = converted
                                                                            // Callout-Beschriftung (Nachtrag 11, Bug-B-Kernfix): labelAngleDeg/
                                                                            // labelLeaderPx sind bildabsolut und wurden bislang NIE mitkonvertiert
                                                                            // (Nutzerbefund: Linie zeigt nach dem Verlassen "woanders hin"). Statt
                                                                            // die abstrakten Zahlen naiv zu übernehmen (falsch, da rotationDegrees
                                                                            // sich durch die SVD-Umrechnung ändern kann und labelAngleDeg relativ
                                                                            // dazu interpretiert wird), wird der TATSÄCHLICHE alte Ankerpunkt (die
                                                                            // sichtbare Nutzer-Intention) über convertProjectedPoint mitkonvertiert
                                                                            // und daraus per labelHandleFromDrag neu hergeleitet -- konsistent zum
                                                                            // neuen rotationDegrees, dämpft statt verstärkt die bekannte
                                                                            // Nicht-Idempotenz von convertOverlayGeometryLocal.
                                                                            var newLabelAngleDeg = ov.labelAngleDeg
                                                                            var newLabelLeaderPx = ov.labelLeaderPx
                                                                            if (ov.showName && ov.text.isNotBlank()) {
                                                                                // WICHTIG: tsg.labelAngleDeg/labelLeaderPx (nicht ov.*) -- das sind
                                                                                // die Werte, die dem Nutzer tatsächlich zuletzt in Tiny Sky angezeigt
                                                                                // wurden (s. displayOverlays' tsg-Zweig, Schritt 8); ov.* könnten noch
                                                                                // vom letzten NATIVEN Stand stammen, falls dieses Objekt in der
                                                                                // aktuellen Tiny-Sky-Sitzung nie angefasst wurde -- ein Mix aus
                                                                                // Tiny-Sky-Geometrie und nativ kalibriertem Label wäre exakt dieselbe
                                                                                // Inkonsistenz, die dieser Fix beheben soll.
                                                                                val oldLayout = OverlayGeometry.markerLabelLayout(
                                                                                    ov.copy(
                                                                                        center = tsg.center, size = tsg.size, rotationDegrees = tsg.rotationDegrees,
                                                                                        labelAngleDeg = tsg.labelAngleDeg, labelLeaderPx = tsg.labelLeaderPx,
                                                                                    ),
                                                                                )
                                                                                val oldAbsoluteAnchor = tsg.center + oldLayout.nameAnchor
                                                                                val newAbsoluteAnchor = convertProjectedPoint(oldAbsoluteAnchor, overviewProj, nativeProj, c)
                                                                                if (newAbsoluteAnchor != null) {
                                                                                    val handle = OverlayGeometry.labelHandleFromDrag(
                                                                                        ov.copy(center = c, size = s, rotationDegrees = r),
                                                                                        newAbsoluteAnchor - c,
                                                                                    )
                                                                                    newLabelAngleDeg = handle.labelAngleDeg
                                                                                    newLabelLeaderPx = handle.labelLeaderPx
                                                                                }
                                                                                // Fehlschlag (newAbsoluteAnchor==null): alte Werte beibehalten,
                                                                                // konsistent mit der Fallback-Philosophie oben (kein Objektverlust).
                                                                            }
                                                                            overlays[i] = ov.copy(
                                                                                center = c, size = s, rotationDegrees = r,
                                                                                labelAngleDeg = newLabelAngleDeg, labelLeaderPx = newLabelLeaderPx,
                                                                                freehandSegments = freehandResult?.segments ?: ov.freehandSegments,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            tinySkyActive = false
                                                        }
                                                        tinySkyBitmap != null -> tinySkyActive = true
                                                        src != null -> {
                                                            tinySkyLoading = true
                                                            scope.launch {
                                                                val warped = withContext(Dispatchers.Default) {
                                                                    runCatching {
                                                                        // Einmaliger Render (kein Live-Loop mehr) -> höhere
                                                                        // Auflösung als frühere Anläufe ist hier vertretbar.
                                                                        // Größe orientiert sich an der Quellauflösung (Nutzer-
                                                                        // Vorgabe 2026-08-24: Tiny Sky soll am Zenit mindestens
                                                                        // dieselbe Pixel-Dichte wie das Original erreichen, s.
                                                                        // TileDeWarp.stereographicOutputSizeForSource) statt
                                                                        // einer festen, von der Quelle unabhängigen Zahl.
                                                                        val size = TileDeWarp.stereographicOutputSizeForSource(src.width)
                                                                        TileDeWarp.buildStereographicOverview(src, outputSize = size) {
                                                                            if (!isActive) throw CancellationException()
                                                                        }
                                                                    }.getOrNull()
                                                                }
                                                                tinySkyLoading = false
                                                                if (warped != null) {
                                                                    tinySkyBitmap = warped
                                                                    tinySkyActive = true
                                                                    // Ab hier gilt dieses Bild als echter
                                                                    // 360°-/Tiny-Sky-Workflow (s. Kommentar
                                                                    // an tinySkyWorkflowUsed).
                                                                    tinySkyWorkflowUsed = true
                                                                    AppDiagnostics.record(
                                                                        "tinysky_workflow_entered outputSize=${warped.width} " +
                                                                            "sourceSize=${src.width}x${src.height}",
                                                                    )
                                                                } else {
                                                                    Toast.makeText(
                                                                        context,
                                                                        context.getString(R.string.toast_warp_failed),
                                                                        Toast.LENGTH_SHORT,
                                                                    ).show()
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = bitmap != null && !tinySkyLoading,
                                            ) {
                                                Text(
                                                    when {
                                                        tinySkyLoading -> context.getString(R.string.status_warping)
                                                        tinySkyActive -> context.getString(R.string.label_360_original)
                                                        else -> context.getString(R.string.label_360_viewer)
                                                    },
                                                )
                                            }
                                            Button(
                                                onClick = { solveAllTiles() },
                                                // Auch anklickbar, wenn schon alles gelöst ist -> derselbe Button
                                                // dient dann als "Neu berechnen" (z. B. nach dem Löschen einer
                                                // Kachel, Nutzerwunsch) statt dafür eine weitere Kachel-
                                                // Platzierung nötig zu machen. Beschriftung ist rein vom
                                                // aktuellen Kachelbestand abgeleitet -> springt automatisch
                                                // zurück auf "Kacheln lösen", sobald eine neue (offene) Kachel
                                                // hinzukommt.
                                                enabled = solveTiles.isNotEmpty(),
                                            ) {
                                                Text(
                                                    if (solveTiles.any { it.status != SolveTileStatus.Solved }) {
                                                        context.getString(R.string.action_solve_tiles)
                                                    } else {
                                                        context.getString(R.string.action_recompute)
                                                    },
                                                )
                                            }
                                            // Ganzes Bild als Einzelbild lösen (ersetzt „Bild automatisch ausrichten").
                                            Button(
                                                onClick = { requestSingleImageSolve() },
                                                enabled = bitmap != null && astapSolveJob == null,
                                            ) { Text(context.getString(R.string.action_solve_single_image)) }
                                            OutlinedButton(onClick = { showSolveLog = true }) {
                                                Text(context.getString(R.string.label_log))
                                            }
                                            if (solveTiles.isNotEmpty()) {
                                                OutlinedButton(onClick = {
                                                    snapshotTilesForUndo()
                                                    solveTiles.clear()
                                                    selectedSolveTileId = null
                                                    refreshPanoramaSeed() // alle Kacheln weg -> Seed zurücksetzen.
                                                }) { Text(context.getString(R.string.action_clear)) }
                                            }
                                        }
                                        if (showSolveLog) {
                                            SolveLogDialog(solveTicker, onDismiss = { showSolveLog = false })
                                        }
                                        Text(
                                            context.getString(
                                                R.string.tiles_status_summary,
                                                solveTiles.size,
                                                solvedTiles,
                                                failedTiles,
                                            ) + (if (suspectTiles > 0) {
                                                context.getString(R.string.tiles_status_suspect_suffix, suspectTiles)
                                            } else {
                                                ""
                                            }) + context.getString(
                                                R.string.single_image_status_suffix,
                                                if (singleImageSolved) {
                                                    context.getString(R.string.status_solved_lower)
                                                } else {
                                                    "–"
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        // Sichtbar machen, welche Projektion (auch im Automodus) zuletzt
                                        // tatsächlich gewählt wurde + wie gut sie passt — vorher nur im
                                        // Diagnose-Log sichtbar, dadurch blieb ein Automodus-Fehlgriff unbemerkt.
                                        val lastRms = alignProjectionRms
                                        if (lastRms != null) {
                                            Text(
                                                if (panoProjectionChoice == "Auto") {
                                                    context.getString(
                                                        R.string.auto_projection_rms,
                                                        alignProjectionKind.toString(),
                                                        "%.1f".format(lastRms),
                                                    )
                                                } else {
                                                    context.getString(
                                                        R.string.selected_projection_rms,
                                                        alignProjectionKind.toString(),
                                                        "%.1f".format(lastRms),
                                                    )
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        // 360°-Erkennung: Seitenverhältnis ~2:1 -> Vorschlag, auf das
                                        // (bereits unterstützte) Equirectangular-Modell umzustellen, statt
                                        // "Auto" bei wenigen Ankern raten zu lassen (siehe Diagnose-Analyse).
                                        val looksLike360 = bitmap?.let { b ->
                                            b.height > 0 && (b.width.toFloat() / b.height.toFloat()) in 1.9f..2.1f
                                        } ?: false
                                        if (looksLike360 && panoProjectionChoice != "Equirectangular") {
                                            Row(
                                                Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    context.getString(R.string.looks_like_360_panorama),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                TextButton(onClick = { panoProjectionChoice = "Equirectangular" }) {
                                                    Text(context.getString(R.string.action_use_equirectangular))
                                                }
                                            }
                                        }
                                    }
                                    1 -> {
                                        val isBusy = astapOperationState is AstapOperationState.Solving ||
                                            astapOperationState is AstapOperationState.SolvingOnline
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp)
                                                .verticalScroll(rememberScrollState()),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            SolverConfigSection(
                                                captureSettings = astapCaptureSettings,
                                                exifFieldOfView = astapExifFieldOfView,
                                                equipmentFieldOfView = astapEquipmentFieldOfView,
                                                fieldOfViewDegrees = astapFieldOfView,
                                                targetObjectQuery = targetObjectQuery,
                                                targetObjectResolved = targetObjectResolved,
                                                isBusy = isBusy,
                                                onCaptureSettingsChange = { value ->
                                                    astapCaptureSettings = value
                                                    clearSolvedIndicator()
                                                    if (astapOperationState is AstapOperationState.Failure) {
                                                        astapOperationState = AstapOperationState.Idle
                                                    }
                                                },
                                                onTargetObjectQueryChange = { targetObjectQuery = it },
                                            )
                                            SettingSlider(
                                                label = context.getString(
                                                    R.string.image_fov_seed_slider,
                                                    fisheyeFovLongDeg.roundToInt(),
                                                ),
                                                value = fisheyeFovLongDeg,
                                                valueRange = 1f..360f,
                                                onValueChange = {
                                                    fisheyeFovLongDeg = it
                                                    preferences.edit().putFloat("fisheye_fov_long_deg", it).apply()
                                                    // FOV geändert -> alten (Auto-/angewendeten) Seed verwerfen, damit
                                                    // "Feinjustierung starten" aus dem neuen FOV neu seedet (sonst
                                                    // zeigt die Feinjustierung weiter den alten Seed).
                                                    fisheyeAlignSeed = null
                                                    updateFisheyePreview()
                                                },
                                            )
                                            HideWhileSliderDrag {
                                            OutlinedButton(
                                                onClick = {
                                                    // postSolveCalibration statt nur originalSolvedWcs: bringt auch
                                                    // Projektionswahl/RMS/Kind/"Eigene" exakt auf den Stand direkt
                                                    // nach dem letzten Solve zurück, nicht nur die reine WCS.
                                                    val baseline = postSolveCalibration
                                                    if (baseline != null) {
                                                        snapshotCalibrationForUndo()
                                                        applyCalibrationSnapshot(baseline)
                                                        AppDiagnostics.record("astrometry_reverted_to_original")
                                                    }
                                                },
                                                enabled = postSolveCalibration != null,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(context.getString(R.string.action_restore_original_astrometry))
                                            }
                                            Button(
                                                onClick = {
                                                    if (fisheyeAlignSeed != null) fisheyeAlignActive = true else startFisheyeAlign()
                                                },
                                                enabled = fisheyeAlignSeed != null ||
                                                    (originalSolvedWcs ?: lastSolvedWcs) != null,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(context.getString(R.string.action_start_fine_tune))
                                            }
                                            }
                                        }
                                    }
                                    else -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Nutzer-Auftrag 2026-09-03: ALLE Projektionen sind IMMER sichtbar.
                                            // Nicht verfügbar heißt jetzt "sichtbar, aber deaktiviert" -- und
                                            // "nicht verfügbar" bedeutet ausschließlich "es existiert technisch
                                            // keine benutzbare Lösung", NICHT "hat die automatische Bewertung
                                            // verloren". Solange noch kein Kachel-Solve gelaufen ist
                                            // (projectionCandidateRms leer), bleibt alles anwählbar -- die Wahl
                                            // wirkt dann wie bisher als Vorgabe für den nächsten Solve.
                                            val rigidAvailable: (PanoProjectionKind) -> Boolean = { kind ->
                                                projectionCandidateRms.isEmpty() || projectionCandidateRms[kind] != null
                                            }
                                            buildList {
                                                add(Triple("Auto", context.getString(R.string.label_auto), true))
                                                add(Triple("Rectilinear", context.getString(R.string.projection_rectilinear), rigidAvailable(PanoProjectionKind.Rectilinear)))
                                                add(Triple("Fisheye", "Fisheye", rigidAvailable(PanoProjectionKind.Fisheye)))
                                                add(Triple("Stereographic", context.getString(R.string.projection_stereographic), rigidAvailable(PanoProjectionKind.Stereographic)))
                                                add(Triple("Equirectangular", context.getString(R.string.label_equirect_short), rigidAvailable(PanoProjectionKind.Equirectangular)))
                                                add(Triple("Cylindrical", context.getString(R.string.projection_cylindrical), rigidAvailable(PanoProjectionKind.Cylindrical)))
                                                add(Triple("Mercator", "Mercator", rigidAvailable(PanoProjectionKind.Mercator)))
                                                // "Mesh" = SparseAnchorMesh (der 7. Kandidat aus
                                                // solvetiles_candidates, FisheyeRefiner.fitMesh über die groben
                                                // Kachel-Anker). Wählbar, sobald so ein Fit existiert -- auch wenn
                                                // er die BIC-Auswahl verloren hat.
                                                add(Triple("Mesh", context.getString(R.string.projection_mesh), sparseMeshFit != null))
                                                // "Detail-Mesh" = RichCorrMesh (dichte echte .corr-Sternmessungen).
                                                // Wählbar, sobald ein technisch gültiger Fit existiert -- die
                                                // Qualitäts-Gates (A8/Kreuzvalidierung) entscheiden nur noch über
                                                // die AUTOMATIK, nicht mehr über die manuelle Verfügbarkeit
                                                // (s. RichCorrMesh.fitCandidate).
                                                add(Triple("DetailMesh", context.getString(R.string.projection_detail_mesh), richMeshFit != null))
                                                // Nur sichtbar, solange ein per Feinjustierung manuell verfeinerter
                                                // Fit existiert (Goldstandard) -> verschwindet wieder bei
                                                // "Original-Astrometrie wiederherstellen". Bewusst weiterhin
                                                // bedingt sichtbar: "Eigene" ist kein Projektionsmodell, sondern
                                                // ein einmaliges Nutzerergebnis.
                                                if (customAlignFit != null) {
                                                    add(Triple("Custom", context.getString(R.string.label_custom), true))
                                                }
                                            }.forEach { (key, label, available) ->
                                                // Optik wie „Kachel +": aktiv = gefüllter Button, sonst umrandet.
                                                val onPick: () -> Unit = {
                                                    // Vor-Zustand sichern -> JEDER Projektionswechsel ist ein
                                                    // globaler Undo/Redo-Schritt. Muss VOR panoProjectionChoice
                                                    // gesetzt werden, sonst friert der Snapshot bereits die neue
                                                    // Wahl ein (s. Kommentar an reprojectPanorama()).
                                                    snapshotCalibrationForUndo()
                                                    panoProjectionChoice = key
                                                    if (key == "Custom") {
                                                        // Kein Neu-Fit -- der gespeicherte, manuell verfeinerte
                                                        // Fit wird 1:1 wieder eingesetzt und geht (wie beim
                                                        // Anwenden selbst) den Kacheln bewusst vor.
                                                        val fit = customAlignFit
                                                        if (fit != null) {
                                                            lastSolvedWcs = fit
                                                            fisheyeBaseFit = fit
                                                            fisheyeAlignSeed = fit
                                                            alignProjectionKind = customAlignKind ?: alignProjectionKind
                                                            alignProjectionRms = customAlignRms
                                                            annotate.constellationsEnabled = true
                                                            syncConstellationLayer(recordUndo = false)
                                                            syncDeepSkyLayer(recordUndo = false)
                                                            syncStarLayer(recordUndo = false)
                                                            logConstellationAudit("manual_$key")
                                                        }
                                                    } else if (key == "Mesh" || key == "DetailMesh") {
                                                        // Kein Neu-Fit -- der beim letzten Kachel-Solve bereits
                                                        // gebaute Mesh-Fit wird 1:1 wieder eingesetzt, exakt wie beim
                                                        // "Custom"-Zweig oben. "Mesh" = SparseAnchorMesh (grobe
                                                        // Kachel-Anker), "Detail-Mesh" = RichCorrMesh (dichte echte
                                                        // .corr-Sternmessungen) -- zwei getrennte Mechanismen, hier
                                                        // bewusst nur EIN gemeinsamer Anwende-Zweig, weil beide
                                                        // dieselbe fertige PanoramaWcsSolution einsetzen. NICHT in
                                                        // preferences persistiert (wie "Custom": überlebt keinen
                                                        // Neustart und keinen weiteren Kachel-Solve ohne Neuberechnung).
                                                        val fit = if (key == "Mesh") sparseMeshFit else richMeshFit
                                                        val rms = if (key == "Mesh") sparseMeshRms else richMeshRms
                                                        if (fit != null) {
                                                            lastSolvedWcs = fit
                                                            fisheyeBaseFit = fit
                                                            fisheyeAlignSeed = fit
                                                            alignProjectionKind = PanoProjectionKind.Mesh
                                                            alignProjectionRms = rms
                                                            annotate.constellationsEnabled = true
                                                            syncConstellationLayer(recordUndo = false)
                                                            syncDeepSkyLayer(recordUndo = false)
                                                            syncStarLayer(recordUndo = false)
                                                            logConstellationAudit("manual_$key")
                                                        }
                                                    } else {
                                                        // Preferences NUR für echte Modelle persistieren -- "Custom"
                                                        // überlebt keinen Neustart (customAlignFit ist In-Memory).
                                                        preferences.edit().putString("pano_projection", key).apply()
                                                        // Bereits gelöst? -> Projektion sofort aus den Ankern neu
                                                        // rechnen (kein erneutes Solve). Sonst wirkt es beim nächsten Solve.
                                                        reprojectPanorama()
                                                    }
                                                }
                                                // Nicht verfügbar = sichtbar, aber deaktiviert. Material3 graut
                                                // einen `enabled = false`-Button selbst dezent aus -- keine
                                                // Warnfarbe, kein Zusatzdialog, Name bleibt lesbar.
                                                if (panoProjectionChoice == key) {
                                                    Button(onClick = onPick, enabled = available) { Text(label) }
                                                } else {
                                                    OutlinedButton(onClick = onPick, enabled = available) { Text(label) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    activePanel?.let { panel ->
        val currentBitmap = bitmap
        // Wird ein Slider gezogen, blendet sich das Popup aus (nur das Bild bleibt sichtbar), damit
        // man die Wirkung am Bild sieht; der (unsichtbare) Slider bleibt unter dem Finger bedienbar.
        // (sliderDragging ist in den Haupt-Scope hochgezogen, damit der Editor-Canvas das Ziehen kennt.)
        // ALLE Menüs erscheinen als Popup OBERHALB der Leiste (nicht mittig, nicht als Bottom-Sheet),
        // damit die untere Leiste sichtbar bleibt. Inhalt scrollt; Tippen daneben (Scrim) schließt.
        val panelContent: @Composable () -> Unit = {
            EditorPanelSheet(
                panel = panel,
                onClose = { activePanel = null },
                selectedTool = selectedTool,
                selectedConstellation = selectedConstellation,
                showDetectedStars = showDetectedStars,
                showConstellationAnchors = showConstellationAnchors,
                starDetectionSensitivity = starDetectionSensitivity,
                isDetecting = isDetecting,
                astapCaptureSettings = astapCaptureSettings,
                astapExifFieldOfView = astapExifFieldOfView,
                astapEquipmentFieldOfView = astapEquipmentFieldOfView,
                astapFieldOfView = astapFieldOfView,
                astapOperationState = astapOperationState,
                astapSolverChoice = astapSolverChoice,
                novaApiKey = novaApiKey,
                leftHandedDrawing = leftHandedDrawing,
                onLeftHandedDrawingChange = {
                    leftHandedDrawing = it
                    preferences.edit().putBoolean("left_handed_drawing", it).apply()
                },
                starDisplayCap = starDisplayCap,
                onStarDisplayCapChange = {
                    starDisplayCap = it
                    preferences.edit().putInt("star_display_cap", it).apply()
                    syncStarLayer(recordUndo = true)
                },
                exportImageFormat = exportImageFormat,
                onExportImageFormatChange = {
                    exportImageFormat = it
                    preferences.edit().putString("export_image_format", it.name).apply()
                },
                canExportSolution = lastSolvedWcs?.isExportable() == true,
                onExportSolution = {
                    AppDiagnostics.record(
                        "solution_export_started projectionKind=$alignProjectionKind " +
                            "exportable=${lastSolvedWcs?.isExportable() == true}",
                    )
                    lastSolvedWcs?.exportBlockReason()?.let {
                        AppDiagnostics.record("solution_export_blocked reason=$it projectionKind=$alignProjectionKind")
                    }
                    exportSolutionLauncher.launch("sternbild_mapper_loesung_${System.currentTimeMillis()}.json")
                },
                onImportSolution = {
                    AppDiagnostics.record("solution_import_started")
                    importSolutionLauncher.launch(arrayOf("application/json"))
                },
                maskPanel = MaskPanelBundle(
                    toolActive = selectedTool == EditorTool.Mask,
                    eraseMode = maskEraseMode,
                    brushFraction = maskBrushFraction,
                    brushHardness = maskBrushHardness,
                    present = editorSession.solveMaskState.value != null,
                    onToolToggle = {
                        // Auto-Aktivierung durch den offenen „Vordergrund"-Tab (MaskPanel-LaunchedEffect):
                        // Maskenpinsel aktiv. Verlassen des Menüs setzt via activePanel-Guard zurück auf Bewegen.
                        selectedTool = EditorTool.Mask
                    },
                    onEraseModeChange = { maskEraseMode = it },
                    onBrushFractionChange = { maskBrushFraction = it },
                    onBrushHardnessChange = { maskBrushHardness = it },
                    onClear = {
                        editorSession.solveMaskState.value = null
                        editorSession.solveMaskVersionState.intValue++
                        AppDiagnostics.record("solve_mask_cleared")
                    },
                ),
                deepSkyAvailable = lastSolvedWcs != null,
                deepSkyObjects = deepSkyObjects,
                targetObjectQuery = targetObjectQuery,
                targetObjectResolved = targetObjectResolved,
                onAstapSolverChoiceChange = { choice ->
                    astapSolverChoice = choice
                    if (astapOperationState is AstapOperationState.Failure) {
                        astapOperationState = AstapOperationState.Idle
                    }
                    AppDiagnostics.record("astap_solver_choice solver=${choice.name}")
                },
                onNovaApiKeyChange = { novaApiKey = it.trim() },
                onTargetObjectQueryChange = { targetObjectQuery = it },
                annotateSelections = annotate,
                // VisualOnly: Sichtbarkeits-Schalter im Objekt-/Katalogmenü, keine neue Lösung.
                onApplyConstellations = { syncConstellationLayer(reason = ConstellationSyncReason.VisualOnly) },
                // Performance-Fix 2026-09-02: `record=false` (Live-Drag-Zwischenwert) löst seit diesem Fix
                // GAR KEINE Pipeline mehr aus (die betroffenen Regler committen entweder gar nicht mehr --
                // reine Darstellungsgrößen wirken über die reaktiven LaunchedEffect(annotate.xxx)-Blöcke
                // weiter unten, s. applyDeepSkyStyleToAll/applyDeepSkyOpacityLive-KDoc -- oder erst bei
                // record=true) -- `record=true` (Commit) läuft jetzt über requestDeepSkySync()
                // hintergrund-berechnet statt synchron im UI-Event. BEWUSST kein onDeepSkyStyleLive/
                // onDeepSkyOpacityLive-Parameter hier (MethodTooLargeException-Risiko an dieser Stelle
                // bereits 2x aufgetreten, s. Memory -- reaktiv statt durchgereicht vermeidet neue
                // Closure-Captures genau an diesem übervollen Aufruf).
                onApplyDeepSky = { record -> if (record) requestDeepSkySync() },
                onApplyStars = { record -> syncStarLayer(recordUndo = record) },
                onConstellationNamesChanged = {
                    // VisualOnly: nur showName ändert sich, die Geometrie bleibt identisch.
                    if (annotate.constellationsEnabled) {
                        syncConstellationLayer(reason = ConstellationSyncReason.VisualOnly)
                    }
                },
                eraserPanel = EraserPanelBundle(
                    active = selectedTool == EditorTool.EraseArea,
                    brushFraction = eraseBrushFraction,
                    brushHardness = eraseBrushHardness,
                    maskPresent = editorSession.annotationEraseMaskState.value != null,
                    onToggle = {
                        // Auto-Aktivierung durch den offenen „Beschriftung"-Tab (MaskPanel-LaunchedEffect):
                        // Beschriftungs-Radierer aktiv.
                        selectedTool = EditorTool.EraseArea
                    },
                    onBrushChange = { eraseBrushFraction = it },
                    onBrushHardnessChange = { eraseBrushHardness = it },
                    onClear = {
                        editorSession.annotationEraseMaskState.value = null
                        editorSession.annotationEraseVersionState.intValue++
                    },
                ),
                onAddConstellation = {
                    // "Sternbild hinzufügen" öffnet den virtuellen Sternhimmel (Sky-Picker),
                    // der das gewählte Sternbild platziert. Macht den alten Sternhimmel-Chip
                    // im Sternbild-Menü überflüssig.
                    activePanel = null
                    showSkyPicker = true
                },
                onStartFisheyeAlign = {
                    activePanel = null
                    startFisheyeAlign()
                },
                onRevertAstrometry = {
                    // Angewendete (Fehl-)Ausrichtung verwerfen -> exakter Stand direkt nach dem letzten
                    // Solve zurück (Mosaik/Projektionswahl/RMS/Kind/"Eigene" inklusive, nicht nur die WCS).
                    val baseline = postSolveCalibration
                    if (baseline != null) {
                        snapshotCalibrationForUndo()
                        applyCalibrationSnapshot(baseline)
                        AppDiagnostics.record("astrometry_reverted_to_original")
                        Toast.makeText(
                            context,
                            context.getString(R.string.toast_original_astrometry_restored),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.label_no_solved_astrometry),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                solveTileCount = solveTiles.size,
                onOpenCalibration = {
                    activePanel = null
                    calibrationActive = true
                    calibrationPopupHidden = false // „Ausrichten" holt das Popup ins zuletzt gewählte Segment zurück.
                },
                fisheyeFovLongDeg = fisheyeFovLongDeg,
                onFisheyeFovChange = {
                    fisheyeFovLongDeg = it
                    preferences.edit().putFloat("fisheye_fov_long_deg", it).apply()
                    // FOV geändert -> alten Seed verwerfen, damit "Feinjustierung starten" aus dem neuen
                    // FOV neu seedet (sonst zeigt die Feinjustierung weiter den alten/Auto-Seed).
                    fisheyeAlignSeed = null
                    // Live-Vorschau der Referenzsterne auf dem Bild aktualisieren (Slider blendet
                    // das Menü aus -> man sieht die Platzierung direkt und tastet sich an den FOV ran).
                    updateFisheyePreview()
                },
                onToolSelected = { tool ->
                    // Overlay-Typen (Sternbild/Form/Text) werden NICHT mehr per Werkzeugwahl
                    // scharf, sondern nur über "Hinzufügen" -> Einmal-Platzierung.
                    when (tool) {
                        EditorTool.Constellation, EditorTool.Ellipse,
                        EditorTool.Rectangle, EditorTool.Text -> armPlacement(tool)
                        else -> selectedTool = tool
                    }
                },
                onPickImage = {
                    activePanel = null
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onToggleStars = { showDetectedStars = !showDetectedStars },
                onToggleConstellationAnchors = {
                    applyConstellationStyle { ov ->
                        ov.copy(showAnchors = !(ov.showAnchors ?: showConstellationAnchors))
                    }
                },
                onToggleConstellationAnchorsGlobal = { showConstellationAnchors = !showConstellationAnchors },
                onStarDetectionSensitivityChange = { starDetectionSensitivity = it },
                onAstapCaptureSettingsChange = { value ->
                    astapCaptureSettings = value
                    clearSolvedIndicator()
                    if (astapOperationState is AstapOperationState.Failure) {
                        astapOperationState = AstapOperationState.Idle
                    }
                },
                onStartAstapSolve = { requestSingleImageSolve() },
                onCancelAstapSolve = ::cancelAstapSolve,
                constellationStyle = ConstellationStyleBundle(
                    colorArgb = constellationColorArgb,
                    font = constellationFont,
                    lineStyle = constellationLineStyle,
                    strokeWidth = constellationStrokeWidth,
                    anchorRadiusRatio = constellationAnchorRadiusRatio,
                    opacity = constellationOpacity,
                    showName = constellationShowName,
                    nameTextSize = constellationNameTextSize,
                    onOpacityChange = { value ->
                        constellationOpacity = value
                        applyConstellationStyle { it.copy(opacity = value) }
                    },
                    onColorChange = { value ->
                        constellationColorArgb = value
                        applyConstellationStyle { it.copy(colorArgb = value) }
                    },
                    onFontChange = { value ->
                        constellationFont = value
                        applyConstellationStyle { it.copy(font = value) }
                    },
                    onLineStyleChange = { value ->
                        constellationLineStyle = value
                        applyConstellationStyle { it.copy(lineStyle = value) }
                    },
                    onStrokeWidthChange = { value ->
                        constellationStrokeWidth = value
                        applyConstellationStyle { it.copy(strokeWidth = value) }
                    },
                    onAnchorRadiusChange = { value ->
                        constellationAnchorRadiusRatio = value
                        applyConstellationStyle { it.copy(anchorRadiusRatio = value) }
                    },
                    onShowNameChange = { value ->
                        constellationShowName = value
                        applyConstellationStyle { it.copy(showName = value) }
                    },
                    onNameTextSizeChange = { value ->
                        constellationNameTextSize = value
                        applyConstellationStyle { it.copy(nameTextSize = value) }
                    },
                    onOpacityChangeGlobal = { value ->
                        constellationOpacity = value
                        applyConstellationStyleToAll { it.copy(opacity = value) }
                    },
                    // NUR State setzen (billig, live für die Vorschau-Kachel) -- das tatsächliche Anwenden
                    // auf ALLE bestehenden Sternbild-Overlays läuft entprellt über einen LaunchedEffect
                    // weiter unten (s. dortiger Kommentar), sonst würde jedes Pixel einer Zieh-Geste im
                    // Farbwähler einen vollen Umfärbe-Durchlauf über alle Sternbild-Overlays auslösen.
                    onColorChangeGlobal = { value -> constellationColorArgb = value },
                    onFontChangeGlobal = { value ->
                        constellationFont = value
                        applyConstellationStyleToAll { it.copy(font = value) }
                    },
                    onLineStyleChangeGlobal = { value ->
                        constellationLineStyle = value
                        applyConstellationStyleToAll { it.copy(lineStyle = value) }
                    },
                    onStrokeWidthChangeGlobal = { value ->
                        constellationStrokeWidth = value
                        applyConstellationStyleToAll { it.copy(strokeWidth = value) }
                    },
                    onAnchorRadiusChangeGlobal = { value ->
                        constellationAnchorRadiusRatio = value
                        applyConstellationStyleToAll { it.copy(anchorRadiusRatio = value) }
                    },
                    onNameTextSizeChangeGlobal = { value ->
                        constellationNameTextSize = value
                        applyConstellationStyleToAll { it.copy(nameTextSize = value) }
                    },
                ),
                layerDrawOrder = layerDrawOrder,
                onLayerDrawOrderChange = { value -> layerDrawOrder = value },
                // Globale Standardfarben (Katalog bearbeiten -> Farben): DSO-Gruppen setzen den State
                // UND stoßen (entprellt, s. LaunchedEffect oben) syncDeepSkyLayer an. Reticle/Formen/
                // Formen-Namen/Text setzen NUR den State -- bewusst OHNE updateSelectedOverlay (anders
                // als onShapeColorChange direkt unten): das globale Menü hat keinen Objekt-Auswahl-
                // Kontext, wirkt nur als Default für künftig Neues.
                dsoGlobalColors = DsoGlobalColorBundle(
                    galaxyColorArgb = dsoGalaxyColorArgb,
                    onGalaxyColorChangeGlobal = { value -> dsoGalaxyColorArgb = value },
                    globularColorArgb = dsoGlobularColorArgb,
                    onGlobularColorChangeGlobal = { value -> dsoGlobularColorArgb = value },
                    openClusterColorArgb = dsoOpenClusterColorArgb,
                    onOpenClusterColorChangeGlobal = { value -> dsoOpenClusterColorArgb = value },
                    nebulaColorArgb = dsoNebulaColorArgb,
                    onNebulaColorChangeGlobal = { value -> dsoNebulaColorArgb = value },
                    otherColorArgb = dsoOtherColorArgb,
                    onOtherColorChangeGlobal = { value -> dsoOtherColorArgb = value },
                    reticleColorArgb = reticleColorArgb,
                    onReticleColorChangeGlobal = { value -> reticleColorArgb = value },
                ),
                shapeStyle = ShapeStyleBundle(
                    colorArgb = shapeColorArgb,
                    strokeWidth = shapeStrokeWidth,
                    lineStyle = shapeLineStyle,
                    opacity = shapeOpacity,
                    filled = shapeFilled,
                    isReticle = selectedTool == EditorTool.ReticleOpen ||
                        selectedTool == EditorTool.ReticleComet ||
                        overlays.firstOrNull { it.id == selectedOverlayId }?.reticle != null,
                    font = shapeFont,
                    showName = shapeShowName,
                    nameText = overlays.firstOrNull { it.id == selectedOverlayId }?.text ?: "",
                    nameTextSize = shapeNameTextSize,
                    nameColorArgb = shapeNameColorArgb,
                    nameBold = shapeNameBold,
                    onColorChange = { value ->
                        shapeColorArgb = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(colorArgb = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onStrokeWidthChange = { value ->
                        shapeStrokeWidth = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(strokeWidth = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onLineStyleChange = { value ->
                        shapeLineStyle = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(lineStyle = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onOpacityChange = { value ->
                        shapeOpacity = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(opacity = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onFilledChange = { value ->
                        shapeFilled = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(filled = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onFontChange = { value ->
                        shapeFont = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(font = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onShowNameChange = { value ->
                        shapeShowName = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(showName = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onEditName = { selectedOverlayId?.let { editingTextOverlayId = it } },
                    onNameTextSizeChange = { value ->
                        shapeNameTextSize = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(nameTextSize = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onNameColorChange = { value ->
                        shapeNameColorArgb = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(nameColorArgb = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onNameBoldChange = { value ->
                        shapeNameBold = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand) {
                                overlay.copy(textBold = value)
                            } else {
                                overlay
                            }
                        }
                    },
                    onColorChangeGlobal = { value -> shapeColorArgb = value },
                    onNameColorChangeGlobal = { value -> shapeNameColorArgb = value },
                ),
                textStyle = TextStyleBundle(
                    colorArgb = textColorArgb,
                    font = textFont,
                    size = textSize,
                    bold = textBold,
                    opacity = textOpacity,
                    onColorChange = { value ->
                        textColorArgb = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Text) overlay.copy(colorArgb = value) else overlay
                        }
                    },
                    onFontChange = { value ->
                        textFont = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Text) overlay.copy(font = value) else overlay
                        }
                    },
                    onSizeChange = { value ->
                        textSize = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Text) overlay.copy(size = Size(value * 5f, value)) else overlay
                        }
                    },
                    onBoldChange = { value ->
                        textBold = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Text) overlay.copy(textBold = value) else overlay
                        }
                    },
                    onOpacityChange = { value ->
                        textOpacity = value
                        updateSelectedOverlay { overlay ->
                            if (overlay.kind == OverlayKind.Text) overlay.copy(opacity = value) else overlay
                        }
                    },
                    onEditSelectedText = {
                        val selectedText = overlays.firstOrNull { it.id == selectedOverlayId && it.kind == OverlayKind.Text }
                        if (selectedText != null) {
                            activePanel = null
                            editingTextOverlayId = selectedText.id
                        }
                    },
                    onColorChangeGlobal = { value -> textColorArgb = value },
                ),
                onDetectStars = {
                    val sourceBitmap = currentBitmap
                    if (sourceBitmap == null) {
                        Toast.makeText(context, context.getString(R.string.toast_load_image_first), Toast.LENGTH_SHORT).show()
                    } else {
                        scope.launch {
                            isDetecting = true
                            AppDiagnostics.record(
                                "local_star_detection_started sensitivity=$starDetectionSensitivity " +
                                    "dimensions=${sourceBitmap.width}x${sourceBitmap.height}",
                            )
                            detectedStars = withContext(Dispatchers.Default) {
                                StarDetector.detect(sourceBitmap, starDetectionSensitivity)
                            }
                            showDetectedStars = true
                            isDetecting = false
                            AppDiagnostics.record("local_star_detection_finished count=${detectedStars.size}")
                        }
                    }
                },
                onOpenSkyPicker = {
                    activePanel = null
                    showSkyPicker = true
                    AppDiagnostics.record(
                        "sky_picker_opened constellation=${selectedConstellation.id} " +
                            "mirrorX=$selectedConstellationMirrorX mirrorY=$selectedConstellationMirrorY",
                    )
                },
                onShareExport = { exportScale, includeBackground, projectionMode, ssaaFactor ->
                    val sourceBitmap = currentBitmap
                    if (sourceBitmap == null) {
                        Toast.makeText(context, context.getString(R.string.toast_load_image_first), Toast.LENGTH_SHORT).show()
                    } else {
                        // Punkt 1 (Nutzer-Vorgabe 2026-08-30): Diagnose, welche Sprache der Export TATSÄCHLICH
                        // sieht -- vergleichbar mit den language_resync-Zeilen oben. Ein Beispiel-Overlay-Text
                        // (falls vorhanden) macht sichtbar, ob overlay.text bereits in dieser Sprache steht,
                        // ohne den vollen Bestand zu loggen (Diagnose-Datei-Budget).
                        AppDiagnostics.record(
                            "export_language lang=${AppLocale.resolvedLanguageTag} appLang=${AppLocale.current} " +
                                "sampleConstellationText=${overlays.firstOrNull { it.layer == AnnotationLayer.Constellation }?.text} " +
                                "sampleDeepSkyText=${overlays.firstOrNull { it.layer == AnnotationLayer.DeepSky }?.text}",
                        )
                        scope.launch {
                            val uri = withContext(Dispatchers.Default) {
                                // Original-Export: Bild in voller Auflösung neu dekodieren (Anzeige-Bitmap ist
                                // heruntergerechnet). Overlays in Anzeige-Koordinaten -> overlayCoordScale skaliert sie hoch.
                                val fullRes = if (exportScale == ExportScale.Original) decodeOriginalBitmap(context, imageUri) else null
                                val exportSource = fullRes ?: sourceBitmap
                                val coordScale = if (fullRes != null && sourceBitmap.width > 0) {
                                    fullRes.width.toFloat() / sourceBitmap.width
                                } else 1f
                                val grid = lastSolvedWcs?.takeIf { annotate.gridEnabled }?.let {
                                    GraticuleRenderer.compute(it, sourceBitmap.width, sourceBitmap.height, annotate.gridDensity)
                                }
                                val milkyWay = lastSolvedWcs?.takeIf { annotate.milkyWayEnabled }?.let {
                                    MilkyWayRenderer.compute(milkyWayLayers, it, sourceBitmap.width, sourceBitmap.height)
                                }
                                val result = ExportRenderer.renderToShareUri(
                                    context = context,
                                    source = exportSource,
                                    overlays = overlays.toList(),
                                    scale = exportScale,
                                    includeBackground = includeBackground,
                                    includeConstellationAnchors = showConstellationAnchors,
                                    annotationEraseMask = editorSession.annotationEraseMaskState.value,
                                    graticule = grid,
                                    graticuleThickness = annotate.gridThickness,
                                    graticuleColorArgb = annotate.gridColorArgb,
                                    graticuleOpacity = annotate.gridOpacity,
                                    graticuleShowLabels = annotate.gridShowLabels,
                                    milkyWay = milkyWay,
                                    milkyWayOpacity = annotate.milkyWayOpacity,
                                    imageBlurIntensity = annotate.imageBlurIntensity,
                                    imageGrayscale = annotate.imageGrayscale,
                                    imageInverted = annotate.imageInverted,
                                    overlayCoordScale = coordScale,
                                    layerDrawOrder = layerDrawOrder,
                                    imageFormat = exportImageFormat,
                                    wcs = lastSolvedWcs,
                                    projectionMode = projectionMode,
                                    textSupersampleFactor = ssaaFactor,
                                    lang = AppLocale.resolvedLanguageTag,
                                    sourceDiagnostics = ExportSourceDiagnostics(
                                        originalWidth = loadedImage?.originalWidth ?: sourceBitmap.width,
                                        originalHeight = loadedImage?.originalHeight ?: sourceBitmap.height,
                                        workingWidth = sourceBitmap.width,
                                        workingHeight = sourceBitmap.height,
                                        backgroundSource = if (fullRes != null) "ORIGINAL_FILE" else "WORKING_BITMAP",
                                        originalFileBytes = originalFileSizeBytes(context, imageUri),
                                    ),
                                )
                                if (fullRes != null && fullRes !== sourceBitmap) fullRes.recycle()
                                result
                            }
                            context.shareImage(
                                uri,
                                ExportRenderer.mimeTypeFor(ExportRenderer.effectiveFormat(exportImageFormat, includeBackground)),
                            )
                            AppDiagnostics.record(
                                "image_export_shared scale=${exportScale.name} background=$includeBackground overlays=${overlays.size}",
                            )
                            activePanel = null
                        }
                    }
                },
                onSaveExport = { exportScale, includeBackground, projectionMode, ssaaFactor ->
                    val sourceBitmap = currentBitmap
                    if (sourceBitmap == null) {
                        Toast.makeText(context, context.getString(R.string.toast_load_image_first), Toast.LENGTH_SHORT).show()
                    } else {
                        AppDiagnostics.record(
                            "export_language lang=${AppLocale.resolvedLanguageTag} appLang=${AppLocale.current} " +
                                "sampleConstellationText=${overlays.firstOrNull { it.layer == AnnotationLayer.Constellation }?.text} " +
                                "sampleDeepSkyText=${overlays.firstOrNull { it.layer == AnnotationLayer.DeepSky }?.text}",
                        )
                        scope.launch {
                            withContext(Dispatchers.Default) {
                                val fullRes = if (exportScale == ExportScale.Original) decodeOriginalBitmap(context, imageUri) else null
                                val exportSource = fullRes ?: sourceBitmap
                                val coordScale = if (fullRes != null && sourceBitmap.width > 0) {
                                    fullRes.width.toFloat() / sourceBitmap.width
                                } else 1f
                                val grid = lastSolvedWcs?.takeIf { annotate.gridEnabled }?.let {
                                    GraticuleRenderer.compute(it, sourceBitmap.width, sourceBitmap.height, annotate.gridDensity)
                                }
                                val milkyWay = lastSolvedWcs?.takeIf { annotate.milkyWayEnabled }?.let {
                                    MilkyWayRenderer.compute(milkyWayLayers, it, sourceBitmap.width, sourceBitmap.height)
                                }
                                ExportRenderer.saveToPictures(
                                    context = context,
                                    source = exportSource,
                                    overlays = overlays.toList(),
                                    scale = exportScale,
                                    includeBackground = includeBackground,
                                    includeConstellationAnchors = showConstellationAnchors,
                                    annotationEraseMask = editorSession.annotationEraseMaskState.value,
                                    graticule = grid,
                                    graticuleThickness = annotate.gridThickness,
                                    graticuleColorArgb = annotate.gridColorArgb,
                                    graticuleOpacity = annotate.gridOpacity,
                                    graticuleShowLabels = annotate.gridShowLabels,
                                    milkyWay = milkyWay,
                                    milkyWayOpacity = annotate.milkyWayOpacity,
                                    imageBlurIntensity = annotate.imageBlurIntensity,
                                    imageGrayscale = annotate.imageGrayscale,
                                    imageInverted = annotate.imageInverted,
                                    overlayCoordScale = coordScale,
                                    layerDrawOrder = layerDrawOrder,
                                    imageFormat = exportImageFormat,
                                    wcs = lastSolvedWcs,
                                    projectionMode = projectionMode,
                                    textSupersampleFactor = ssaaFactor,
                                    lang = AppLocale.resolvedLanguageTag,
                                    sourceDiagnostics = ExportSourceDiagnostics(
                                        originalWidth = loadedImage?.originalWidth ?: sourceBitmap.width,
                                        originalHeight = loadedImage?.originalHeight ?: sourceBitmap.height,
                                        workingWidth = sourceBitmap.width,
                                        workingHeight = sourceBitmap.height,
                                        backgroundSource = if (fullRes != null) "ORIGINAL_FILE" else "WORKING_BITMAP",
                                        originalFileBytes = originalFileSizeBytes(context, imageUri),
                                    ),
                                )
                                if (fullRes != null && fullRes !== sourceBitmap) fullRes.recycle()
                            }
                            Toast.makeText(context, context.getString(R.string.toast_export_saved), Toast.LENGTH_SHORT).show()
                            AppDiagnostics.record(
                                "image_export_saved scale=${exportScale.name} background=$includeBackground overlays=${overlays.size}",
                            )
                            activePanel = null
                        }
                    }
                },
                onShareAppDiagnostic = ::shareFullAppDiagnostic,
                onExportSolveCrop = {
                    val src = currentBitmap
                    if (src == null) {
                        Toast.makeText(context, context.getString(R.string.toast_load_image_first), Toast.LENGTH_SHORT).show()
                    } else {
                        val isFisheye = astapCaptureSettings.captureType == AstapCaptureType.Fisheye
                        // Diagnose-Crop: erste Kachel (Bounding-Box) hat Vorrang, sonst Fisheye-Center,
                        // sonst ganzes Bild. (1:1 mit dem, was der Solver bekommt.)
                        val region = solveTiles.firstOrNull()?.let { solveTileBoundingBox(it) }
                        scope.launch {
                            var cropLog = ""
                            withContext(Dispatchers.Default) {
                                val crop = when {
                                    region != null -> {
                                        val rx = region.left.roundToInt().coerceIn(0, src.width - 1)
                                        val ry = region.top.roundToInt().coerceIn(0, src.height - 1)
                                        val rw = region.width.roundToInt().coerceIn(64, src.width - rx)
                                        val rh = region.height.roundToInt().coerceIn(64, src.height - ry)
                                        cropLog = "region=true off=$rx,$ry size=${rw}x${rh}"
                                        Bitmap.createBitmap(src, rx, ry, rw, rh)
                                    }
                                    isFisheye -> {
                                        val frac = PanoramaSolver.CENTER_CROP_FRACTION
                                        val cw = (src.width * frac).roundToInt().coerceAtLeast(64).coerceAtMost(src.width)
                                        val ch = (src.height * frac).roundToInt().coerceAtLeast(64).coerceAtMost(src.height)
                                        val cx = (src.width - cw) / 2
                                        val cy = (src.height - ch) / 2
                                        cropLog = "region=false off=$cx,$cy size=${cw}x${ch}"
                                        Bitmap.createBitmap(src, cx, cy, cw, ch)
                                    }
                                    else -> {
                                        cropLog = "region=false off=0,0 size=${src.width}x${src.height}"
                                        src
                                    }
                                }
                                // Reines Bild (keine Overlays) speichern = exakt der gelöste Ausschnitt.
                                ExportRenderer.saveToPictures(
                                    context = context,
                                    source = crop,
                                    overlays = emptyList(),
                                    scale = ExportScale.Original,
                                    includeBackground = true,
                                    includeConstellationAnchors = false,
                                )
                                if (crop !== src) crop.recycle()
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_solved_crop_saved),
                                Toast.LENGTH_SHORT,
                            ).show()
                            AppDiagnostics.record("solve_crop_exported fisheye=$isFisheye $cropLog")
                            activePanel = null
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
        // Einheitlicher Popup-Container für ALLE Panels (gemeinsame Logik, siehe AppBottomPopup):
        // unten verankert, antippbarer Scrim zum Schließen, Slider-Hide. EditorPanelSheet bringt
        // eigenen Scroll + Kopfzeile + Höhen-Deckel mit.
        AppBottomPopup(
            onDismiss = { activePanel = null },
            sliderDragging = sliderDragging,
            onSliderDraggingChange = { sliderDragging = it },
        ) {
            panelContent()
        }
      }
    }

    if (showSkyPicker) {
        SkyPickerDialog(
            selected = selectedConstellation,
            mirrorX = selectedConstellationMirrorX,
            mirrorY = selectedConstellationMirrorY,
            catalog = catalog,
            milkyWayLayers = milkyWayLayers,
            skyStars = skyCatalogStars,
            deepSkyObjects = deepSkyObjects,
            d3Settings = d3CatalogSettings,
            onDismiss = {
                AppDiagnostics.record("sky_picker_dismissed")
                showSkyPicker = false
            },
            onPreviewSelect = { pattern ->
                AppDiagnostics.record("sky_picker_constellation_selected id=${pattern.id}")
                selectedConstellation = pattern
                preferences.edit()
                    .putString("last_constellation_id", pattern.id)
                    .apply()
            },
            onToggleMirrorX = { selectedConstellationMirrorX = !selectedConstellationMirrorX },
            onToggleMirrorY = { selectedConstellationMirrorY = !selectedConstellationMirrorY },
            onD3SettingsChange = ::updateD3CatalogSettings,
            onPlace = { pattern ->
                AppDiagnostics.record("sky_picker_constellation_picked id=${pattern.id}")
                selectedConstellation = pattern
                preferences.edit()
                    .putString("last_constellation_id", pattern.id)
                    .apply()
                showSkyPicker = false
                // Nicht mittig setzen, sondern Tap-to-place scharf schalten (Position antippen).
                armPlacement(EditorTool.Constellation)
            },
        )
    }

    if (showPlacementMenu) {
        // Unten verankert + M3-Optik wie alle Popups (kein zentrierter AlertDialog mehr); Schließen
        // per Tipp ins Freie/Back. Kein „Abbrechen"-Button nötig (konsistent, kein X).
        AppBottomPopup(onDismiss = { showPlacementMenu = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MenuHeader(title = context.getString(R.string.action_place_object))
                Button(
                    onClick = { showPlacementMenu = false; showSkyPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_constellation)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.Text) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_text)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.Ellipse) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_circle_ellipse)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.Rectangle) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_rectangle)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.ReticleOpen) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_crosshair_open)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.ReticleComet) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.label_comet_marker)) }
                Button(
                    onClick = { showPlacementMenu = false; armPlacement(EditorTool.Draw) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(context.getString(R.string.action_draw)) }
            }
        }
    }

    if (fisheyeAlignActive) {
        val seed = fisheyeAlignSeed
        val source = bitmap
        if (seed != null && source != null) {
            FisheyeAlignScreen(
                bitmap = source,
                seed = seed,
                brightStars = fisheyeAlignBright,
                // Voller Namens-Katalog zum Suchen + manuellen Platzieren einzelner Sterne.
                allNamedStars = skyCatalogStars.filter { it.properName.isNotBlank() },
                detectedStars = fisheyeAlignBlobs,
                projectionKind = alignProjectionKind,
                seedRms = alignProjectionRms,
                onApply = { fit, rms ->
                    // Vor-Zustand sichern -> Anwenden ist ein globaler Undo/Redo-Schritt.
                    snapshotCalibrationForUndo()
                    // Die vom Nutzer manuell auf echte Sterne gezogenen Referenzen bleiben Goldstandard:
                    // als "Eigene" in der Projektionsleiste wählbar, jederzeit ohne Neu-Fit wieder einsetzbar.
                    customAlignFit = fit
                    customAlignRms = rms
                    customAlignKind = alignProjectionKind
                    // Die vom Nutzer bestätigten Referenzsterne zusätzlich für die Sichtfeld-Kappung
                    // bekanntmachen (isNearAnyAnchor, alle 3 sync*Layer) -- die kannte sonst nur die
                    // automatisch gelösten Kacheln, nie die Feinjustierung (Nutzerbeobachtung
                    // 2026-07-24). BEWUSST NICHT in panoSolveAnchors/-Weights: das wäre eine Fit-
                    // Eingabe und würde die Feinjustierung (Goldstandard -- Nutzer hat den Stern
                    // selbst visuell auf seine echte Position gezogen) bei einem künftigen Neu-Fit
                    // (Reprojektion/Nachschärfung) wieder mit schwächeren Kachel-Ankern verrechnen und
                    // verwässern (Nutzer-Korrektur 2026-07-24). Eigene, reine Sichtfeld-Liste stattdessen.
                    fisheyeConfirmedAnchorDirs = fisheyeAlignRefs.orEmpty()
                        .filter { it.active && !it.deleted }
                        .map { ref ->
                            raDecToVector(
                                ref.star.point.raDegrees.toDouble(),
                                ref.star.point.decDegrees.toDouble(),
                            )
                        }
                    // Feinjustierung ist Goldstandard und geht den Kacheln bewusst vor (Nutzer-
                    // Entscheidung 2026-07-29): manche Aufnahmen sind an den Rändern so stark verzeichnet,
                    // dass die Kachelmethode dort selbst bei Status "gelöst" schlechter greift als vom
                    // Nutzer selbst auf echte Sterne gezogene Referenzen. Rückkehr zu einer Projektionsart
                    // (Chip-Leiste) zeigt weiterhin deren eigene Kachel-geschützte Lösung; von dort wieder
                    // zu "Eigene" wechseln stellt genau diesen Fit erneut her (kein Neu-Fit nötig).
                    lastSolvedWcs = fit
                    fisheyeBaseFit = fit
                    fisheyeAlignSeed = fit // angewendeten Fit als neuen Seed merken (Re-Entry startet dort)
                    alignProjectionRms = rms
                    panoProjectionChoice = "Custom"
                    annotate.constellationsEnabled = true
                    syncConstellationLayer(recordUndo = false)
                    syncDeepSkyLayer(recordUndo = false)
                    syncStarLayer(recordUndo = false)
                    fisheyeAlignActive = false
                    AppDiagnostics.record(
                        "fisheye_align_applied rms=${rms?.let { "%.1f".format(it) } ?: "?"}",
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_fine_align_applied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onClose = { fisheyeAlignActive = false },
                // Beim Verlassen ohne Anwenden: letzten Fit als Seed sichern -> Re-Entry zeigt die
                // zuletzt gesetzte Ausrichtung (statt wieder beim Roh-Seed zu beginnen).
                onPersistFit = { fit -> fisheyeAlignSeed = fit },
                // Referenz-Zustand exakt merken -> Wieder-Öffnen ohne neues Solve zeigt dieselben Sterne.
                initialRefs = fisheyeAlignRefs,
                onPersistRefs = { refs -> fisheyeAlignRefs = refs },
            )
        } else {
            fisheyeAlignActive = false
        }
    }

    pendingTextPosition?.let { position ->
        TextOverlayDialog(
            initialText = "",
            onDismiss = { pendingTextPosition = null },
            onConfirm = { text ->
                editorSession.recordHistory()
                // Default-Textgröße an die Bildgröße koppeln (fixe 42 px sind auf großen Bildern winzig).
                val defaultTextHeight = bitmap
                    ?.let { (min(it.width, it.height) * 0.045f).coerceAtLeast(48f) }
                    ?: textSize
                overlays += AnnotationOverlay(
                    id = nextOverlayId++,
                    kind = OverlayKind.Text,
                    center = position,
                    size = Size(defaultTextHeight * 5f, defaultTextHeight),
                    text = text.ifBlank { "M31" },
                    colorArgb = textColorArgb,
                    strokeWidth = 1f,
                    opacity = textOpacity,
                    textBold = textBold,
                    font = textFont,
                ).snapshotTinySkyGeometryIfActive()
                selectedOverlayId = overlays.last().id
                pendingTextPosition = null
                dsoNeighborRecheckTick++
            },
        )
    }

    editingTextOverlayId?.let { id ->
        // Auch für Ellipse/Rechteck nutzbar (Namens-Schwänzchen-Text) -- derselbe Dialog wie für
        // Freitext-Overlays, keine zweite Text-Eingabe-UI.
        val overlay = overlays.firstOrNull {
            it.id == id &&
                (
                    it.kind == OverlayKind.Text || it.kind == OverlayKind.Ellipse ||
                        it.kind == OverlayKind.Rectangle || it.kind == OverlayKind.Freehand
                    )
        }
        if (overlay == null) {
            editingTextOverlayId = null
        } else {
            TextOverlayDialog(
                initialText = overlay.text,
                onDismiss = { editingTextOverlayId = null },
                onConfirm = { text ->
                    val index = overlays.indexOfFirst { it.id == id }
                    if (index >= 0) {
                        editorSession.recordHistory()
                        val current = overlays[index]
                        // Beim ersten Eintippen eines Namens für eine Form direkt sichtbar schalten,
                        // damit der Nutzer nicht zusätzlich noch den Schalter suchen muss.
                        val isShape = current.kind == OverlayKind.Ellipse || current.kind == OverlayKind.Rectangle ||
                            current.kind == OverlayKind.Freehand
                        val newText = text.ifBlank { current.text }
                        // Kollisionsfreie Erst-Platzierung NUR beim allerersten Benennen (labelLeaderPx
                        // noch beim "nie verändert"-Sentinel, s. OverlayGeometry.findManualShapeLabelPlacement-
                        // Kommentar) -- ein späteres Umbenennen/Verschieben tastet eine vom Nutzer per
                        // Hand nachjustierte Position nicht an (Nutzerwunsch 2026-08-20).
                        val placement = if (isShape && current.labelLeaderPx <= 0f && newText.isNotBlank()) {
                            OverlayGeometry.findManualShapeLabelPlacement(
                                current.copy(text = newText),
                                overlays.filter { it.id != current.id },
                                bitmap?.width?.toFloat() ?: Float.MAX_VALUE,
                                bitmap?.height?.toFloat() ?: Float.MAX_VALUE,
                            )
                        } else {
                            null
                        }
                        overlays[index] = current.copy(
                            text = newText,
                            showName = if (isShape) true else current.showName,
                            labelAngleDeg = placement?.labelAngleDeg ?: current.labelAngleDeg,
                            labelLeaderPx = placement?.labelLeaderPx ?: current.labelLeaderPx,
                        )
                        if (isShape) shapeShowName = true
                    }
                    editingTextOverlayId = null
                },
            )
        }
    }

    if (showRegionInfoDialog) {
        RegionInfoDialog(
            overlays = overlays,
            deepSkyObjects = deepSkyObjects,
            catalogStars = referenceCatalogStars.ifEmpty { skyCatalogStars },
            lang = AppLocale.resolvedLanguageTag,
            onDismiss = { showRegionInfoDialog = false },
        )
    }

    tileInfoDialogId?.let { tileId ->
        val tile = solveTiles.firstOrNull { it.id == tileId }
        val src = bitmap
        if (tile == null || src == null) {
            tileInfoDialogId = null
        } else {
            // Zentrum-Himmelsposition + Bildfeld dieser Kachel aus ihrer eigenen (Crop-lokalen) WCS —
            // dieselbe Geometrie, die auch tileAnchorPairs() und solvedFovLongDeg() sonst verwenden.
            val bb = solveTileBoundingBox(tile)
            val tileW = bb.width.roundToInt().coerceIn(64, src.width)
            val tileH = bb.height.roundToInt().coerceIn(64, src.height)
            val centerSky = tile.wcs?.imageToSky(tileW / 2.0, tileH / 2.0, tileH)
            val fovDeg = tile.wcs?.let { w -> solvedFovLongDeg(w, tileW, tileH) }
            TileInfoDialog(
                tile = tile,
                medianErrorPx = tileQualityById[tileId],
                ownAccuracy = tileOwnRmsById[tileId],
                minError = (TileConsistency.MIN_ERROR_FRACTION * maxOf(src.width, src.height)).toDouble(),
                centerRaDeg = centerSky?.raDegrees,
                centerDecDeg = centerSky?.decDegrees,
                fovDeg = fovDeg,
                onDismiss = { tileInfoDialogId = null },
            )
        }
    }

    manualHintDialogTileId?.let { tileId ->
        val tile = solveTiles.firstOrNull { it.id == tileId }
        if (tile == null) {
            manualHintDialogTileId = null
        } else {
            ManualHintDialog(
                initialText = tile.manualHintStarName ?: "",
                referenceCatalogStars = referenceCatalogStars,
                skyCatalogStars = skyCatalogStars,
                onDismiss = { manualHintDialogTileId = null },
                onClear = {
                    val i = solveTiles.indexOfFirst { it.id == tileId }
                    if (i >= 0) solveTiles[i] = solveTiles[i].copy(manualHintStarName = null)
                    manualHintDialogTileId = null
                },
                onConfirm = { name ->
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) {
                        manualHintDialogTileId = null
                    } else {
                        val found = findStarByName(trimmed, referenceCatalogStars, skyCatalogStars)
                        if (found == null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.star_not_found, trimmed),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            val i = solveTiles.indexOfFirst { it.id == tileId }
                            if (i >= 0) solveTiles[i] = solveTiles[i].copy(manualHintStarName = trimmed)
                            manualHintDialogTileId = null
                        }
                    }
                },
            )
        }
    }

    // Unit 6 (2026-09-02): Positions-Hinweis-Dialog für den EINZELBILD-Solve -- reine Wiederverwendung
    // von ManualHintDialog/findStarByName (dieselbe Mechanik wie manualHintDialogTileId oben), self-
    // contained hier direkt in StarMapperApp() platziert (KEIN neuer EditorPanelSheet-Parameter, s.
    // project_methodtoolarge_editorpanelsheet in Memory -- die 67-Parameter-Reduktion bleibt unberührt).
    // initialText immer leer: jede Öffnung ist eine frische, unabhängige Wahl, kein persistierter
    // Zustand wie bei der Kachel (deshalb ist "Entfernen" hier nie sichtbar, s. ManualHintDialog).
    if (showSingleSolveHintDialog) {
        ManualHintDialog(
            initialText = "",
            referenceCatalogStars = referenceCatalogStars,
            skyCatalogStars = skyCatalogStars,
            onDismiss = {
                // Schließen des HINWEIS-Dialogs (Zurück/Antippen daneben/"Abbrechen") ist NICHT dasselbe
                // wie einen laufenden Solve abzubrechen -- der Hinweis wird nur übersprungen, der Solve
                // startet trotzdem, nur blind.
                showSingleSolveHintDialog = false
                singleSolveHintPoint = null
                AppDiagnostics.record("single_solve_hint_skipped fallback=blind")
                startSingleImageSolve()
            },
            onClear = {
                showSingleSolveHintDialog = false
                singleSolveHintPoint = null
                AppDiagnostics.record("single_solve_hint_skipped fallback=blind")
                startSingleImageSolve()
            },
            onConfirm = { name ->
                val trimmed = name.trim()
                if (trimmed.isBlank()) {
                    showSingleSolveHintDialog = false
                    singleSolveHintPoint = null
                    AppDiagnostics.record("single_solve_hint_skipped fallback=blind")
                    startSingleImageSolve()
                } else {
                    val found = findStarByName(trimmed, referenceCatalogStars, skyCatalogStars)
                    if (found == null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.star_not_found, trimmed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        singleSolveHintPoint = found.point
                        showSingleSolveHintDialog = false
                        AppDiagnostics.record(
                            "single_solve_hint_selected ra=${found.point.raDegrees} dec=${found.point.decDegrees} " +
                                "radiusDeg=${astapFieldOfView.toDouble().coerceIn(5.0, 90.0)} source=manual",
                        )
                        startSingleImageSolve()
                    }
                }
            },
        )
    }

    referenceOverlayId?.let { id ->
        val overlay = overlays.firstOrNull { it.id == id && it.kind == OverlayKind.Constellation }
        val pattern = overlay?.constellation
        if (pattern == null) {
            referenceOverlayId = null
        } else {
            ConstellationReferenceDialog(
                pattern = pattern,
                skyStars = referenceCatalogStars.ifEmpty { skyCatalogStars },
                deepSkyObjects = deepSkyObjects,
                milkyWayLayers = milkyWayLayers,
                mirrorX = overlay.mirrorX,
                mirrorY = overlay.mirrorY,
                onDismiss = { referenceOverlayId = null },
            )
        }
    }
}

@Composable
private fun EmptyEditor(
    modifier: Modifier,
    onPickImage: () -> Unit,
    onShareDiagnostic: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                // NICHT R.mipmap.ic_launcher: seit 0.19.0 ein <adaptive-icon>-XML (mipmap-anydpi-v26)
                // -> painterResource() crasht darauf ("Only VectorDrawables and rasterized asset
                // types are supported"). app_logo ist eine eigene, einfache PNG-Ressource nur für
                // UI-Zwecke (unabhängig vom Adaptive-Icon-Foreground, das jetzt eingerückt ist).
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(86.dp).clip(CircleShape),
            )
            Text(
                text = "Map my Sky",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Your mobile annotation tool",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Button(onClick = onPickImage) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_choose_image))
            }
            TextButton(onClick = onShareDiagnostic) {
                Text(stringResource(R.string.action_share_app_diagnostics))
            }
        }
    }
}

// Spitze/Radiergummi-Position im Stift-Cursor-Bild (res/drawable/pencil_cursor.png), als Anteil der
// Bildgröße -- vermessen aus dem vom Nutzer gelieferten Icon (scratchpad/process_pencil.py: reddish-
// Pixel-Schwerpunkt für den Radiergummi, entferntester dunkler Pixel dazu für die Spitze). Daraus
// werden Drehwinkel + Größe für den Zeichnen-Cursor live berechnet (s. EditorCanvas-Zeichen-Vorschau).
private const val PENCIL_TIP_FRACTION_X = 0.01730f
private const val PENCIL_TIP_FRACTION_Y = 0.98734f
private const val PENCIL_ERASER_FRACTION_X = 0.87473f
private const val PENCIL_ERASER_FRACTION_Y = 0.10610f
// Vermessen (Farbwechsel dunkel->hell entlang der Spitze-Radiergummi-Achse, s. Analyse-Skript):
// die WIRKLICH schwarze Spitzen-Fläche endet bei ca. 9-10% der Gesamtlänge, danach folgt die
// helle Holz-Anspitzung. Bruchteil der Gesamtlänge -> skaliert automatisch mit der Cursor-Größe.
private const val PENCIL_TIP_CLIP_FRACTION = 0.09f

@Composable
private fun EditorCanvas(
    modifier: Modifier,
    bitmap: Bitmap,
    overlays: List<AnnotationOverlay>,
    previewOverlays: List<AnnotationOverlay>,
    previewDetectedStars: List<DetectedStar>,
    previewMarkers: List<Pair<String, Offset>> = emptyList(),
    graticule: GraticuleGeometry? = null,
    graticuleThickness: Float = 0.0012f,
    graticuleColorArgb: Long = 0xFF8FD8FFL,
    graticuleOpacity: Float = 0.7f,
    graticuleShowLabels: Boolean = true,
    milkyWay: MilkyWayGeometry? = null,
    milkyWayOpacity: Float = 0.5f,
    layerDrawOrder: List<DrawLayer> = DEFAULT_DRAW_LAYER_ORDER,
    imageBlurIntensity: Float = 0f,
    imageGrayscale: Boolean = false,
    imageInverted: Boolean = false,
    selectedTool: EditorTool,
    selectedOverlayId: Long?,
    selectedConstellation: ConstellationPattern,
    detectedStars: List<DetectedStar>,
    showDetectedStars: Boolean,
    showConstellationAnchors: Boolean,
    activePanel: EditorPanel?,
    maskBitmap: Bitmap?,
    maskVersion: Int,
    maskBrushFraction: Float = 0.04f,
    eraseBrushFraction: Float = 0.06f,
    maskBrushHardness: Float = 1f,
    eraseBrushHardness: Float = 1f,
    brushPreviewActive: Boolean = false,
    annotationEraseMask: Bitmap? = null,
    annotationEraseVersion: Int = 0,
    annotationErasePreviewVersion: Int = 0,
    solvedTileKeys: Set<Pair<Int, Int>>,
    onMaskStroke: (Offset, Offset) -> Unit,
    onAnnotationEraseStroke: (Offset, Offset) -> Unit = { _, _ -> },
    solveTiles: List<SolveTile> = emptyList(),
    // Nur Kacheln mit passendem sourceSpace sind in der aktuell aktiven Ansicht sichtbar/anfassbar (Plan
    // Nachtrag 3) -- exakt wie beim Wechsel zu einem komplett anderen Foto, keine Sonderbehandlung.
    activeTileSpace: TileSourceSpace = TileSourceSpace.Native,
    // Periodische 360°-Naht der Tiny-Sky-Ansicht (nur dort != null, s. Berechnungsstelle in
    // StarMapperApp). Reine Bedienhilfe: rote Linie + Kachel-Snap. Wird NIE in Overlays, Projekte
    // oder Export übernommen.
    tinySkySeam: TileDeWarp.TinySkySeam? = null,
    // Kachel-Zuverlässigkeits-Signale (s. TileConsistency), NUR für die Warnmarkierung unten in der
    // Kachel-Zeichen-Schleife -- beeinflussen nichts an der eigentlichen Fit-/Solve-Logik hier.
    tileOwnRmsById: Map<Long, TileOwnAccuracy> = emptyMap(),
    tileOverlapRmsById: Map<Long, TileConsistency.OverlapAccuracy> = emptyMap(),
    selectedSolveTileId: Long? = null,
    tilesLocked: Boolean = false,
    calibrationActive: Boolean = false,
    tilesVisible: Boolean = true,
    onTileEditBegin: () -> Unit = {},
    onAddSolveTile: (Offset, Size) -> Unit = { _, _ -> },
    onSelectSolveTile: (Long?) -> Unit = {},
    onUpdateSolveTile: (SolveTile, GeometryEditKind) -> Unit = { _, _ -> },
    onDeleteSolveTile: (Long) -> Unit = {},
    // De-Warp-Wunsch DIESER Kachel umschalten (ohne eine vorhandene Lösung zu invalidieren).
    onToggleTileDewarp: (Long) -> Unit = {},
    onPlaceNextTile: () -> Unit = {},
    // Öffnet den Sternname-Hinweis-Dialog für diese Kachel (manueller Positions-Hinweis).
    onOpenManualHint: (Long) -> Unit = {},
    // Öffnet das Info-Popup (Status/De-Warp/Qualität) für diese Kachel.
    onOpenTileInfo: (Long) -> Unit = {},
    onSelectOverlay: (Long?) -> Unit,
    onEditingOverlay: (Long, OverlayKind) -> Unit,
    onEditOverlayText: (Long) -> Unit,
    onExitEditing: () -> Unit,
    // Tipp auf leere Bildfläche (nichts getroffen) -> z. B. Kalibrierungs-Popup ausblenden.
    onEmptyTap: () -> Unit = {},
    onAddDetectedStar: (DetectedStar) -> Unit,
    onRemoveDetectedStar: (Int) -> Unit,
    onRemoveOverlay: (Long) -> Unit,
    onOpenConstellationReference: (Long) -> Unit,
    onTransformOverlay: (Long, Offset, Float, Float) -> Unit,
    onUpdateOverlay: (AnnotationOverlay, GeometryEditKind) -> Unit,
    onCreateOverlay: (EditorTool, Offset) -> Unit,
    // „Fertig zeichnen" gedrückt: alle in dieser Sitzung gesammelten Freihand-SEGMENTE (Bild-px,
    // je ein Strich zwischen Zweitfinger-Drücken/-Loslassen) committen.
    onCreateFreehandOverlay: (List<List<Offset>>) -> Unit = {},
    // Langdruck-Button "Weiterzeichnen" auf einem Freihand-Overlay: Stift erneut aktivieren, um
    // weiterzumalen oder Teile davon wegzuradieren (statt es nur ganz löschen zu können).
    onContinueDrawing: (AnnotationOverlay) -> Unit = {},
    // Aktueller Formen-Stil (Popup-Regler) fürs Zeichnen-Live-Vorschau (WYSIWYG, s. allFreehandSegments).
    shapeColorArgb: Long = 0xFFFFD28A,
    shapeStrokeWidth: Float = 1.5f,
    shapeLineStyle: OverlayLineStyle = OverlayLineStyle.Solid,
    pendingPlacement: EditorTool? = null,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    onModeActiveChange: (Boolean) -> Unit = {},
    // Erhöht sich, wenn die Aktions-Bubble „Fertig verschieben" gedrückt wurde -> Modus beenden.
    exitMoveModeRequest: Int = 0,
    // Erhöht sich, wenn die Aktions-Bubble „Fertig zeichnen" gedrückt wurde -> Zeichnen-Sitzung
    // committen + beenden (s. onCreateFreehandOverlay).
    exitDrawModeRequest: Int = 0,
    // Rechts-/Linkshänder (Einstellungen): spiegelt den Stift-Greifversatz + das Stift-Bild.
    leftHandedDrawing: Boolean = false,
    // Fertige Zeichnen-Segmente dieser Sitzung, AUTORITATIV in EditorSessionViewModel gehalten (für
    // globales Undo/Redo, ein Strich = ein Schritt). Lokal wird zusätzlich `allSegments` geführt (für
    // den heißen Gesten-Pfad, s. u.) und per LaunchedEffect mit diesem Parameter synchronisiert.
    drawSegments: List<List<Offset>> = emptyList(),
    // Finger B gedrückt (neuer Strich, Zeichnen ODER Radieren) -> Aufrufer sichert den aktuellen
    // Stand für Undo (editorSession.snapshotDrawSegments()).
    onDrawStrokeBegin: () -> Unit = {},
    // Strich fertig (Finger B oder A losgelassen) -> Aufrufer übernimmt den neuen Stand.
    onDrawSegmentsCommitted: (List<List<Offset>>) -> Unit = {},
    // Radius (Bild-px des ÜBERGEBENEN bitmap) der automatisch einzuzoomenden Scheibe, z.B. die
    // Himmel-Halbkugel der Tiny-Sky-Ganzansicht (s. TileDeWarp.stereographicSkyDiscRadius) -- null
    // für die normale 2:1-Ansicht (kein Auto-Zoom). EditorCanvas selbst weiß nichts über Tiny Sky,
    // bekommt nur "zoome auf einen Kreis mit diesem Radius, zentriert im Bild" mitgeteilt.
    tinySkyAutoFitRadiusPx: Float? = null,
) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // BUGFIX Tiny-Sky-Verschieben (Plan Nachtrag 3): `overlays` ist ein Parameter, der in Tiny Sky bei
    // jeder Rekomposition als FRISCHE Listen-Kopie hereinkommt (displayOverlays beim Aufrufer, wegen der
    // Cross-Projektions-Umrechnung nach wie vor nötig, s. dort). Die Gesten-Coroutine unten läuft über
    // eine ganze -- inzwischen sogar über mehrere -- Gesten in derselben Coroutine weiter (s. den
    // geometriefreien pointerInput-Key oben); ein direkter Zugriff auf `overlays` dort würde für die
    // gesamte Lebensdauer der Coroutine auf dem beim letzten (Re-)Start aktuellen Listen-Objekt
    // "einfrieren" statt die eigene vorherige Änderung zu sehen -> Verschieben akkumuliert nicht (exakt
    // derselbe Fehler wie bei den Kacheln, nur dort durch die Architektur-Vereinfachung gegenstandslos).
    // rememberUpdatedState hält bei jedem Lesen automatisch den jeweils aktuellen Parameterwert bereit.
    val liveOverlays by rememberUpdatedState(overlays)
    // Einmaliges Einzoomen auf die Kreis-Scheibe (s. tinySkyAutoFitRadiusPx oben), sobald canvasSize
    // bekannt ist -- ein Kreis füllt in einem umschließenden Quadrat rechnerisch nie mehr als ~78,5%
    // der Fläche; ohne dies bliebe die Himmel-Scheibe trotz Fix D (s. TileDeWarp) nur zentriert-aber-
    // kleiner als nötig. Analog zum Stift-Zentrieren-Muster oben (LaunchedEffect(pendingPlacement,
    // canvasSize)): EINMALIG pro neuem Bitmap gesetzt, kein Live-Folgen -- danach frei zoom-/pan-bar.
    LaunchedEffect(bitmap, canvasSize, tinySkyAutoFitRadiusPx) {
        val discRadius = tinySkyAutoFitRadiusPx
        if (discRadius != null && discRadius > 0f && canvasSize != IntSize.Zero) {
            val baseScale = min(
                canvasSize.width / bitmap.width.toFloat(),
                canvasSize.height / bitmap.height.toFloat(),
            )
            val targetZoom = (bitmap.width.toFloat() / (2f * discRadius)).coerceAtLeast(1f)
            zoom = targetZoom
            pan = Offset(
                (bitmap.width * baseScale / 2f) * (1f - targetZoom),
                (bitmap.height * baseScale / 2f) * (1f - targetZoom),
            )
        }
    }
    var editingOverlayId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Langdruck-Aktionsbuttons (Verschieben/Bearbeiten/[Text]/Löschen) für dieses Overlay sichtbar.
    var actionOverlayId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Bild-Koordinate des Fingerdrucks, an dem die Aktionsbuttons erscheinen sollen (statt am Objekt).
    var actionAnchorImage by remember(bitmap) { mutableStateOf<Offset?>(null) }
    // „Verschieben" aktiv für dieses Overlay -> ganzes Objekt folgt dem Finger, pink.
    var moveOverlayId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // „Anker verschieben" aktiv für dieses Sternbild -> nur Ankerringe/Linien ziehbar (umformen).
    var anchorEditOverlayId by remember(bitmap) { mutableStateOf<Long?>(null) }
    // Live gezeichnetes Solve-Bereich-Rechteck (Bild-px), während das SolveRegion-Werkzeug aktiv ist.
    var solveRegionDraft by remember(bitmap) { mutableStateOf<Rect?>(null) }
    // ZEICHNEN (Zweifinger-Stift, dauerhafter Modus): Sitzungs-Zustand, überlebt beliebig viele
    // Greif-/Auslöser-Zyklen bis „Fertig zeichnen". penPositionImg = Bild-Koordinate der Stift-SPITZE
    // (übersteht Pan/Zoom), null solange der Modus noch nie betreten wurde (wird dann auf die
    // Sichtfeld-Mitte gesetzt). allSegments = alle in dieser Sitzung fertiggestellten Striche;
    // activeSegment = der gerade laufende Strich (nicht-null nur während Finger B hält und nicht
    // radiert wird) -- getrennt gehalten wie zuvor freehandDraftPoints, damit jeder neue Punkt eine
    // Neuzeichnung auslöst.
    var penPositionImg by remember(bitmap) { mutableStateOf<Offset?>(null) }
    var penIsEraser by remember(bitmap) { mutableStateOf(false) }
    var allSegments by remember(bitmap) { mutableStateOf(drawSegments) }
    // Extern geändert (Undo/Redo-Klick, oder Rundlauf der eigenen onDrawSegmentsCommitted-Meldung) ->
    // lokalen Stand synchron halten. Während eines laufenden Strichs wird NICHT über diesen Parameter
    // mutiert (das passiert direkt auf `allSegments`, s. handleDrawMode) -- nur externe Änderungen
    // fließen hier zurück, daher kein Konflikt mit dem heißen Gesten-Pfad.
    LaunchedEffect(drawSegments) { allSegments = drawSegments }
    var activeSegment by remember(bitmap) { mutableStateOf<List<Offset>?>(null) }
    // Doppel-Tipp-Erkennung fürs Stift-Umdrehen (Bleistift<->Radiergummi): Zeit/Position des letzten
    // QUALIFIZIERENDEN Tipps (kein Zug, kurz gehalten) -- überlebt einzelne Greif-Vorgänge, da ein
    // Doppel-Tipp aus zwei GETRENNTEN Greif-Vorgängen besteht.
    var lastPenTapUpTimeMs by remember(bitmap) { mutableStateOf(-1L) }
    var lastPenTapUpScreenPos by remember(bitmap) { mutableStateOf<Offset?>(null) }
    // Bildschirm-Position von Finger B (Auslöser), solange er hält -- für den Puls-Indikator; null
    // wenn B nicht hält.
    var drawTriggerScreenPos by remember(bitmap) { mutableStateOf<Offset?>(null) }
    // Pink pulsierender Hinweis auf das gerade per Long-Press gezogene Overlay.
    var draggingOverlayId by remember(bitmap) { mutableStateOf<Long?>(null) }
    val overlayDragPulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
    )
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val handleHitRadius = with(density) { 24.dp.toPx() }
    val rotationHandleOffset = with(density) { 38.dp.toPx() }
    val fixedControlInset = with(density) { 42.dp.toPx() }
    val fixedControlSpacing = with(density) { 56.dp.toPx() }
    val fixedControlHitRadius = with(density) { 36.dp.toPx() }
    val starDeleteHitRadius = with(density) { 22.dp.toPx() }
    // Virtueller Zeichen-Stift (EditorTool.Draw): die Spitze (zeichnet) liegt versetzt vom Finger,
    // der am "hinteren Ende" (Radiergummi-Seite) greift, damit der Finger die Zeichenstelle nie
    // verdeckt. Rechtshänder: Finger unten-rechts der Spitze. Linkshänder spiegelt NUR die
    // X-Komponente -> Finger unten-links der Spitze (Einstellungen-Panel, Design-Entscheidung 10).
    val handSign = if (leftHandedDrawing) -1f else 1f
    val freehandPenOffset = with(density) { Offset(-28.dp.toPx() * handSign, (-56).dp.toPx()) }
    // Radius um den Stift-Greif-Hotspot (hinteres Ende), innerhalb dessen ein Down-Ereignis die
    // Stift-Rolle (A) zugewiesen bekommt -- wiederverwendet handleHitRadius (gleiche Größenordnung
    // wie andere Griff-Ziele in diesem Editor).
    val penGrabHitRadius = handleHitRadius
    // Radius (Bildschirm-px), innerhalb dessen der Radiergummi-Modus Punkte der eigenen, gerade
    // gezeichneten Linie entfernt.
    val penEraseRadiusPx = with(density) { 16.dp.toPx() }
    // Nächster Zeichen-Punkt wird erst übernommen, wenn er mindestens so weit vom letzten entfernt
    // liegt -- verhindert hunderte Punkte pro Sekunde ohne sichtbaren Qualitätsverlust.
    val freehandDecimateMinPx = with(density) { 3.dp.toPx() }
    // Fängt einen frei gezeichneten Strich automatisch ein, wenn sein Ende beim Loslassen wieder
    // nahe am eigenen Anfang liegt (Lasso-artiges Auto-Schließen, s. closeFreehandSegmentIfNearStart).
    val freehandCloseSnapPx = with(density) { 20.dp.toPx() }
    // Stift-Grafik für den Zeichnen-Cursor (Nutzer-Icon, res/drawable/pencil_cursor.png).
    val pencilCursorImage = ImageBitmap.imageResource(id = R.drawable.pencil_cursor)

    // PERFORMANCE (Fix 1, 2026-08-21): viewport bewusst NICHT mehr hier (Composition-Phase) berechnet --
    // jeder Pan/Zoom-Tick hätte sonst die gesamte ~1780-Zeilen-Funktion neu komponiert statt nur billig
    // neu zu zeichnen. Stattdessen Lambda-lokal im Canvas{}-Zeichenblock unten (reine Zeichenphase) und an
    // den 3 Ausnahmestellen (LaunchedEffect + die 2 Aktions-Button-Reihen außerhalb des Zeichenblocks)
    // jeweils frisch inline berechnet -- billig genug (ein paar Float-Multiplikationen), Cachen unnötig.

    // PERFORMANCE: das Software-`bitmap` (für Solve/Crop/Export/Pixelzugriff nötig) wird zusätzlich
    // einmalig in eine GPU-Hardware-Kopie überführt, die nur zum BILDSCHIRM-Zeichnen dient. Damit
    // lädt die GPU die Textur einmal statt jedes Frame ein 100-MB-Software-Bitmap neu zu sampeln.
    // Fällt die Kopie aus (null), wird unverändert das Software-Bitmap gezeichnet.
    // Weichzeichnen (0.19.0) läuft VOR der Hardware-Kopie auf dem Software-Bitmap (ImageEffects.blur);
    // Graustufen/Invertieren laufen dagegen als billiger ColorFilter beim Zeichnen (kein Bitmap-Umbau).
    val displayImage = remember(bitmap, imageBlurIntensity) {
        val minDim = min(bitmap.width, bitmap.height).toFloat()
        val blurRadiusPx = imageBlurIntensity * minDim * 0.03f
        val blurred = if (blurRadiusPx > 0.5f) ImageEffects.blur(bitmap, blurRadiusPx) else bitmap
        val hw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { blurred.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
        } else {
            null
        }
        (hw ?: blurred).asImageBitmap()
    }
    val imageColorFilter = remember(imageGrayscale, imageInverted) {
        if (!imageGrayscale && !imageInverted) {
            null
        } else {
            val matrix = ColorMatrix()
            if (imageInverted) {
                matrix *= ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                )
            }
            if (imageGrayscale) {
                matrix *= ColorMatrix().apply { setToSaturation(0f) }
            }
            ColorFilter.colorMatrix(matrix)
        }
    }
    // Offscreen-Cache für ALLE statischen Overlays (Sternbilder/Marker/Text) inkl. Radierer-Stanze.
    // Wird nur neu gerendert, wenn sich der Inhalt ändert (nicht bei Pan/Zoom) -> keine
    // Vektor-Neuberechnung pro Frame; pro Frame nur ein drawImage des Caches. Auflösung nahe am
    // Bild, aber gedeckelt (Speicher/Texturgröße). Eigenes Bitmap = isolierter Stanz-Zielpuffer.
    // EIN Cache je Overlay-Ziel-Schicht (Constellation/Objects/Star, s. DrawLayer) statt EINEM
    // gemeinsamen -- notwendig, damit Milchstraße/Gradnetz auch ZWISCHEN zwei Overlay-Schichten
    // einsortiert werden können (frei konfigurierbare Reihenfolge, s. layerDrawOrder). LAZY angelegt
    // (erst bei tatsächlichem Inhalt, s. drawOverlayBucket) -- eine leere Kategorie (z.B. nie
    // eingeblendete Einzelsterne) kostet dann NIE ihr eigenes Cache-Bitmap (~bis zu 38 MB je Kategorie
    // bei OVERLAY_CACHE_MAX_LONG_EDGE).
    val overlayCaches = remember(bitmap) { HashMap<DrawLayer, OverlayCache>(3) }
    val overlayCacheSigs = remember(bitmap) { HashMap<DrawLayer, String>(3) }
    // PERFORMANCE (Fix 2, 2026-08-21, Ruckeln bei 2+ gleichzeitig aktiven Overlay-Schichten): jede aktive
    // Overlay-Zielschicht (Constellation/Objects/Star) bekam bisher ihren EIGENEN, fast bildauflösungs-
    // großen Cache -- jeder Frame zeichnete jede aktive Schicht per eigenem drawImage(...), die GPU-
    // Füllraten-/Bandbreitenkosten skalierten mit der ANZAHL gleichzeitig aktiver Schichten (nicht mit der
    // Objektzahl darin). Da es nur 3 solcher Schichten gibt, kann es nie mehr als EINEN Mehrfach-Lauf
    // (>=2 davon direkt hintereinander in layerDrawOrder) gleichzeitig geben -- eine Map mit max. 1
    // Eintrag reicht. Absichtlich NICHT einfach "erst alle zusammen zeichnen, dann einmal die
    // Radierer-Maske stanzen" (das wäre bei einer gefederten Maske -- Härte-Regler < 1 -- NICHT
    // pixelgleich zum heutigen Verhalten, s. drawCombinedOverlayRun) -- stattdessen jedes Lauf-Mitglied
    // per eigenem saveLayer/restoreToCount (identisches Muster wie ExportRenderer.kt) isoliert gestanzt,
    // dann erst zusammengesetzt -- exakt dieselbe Reihenfolge Zeichnen->Stanzen->Zusammensetzen wie
    // heute, nur einmalig bei Änderung statt jeden Frame.
    val combinedRunCaches = remember(bitmap) { HashMap<List<DrawLayer>, OverlayCache>(1) }
    val combinedRunCacheSigs = remember(bitmap) { HashMap<List<DrawLayer>, String>(1) }
    // PERFORMANCE (2026-08-21, Ruckeln bei Pan/Zoom auf überlagerungsreichen Panoramen): Milchstraßen-/
    // Gradnetz-Ringe bzw. -Linien ändern sich bei REINEM Pan/Zoom NICHT (nur der Blickausschnitt bewegt
    // sich), wurden aber trotzdem bei JEDEM Zeichen-Frame Punkt für Punkt neu transformiert -- skaliert
    // mit der Punktzahl der Geometrie, bei weitwinkligen Panoramen spürbar als Ruckeln. Gemerkt wird
    // jeweils die zuletzt gesehene Quelle (Referenzvergleich `===`) + ihr Ergebnis; erst bei einer
    // tatsächlich NEUEN Quelle wird neu gerechnet (s. Verwendung weiter unten). WICHTIG, per Regression
    // selbst erlebt: dasselbe Muster ist für `overlays` NICHT sicher anwendbar, obwohl auch dessen
    // Gruppierung (s. groupByDrawLayer weiter unten) bei Pan/Zoom unverändert bliebe -- `overlays` ist
    // eine `mutableStateListOf` (EditorSessionViewModel.overlays), deren Referenz sich beim Befüllen
    // (z.B. nach einem Solve, s. syncDeepSkyLayer) oder bei Einzel-Edits NIE ändert, nur der Inhalt. Ein
    // `===`-Vergleich auf `overlays` selbst hätte daher NIE einen Treffer verfehlt -- nach dem ersten
    // Zeichnen (typischerweise mit noch leerer/kaum gefüllter Liste) wäre die Gruppierung für immer
    // eingefroren geblieben, ALLE künftigen Overlays (Sternbilder/Objekte nach dem Solve, jede spätere
    // Änderung) wären unsichtbar geblieben. `graticuleGeometry`/`milkyWayGeometry` (unten) sind dagegen
    // `remember(...)`-berechnete WERTE, die bei echter inhaltlicher Änderung eine NEUE Objekt-Referenz
    // bekommen -- dort ist derselbe `===`-Vergleich korrekt.
    var milkyWayPathsMemo by remember(bitmap) {
        mutableStateOf<Pair<MilkyWayGeometry, List<Pair<Int, List<androidx.compose.ui.graphics.Path>>>>?>(null)
    }
    var graticulePathsMemo by remember(bitmap) {
        mutableStateOf<Pair<GraticuleGeometry, List<androidx.compose.ui.graphics.Path>>?>(null)
    }
    // PERFORMANCE (Nachtrag 4, 2026-08-22, Ruckeln bei sehr hoher Overlay-Zahl, z.B. 600 Sterne): anders
    // als milkyWayPathsMemo/graticulePathsMemo oben braucht diese Gruppierung einen INHALTS-Hash statt
    // `===` -- `overlays` ist dieselbe dauerhaft in-place mutierte `mutableStateListOf` wie bei
    // groupedOverlays weiter unten (s. dortiger Kommentar), ein `===`-Vergleich auf sie selbst würde nach
    // dem ersten Zeichnen für immer einfrieren (bereits einmal als Regression erlebt).
    var groupedOverlaysMemo by remember(bitmap) {
        mutableStateOf<Pair<Int, Map<DrawLayer, List<AnnotationOverlay>>>?>(null)
    }

    // Verschiebe-/Anker-Modus aktiv -> Eltern sperren das Menü (nur Zoom/Pan bleiben erlaubt).
    LaunchedEffect(moveOverlayId, anchorEditOverlayId) {
        onModeActiveChange(moveOverlayId != null || anchorEditOverlayId != null)
    }
    // „Fertig verschieben" aus der Aktions-Bubble beendet den Modus (ersetzt den früheren grünen Balken).
    LaunchedEffect(exitMoveModeRequest) {
        if (exitMoveModeRequest > 0) {
            moveOverlayId = null
            anchorEditOverlayId = null
        }
    }
    // Zeichnen-Modus betreten: Stift sofort sichtbar an der AKTUELLEN Sichtfeld-Mitte -- NICHT erst bei
    // der ersten Geste. Bei JEDEM erneuten Aktivieren neu zentriert (nicht nur beim allerersten Mal
    // überhaupt), damit der Stift auch nach zwischenzeitlichem Pan/Zoom im gerade sichtbaren Ausschnitt
    // auftaucht statt an seiner alten, evtl. jetzt weit entfernten Bild-Position (Nutzerwunsch
    // 2026-08-22). Während einer laufenden Zeichen-Sitzung selbst bleibt der Stift unangetastet -- zoom/
    // pan sind bewusst KEINE Keys, nur ein einmaliger Schnappschuss bei Eintritt in den Modus.
    LaunchedEffect(pendingPlacement, canvasSize) {
        if (pendingPlacement == EditorTool.Draw && canvasSize != IntSize.Zero) {
            val launchViewport = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)
            penPositionImg = launchViewport.screenToImage(Offset(canvasSize.width / 2f, canvasSize.height / 2f))
        }
    }
    // „Fertig zeichnen" aus der Aktions-Bubble: alle in dieser Sitzung gesammelten Segmente (inkl.
    // eines evtl. noch laufenden, nie regulär per Finger-B-Loslassen beendeten Segments) als EIN
    // Overlay committen und die Sitzung zurücksetzen. pendingPlacement=null (Modus verlassen) setzt
    // onCreateFreehandOverlay selbst, exakt wie die übrigen Platzieren-Werkzeuge.
    LaunchedEffect(exitDrawModeRequest) {
        if (exitDrawModeRequest > 0) {
            val trailing = activeSegment
            val finalSegments = if (trailing != null && trailing.size >= 2) {
                val vp = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)
                val closed = OverlayGeometry.closeFreehandSegmentIfNearStart(trailing, freehandCloseSnapPx / vp.scale)
                allSegments + listOf(closed)
            } else {
                allSegments
            }
            onCreateFreehandOverlay(finalSegments)
            allSegments = emptyList()
            activeSegment = null
            penIsEraser = false
            lastPenTapUpTimeMs = -1L
            lastPenTapUpScreenPos = null
            drawTriggerScreenPos = null
        }
    }

    fun openOverlayEditing(overlay: AnnotationOverlay) {
        // Kein Auto-Zoom mehr auf das Objekt (Nutzerwunsch) – nur Auswahl + Bearbeiten-Menü öffnen.
        editingOverlayId = overlay.id
        onEditingOverlay(overlay.id, overlay.kind)
    }

    Box(
        modifier = modifier
            // Transparent statt Schwarz: der geblurte Bild-Hintergrund scheint in den Letterbox-Rändern
            // (rund um das eingepasste Bild) durch, statt harter schwarzer Balken.
            .background(Color.Transparent)
            .onSizeChanged { canvasSize = it }
            .pointerInput(
                bitmap,
                selectedTool,
                // WICHTIG: pendingPlacement muss ein Key sein, sonst hält die laufende Gesten-Coroutine
                // einen veralteten (null) Wert fest -> Tap-to-place setzt nichts (Auswahl ginge aber).
                pendingPlacement,
                // Gleicher Grund: die Zeichnen-Geste liest die Hand-Einstellung für den Greif-Hotspot;
                // ohne Key bliebe eine bereits laufende Gesten-Coroutine auf der alten Hand stehen.
                leftHandedDrawing,
                selectedOverlayId,
                editingOverlayId,
                // NICHT die volle overlays-Liste als Key: in Tiny Sky ist die hier ANKOMMENDE Liste
                // (displayOverlays beim Aufrufer) bei jeder Cross-Projektions-Umrechnung eine frisch
                // allozierte Kopie mit geänderter Geometrie -- jede einzelne Zieh-Bewegung
                // (onUpdateOverlay pro Pointer-Event) hätte die Liste dadurch strukturell verändert und
                // den Key pro Frame wechseln lassen -> Compose bricht die laufende Gesten-Coroutine
                // MITTEN in der Geste ab und startet neu (Symptom: Objekt "springt" nach einem Frame und
                // lässt sich danach nicht mehr sauber ziehen; für den Bewegen-Fall zusätzlich per
                // rememberUpdatedState/liveOverlays abgesichert, s. dort). Stattdessen ein geometriefreier
                // Schlüssel, der nur wechselt, wenn tatsächlich ein Objekt hinzukommt/verschwindet --
                // ID/Kind ändern sich bei reinem Verschieben/Skalieren/Rotieren nie. `solveTiles` ist seit
                // Plan Nachtrag 3 ohnehin immer die kanonische, stabile Liste (keine Cross-Projektions-
                // Kopie mehr) -- der geometriefreie Key dort ist nicht mehr strikt nötig, aber als
                // einheitliches "Neustart nur bei echtem Hinzufügen/Entfernen"-Muster beibehalten.
                overlays.map { it.id to it.kind },
                detectedStars,
                showDetectedStars,
                showConstellationAnchors,
                touchSlop,
                handleHitRadius,
                rotationHandleOffset,
                solveTiles.map { it.id to it.sourceSpace },
                selectedSolveTileId,
                tilesLocked,
                calibrationActive,
            ) {
                fun currentViewport() = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)

                fun resolveTarget(screenPoint: Offset): EditorGestureTarget {
                    val mid = moveOverlayId
                    if (mid != null) {
                        // Verschiebe-Modus: jede Geste bewegt NUR dieses Overlay; nichts anderes
                        // ist Ziel (kein Resize/Rotate/Anchor/anderes Objekt).
                        return if (liveOverlays.any { it.id == mid }) {
                            EditorGestureTarget.OverlayBody(mid)
                        } else {
                            EditorGestureTarget.FreeImage
                        }
                    }
                    val anchorMode = anchorEditOverlayId
                    if (anchorMode != null) {
                        // Anker-Modus: nur Ankerringe dieses Sternbilds ziehbar (Linien folgen),
                        // sonst nur Zoom/Pan.
                        val t = resolveEditorGestureTarget(
                            screenPoint = screenPoint,
                            canvasSize = canvasSize,
                            overlays = liveOverlays,
                            viewport = currentViewport(),
                            selectedOverlayId = anchorMode,
                            editingOverlayId = anchorMode,
                            showConstellationAnchors = true,
                            handleHitRadius = handleHitRadius,
                            fixedControlInsetPx = fixedControlInset,
                            fixedControlSpacingPx = fixedControlSpacing,
                            fixedControlHitRadiusPx = fixedControlHitRadius,
                            rotationHandleOffsetPx = rotationHandleOffset,
                        )
                        return if (t is EditorGestureTarget.ConstellationAnchor && t.id == anchorMode) {
                            t
                        } else {
                            EditorGestureTarget.FreeImage
                        }
                    }
                    val resolved = resolveEditorGestureTarget(
                        screenPoint = screenPoint,
                        canvasSize = canvasSize,
                        overlays = liveOverlays,
                        viewport = currentViewport(),
                        selectedOverlayId = selectedOverlayId,
                        editingOverlayId = editingOverlayId,
                        showConstellationAnchors = showConstellationAnchors,
                        handleHitRadius = handleHitRadius,
                        fixedControlInsetPx = fixedControlInset,
                        fixedControlSpacingPx = fixedControlSpacing,
                        fixedControlHitRadiusPx = fixedControlHitRadius,
                        rotationHandleOffsetPx = rotationHandleOffset,
                    )
                    // Anker sind NUR im Anker-Modus ziehbar -> sonst wie Objekt-Body behandeln.
                    if (resolved is EditorGestureTarget.ConstellationAnchor) {
                        return EditorGestureTarget.OverlayBody(resolved.id)
                    }
                    // Sind die Langdruck-Aktionsbuttons offen, sind ANDERE Objekte gesperrt
                    // (nur das aktive Objekt + leerer Bereich reagieren).
                    val aid = actionOverlayId
                    if (aid != null) {
                        val hitId = when (resolved) {
                            is EditorGestureTarget.OverlayBody -> resolved.id
                            is EditorGestureTarget.ConstellationAnchor -> resolved.id
                            is EditorGestureTarget.ResizeHandle -> resolved.id
                            is EditorGestureTarget.RotateHandle -> resolved.id
                            is EditorGestureTarget.LabelHandle -> resolved.id
                            is EditorGestureTarget.ConstellationNameHandle -> resolved.id
                            is EditorGestureTarget.FixedReference -> resolved.id
                            else -> null
                        }
                        if (hitId != null && hitId != aid) return EditorGestureTarget.FreeImage
                    }
                    return resolved
                }

                fun applyDrag(target: EditorGestureTarget, currentPoint: Offset, screenDelta: Offset) {
                    when (target) {
                        EditorGestureTarget.FreeImage -> {
                            when (selectedTool) {
                                EditorTool.Mask -> currentViewport().let { vp ->
                                    onMaskStroke(vp.screenToImage(currentPoint - screenDelta), vp.screenToImage(currentPoint))
                                }
                                EditorTool.EraseArea -> currentViewport().let { vp ->
                                    onAnnotationEraseStroke(vp.screenToImage(currentPoint - screenDelta), vp.screenToImage(currentPoint))
                                }
                                else -> pan += screenDelta
                            }
                        }
                        is EditorGestureTarget.OverlayBody -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                onSelectOverlay(overlay.id)
                                val imageDelta = dragViewport.screenDeltaToImage(screenDelta)
                                // Kein Magnet beim Ziehen mehr (verursachte Hin-und-Her-Springen,
                                // wenn Text über Objekte gezogen wurde) -> Text frei bewegen.
                                onUpdateOverlay(translateOverlay(overlay, imageDelta), GeometryEditKind.Move)
                            }
                        }
                        is EditorGestureTarget.ConstellationAnchor -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                val rawImagePoint = dragViewport.screenToImage(currentPoint)
                                val snappedPoint = nearestDetectedStar(rawImagePoint, detectedStars, dragViewport, 42f)
                                    ?: rawImagePoint
                                onUpdateOverlay(
                                    overlay.copy(
                                        anchorOverrides = overlay.anchorOverrides + (target.anchorIndex to snappedPoint),
                                        // Anker verschoben -> vorprojizierte (gekrümmte) Kanten passen nicht
                                        // mehr; verwerfen, damit gerade Kanten zwischen den neuen Ankern
                                        // gezeichnet werden und die Linien dem Anker FOLGEN.
                                        edgePolylines = null,
                                    ),
                                    GeometryEditKind.AnchorPoint,
                                )
                            }
                        }
                        is EditorGestureTarget.ResizeHandle -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                onUpdateOverlay(
                                    resizeOverlayFromScreenPoint(overlay, currentPoint, dragViewport, liveOverlays),
                                    GeometryEditKind.Resize,
                                )
                            }
                        }
                        is EditorGestureTarget.RotateHandle -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                onUpdateOverlay(
                                    rotateOverlayFromScreenPoint(overlay, currentPoint, dragViewport),
                                    GeometryEditKind.Rotate,
                                )
                            }
                        }
                        is EditorGestureTarget.LabelHandle -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                val imagePoint = dragViewport.screenToImage(currentPoint)
                                val handle = OverlayGeometry.labelHandleFromDrag(overlay, imagePoint - overlay.center)
                                onUpdateOverlay(
                                    overlay.copy(
                                        labelAngleDeg = handle.labelAngleDeg,
                                        labelLeaderPx = handle.labelLeaderPx,
                                    ),
                                    GeometryEditKind.FieldOnly,
                                )
                            }
                        }
                        is EditorGestureTarget.ConstellationNameHandle -> {
                            val dragViewport = currentViewport()
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                val imageDelta = dragViewport.screenDeltaToImage(screenDelta)
                                val base = overlay.constellationNameOffset ?: Offset.Zero
                                onUpdateOverlay(
                                    overlay.copy(constellationNameOffset = base + imageDelta),
                                    GeometryEditKind.ConstellationName,
                                )
                            }
                        }
                        is EditorGestureTarget.FixedDelete,
                        is EditorGestureTarget.FixedReference -> Unit
                    }
                }

                fun handleTap(target: EditorGestureTarget, tap: Offset) {
                    // Verschiebe-/Anker-Modus werden über den „Fertig"-Button beendet, nicht per Tipp.
                    if (moveOverlayId != null || anchorEditOverlayId != null) return
                    // Sind die Aktionsbuttons offen: Tipp auf das aktive Objekt = nichts;
                    // Tipp woanders schließt die Buttons (andere Objekte bleiben gesperrt).
                    val openActionId = actionOverlayId
                    if (openActionId != null) {
                        val sameObject = (target as? EditorGestureTarget.OverlayBody)?.id == openActionId
                        if (!sameObject) {
                            actionOverlayId = null
                            onSelectOverlay(null)
                        }
                        return
                    }
                    val tapViewport = currentViewport()
                    val imagePoint = tapViewport.screenToImage(tap)
                    val bodyHitId = (target as? EditorGestureTarget.OverlayBody)?.id
                    when {
                        target is EditorGestureTarget.FixedReference -> onOpenConstellationReference(target.id)
                        target is EditorGestureTarget.FixedDelete -> {
                            editingOverlayId = null
                            onRemoveOverlay(target.id)
                        }
                        selectedTool == EditorTool.Erase && bodyHitId != null -> onRemoveOverlay(bodyHitId)
                        selectedTool == EditorTool.Mask -> onMaskStroke(imagePoint, imagePoint)
                        selectedTool == EditorTool.EraseArea -> onAnnotationEraseStroke(imagePoint, imagePoint)
                        selectedTool == EditorTool.DeleteStar && showDetectedStars -> {
                            val starIndex = nearestDetectedStarIndex(imagePoint, detectedStars, tapViewport, starDeleteHitRadius)
                            if (starIndex != null) onRemoveDetectedStar(starIndex)
                        }
                        selectedTool == EditorTool.AddStar -> onAddDetectedStar(
                            DetectedStar(
                                x = imagePoint.x.coerceIn(0f, bitmap.width.toFloat()),
                                y = imagePoint.y.coerceIn(0f, bitmap.height.toFloat()),
                                radius = 4f,
                                score = 1f,
                            ),
                        )
                        target is EditorGestureTarget.ConstellationAnchor ||
                            target is EditorGestureTarget.ResizeHandle ||
                            target is EditorGestureTarget.RotateHandle ||
                            target is EditorGestureTarget.LabelHandle -> Unit
                        // Antippen des Sternbildnamens OHNE Ziehen = manuellen Versatz zurücksetzen
                        // (Rückkehr zur automatischen Position). Ziehen selbst läuft über applyDrag.
                        target is EditorGestureTarget.ConstellationNameHandle -> {
                            liveOverlays.firstOrNull { it.id == target.id }?.let { overlay ->
                                if (overlay.constellationNameOffset != null) {
                                    AppDiagnostics.record("constellation_name_reset id=${overlay.id}")
                                    onUpdateOverlay(
                                        overlay.copy(constellationNameOffset = null),
                                        GeometryEditKind.ConstellationName,
                                    )
                                }
                            }
                        }
                        // Bewusstes Platzieren (Einmal-Modus): nur wenn vorher "Hinzufügen"
                        // gedrückt wurde. Ohne pendingPlacement setzt ein Tipp NICHTS.
                        pendingPlacement != null -> onCreateOverlay(pendingPlacement, imagePoint)
                        bodyHitId != null -> onSelectOverlay(bodyHitId)
                        editingOverlayId != null -> {
                            editingOverlayId = null
                            onSelectOverlay(null)
                            onExitEditing()
                        }
                        else -> onEmptyTap()
                    }
                }

                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleMultiTouchViewport(
                    initialPressed: List<PointerInputChange>,
                ) {
                    if (initialPressed.size < 2) return
                    var previousCentroid = (initialPressed[0].position + initialPressed[1].position) / 2f
                    var previousDistance = (initialPressed[0].position - initialPressed[1].position).getDistance().coerceAtLeast(1f)
                    initialPressed.forEach { it.consume() }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size < 2) {
                            event.changes.forEach { if (it.pressed) it.consume() }
                            while (event.changes.any { it.pressed }) {
                                val rest = awaitPointerEvent()
                                rest.changes.forEach { if (it.pressed) it.consume() }
                                if (rest.changes.none { it.pressed }) break
                            }
                            return
                        }

                        val first = pressed[0].position
                        val second = pressed[1].position
                        val centroid = (first + second) / 2f
                        val distance = (first - second).getDistance().coerceAtLeast(1f)
                        val zoomChange = (distance / previousDistance).coerceIn(0.78f, 1.28f)
                        val viewportBefore = currentViewport()
                        val imageFocus = viewportBefore.screenToImage(previousCentroid)
                        val newZoom = (zoom * zoomChange).coerceIn(0.45f, 24f)
                        val newViewportBase = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, newZoom, Offset.Zero)
                        pan = centroid - newViewportBase.offset - imageFocus * newViewportBase.scale
                        zoom = newZoom
                        event.changes.forEach { it.consume() }
                        previousCentroid = centroid
                        previousDistance = distance
                    }
                }

                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.continueSingleDrag(
                    target: EditorGestureTarget,
                    pointerId: androidx.compose.ui.input.pointer.PointerId,
                    startPoint: Offset,
                ) {
                    var previousPoint = startPoint
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) return
                        if (pressed.size >= 2) {
                            handleMultiTouchViewport(pressed)
                            return
                        }
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: pressed.first()
                        val currentPoint = change.position
                        if (!change.pressed) return
                        if (change.isConsumed) {
                            previousPoint = currentPoint
                            continue
                        }
                        val dragAmount = currentPoint - previousPoint
                        previousPoint = currentPoint
                        if (dragAmount.getDistance() <= 0f) continue
                        applyDrag(target, currentPoint, dragAmount)
                        change.consume()
                    }
                }

                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleAfterLongPress(
                    target: EditorGestureTarget,
                    pointerId: androidx.compose.ui.input.pointer.PointerId,
                    holdPoint: Offset,
                ) {
                    var previousPoint = holdPoint
                    var dragTotal = Offset.Zero
                    var dragStarted = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            handleMultiTouchViewport(pressed)
                            return
                        }
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        val currentPoint = change?.position ?: previousPoint
                        if (change == null || !change.pressed) {
                            if (!dragStarted) {
                                when (target) {
                                    is EditorGestureTarget.FixedDelete,
                                    is EditorGestureTarget.FixedReference -> handleTap(target, holdPoint)
                                    is EditorGestureTarget.OverlayBody -> {
                                        // Langdruck öffnet NICHT mehr direkt das Menü, sondern zeigt
                                        // die runden Aktionsbuttons am Objekt. Im Verschiebe-Modus
                                        // (moveOverlayId gesetzt) NICHT zurück ins Button-Menü.
                                        if (moveOverlayId == null) {
                                            onSelectOverlay(target.id)
                                            actionOverlayId = target.id
                                            // Buttons dort zeigen, wo der Finger war (Bild-Koordinate).
                                            actionAnchorImage = currentViewport().screenToImage(holdPoint)
                                        }
                                    }
                                    is EditorGestureTarget.ConstellationAnchor,
                                    is EditorGestureTarget.ResizeHandle,
                                    is EditorGestureTarget.RotateHandle,
                                    is EditorGestureTarget.LabelHandle,
                                    is EditorGestureTarget.ConstellationNameHandle,
                                    EditorGestureTarget.FreeImage -> Unit
                                }
                            }
                            change?.consume()
                            return
                        }
                        if (change.isConsumed) {
                            previousPoint = currentPoint
                            continue
                        }
                        val dragAmount = currentPoint - previousPoint
                        previousPoint = currentPoint
                        if (dragAmount.getDistance() <= 0f) continue
                        dragTotal += dragAmount
                        if (!dragStarted && dragTotal.getDistance() < touchSlop) continue

                        val effectiveDrag = if (dragStarted) dragAmount else dragTotal
                        dragStarted = true
                        val dragTarget = when (target) {
                            is EditorGestureTarget.FixedDelete,
                            is EditorGestureTarget.FixedReference -> EditorGestureTarget.FreeImage
                            // Langdruck bewegt nur im Verschiebe-Modus; sonst kein Direkt-Move.
                            is EditorGestureTarget.OverlayBody ->
                                if (moveOverlayId == target.id) target else null
                            else -> target
                        }
                        if (dragTarget != null) applyDrag(dragTarget, currentPoint, effectiveDrag)
                        change.consume()
                    }
                }

                // ZEICHNEN (Zweifinger-Stift, dauerhafter Modus): Finger A greift den Stift am
                // Greif-Hotspot (hinteres Ende) und bewegt NUR ihn; ein zweiter Finger B, IRGENDWO
                // sonst im Bild, aktiviert while-held das Zeichnen (oder Radieren, je nach
                // Stift-Modus) an der aktuellen Spitze. Rollen-Zuweisung EINMALIG pro Finger beim
                // jeweils eigenen Down: trifft er den Hotspot UND A ist noch frei -> A; sonst, wenn A
                // schon aktiv ist -> B; sonst wirkungslos (erzwingt "Stift muss immer zuerst gegriffen
                // werden", verhindert nachträgliches Umwidmen bereits liegender Finger). 2 Finger
                // bedeuten in diesem Modus NIE Pinch-Zoom -> kein Fallback auf
                // handleMultiTouchViewport. Löst sich A, während B noch hält, wird das laufende
                // Segment sofort beendet (Lücke).
                suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.handleDrawMode(
                    initialDown: PointerInputChange,
                ) {
                    fun hotspotScreen(): Offset? {
                        val tipImg = penPositionImg ?: return null
                        return currentViewport().imageToScreen(tipImg) - freehandPenOffset
                    }
                    fun nearHotspot(pos: Offset): Boolean {
                        val hotspot = hotspotScreen() ?: return false
                        return (pos - hotspot).getDistance() <= penGrabHitRadius
                    }

                    var pointerA: androidx.compose.ui.input.pointer.PointerId? = null
                    var pointerB: androidx.compose.ui.input.pointer.PointerId? = null
                    var aDownTimeMs = 0L
                    var aDownPos = Offset.Zero
                    var aMovedTooFar = false
                    var aSegmentStarted = false
                    val inertIds = mutableSetOf<androidx.compose.ui.input.pointer.PointerId>()
                    val knownIds = mutableSetOf<androidx.compose.ui.input.pointer.PointerId>()

                    fun beginGripA(c: PointerInputChange) {
                        pointerA = c.id
                        aDownTimeMs = c.uptimeMillis
                        aDownPos = c.position
                        aMovedTooFar = false
                        aSegmentStarted = false
                        c.consume()
                    }
                    fun beginTriggerB(c: PointerInputChange) {
                        pointerB = c.id
                        // Zählt (Zeichnen ODER Radieren) als "hier ist etwas passiert" -> disqualifiziert
                        // diesen Greif-Vorgang als Tipp-Kandidat für die Doppel-Tipp-Erkennung unten.
                        aSegmentStarted = true
                        if (!penIsEraser) {
                            activeSegment = penPositionImg?.let { listOf(it) } ?: emptyList()
                        }
                        drawTriggerScreenPos = c.position
                        // Neuer Strich (Zeichnen ODER Radieren) -> Aufrufer sichert für Undo, analog zu
                        // den Masken-Pinseln (snapshotXxxMask() bei Strichbeginn).
                        onDrawStrokeBegin()
                        c.consume()
                    }
                    fun endGripA(c: PointerInputChange) {
                        // War währenddessen ein B-Strich aktiv, wird er hier zwangsweise beendet (der
                        // Stift muss immer zuerst gegriffen sein) -- die alte B-Kennung wird wirkungslos,
                        // damit ein evtl. noch aufliegender Finger nicht in derselben Geste weiterwirkt.
                        val hadActiveB = pointerB != null
                        activeSegment?.let { seg ->
                            if (seg.size >= 2) {
                                val closed = OverlayGeometry.closeFreehandSegmentIfNearStart(
                                    seg,
                                    freehandCloseSnapPx / currentViewport().scale,
                                )
                                allSegments = allSegments + listOf(closed)
                            }
                        }
                        activeSegment = null
                        if (hadActiveB) {
                            onDrawSegmentsCommitted(allSegments)
                            pointerB?.let { inertIds += it }
                            pointerB = null
                        }
                        val durationMs = c.uptimeMillis - aDownTimeMs
                        val wasTap = !aMovedTooFar && durationMs <= MOVE_LONG_PRESS_MS && !aSegmentStarted
                        if (wasTap) {
                            val lastTime = lastPenTapUpTimeMs
                            val lastPos = lastPenTapUpScreenPos
                            val isDouble = lastTime >= 0 &&
                                (aDownTimeMs - lastTime) <= PEN_DOUBLE_TAP_MS &&
                                lastPos != null && (aDownPos - lastPos).getDistance() <= touchSlop * 2f
                            if (isDouble) {
                                penIsEraser = !penIsEraser
                                lastPenTapUpTimeMs = -1L
                                lastPenTapUpScreenPos = null
                            } else {
                                lastPenTapUpTimeMs = c.uptimeMillis
                                lastPenTapUpScreenPos = aDownPos
                            }
                        } else {
                            lastPenTapUpTimeMs = -1L
                            lastPenTapUpScreenPos = null
                        }
                        pointerA = null
                        drawTriggerScreenPos = null
                    }
                    fun endTriggerB() {
                        activeSegment?.let { seg ->
                            if (seg.size >= 2) {
                                val closed = OverlayGeometry.closeFreehandSegmentIfNearStart(
                                    seg,
                                    freehandCloseSnapPx / currentViewport().scale,
                                )
                                allSegments = allSegments + listOf(closed)
                            }
                        }
                        activeSegment = null
                        onDrawSegmentsCommitted(allSegments)
                        pointerB = null
                        drawTriggerScreenPos = null
                    }

                    if (nearHotspot(initialDown.position)) {
                        beginGripA(initialDown)
                    } else {
                        inertIds += initialDown.id
                        initialDown.consume()
                    }
                    knownIds += initialDown.id

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }

                        // Kein Stift gegriffen + 2 (oder mehr) Finger unten -> normales Pinch-Zoom
                        // (unabhängig davon, ob einzelne dieser Finger vorher schon "wirkungslos"
                        // waren). Nur WÄHREND der Stift aktiv gehalten wird, bedeutet ein zweiter
                        // Finger den Zeichnen/Radieren-Auslöser statt Zoom.
                        if (pointerA == null && pressed.size >= 2) {
                            handleMultiTouchViewport(pressed)
                            return
                        }

                        for (c in pressed) {
                            if (c.id in knownIds) continue
                            knownIds += c.id
                            when {
                                pointerA == null && nearHotspot(c.position) -> beginGripA(c)
                                pointerA != null && pointerB == null -> beginTriggerB(c)
                                else -> { inertIds += c.id; c.consume() }
                            }
                        }

                        val aChange = pointerA?.let { id -> event.changes.firstOrNull { it.id == id } }
                        if (aChange != null && aChange.pressed) {
                            val vpNow = currentViewport()
                            penPositionImg = vpNow.screenToImage(aChange.position + freehandPenOffset)
                            if ((aChange.position - aDownPos).getDistance() > touchSlop) aMovedTooFar = true
                            aChange.consume()
                        }

                        val bChange = pointerB?.let { id -> event.changes.firstOrNull { it.id == id } }
                        if (bChange != null && bChange.pressed) {
                            drawTriggerScreenPos = bChange.position
                            val tipNow = penPositionImg
                            val vpNow = currentViewport()
                            if (tipNow != null) {
                                if (penIsEraser) {
                                    val eraseRadiusImg = penEraseRadiusPx / vpNow.scale
                                    allSegments = OverlayGeometry.eraseFromFreehandSegments(allSegments, tipNow, eraseRadiusImg)
                                } else {
                                    val seg = activeSegment
                                    if (seg != null) {
                                        val last = seg.lastOrNull()
                                        val dist = if (last != null) {
                                            hypot((tipNow.x - last.x).toDouble(), (tipNow.y - last.y).toDouble()).toFloat()
                                        } else {
                                            Float.MAX_VALUE
                                        }
                                        if (last == null || dist >= freehandDecimateMinPx / vpNow.scale) {
                                            activeSegment = seg + tipNow
                                        }
                                    }
                                }
                            }
                            bChange.consume()
                        }

                        if (aChange != null && !aChange.pressed) endGripA(aChange)
                        if (bChange != null && !bChange.pressed) endTriggerB()
                        event.changes.firstOrNull { it.id in inertIds && it.pressed }?.consume()

                        if (pressed.isEmpty()) return
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture

                    // ZEICHNEN: dauerhafter Modus, komplett eigenständiger Gestenpfad (kein Fallback
                    // auf Pinch-Zoom/Pan -- s. Kommentar bei handleDrawMode oben). Der Stift ist ab
                    // Moduseintritt sichtbar (penPositionImg wird beim ersten Mal auf die Sichtfeld-
                    // Mitte gesetzt, s. LaunchedEffect(pendingPlacement) unten); pendingPlacement bleibt
                    // hier bewusst gesetzt, bis „Fertig zeichnen" es zurücknimmt.
                    if (pendingPlacement == EditorTool.Draw) {
                        handleDrawMode(down)
                        return@awaitEachGesture
                    }

                    // KACHEL-AUSRICHTUNG (nur im Kalibrierungs-Modus): Bereich hinzufügen = Rechteck
                    // aufziehen -> neue Kachel.
                    // 360°-Naht-Snap (Nutzer-Auftrag 2026-09-03): eine Solve-Kachel darf in der
                    // Tiny-Sky-Ansicht nie beide Seiten der periodischen Naht gleichzeitig überdecken.
                    // Läuft AUSSCHLIESSLICH beim Bestätigen (Erzeugen bzw. Loslassen), nicht pro
                    // Zieh-Frame -- während der Geste folgt die Kachel frei dem Finger, dadurch gibt es
                    // kein Links/Rechts-Flackern direkt auf der Naht und es braucht keine Hysterese.
                    // Liefert null, wenn nichts zu tun ist (keine Naht / andere Ansicht / Kachel liegt
                    // schon sauber auf einer Seite).
                    fun seamSnapCenter(
                        tileId: Long?,
                        center: Offset,
                        left: Float,
                        top: Float,
                        right: Float,
                        bottom: Float,
                        vp: ImageViewport,
                    ): Offset? {
                        val seam = tinySkySeam ?: return null
                        if (activeTileSpace != TileSourceSpace.TinySky) return null
                        // 10 dp sichtbarer Abstand -> in Bild-px zur aktuellen Zoomstufe; geklemmt, damit
                        // extremes Ein-/Auszoomen keinen absurd kleinen/großen Sprung erzeugt.
                        val marginPx = (10.dp.toPx() / vp.scale).coerceIn(2f, 64f)
                        val snap = TileDeWarp.tinySkySeamSnap(
                            left = left, top = top, right = right, bottom = bottom,
                            centerY = center.y, seam = seam, safetyMarginPx = marginPx,
                        ) ?: return null
                        AppDiagnostics.record(
                            "tiny_seam_tile_snap tile=${tileId ?: -1} seamAxis=horizontal " +
                                "seamY=${"%.1f".format(seam.y)} " +
                                "beforeTop=${"%.1f".format(top)} beforeBottom=${"%.1f".format(bottom)} " +
                                "aboveOverlap=${"%.1f".format(snap.overlapAbovePx)} " +
                                "belowOverlap=${"%.1f".format(snap.overlapBelowPx)} " +
                                "chosenSide=${snap.side} " +
                                "afterTop=${"%.1f".format(top + snap.deltaY)} " +
                                "afterBottom=${"%.1f".format(bottom + snap.deltaY)} " +
                                "safetyMarginPx=${"%.1f".format(marginPx)}",
                        )
                        return Offset(center.x, center.y + snap.deltaY)
                    }

                    if (calibrationActive && !tilesLocked && selectedTool == EditorTool.SolveRegion) {
                        val startImg = currentViewport().screenToImage(down.position)
                        var curImg = startImg
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change != null) {
                                curImg = currentViewport().screenToImage(change.position)
                                solveRegionDraft = Rect(
                                    min(startImg.x, curImg.x), min(startImg.y, curImg.y),
                                    max(startImg.x, curImg.x), max(startImg.y, curImg.y),
                                )
                                change.consume()
                            }
                            if (change != null && !change.pressed) break
                            if (event.changes.none { it.pressed }) break
                        }
                        val draft = solveRegionDraft
                        solveRegionDraft = null
                        if (draft != null && draft.width >= 48f && draft.height >= 48f) {
                            val cx = ((draft.left + draft.right) / 2f).coerceIn(0f, bitmap.width.toFloat())
                            val cy = ((draft.top + draft.bottom) / 2f).coerceIn(0f, bitmap.height.toFloat())
                            // Frisch aufgezogene Kachel ist unrotiert -> Hüllbox = center +/- size/2.
                            val halfW = draft.width / 2f
                            val halfH = draft.height / 2f
                            val center = seamSnapCenter(
                                tileId = null, center = Offset(cx, cy),
                                left = cx - halfW, top = cy - halfH, right = cx + halfW, bottom = cy + halfH,
                                vp = currentViewport(),
                            ) ?: Offset(cx, cy)
                            onAddSolveTile(center, Size(draft.width, draft.height))
                        }
                        return@awaitEachGesture
                    }
                    // KACHEL-AUSRICHTUNG (Kalibrierungs-Modus): in sich geschlossener Gestenpfad
                    // (Kacheln + Pan/Zoom), berührt NICHT die Overlay-Bearbeitung. Kacheln sind erst
                    // nach LANG-DRUCK auswählbar/editierbar -> kein versehentliches Verschieben beim
                    // Schieben/Zoomen.
                    if (calibrationActive) {
                        val vp0 = currentViewport()
                        val sel = solveTiles.firstOrNull { it.id == selectedSolveTileId }
                        // (1) Ausgewählte Kachel: Griffe (Resize/Rotate) bzw. Body direkt ziehen.
                        if (!tilesLocked && sel != null) {
                            val onRotate = solveTileRotateHandleCenter(sel, vp0, rotationHandleOffset)
                                .distanceTo(down.position) <= handleHitRadius
                            val onResize = !onRotate && solveTileResizeHit(sel, down.position, vp0, handleHitRadius)
                            val onBody = !onRotate && !onResize &&
                                solveTileAtScreen(down.position, solveTiles, vp0, handleHitRadius, activeTileSpace)?.id == sel.id
                            if (onRotate || onResize || onBody) {
                                onTileEditBegin()
                                down.consume()
                                var last = down.position
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change != null) {
                                        when {
                                            onRotate -> onUpdateSolveTile(
                                                rotateSolveTile(sel, change.position, currentViewport()),
                                                GeometryEditKind.Rotate,
                                            )
                                            onResize -> onUpdateSolveTile(
                                                resizeSolveTile(sel, change.position, currentViewport()),
                                                GeometryEditKind.Resize,
                                            )
                                            else -> {
                                                val d = change.position - last
                                                val cur = solveTiles.firstOrNull { it.id == sel.id }
                                                if (cur != null && d.getDistance() > 0f) {
                                                    onUpdateSolveTile(
                                                        cur.copy(center = cur.center + currentViewport().screenDeltaToImage(d)),
                                                        GeometryEditKind.Move,
                                                    )
                                                }
                                            }
                                        }
                                        last = change.position
                                        change.consume()
                                    }
                                    if (change != null && !change.pressed) break
                                    if (event.changes.none { it.pressed }) break
                                }
                                // Loslassen: jetzt (und nur jetzt) ggf. von der 360°-Naht wegschnappen.
                                // Gilt für Verschieben, Skalieren UND Drehen -- alle drei können die
                                // Hüllbox über die Naht bringen.
                                solveTiles.firstOrNull { it.id == sel.id }?.let { moved ->
                                    val bb = solveTileBoundingBox(moved)
                                    seamSnapCenter(
                                        tileId = moved.id, center = moved.center,
                                        left = bb.left, top = bb.top, right = bb.right, bottom = bb.bottom,
                                        vp = currentViewport(),
                                    )?.let { snapped ->
                                        onUpdateSolveTile(moved.copy(center = snapped), GeometryEditKind.Move)
                                    }
                                }
                                return@awaitEachGesture
                            }
                        }
                        // (2) Sonst: Lang-Druck auf Kachel = auswählen; 2 Finger = Zoom; 1-Finger-Drag = Pan.
                        val tileUnder = solveTileAtScreen(down.position, solveTiles, vp0, handleHitRadius, activeTileSpace)
                        var twoFinger: List<PointerInputChange>? = null
                        val outcome = withTimeoutOrNull(MOVE_LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) return@withTimeoutOrNull "tap"
                                if (pressed.size >= 2) { twoFinger = pressed; return@withTimeoutOrNull "zoom" }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                                if (!change.pressed) return@withTimeoutOrNull "tap"
                                if ((change.position - down.position).getDistance() > touchSlop) {
                                    return@withTimeoutOrNull "pan"
                                }
                            }
                            @Suppress("UNREACHABLE_CODE") "tap"
                        }
                        when (outcome) {
                            null -> {
                                // Lang-Druck: Kachel auswählen (Edit-Modus) bzw. leere Stelle = abwählen.
                                if (!tilesLocked) onSelectSolveTile(tileUnder?.id)
                                down.consume()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    if (e.changes.none { it.pressed }) break
                                }
                            }
                            "zoom" -> twoFinger?.let { handleMultiTouchViewport(it) }
                            "pan" -> {
                                var last = down.position
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) { handleMultiTouchViewport(pressed); break }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                                    val d = change.position - last
                                    last = change.position
                                    pan += d
                                    change.consume()
                                    if (!change.pressed) break
                                }
                            }
                            else -> if (tileUnder == null) onSelectSolveTile(null) // Tipp ins Leere = abwählen.
                        }
                        return@awaitEachGesture
                    }
                    // Aktionsbuttons bleiben bewusst sichtbar (bis Löschen/Bearbeiten oder neuer
                    // Langdruck) -> hier NICHT löschen.

                    // Vor jeder Geste Zustand merken; nur bei tatsächlicher Mutation
                    // wird daraus ein Undo-Schritt (Drag = ein Schritt).
                    onInteractionStart()
                    val target = resolveTarget(down.position)
                    var lastPoint = down.position
                    // WICHTIG: der Timeout dient NUR der Erkennung (Tipp/Langdruck/Drag/Pinch).
                    // Die eigentlichen Dauer-Schleifen (continueSingleDrag/Multitouch) laufen DANACH,
                    // ungetimed – sonst brach der Timeout Pan/Zoom/Drag nach ~600 ms ab ("Bild steht").
                    var dragContinuation: EditorGestureTarget? = null
                    var multiPressed: List<androidx.compose.ui.input.pointer.PointerInputChange>? = null
                    val initialState = withTimeoutOrNull(MOVE_LONG_PRESS_MS) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val tracked = event.changes.firstOrNull { it.id == down.id }
                            if (tracked != null) lastPoint = tracked.position
                            if (tracked != null && !tracked.pressed) {
                                return@withTimeoutOrNull EditorGestureState.Idle
                            }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                return@withTimeoutOrNull EditorGestureState.Idle
                            }
                            if (pressed.size >= 2) {
                                multiPressed = pressed
                                return@withTimeoutOrNull EditorGestureState.MultiTouchViewport
                            }
                            val change = tracked ?: pressed.first()
                            val currentPoint = change.position
                            lastPoint = currentPoint
                            if (change.isConsumed) continue
                            val totalDrag = currentPoint - down.position
                            if (totalDrag.getDistance() >= touchSlop) {
                                val directDragTarget = when (target) {
                                    is EditorGestureTarget.ConstellationAnchor,
                                    is EditorGestureTarget.ResizeHandle,
                                    is EditorGestureTarget.RotateHandle,
                                    is EditorGestureTarget.LabelHandle,
                                    is EditorGestureTarget.ConstellationNameHandle -> target
                                    // Im Verschiebe-Modus folgt das Objekt direkt dem Finger.
                                    is EditorGestureTarget.OverlayBody ->
                                        if (moveOverlayId == target.id) target else EditorGestureTarget.FreeImage
                                    else -> EditorGestureTarget.FreeImage
                                }
                                applyDrag(directDragTarget, currentPoint, totalDrag)
                                change.consume()
                                dragContinuation = directDragTarget
                                return@withTimeoutOrNull EditorGestureState.ImagePan
                            }
                        }
                    }

                    when (initialState) {
                        null -> handleAfterLongPress(target, down.id, lastPoint)
                        EditorGestureState.Idle -> handleTap(target, lastPoint)
                        EditorGestureState.MultiTouchViewport -> multiPressed?.let { handleMultiTouchViewport(it) }
                        else -> dragContinuation?.let { continueSingleDrag(it, down.id, lastPoint) }
                    }
                    draggingOverlayId = null // Drag beendet -> Puls aus, Overlay normal.
                    onInteractionEnd()
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // PERFORMANCE (Fix 1): hier (Zeichenphase) berechnet statt im Composition-Scope oben --
            // lokale Funktionen weiter unten in diesem Lambda (drawOverlayBucket/drawMilkyWayLayer/
            // drawGraticuleLayer) binden sich per normalem Kotlin-Closure-Scoping automatisch daran.
            val viewport = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)
            val imageBitmap = displayImage
            val imageDstOffset = IntOffset(viewport.offset.x.roundToInt(), viewport.offset.y.roundToInt())
            val imageDstSize = IntSize(
                (bitmap.width * viewport.scale).roundToInt(),
                (bitmap.height * viewport.scale).roundToInt(),
            )
            drawImage(
                image = imageBitmap,
                dstOffset = imageDstOffset,
                dstSize = imageDstSize,
                colorFilter = imageColorFilter,
            )

            // Nur die AKTIVE Maske zeigen: die Vordergrund-Solve-Maske ausschließlich, während der
            // Maskenpinsel aktiv ist (Tab „Vordergrund"). Beim Beschriftungs-Radierer (Tab „Beschriftung")
            // bleibt sie ausgeblendet, damit sich die beiden Masken im Maskieren-Menü nicht überlagern.
            if (maskBitmap != null && selectedTool == EditorTool.Mask) {
                @Suppress("UNUSED_EXPRESSION")
                maskVersion // Lesezugriff invalidiert den Canvas bei jedem Pinselstrich
                drawImage(
                    image = maskBitmap.asImageBitmap(),
                    dstOffset = imageDstOffset,
                    dstSize = imageDstSize,
                    alpha = 0.45f,
                )
            }

            // Radierer-Pinsel-Vorschau: die Radier-Maske nur sichtbar machen, solange das
            // Radierwerkzeug aktiv ist (zeigt, wo bereits ausgeblendet wird).
            if (annotationEraseMask != null && selectedTool == EditorTool.EraseArea) {
                @Suppress("UNUSED_EXPRESSION")
                annotationEraseVersion // committete Änderungen (Undo/Redo/Löschen) invalidieren die Vorschau
                @Suppress("UNUSED_EXPRESSION")
                annotationErasePreviewVersion // Live-Striche invalidieren die Vorschau pro Segment (flüssig)
                drawImage(
                    image = annotationEraseMask.asImageBitmap(),
                    dstOffset = imageDstOffset,
                    dstSize = imageDstSize,
                    alpha = 0.35f,
                )
            }

            // Pinsel-Vorschaukreis: zeigt mittig die aktuelle Pinselgröße (Maske/Radierer) NUR, während
            // der Größen-Slider gezogen wird (Popup ist dann ausgeblendet); verschwindet beim Loslassen.
            if (brushPreviewActive && (selectedTool == EditorTool.Mask || selectedTool == EditorTool.EraseArea)) {
                val frac = if (selectedTool == EditorTool.Mask) maskBrushFraction else eraseBrushFraction
                val hardness = (if (selectedTool == EditorTool.Mask) maskBrushHardness else eraseBrushHardness)
                    .coerceIn(0f, 1f)
                val previewRadius = frac * min(imageDstSize.width, imageDstSize.height)
                // Mitte der SICHTBAREN Fläche (nicht der Bildmitte) -> erscheint dort, wo man gerade
                // hinschaut, auch nach Pan/Zoom. previewRadius bleibt der korrekte On-Screen-Radius.
                val previewCenter = Offset(size.width / 2f, size.height / 2f)
                val previewColor = if (selectedTool == EditorTool.Mask) {
                    Color(0xFF4FC3F7)
                } else {
                    Color(0xFFFFB74D)
                }
                // Füllung als radialer Verlauf: voll bis zum „harten Kern", danach weicher Auslauf bis
                // transparent am Rand. Kern-Anteil deckungsgleich mit SolveMask.brushCore (0.30–1.0),
                // damit die Vorschau exakt der gemalten Weichheit entspricht; Außenkante = previewRadius.
                val core = (0.30f + 0.70f * hardness.coerceIn(0f, 1f)).coerceAtMost(0.98f)
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        0f to previewColor.copy(alpha = 0.30f),
                        core to previewColor.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                        center = previewCenter,
                        radius = previewRadius,
                    ),
                    radius = previewRadius,
                    center = previewCenter,
                )
                // Dünne Außenkontur: zeigt die nominale Pinselgröße (gestrichelt = weicher Rand).
                drawCircle(
                    color = previewColor.copy(alpha = 0.5f + 0.5f * hardness),
                    radius = previewRadius,
                    center = previewCenter,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            if (showDetectedStars) {
                detectedStars.forEach { star ->
                    val markerRadius = (star.radius.coerceAtLeast(3.2f) * viewport.scale * 2.15f)
                        .coerceIn(3.8.dp.toPx(), 24.dp.toPx())
                    drawCircle(
                        color = Color(0xFFFFD166).copy(alpha = 0.56f),
                        center = viewport.imageToScreen(star.offset),
                        radius = markerRadius,
                        style = Stroke(width = 1.05.dp.toPx()),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        center = viewport.imageToScreen(star.offset),
                        radius = 1.0.dp.toPx(),
                    )
                }
            }

            previewDetectedStars.forEach { star ->
                val markerRadius = (star.radius.coerceAtLeast(3.2f) * viewport.scale * 2.15f)
                    .coerceIn(3.8.dp.toPx(), 24.dp.toPx())
                drawCircle(
                    color = Color(0xFF70E1F5).copy(alpha = 0.72f),
                    center = viewport.imageToScreen(star.offset),
                    radius = markerRadius,
                    style = Stroke(width = 1.15.dp.toPx()),
                )
            }

            previewOverlays.forEach { overlay ->
                drawOverlay(
                    overlay = overlay,
                    viewport = viewport,
                    selected = false,
                    editing = false,
                    showConstellationAnchors = false,
                )
            }

            // Zeichenreihenfolge der 5 Schichten frei konfigurierbar (Katalog bearbeiten -> Schichten,
            // Nutzerwunsch 2026-08-20) -- bei layerDrawOrder == DEFAULT_DRAW_LAYER_ORDER identisch zur
            // vorherigen fest verdrahteten Reihenfolge (Milchstraße -> Gradnetz -> Sternbilder/Objekte/
            // Sterne). previewMarkers (reine Kalibrier-Hilfsanzeige, kein persistenter Content-Layer)
            // bleibt bewusst AUSSERHALB dieser Schleife und zeichnet zuletzt über allen 5 Schichten (war
            // vorher nur Zufallsprodukt der alten festen Reihenfolge -- lässt sich mit einer frei
            // wählbaren Gradnetz-Position ohnehin nicht mehr an einer festen relativen Stelle verankern).

            // Milchstraße (0.19.0): wie das Koordinatennetz ein nicht-interaktiver Pass mit
            // vorab berechneter Geometrie (Bildkoordinaten) -> pro Frame nur Transform + Fill je Ring.
            // Dieselbe Fill-Farbe/Alpha-Staffelung wie im Sternhimmel-Globus (milkyWayLayerFill),
            // zusätzlich mit dem Opazitäts-Regler skaliert.
            fun drawMilkyWayLayer() {
                milkyWay?.let { mw ->
                    // Deckkraft-Regler ist WÖRTLICH gemeint: 100% = die dichteste Stufe voll deckend
                    // (alpha 1.0), nicht nur der (zum Globus passende) dezente Default-Look. Die
                    // Basiswerte je Dichtestufe sind daher deutlich höher angesetzt als im Globus.
                    val alphaScale = milkyWayOpacity.coerceIn(0f, 1f)
                    val mwMinDim = min(bitmap.width, bitmap.height).toFloat()
                    // PERFORMANCE: Ring-Pfade in BILD-Koordinaten (unabhängig vom Blickausschnitt) nur
                    // neu bauen, wenn sich `mw` selbst ändert -- vorher wurde hier JEDER Punkt JEDES
                    // Rings JEDEN Frame einzeln per viewport.imageToScreen() transformiert (kostet bei
                    // vielen/feinen Ringen spürbar Zeit, gerade während einer laufenden Zieh-/Pinch-
                    // Geste). Der aktuelle Pan/Zoom-Blickausschnitt wird stattdessen unten per
                    // withTransform (eine GPU-Matrix für den ganzen Layer) angewendet.
                    val cachedMw = milkyWayPathsMemo
                    val levelPaths = if (cachedMw != null && cachedMw.first === mw) {
                        cachedMw.second
                    } else {
                        mw.levels.map { level ->
                            level.level to level.rings.mapNotNull { ring ->
                                val pts = ring.points
                                if (pts.size < 3) return@mapNotNull null
                                androidx.compose.ui.graphics.Path().apply {
                                    moveTo(pts[0].x, pts[0].y)
                                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                                    close()
                                }
                            }
                        }.also { milkyWayPathsMemo = mw to it }
                    }
                    withTransform({
                        translate(viewport.offset.x, viewport.offset.y)
                        scale(scaleX = viewport.scale, scaleY = viewport.scale, pivot = Offset.Zero)
                    }) {
                        levelPaths.forEach { (levelNum, paths) ->
                            val baseAlpha = when (levelNum) {
                                1 -> 0.35f
                                2 -> 0.50f
                                3 -> 0.65f
                                4 -> 0.80f
                                else -> 1.00f
                            }
                            val fill = Color(0xFFE8EEFF).copy(alpha = (baseAlpha * alphaScale).coerceIn(0f, 1f))
                            // Kontur deutlich sichtbar von der Fläche abgesetzt: reines Weiß (statt fast
                            // gleicher Farbton wie die Fläche) UND spürbar höhere Deckkraft als die eigene
                            // Fläche -> zeichnet sich als klare Linie ab, auch wenn die Fläche selbst bei
                            // hoher Deckkraft schon fast blickdicht ist. Breite skaliert mit der Bildgröße
                            // (analog zum Koordinatennetz), sonst bei hochauflösenden Fotos kaum sichtbar.
                            val outlineAlpha = (baseAlpha + 0.25f).coerceAtMost(1f)
                            val outline = Color.White.copy(alpha = (outlineAlpha * alphaScale).coerceIn(0f, 1f))
                            val outlineWidthScreen = (mwMinDim * 0.0018f * viewport.scale).coerceAtLeast(1.5.dp.toPx())
                            // Breite hier durch viewport.scale geteilt, weil sie durch die aktive
                            // scale()-Transformation oben gleich wieder mit viewport.scale multipliziert
                            // wird -- Nettoeffekt identisch zur vorherigen, direkt in Bildschirm-Einheiten
                            // angegebenen Breite (inkl. deren Mindestbreite).
                            paths.forEach { path ->
                                drawPath(path, fill)
                                drawPath(path, outline, style = Stroke(width = outlineWidthScreen / viewport.scale))
                            }
                        }
                    }
                }
            }

            // Koordinatennetz (0.10.5): nicht-interaktiver Pass. Die Geometrie (Linien/Gradzahlen in
            // Bildkoordinaten) ist vorab berechnet; pro Frame nur Transform + EIN drawPath je Linie.
            // Strichbreite/Textgröße skalieren mit dem Zoom -> optisch == Export (1:1). Schatten-Alpha
            // ∝ Deckkraft (sonst dunkelt der Schatten transparente Zahlen).
            fun drawGraticuleLayer() {
                graticule?.let { grid ->
                    val minDim = min(bitmap.width, bitmap.height).toFloat()
                    val alpha = graticuleOpacity.coerceIn(0.05f, 1f)
                    val gridColor = Color(graticuleColorArgb).copy(alpha = alpha)
                    val strokeScreen = (graticuleThickness * minDim * viewport.scale).coerceAtLeast(0.75f)
                    // PERFORMANCE: s. ausführlicher Kommentar in drawMilkyWayLayer -- Linien-Pfade in
                    // Bild-Koordinaten gecacht (nur bei neuem `grid` neu gebaut), Pan/Zoom per
                    // withTransform statt Punkt-für-Punkt-Neuberechnung in jedem Zeichen-Frame.
                    val cachedGrid = graticulePathsMemo
                    val linePaths = if (cachedGrid != null && cachedGrid.first === grid) {
                        cachedGrid.second
                    } else {
                        grid.lines.mapNotNull { line ->
                            val pts = line.points
                            if (pts.size < 2) return@mapNotNull null
                            androidx.compose.ui.graphics.Path().apply {
                                moveTo(pts[0].x, pts[0].y)
                                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                            }
                        }.also { graticulePathsMemo = grid to it }
                    }
                    withTransform({
                        translate(viewport.offset.x, viewport.offset.y)
                        scale(scaleX = viewport.scale, scaleY = viewport.scale, pivot = Offset.Zero)
                    }) {
                        linePaths.forEach { path ->
                            drawPath(path, color = gridColor, style = Stroke(width = strokeScreen / viewport.scale))
                        }
                    }
                    if (graticuleShowLabels && grid.labels.isNotEmpty()) {
                        val shadowAlpha = (alpha * 255f).toInt().coerceIn(0, 255)
                        val gridLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = graticuleColorArgb.toInt()
                            this.alpha = shadowAlpha
                            textAlign = android.graphics.Paint.Align.LEFT
                            typeface = android.graphics.Typeface.create(
                                android.graphics.Typeface.SANS_SERIF,
                                android.graphics.Typeface.BOLD,
                            )
                            textSize = (minDim * 0.016f * viewport.scale).coerceIn(9.dp.toPx(), 64.dp.toPx())
                            setShadowLayer(4f, 0f, 0f, android.graphics.Color.argb(shadowAlpha, 0, 0, 0))
                        }
                        grid.labels.forEach { label ->
                            val p = viewport.imageToScreen(label.pos)
                            drawContext.canvas.nativeCanvas.drawText(
                                label.text,
                                p.x + 4.dp.toPx(),
                                p.y + gridLabelPaint.textSize * 0.35f,
                                gridLabelPaint,
                            )
                        }
                    }
                }
            }

            // PERFORMANCE + Radierer: statische Overlays werden je Ziel-Schicht (s. DrawLayer) in
            // BILD-Koordinaten EINMAL in einen GraphicsLayer aufgezeichnet (Display-List, vektoriell,
            // bei jedem Zoom scharf) und pro Frame nur skaliert wiedergegeben -> kein saveLayer / keine
            // Vektor-Neuberechnung pro Frame. Das gerade ausgewählte/verschobene/bearbeitete Overlay
            // bleibt LIVE (Auswahl/Puls/Anker ändern sich pro Frame) und wird vom Cache ausgenommen und
            // darüber gezeichnet.
            @Suppress("UNUSED_EXPRESSION") annotationEraseVersion
            // IDs, die live (nicht gecacht) gezeichnet werden müssen.
            val liveIds = listOfNotNull(
                selectedOverlayId, editingOverlayId, moveOverlayId, draggingOverlayId, anchorEditOverlayId,
            ).toHashSet()
            // NICHT hinter einem `===`-Referenz-Cache auf `overlays` versteckt (s. ausführliche Erklärung
            // bei der Deklaration von milkyWayPathsMemo oben) -- `overlays` ist eine dauerhaft in-place
            // mutierte `mutableStateListOf`, deren Referenz sich nie ändert, ein solcher Cache würde nach
            // dem ersten Zeichnen für immer einfrieren (u.a. nach jedem Solve alles unsichtbar machen).
            // Stattdessen (Nachtrag 4) hinter einem INHALTS-Hash gecacht -- ändert sich garantiert bei
            // jeder echten Änderung (Hinzufügen/Entfernen/copy()-Ersetzen ändert immer mind. einen Term),
            // bleibt bei reinem Pan/Zoom stabil. Nötig bei sehr hoher Overlay-Zahl (600 Sterne): ein volles
            // groupBy jeden Frame skaliert sonst spürbar mit der Gesamtzahl aktiver Overlays.
            var overlaysContentHash = 0
            overlays.forEach { ov -> overlaysContentHash = overlaysContentHash * 31 + System.identityHashCode(ov) }
            val groupedOverlays = groupedOverlaysMemo?.takeIf { it.first == overlaysContentHash }?.second
                ?: OverlayGeometry.groupByDrawLayer(overlays).also { groupedOverlaysMemo = overlaysContentHash to it }

            // Cache-Signatur: identityHashCode pro gecachtem Overlay DIESER Schicht (Edits ersetzen die
            // Instanz via copy -> ändert sich bei jeder echten Änderung, stabil bei Pan/Zoom) + Radierer-
            // Version + globaler Ankerschalter. Billig (wenige Overlays), kein Tiefenvergleich von
            // edgePolylines. Extrahiert (Fix 2), damit drawCombinedOverlayRun dieselbe Signatur-Logik je
            // Mitglied wiederverwenden kann, statt sie zu duplizieren. Ganzzahl-Rolling-Hash statt
            // String-Verkettung (Nachtrag 4) -- bei 600 Overlays sonst 600x Ganzzahl-zu-Text-Umwandlung
            // JEDEN Frame, nur um eine Cache-Invalidierung zu prüfen.
            fun bucketCacheSig(bucket: List<AnnotationOverlay>): String {
                var hash = 0
                bucket.forEach { ov ->
                    if (ov.id !in liveIds) {
                        hash = hash * 31 + System.identityHashCode(ov)
                    }
                }
                hash = hash * 31 + annotationEraseVersion
                hash = hash * 31 + showConstellationAnchors.hashCode()
                hash = hash * 31 + (if (annotationEraseMask != null) 1 else 0)
                return hash.toString()
            }

            fun drawOverlayBucket(layer: DrawLayer) {
                val bucket = groupedOverlays[layer].orEmpty()
                if (bucket.isEmpty()) return
                val cacheSig = bucketCacheSig(bucket)
                // Cache-Bitmap LAZY, nur für tatsächlich befüllte Schichten (s. overlayCaches-Deklaration).
                val cache = overlayCaches.getOrPut(layer) { buildOverlayCache(bitmap) }
                if (overlayCacheSigs[layer] != cacheSig) {
                    // Overlays EINMAL in den Offscreen-Cache rendern (Bild -> Cache skaliert). Eigenes
                    // Bitmap = isolierter Zielpuffer: die DST_OUT-Stanze entfernt NUR Overlay-Pixel (kein
                    // saveLayer nötig); transparente Stellen lassen später das Hintergrundbild durchscheinen.
                    val cacheViewport = ImageViewport(bitmap.width, bitmap.height, cache.scale, Offset.Zero)
                    CanvasDrawScope().draw(
                        this,
                        this.layoutDirection,
                        androidx.compose.ui.graphics.Canvas(cache.image),
                        Size(cache.width.toFloat(), cache.height.toFloat()),
                    ) {
                        val rc = drawContext.canvas.nativeCanvas
                        // Wiederverwendetes Bitmap zuerst leeren (vollständig transparent).
                        rc.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                        bucket.forEach { ov ->
                            if (ov.id !in liveIds) {
                                drawOverlay(
                                    overlay = ov,
                                    viewport = cacheViewport,
                                    selected = false,
                                    editing = false,
                                    showConstellationAnchors = ov.showAnchors ?: showConstellationAnchors,
                                    pulse = null,
                                )
                            }
                        }
                        if (annotationEraseMask != null) {
                            val punchPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
                            }
                            rc.drawBitmap(
                                annotationEraseMask,
                                null,
                                android.graphics.Rect(0, 0, cache.width, cache.height),
                                punchPaint,
                            )
                        }
                    }
                    overlayCacheSigs[layer] = cacheSig
                }
                // Cache pro Frame als EIN Bild zeichnen, in den Bild-Screen-Bereich skaliert
                // (screen = bild*scale+offset, identisch zur Basisbild-Transformation).
                drawImage(
                    image = cache.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(cache.width, cache.height),
                    dstOffset = imageDstOffset,
                    dstSize = imageDstSize,
                )
            }

            // Fix 2: mehrere UNMITTELBAR aufeinanderfolgende Overlay-Zielschichten (Constellation/Objects/
            // Star) werden in EINEN gemeinsamen Cache zusammengelegt -- pro Frame dann nur EIN drawImage
            // für den ganzen Lauf statt N. Jedes Lauf-Mitglied wird dabei per eigenem saveLayer/
            // restoreToCount isoliert gestanzt (identisch zur Radierer-Reihenfolge in drawOverlayBucket
            // oben, s. dortiger Kommentar zur Nicht-Kommutativität mit einer gefederten Maske) -- danach
            // exakt dasselbe Ergebnis wie heute, nur einmalig bei Änderung statt jeden Frame neu
            // zusammengesetzt. Nur EIN Mehrfach-Lauf kann je Frame existieren (nur 3 Overlay-Zielschichten
            // insgesamt, ein Lauf braucht >=2 davon, zwei getrennte Läufe bräuchten >=4) -- eine Map mit
            // maximal 1 Eintrag reicht (s. combinedRunCaches-Deklaration oben).
            fun drawCombinedOverlayRun(members: List<DrawLayer>) {
                if (members.size < 2) {
                    members.firstOrNull()?.let { drawOverlayBucket(it) }
                    return
                }
                // Läuft layerDrawOrder um (Umsortieren im Schichten-Menü) -> alter Lauf existiert nicht
                // mehr unter demselben Schlüssel, verwaiste ~45-MB-Einträge nicht anhäufen lassen.
                if (combinedRunCaches.keys.any { it != members }) {
                    combinedRunCaches.clear()
                    combinedRunCacheSigs.clear()
                }
                val runSig = members.joinToString("|") { layer ->
                    "$layer=${bucketCacheSig(groupedOverlays[layer].orEmpty())}"
                }
                val cache = combinedRunCaches.getOrPut(members) { buildOverlayCache(bitmap) }
                if (combinedRunCacheSigs[members] != runSig) {
                    val cacheViewport = ImageViewport(bitmap.width, bitmap.height, cache.scale, Offset.Zero)
                    CanvasDrawScope().draw(
                        this,
                        this.layoutDirection,
                        androidx.compose.ui.graphics.Canvas(cache.image),
                        Size(cache.width.toFloat(), cache.height.toFloat()),
                    ) {
                        val rc = drawContext.canvas.nativeCanvas
                        rc.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                        members.forEach { layer ->
                            val bucket = groupedOverlays[layer].orEmpty()
                            if (bucket.isEmpty()) return@forEach
                            // saveLayer isoliert GENAU dieses Mitglied (wie ExportRenderer.kt:208-212) --
                            // die Radierer-Stanze gleich danach trifft NUR seinen eigenen, gerade
                            // gezeichneten Inhalt, nicht die bereits darunter zusammengesetzten Mitglieder.
                            val layerToken = if (annotationEraseMask != null) rc.saveLayer(null, null) else -1
                            bucket.forEach { ov ->
                                if (ov.id !in liveIds) {
                                    drawOverlay(
                                        overlay = ov,
                                        viewport = cacheViewport,
                                        selected = false,
                                        editing = false,
                                        showConstellationAnchors = ov.showAnchors ?: showConstellationAnchors,
                                        pulse = null,
                                    )
                                }
                            }
                            if (annotationEraseMask != null) {
                                val punchPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
                                }
                                rc.drawBitmap(
                                    annotationEraseMask,
                                    null,
                                    android.graphics.Rect(0, 0, cache.width, cache.height),
                                    punchPaint,
                                )
                                rc.restoreToCount(layerToken)
                            }
                        }
                    }
                    combinedRunCacheSigs[members] = runSig
                }
                drawImage(
                    image = cache.image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(cache.width, cache.height),
                    dstOffset = imageDstOffset,
                    dstSize = imageDstSize,
                )
            }

            run {
                var layerIndex = 0
                while (layerIndex < layerDrawOrder.size) {
                    when (layerDrawOrder[layerIndex]) {
                        DrawLayer.MilkyWay -> { drawMilkyWayLayer(); layerIndex++ }
                        DrawLayer.Graticule -> { drawGraticuleLayer(); layerIndex++ }
                        DrawLayer.Constellation, DrawLayer.Objects, DrawLayer.Star -> {
                            var runEnd = layerIndex
                            while (runEnd < layerDrawOrder.size &&
                                layerDrawOrder[runEnd] != DrawLayer.MilkyWay &&
                                layerDrawOrder[runEnd] != DrawLayer.Graticule
                            ) {
                                runEnd++
                            }
                            val nonEmptyRun = layerDrawOrder.subList(layerIndex, runEnd)
                                .filter { groupedOverlays[it]?.isNotEmpty() == true }
                            drawCombinedOverlayRun(nonEmptyRun)
                            layerIndex = runEnd
                        }
                    }
                }
            }

            run {
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(235, 255, 224, 130)
                    textAlign = android.graphics.Paint.Align.LEFT
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.SANS_SERIF,
                        android.graphics.Typeface.BOLD,
                    )
                    textSize = 12.dp.toPx()
                    setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                }
                previewMarkers.forEach { (name, point) ->
                    val center = viewport.imageToScreen(point)
                    drawCircle(
                        color = Color(0xFFFFC107).copy(alpha = 0.85f),
                        center = center,
                        radius = 9.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    if (name.isNotBlank()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            name,
                            center.x + 11.dp.toPx(),
                            center.y + labelPaint.textSize * 0.35f,
                            labelPaint,
                        )
                    }
                }
            }

            // Live-Overlays (Auswahl/Verschieben/Bearbeiten/Anker) exakt wie bisher, oben drauf.
            overlays.forEach { overlay ->
                if (overlay.id in liveIds) {
                    val selected = overlay.id == selectedOverlayId
                    val editing = selected && overlay.id == editingOverlayId
                    val anchorsVisible = (overlay.showAnchors ?: showConstellationAnchors) ||
                        overlay.id == anchorEditOverlayId
                    drawOverlay(
                        overlay = overlay,
                        viewport = viewport,
                        selected = selected,
                        editing = editing,
                        showConstellationAnchors = anchorsVisible,
                        pulse = if (overlay.id == draggingOverlayId || overlay.id == moveOverlayId) {
                            overlayDragPulse
                        } else {
                            null
                        },
                    )
                }
            }

            // ZEICHNEN: alle fertigen Segmente dieser Sitzung + das gerade laufende Segment (jede
            // Lücke bleibt sichtbar getrennt, keine Verbindungslinie) sowie der Stift-Cursor, der ab
            // Moduseintritt DAUERHAFT sichtbar ist (nicht mehr an eine laufende Geste gekoppelt).
            // Gleiche Stil-Werte wie das spätere Overlay (shapeColorArgb etc.) -> WYSIWYG schon
            // während des Zeichnens.
            if (pendingPlacement == EditorTool.Draw) {
                val draftColor = Color(shapeColorArgb)
                val draftStrokeWidth = imageStrokeToScreen(shapeStrokeWidth, viewport)
                (allSegments + listOfNotNull(activeSegment)).forEach { segment ->
                    if (segment.size >= 2) {
                        drawStyledPolyline(
                            segment.map { viewport.imageToScreen(it) },
                            draftColor,
                            draftStrokeWidth,
                            shapeLineStyle,
                            -1L,
                            taper = true,
                        )
                    }
                }
                // Live-Touch-Indikator für Finger B (Auslöser): pulsierender Ring, solange er hält,
                // sofort weg beim Loslassen (overlayDragPulse -- bestehende Puls-Animation).
                drawTriggerScreenPos?.let { triggerScreen ->
                    drawCircle(
                        color = draftColor,
                        radius = with(density) { (32 + 14 * overlayDragPulse).dp.toPx() },
                        center = triggerScreen,
                        alpha = 0.5f * (1f - overlayDragPulse * 0.4f),
                        style = Stroke(width = with(density) { 4.dp.toPx() }),
                    )
                }
                penPositionImg?.let { penImg ->
                    val tipScreen = viewport.imageToScreen(penImg)
                    val backScreen = tipScreen - freehandPenOffset
                    // Beim Radiergummi (nach Doppel-Tipp) tauschen Spitze und Radiergummi-Ende ihre
                    // FUNKTIONALE Rolle: das native Radiergummi-Bildmerkmal wird jetzt an tipScreen
                    // geklebt (arbeitet dort), das native Spitzen-Merkmal an backScreen (Griff-Hotspot)
                    // -- das ist ein ECHTER Rollentausch, kein additiver Dreh-Hack. Da penPositionImg
                    // (tipScreen) unverändert derselben Formel folgt, ergibt sich daraus automatisch ein
                    // Flip um die AKTUELLE Stift-Mitte (Nutzer-Korrektur: nicht um die Spitze).
                    val imgW = pencilCursorImage.width.toFloat()
                    val imgH = pencilCursorImage.height.toFloat()
                    val tipLocalPx = Offset(PENCIL_TIP_FRACTION_X * imgW, PENCIL_TIP_FRACTION_Y * imgH)
                    val eraserLocalPx = Offset(PENCIL_ERASER_FRACTION_X * imgW, PENCIL_ERASER_FRACTION_Y * imgH)
                    val workingLocalPx = if (penIsEraser) eraserLocalPx else tipLocalPx
                    val backLocalPx = if (penIsEraser) tipLocalPx else eraserLocalPx
                    val nativeWorkingToBack = backLocalPx - workingLocalPx
                    val nativeLengthPx = nativeWorkingToBack.getDistance()
                    if (nativeLengthPx > 0.01f) {
                        val nativeAngleDeg = Math.toDegrees(
                            atan2(nativeWorkingToBack.y.toDouble(), nativeWorkingToBack.x.toDouble()),
                        ).toFloat()
                        val desiredWorkingToBack = backScreen - tipScreen
                        val desiredAngleDeg = Math.toDegrees(
                            atan2(desiredWorkingToBack.y.toDouble(), desiredWorkingToBack.x.toDouble()),
                        ).toFloat()
                        // Ziel-Länge Spitze<->Radiergummi auf dem Bildschirm (Cursor-Größe).
                        val desiredLengthPx = with(density) { 100.dp.toPx() }
                        val renderScale = desiredLengthPx / nativeLengthPx
                        val dstSize = IntSize(
                            (imgW * renderScale).roundToInt().coerceAtLeast(1),
                            (imgH * renderScale).roundToInt().coerceAtLeast(1),
                        )
                        val dstWorkingLocal = Offset(workingLocalPx.x * renderScale, workingLocalPx.y * renderScale)
                        val dstOffset = IntOffset(
                            (tipScreen.x - dstWorkingLocal.x).roundToInt(),
                            (tipScreen.y - dstWorkingLocal.y).roundToInt(),
                        )
                        val isMarking = drawTriggerScreenPos != null
                        withTransform({
                            // Linkshänder: echtes Spiegelbild (nicht nur gedreht) um dieselbe Spitze --
                            // eine reine Drehung allein ergäbe bei einer nicht rotationssymmetrischen
                            // Zeichnung kein echtes Spiegelbild.
                            if (leftHandedDrawing) scale(scaleX = -1f, scaleY = 1f, pivot = tipScreen)
                            rotate(degrees = desiredAngleDeg - nativeAngleDeg, pivot = tipScreen)
                        }) {
                            if (isMarking) {
                                // Geteilte Deckkraft beim aktiven Zeichnen/Radieren (Nutzeranforderung):
                                // NUR die schwarze Spitze bleibt voll deckend, der Rest (Stiftkörper)
                                // wird stark transparent. Clip-Radius vermessen (s.
                                // PENCIL_TIP_CLIP_FRACTION) -- deckt exakt die schwarze Tipp-Fläche ab,
                                // nicht die helle Holz-Anspitzung. Liegt exakt auf dem Rotationspivot
                                // -> rotationsinvariant, die Reihenfolge zu rotate() ist unkritisch.
                                val tipClip = Path().apply {
                                    addOval(Rect(center = tipScreen, radius = desiredLengthPx * PENCIL_TIP_CLIP_FRACTION))
                                }
                                clipPath(tipClip) {
                                    drawImage(pencilCursorImage, dstOffset = dstOffset, dstSize = dstSize, alpha = 1f)
                                }
                                clipPath(tipClip, clipOp = ClipOp.Difference) {
                                    drawImage(pencilCursorImage, dstOffset = dstOffset, dstSize = dstSize, alpha = 0.15f)
                                }
                            } else {
                                drawImage(pencilCursorImage, dstOffset = dstOffset, dstSize = dstSize, alpha = 1f)
                            }
                        }
                    }
                }
            }

            // KACHEL-AUSRICHTUNG: Kacheln NUR im Kalibrierungs-Modus zeichnen (sonst unsichtbar im
            // normalen Editor) und nur, wenn nicht per Toggle ausgeblendet. Bleiben so sichtbar, solange
            // man kalibriert. Farbcodiert (gelb=offen, blau=läuft, grün=gelöst, rot=Fehler);
            // ausgewählte Kachel mit Eck-Griffen (Resize) + Rotationsgriff.
            if (calibrationActive && tilesVisible) {
            // 360°-Naht der Tiny-Sky-Ansicht als reine Bedienhilfe (Nutzer-Auftrag 2026-09-03) -- VOR
            // der Kachel-Schleife, damit Kachelrahmen/Griffe darüber liegen und bedienbar bleiben.
            // tinySkySeam ist nur in Tiny Sky bei einem echten 2:1-Panorama != null; im normalen Editor,
            // in Annotationen, in gespeicherten Projekten und in JEDEM Export existiert diese Linie nicht.
            // strokeWidth in dp -> konstante Bildschirmbreite unabhängig vom Bildzoom (Vorgabe Punkt 1).
            tinySkySeam?.let { seam ->
                drawLine(
                    color = Color(0xFFFF1744).copy(alpha = 0.60f),
                    start = viewport.imageToScreen(Offset(seam.minX, seam.y)),
                    end = viewport.imageToScreen(Offset(seam.maxX, seam.y)),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Nur Kacheln der aktiven Ansicht (Plan Nachtrag 3) -- forEachIndexed statt forEach, damit
            // die Nummerierung unten (1,2,3...) innerhalb JEDER Ansicht bei 1 beginnt, unabhängig davon,
            // wie viele Kacheln der jeweils anderen Ansicht in derselben Sitzung existieren.
            solveTiles.filter { it.sourceSpace == activeTileSpace }.forEachIndexed { tileIdx, tile ->
                val corners = solveTileScreenCorners(tile, viewport)
                val color = when (tile.status) {
                    SolveTileStatus.Pending -> Color(0xFFFFEB3B)
                    SolveTileStatus.Solving -> Color(0xFF42A5F5)
                    SolveTileStatus.Solved -> Color(0xFF4CD964)
                    SolveTileStatus.Failed -> Color(0xFFFF5252)
                    SolveTileStatus.Suspect -> Color(0xFFFF9800)
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))
                for (i in 0 until 4) {
                    drawLine(
                        color = color,
                        start = corners[i],
                        end = corners[(i + 1) % 4],
                        strokeWidth = 2.5.dp.toPx(),
                        pathEffect = dash,
                    )
                }
                // Kachel-Nummer (1-basiert) gut lesbar in die obere-linke Ecke, leicht nach innen versetzt.
                run {
                    val n = tileIdx + 1
                    val tl = corners[0]
                    val toCenter = viewport.imageToScreen(tile.center) - tl
                    val pos = tl + toCenter * 0.14f
                    val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.LEFT
                        textSize = 28.dp.toPx()
                        isFakeBoldText = true
                        setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        n.toString(), pos.x, pos.y + numPaint.textSize / 2f, numPaint,
                    )
                }
                // De-Warp-Markierung (≈): VORSCHAU (halbtransparent) schon bei dewarpRequested (der
                // Wunsch, diese Kachel zu entzerren), voll deckend sobald sie tatsächlich entzerrt
                // GELÖST wurde. So sieht man pro Kachel vorab, welche entzerrt wird.
                if (tile.dewarpRequested || tile.dewarp) {
                    val c = viewport.imageToScreen(tile.center)
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = 0xFF7CF5FF.toInt()
                        alpha = if (tile.dewarp) 255 else 130
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 20.dp.toPx()
                        isFakeBoldText = true
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText("≈", c.x, c.y + paint.textSize / 3f, paint)
                }
                // Warnmarkierung (!): eigene Solve-Genauigkeit ODER Überlappungs-Widerspruch zu einer
                // Nachbarkachel erreicht/überschreitet dieselbe "schlecht"-Schwelle wie im Kachel-Info-
                // Popup (s. TILE_WEAK_RMS_THRESHOLD_PX), oder es gab schlicht zu wenige Treffer für
                // eine belastbare Aussage (rmsPx == null trotz vorhandenem Eintrag). Eigenes Symbol
                // (Amber, unteren-rechte Ecke) statt eines neuen SolveTileStatus-Werts -- der Status
                // bleibt Solve-Fortschritt/Gesamt-Konsistenz vorbehalten (s. Suspect-Kommentar), diese
                // Markierung ist ein zusätzlicher, unabhängiger Hinweis nur für den Menschen.
                if (tile.status == SolveTileStatus.Solved || tile.status == SolveTileStatus.Suspect) {
                    val ownAcc = tileOwnRmsById[tile.id]
                    val overlapAcc = tileOverlapRmsById[tile.id]
                    val isWeak = (ownAcc != null && (ownAcc.rmsPx == null || ownAcc.rmsPx >= TILE_WEAK_RMS_THRESHOLD_PX)) ||
                        (overlapAcc?.rmsPx != null && overlapAcc.rmsPx >= TILE_WEAK_RMS_THRESHOLD_PX)
                    if (isWeak) {
                        val br = corners[2]
                        val pos = br + (viewport.imageToScreen(tile.center) - br) * 0.14f
                        val warnPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.color = 0xFFFFC107.toInt()
                            textAlign = android.graphics.Paint.Align.CENTER
                            textSize = 26.dp.toPx()
                            isFakeBoldText = true
                            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                        }
                        drawContext.canvas.nativeCanvas.drawText("!", pos.x, pos.y + warnPaint.textSize / 3f, warnPaint)
                    }
                }
                if (tile.id == selectedSolveTileId) {
                    corners.forEach { c ->
                        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = c)
                        drawCircle(color = color, radius = 6.dp.toPx(), center = c, style = Stroke(width = 2f))
                    }
                    val topMid = (corners[0] + corners[1]) / 2f
                    val rot = solveTileRotateHandleCenter(tile, viewport, rotationHandleOffset)
                    drawLine(color = color, start = topMid, end = rot, strokeWidth = 2.dp.toPx())
                    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = rot)
                    drawCircle(color = color, radius = 7.dp.toPx(), center = rot, style = Stroke(width = 2f))
                }
            }
            // Draft beim Hinzufügen (achsenparallel, gelb gestrichelt).
            solveRegionDraft?.let { region ->
                val tl = viewport.imageToScreen(Offset(region.left, region.top))
                val br = viewport.imageToScreen(Offset(region.right, region.bottom))
                drawRect(
                    color = Color(0xFFFFEB3B),
                    topLeft = tl,
                    size = Size(br.x - tl.x, br.y - tl.y),
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
                    ),
                )
            }
            } // Ende if (calibrationActive)
        }

        // KACHEL-AUSRICHTUNG: Löschen-Button UNTERHALB der ausgewählten Kachel (klar getrennt vom
        // Rotationsgriff, der oberhalb sitzt).
        val selectedTile = solveTiles.firstOrNull { it.id == selectedSolveTileId }
        if (calibrationActive && selectedTile != null) {
            // PERFORMANCE (Fix 1): eigene lokale Berechnung statt der (jetzt Zeichenphase-lokalen,
            // hier nicht mehr sichtbaren) viewport-Variable oben -- diese Button-Reihe folgt ihrem
            // Bild-Anker weiterhin korrekt beim Pannen, das ist hier gewollt/nötig.
            val actionViewport = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)
            val corners = solveTileScreenCorners(selectedTile, actionViewport)
            val bottomMid = (corners[2] + corners[3]) / 2f
            val belowPx = with(density) { 56.dp.toPx() }
            // 3 Buttons à 48.dp: Mittenabstand 56.dp -> 8.dp Lücke (44.dp überlappten die 48.dp-Buttons).
            val sidePx = with(density) { 56.dp.toPx() }
            // Fünf Pro-Kachel-Aktionen nebeneinander: manueller Hinweis (ganz links), De-Warp,
            // nächste Kachel platzieren (Mitte, grün), Löschen, Info (ganz rechts).
            // Manueller Positions-Hinweis (Sternname statt automatisch geschätzter Position) — v.a.
            // für die ERSTE Kachel oder Kacheln weit weg von bereits gelösten Bereichen nützlich.
            // Amber (gesetzt) vs. grau (kein manueller Hinweis, Standard-Schätzung wird verwendet).
            ObjectActionButton(
                screenPos = Offset(bottomMid.x - sidePx * 2f, bottomMid.y + belowPx),
                color = if (selectedTile.manualHintStarName != null) Color(0xFFFFA000) else Color(0xFF546E7A),
                icon = Icons.Default.Search,
                contentDescription = if (selectedTile.manualHintStarName != null) {
                    stringResource(R.string.manual_hint_active, selectedTile.manualHintStarName ?: "")
                } else {
                    stringResource(R.string.action_enter_manual_hint)
                },
                onClick = { onOpenManualHint(selectedTile.id) },
            )
            // De-Warp DIESER Kachel (pro Kachel): cyan (an) = wird vor dem Solve entzerrt; grau (aus) =
            // roh gelöst. Wirkt erst beim nächsten Lösen; invalidiert keine vorhandene Lösung. Gleiche
            // Kachellogik wie im Normalmodus, auch für Tiny-Sky-Kacheln verfügbar (s. solveAllTiles()/
            // dewarpModel -- die Tiny-Sky-Projektion ist als Korrekturgrundlage von Anfang an exakt
            // bekannt, kein natives Quellbild dafür nötig).
            ObjectActionButton(
                screenPos = Offset(bottomMid.x - sidePx * 1f, bottomMid.y + belowPx),
                color = if (selectedTile.dewarpRequested) Color(0xFF00B8D4) else Color(0xFF546E7A),
                icon = Icons.Default.Waves,
                contentDescription = if (selectedTile.dewarpRequested) {
                    stringResource(R.string.action_dewarp_off_tile)
                } else {
                    stringResource(R.string.action_dewarp_on_tile)
                },
                onClick = { onToggleTileDewarp(selectedTile.id) },
            )
            // Grün: sofort die nächste Kachel platzieren (spart den Weg zurück ins Solver-Menü).
            ObjectActionButton(
                screenPos = Offset(bottomMid.x, bottomMid.y + belowPx),
                color = Color(0xFF43A047),
                icon = Icons.Outlined.AddBox,
                contentDescription = stringResource(R.string.action_place_next_tile),
                onClick = { onPlaceNextTile() },
            )
            ObjectActionButton(
                screenPos = Offset(bottomMid.x + sidePx * 1f, bottomMid.y + belowPx),
                color = Color(0xFFE53935),
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.action_delete_tile),
                onClick = { onDeleteSolveTile(selectedTile.id) },
            )
            // Info: Status/De-Warp/Qualitäts-Einstufung dieser Kachel als Popup — macht sichtbar,
            // was vorher nur im Diagnose-Log stand.
            ObjectActionButton(
                screenPos = Offset(bottomMid.x + sidePx * 2f, bottomMid.y + belowPx),
                color = Color(0xFF546E7A),
                icon = Icons.Default.Info,
                contentDescription = stringResource(R.string.label_tile_info),
                onClick = { onOpenTileInfo(selectedTile.id) },
            )
        }

        // Verschiebe-/Anker-Modus: kein Fähnchen; Objekt folgt dem Finger (pink) bzw. Anker ziehbar.
        // Der frühere grüne „Fertig verschieben"-Balken entfällt -> die Aktion liegt jetzt in der
        // globalen Aktions-Bubble oben-mittig (kein Überlappen mit Vor/Zurück mehr).

        // Langdruck-Aktionsbuttons: Reihe OBERHALB der Bounding-Box. Löschen IMMER ganz rechts (außen).
        val actionOverlay = actionOverlayId?.let { aid -> overlays.firstOrNull { it.id == aid } }
        if (actionOverlay != null) {
            // PERFORMANCE (Fix 1): eigene lokale Berechnung statt der (Zeichenphase-lokalen, hier nicht
            // mehr sichtbaren) viewport-Variable oben -- diese Button-Reihe folgt ihrem Objekt weiterhin
            // korrekt beim Pannen, das ist hier gewollt/nötig.
            val actionViewport = ImageViewport.from(canvasSize, bitmap.width, bitmap.height, zoom, pan)
            val bounds = overlayImageBounds(actionOverlay)
            // Anker = Fingerdruck-Ort (falls bekannt), sonst Fallback Oberkante der Bounding-Box.
            val anchor = actionAnchorImage?.let { actionViewport.imageToScreen(it) }
                ?: actionViewport.imageToScreen(Offset((bounds.left + bounds.right) / 2f, bounds.top))
            val stepPx = with(density) { 60.dp.toPx() }
            val abovePx = with(density) { 64.dp.toPx() }
            data class ActBtn(
                val color: Color,
                val icon: androidx.compose.ui.graphics.vector.ImageVector,
                val desc: String,
                val onClick: () -> Unit,
            )
            val btns = buildList {
                add(ActBtn(Color(0xFF455A64), Icons.Default.OpenWith, stringResource(R.string.action_move)) {
                    actionOverlayId = null
                    moveOverlayId = actionOverlay.id
                })
                add(ActBtn(Color(0xFF9C27B0), Icons.Default.Build, stringResource(R.string.action_edit)) {
                    actionOverlayId = null
                    moveOverlayId = null
                    openOverlayEditing(actionOverlay)
                })
                if (actionOverlay.kind == OverlayKind.Constellation) {
                    // Gelb: Ankerringe ziehen = Sternbild umformen (Linien folgen).
                    add(ActBtn(Color(0xFFFBC02D), Icons.Default.ControlPoint, stringResource(R.string.label_anchors)) {
                        actionOverlayId = null
                        onSelectOverlay(actionOverlay.id)
                        anchorEditOverlayId = actionOverlay.id
                    })
                }
                if (actionOverlay.kind == OverlayKind.Text) {
                    add(ActBtn(Color(0xFF3949AB), Icons.Default.TextFields, stringResource(R.string.action_edit_text)) {
                        actionOverlayId = null
                        onEditOverlayText(actionOverlay.id)
                    })
                }
                if (actionOverlay.kind == OverlayKind.Freehand) {
                    // Behebt: nach Verlassen der Zeichnen-Sitzung ließ sich ein Freihand-Strich nur noch
                    // komplett löschen, nicht mehr weitermalen/radieren. Aktiviert den Stift erneut mit
                    // genau diesem Overlay geladen (s. continueEditingFreehand).
                    add(ActBtn(Color(0xFF00897B), Icons.Default.Brush, stringResource(R.string.action_continue_drawing)) {
                        actionOverlayId = null
                        onContinueDrawing(actionOverlay)
                    })
                }
                add(ActBtn(Color(0xFFE53935), Icons.Default.Delete, stringResource(R.string.action_delete)) {
                    actionOverlayId = null
                    moveOverlayId = null
                    onRemoveOverlay(actionOverlay.id)
                })
            }
            val count = btns.size
            // Buttons immer im sichtbaren Bereich halten: bei starkem Zoom kann die Objekt-Oberkante
            // außerhalb des Canvas liegen -> die Reihe würde sonst unsichtbar oben/seitlich rauslaufen.
            val marginPx = with(density) { 28.dp.toPx() }
            val rowHalf = (count - 1) / 2f * stepPx
            val loX = marginPx + rowHalf
            val hiX = canvasSize.width.toFloat() - marginPx - rowHalf
            val rowCenterX = if (loX <= hiX) anchor.x.coerceIn(loX, hiX) else canvasSize.width / 2f
            val rowY = (anchor.y - abovePx)
                .coerceIn(marginPx, (canvasSize.height.toFloat() - marginPx).coerceAtLeast(marginPx))
            btns.forEachIndexed { i, b ->
                ObjectActionButton(
                    screenPos = Offset(rowCenterX + (i - (count - 1) / 2f) * stepPx, rowY),
                    color = b.color,
                    icon = b.icon,
                    contentDescription = b.desc,
                    onClick = b.onClick,
                )
            }
        }
    }
}

@Composable
private fun UndoRedoBar(
    modifier: Modifier = Modifier,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.action_undo),
                    tint = if (canUndo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.action_redo),
                    tint = if (canRedo) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorBottomBar(
    modifier: Modifier = Modifier,
    activePanel: EditorPanel?,
    onPickImage: () -> Unit,
    onPanelSelected: (EditorPanel) -> Unit,
    onOpenCalibration: () -> Unit,
    onAddObject: () -> Unit,
    onOpenRegionInfo: () -> Unit,
) {
    val density = LocalDensity.current
    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    val bottomPadding = (bottomInset + 8.dp - 50.dp).coerceAtLeast(8.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Auf schmalen Displays passen nicht alle Werkzeuge nebeneinander;
            // die Leiste bleibt deshalb innerhalb der Bildschirmbreite scrollbar.
            modifier = Modifier.widthIn(max = 560.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Menü-Icon-Farben aus dem (bild-abgeleiteten) Theme: rotierende Rollen -> verschiedene
                // Farben, die sich mit dem geladenen Bild ändern.
                val cs = MaterialTheme.colorScheme
                ActionPillButton(
                    contentDescription = stringResource(R.string.action_load_image),
                    onClick = onPickImage,
                    restingTint = cs.primary,
                ) { tint ->
                    Icon(Icons.Default.Image, contentDescription = null, tint = tint)
                }
                // „Ausrichten"-Zwischenmenü entfällt: der Button öffnet direkt die Astrometrie-Kalibrierung.
                ActionPillButton(
                    contentDescription = stringResource(R.string.label_astrometry_calibration),
                    onClick = onOpenCalibration,
                    restingTint = cs.tertiary,
                ) { tint ->
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = tint)
                }
                PanelButton(EditorPanel.Mask, activePanel, stringResource(R.string.label_masking), onPanelSelected, restingTint = cs.secondary) { tint ->
                    Icon(Icons.Default.Brush, contentDescription = null, tint = tint)
                }
                PanelButton(EditorPanel.Annotate, activePanel, stringResource(R.string.label_catalog), onPanelSelected, restingTint = cs.primary) { tint ->
                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = tint)
                }
                ActionPillButton(
                    contentDescription = stringResource(R.string.label_region_info),
                    onClick = onOpenRegionInfo,
                    restingTint = cs.primary,
                ) { tint ->
                    Icon(Icons.Default.Info, contentDescription = null, tint = tint)
                }
                ActionPillButton(
                    contentDescription = stringResource(R.string.action_add_object),
                    onClick = onAddObject,
                    restingTint = cs.tertiary,
                ) { tint ->
                    Icon(Icons.Default.Add, contentDescription = null, tint = tint)
                }
                PanelButton(EditorPanel.Export, activePanel, stringResource(R.string.action_share), onPanelSelected, restingTint = cs.secondary) { tint ->
                    Icon(Icons.Default.Share, contentDescription = null, tint = tint)
                }
                PanelButton(EditorPanel.Settings, activePanel, stringResource(R.string.label_settings), onPanelSelected) { tint ->
                    Icon(Icons.Default.Settings, contentDescription = null, tint = tint)
                }
            }
        }
    }
}

// Performance-Fix 2026-09-02 (MethodTooLargeException, s. Memory project_methodtoolarge_editorpanelsheet):
// EditorPanelSheet war auf 151 Parameter angewachsen -- Compose generiert pro Composable-Aufruf
// $changed-Bit-Tracking-Bytecode, dessen Umfang mit der Parameterzahl wächst, und die Aufrufstelle in
// StarMapperApp() (der Lambda, die EditorPanelSheet(...) konstruiert) hat dadurch wiederholt die JVM-
// 64KB-Methodengrenze gerissen. Diese 6 Bündel fassen thematisch zusammengehörige Werte+Callbacks
// (Sternbild-/Formen-/Text-Stil, DSO-Standardfarben, Maske, Radierer) zu je EINEM Parameter zusammen --
// EditorPanelSheet selbst entpackt sie am Anfang seines Rumpfs wieder in lokale `val`s mit IDENTISCHEM
// Namen wie die bisherigen Parameter, sodass der GESAMTE übrige Funktionskörper unverändert bleibt.
private data class ConstellationStyleBundle(
    val colorArgb: Long,
    val font: OverlayFont,
    val lineStyle: OverlayLineStyle,
    val strokeWidth: Float,
    val anchorRadiusRatio: Float,
    val opacity: Float,
    val showName: Boolean,
    val nameTextSize: Float,
    val onOpacityChange: (Float) -> Unit,
    val onColorChange: (Long) -> Unit,
    val onFontChange: (OverlayFont) -> Unit,
    val onLineStyleChange: (OverlayLineStyle) -> Unit,
    val onStrokeWidthChange: (Float) -> Unit,
    val onAnchorRadiusChange: (Float) -> Unit,
    val onShowNameChange: (Boolean) -> Unit,
    val onNameTextSizeChange: (Float) -> Unit,
    val onOpacityChangeGlobal: (Float) -> Unit,
    val onColorChangeGlobal: (Long) -> Unit,
    val onFontChangeGlobal: (OverlayFont) -> Unit,
    val onLineStyleChangeGlobal: (OverlayLineStyle) -> Unit,
    val onStrokeWidthChangeGlobal: (Float) -> Unit,
    val onAnchorRadiusChangeGlobal: (Float) -> Unit,
    val onNameTextSizeChangeGlobal: (Float) -> Unit,
)

private data class ShapeStyleBundle(
    val colorArgb: Long,
    val strokeWidth: Float,
    val lineStyle: OverlayLineStyle,
    val opacity: Float,
    val filled: Boolean,
    val isReticle: Boolean,
    val font: OverlayFont,
    val showName: Boolean,
    val nameText: String,
    val nameTextSize: Float,
    val nameColorArgb: Long,
    val nameBold: Boolean,
    val onColorChange: (Long) -> Unit,
    val onStrokeWidthChange: (Float) -> Unit,
    val onLineStyleChange: (OverlayLineStyle) -> Unit,
    val onOpacityChange: (Float) -> Unit,
    val onFilledChange: (Boolean) -> Unit,
    val onFontChange: (OverlayFont) -> Unit,
    val onShowNameChange: (Boolean) -> Unit,
    val onEditName: () -> Unit,
    val onNameTextSizeChange: (Float) -> Unit,
    val onNameColorChange: (Long) -> Unit,
    val onNameBoldChange: (Boolean) -> Unit,
    val onColorChangeGlobal: (Long) -> Unit,
    val onNameColorChangeGlobal: (Long) -> Unit,
)

private data class TextStyleBundle(
    val colorArgb: Long,
    val font: OverlayFont,
    val size: Float,
    val bold: Boolean,
    val opacity: Float,
    val onColorChange: (Long) -> Unit,
    val onFontChange: (OverlayFont) -> Unit,
    val onSizeChange: (Float) -> Unit,
    val onBoldChange: (Boolean) -> Unit,
    val onOpacityChange: (Float) -> Unit,
    val onEditSelectedText: () -> Unit,
    val onColorChangeGlobal: (Long) -> Unit,
)

private data class DsoGlobalColorBundle(
    val galaxyColorArgb: Long,
    val onGalaxyColorChangeGlobal: (Long) -> Unit,
    val globularColorArgb: Long,
    val onGlobularColorChangeGlobal: (Long) -> Unit,
    val openClusterColorArgb: Long,
    val onOpenClusterColorChangeGlobal: (Long) -> Unit,
    val nebulaColorArgb: Long,
    val onNebulaColorChangeGlobal: (Long) -> Unit,
    val otherColorArgb: Long,
    val onOtherColorChangeGlobal: (Long) -> Unit,
    val reticleColorArgb: Long,
    val onReticleColorChangeGlobal: (Long) -> Unit,
)

private data class MaskPanelBundle(
    val toolActive: Boolean,
    val eraseMode: Boolean,
    val brushFraction: Float,
    val brushHardness: Float,
    val present: Boolean,
    val onToolToggle: () -> Unit,
    val onEraseModeChange: (Boolean) -> Unit,
    val onBrushFractionChange: (Float) -> Unit,
    val onBrushHardnessChange: (Float) -> Unit,
    val onClear: () -> Unit,
)

private data class EraserPanelBundle(
    val active: Boolean,
    val brushFraction: Float,
    val brushHardness: Float,
    val maskPresent: Boolean,
    val onToggle: () -> Unit,
    val onBrushChange: (Float) -> Unit,
    val onBrushHardnessChange: (Float) -> Unit,
    val onClear: () -> Unit,
)

@Composable
private fun EditorPanelSheet(
    panel: EditorPanel,
    selectedTool: EditorTool,
    selectedConstellation: ConstellationPattern,
    showDetectedStars: Boolean,
    showConstellationAnchors: Boolean,
    starDetectionSensitivity: Float,
    constellationStyle: ConstellationStyleBundle,
    shapeStyle: ShapeStyleBundle,
    textStyle: TextStyleBundle,
    isDetecting: Boolean,
    astapCaptureSettings: AstapCaptureSettings,
    astapExifFieldOfView: AstapExifFieldOfView?,
    astapEquipmentFieldOfView: AstapFieldOfView,
    astapFieldOfView: Float,
    astapOperationState: AstapOperationState,
    astapSolverChoice: AstapSolverChoice,
    novaApiKey: String,
    leftHandedDrawing: Boolean,
    onLeftHandedDrawingChange: (Boolean) -> Unit,
    starDisplayCap: Int,
    onStarDisplayCapChange: (Int) -> Unit,
    exportImageFormat: ExportImageFormat,
    onExportImageFormatChange: (ExportImageFormat) -> Unit,
    canExportSolution: Boolean,
    onExportSolution: () -> Unit,
    onImportSolution: () -> Unit,
    maskPanel: MaskPanelBundle,
    deepSkyAvailable: Boolean,
    deepSkyObjects: List<DeepSkyObject>,
    targetObjectQuery: String,
    targetObjectResolved: Boolean?,
    annotateSelections: AnnotateSelections,
    onAstapSolverChoiceChange: (AstapSolverChoice) -> Unit,
    onNovaApiKeyChange: (String) -> Unit,
    onTargetObjectQueryChange: (String) -> Unit,
    onApplyConstellations: () -> Unit,
    onApplyDeepSky: (Boolean) -> Unit,
    onApplyStars: (Boolean) -> Unit,
    onConstellationNamesChanged: () -> Unit,
    eraserPanel: EraserPanelBundle,
    onAddConstellation: () -> Unit,
    onStartFisheyeAlign: () -> Unit,
    onRevertAstrometry: () -> Unit,
    solveTileCount: Int = 0,
    onOpenCalibration: () -> Unit = {},
    fisheyeFovLongDeg: Float,
    onFisheyeFovChange: (Float) -> Unit,
    onToolSelected: (EditorTool) -> Unit,
    onPickImage: () -> Unit,
    onToggleStars: () -> Unit,
    onToggleConstellationAnchors: () -> Unit,
    onToggleConstellationAnchorsGlobal: () -> Unit,
    onStarDetectionSensitivityChange: (Float) -> Unit,
    onAstapCaptureSettingsChange: (AstapCaptureSettings) -> Unit,
    onStartAstapSolve: () -> Unit,
    onCancelAstapSolve: () -> Unit,
    layerDrawOrder: List<DrawLayer>,
    onLayerDrawOrderChange: (List<DrawLayer>) -> Unit,
    dsoGlobalColors: DsoGlobalColorBundle,
    onDetectStars: () -> Unit,
    onOpenSkyPicker: () -> Unit,
    onShareExport: (ExportScale, Boolean, ExportProjectionMode, Int) -> Unit,
    onSaveExport: (ExportScale, Boolean, ExportProjectionMode, Int) -> Unit,
    onShareAppDiagnostic: () -> Unit,
    onExportSolveCrop: () -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Performance-Fix 2026-09-02: die 6 Bündel-Parameter oben (s. Klassen direkt vor dieser Funktion)
    // werden HIER, ganz am Anfang, in lokale `val`s mit IDENTISCHEM Namen wie die vorherigen einzelnen
    // Parameter entpackt -- der GESAMTE übrige Funktionskörper (inkl. aller Weiterreichungen an
    // AnnotatePanel/ConstellationPanel/ShapesPanel/TextPanel/MaskPanel weiter unten) bleibt dadurch
    // UNVERÄNDERT, ohne jede einzelne Verwendungsstelle einzeln anpassen zu müssen.
    val constellationColorArgb = constellationStyle.colorArgb
    val constellationFont = constellationStyle.font
    val constellationLineStyle = constellationStyle.lineStyle
    val constellationStrokeWidth = constellationStyle.strokeWidth
    val constellationAnchorRadiusRatio = constellationStyle.anchorRadiusRatio
    val constellationOpacity = constellationStyle.opacity
    val constellationShowName = constellationStyle.showName
    val constellationNameTextSize = constellationStyle.nameTextSize
    val onConstellationOpacityChange = constellationStyle.onOpacityChange
    val onConstellationColorChange = constellationStyle.onColorChange
    val onConstellationFontChange = constellationStyle.onFontChange
    val onConstellationLineStyleChange = constellationStyle.onLineStyleChange
    val onConstellationStrokeWidthChange = constellationStyle.onStrokeWidthChange
    val onConstellationAnchorRadiusChange = constellationStyle.onAnchorRadiusChange
    val onConstellationShowNameChange = constellationStyle.onShowNameChange
    val onConstellationNameTextSizeChange = constellationStyle.onNameTextSizeChange
    val onConstellationOpacityChangeGlobal = constellationStyle.onOpacityChangeGlobal
    val onConstellationColorChangeGlobal = constellationStyle.onColorChangeGlobal
    val onConstellationFontChangeGlobal = constellationStyle.onFontChangeGlobal
    val onConstellationLineStyleChangeGlobal = constellationStyle.onLineStyleChangeGlobal
    val onConstellationStrokeWidthChangeGlobal = constellationStyle.onStrokeWidthChangeGlobal
    val onConstellationAnchorRadiusChangeGlobal = constellationStyle.onAnchorRadiusChangeGlobal
    val onConstellationNameTextSizeChangeGlobal = constellationStyle.onNameTextSizeChangeGlobal
    val shapeColorArgb = shapeStyle.colorArgb
    val shapeStrokeWidth = shapeStyle.strokeWidth
    val shapeLineStyle = shapeStyle.lineStyle
    val shapeOpacity = shapeStyle.opacity
    val shapeFilled = shapeStyle.filled
    val shapeIsReticle = shapeStyle.isReticle
    val shapeFont = shapeStyle.font
    val shapeShowName = shapeStyle.showName
    val shapeNameText = shapeStyle.nameText
    val shapeNameTextSize = shapeStyle.nameTextSize
    val shapeNameColorArgb = shapeStyle.nameColorArgb
    val shapeNameBold = shapeStyle.nameBold
    val onShapeColorChange = shapeStyle.onColorChange
    val onShapeStrokeWidthChange = shapeStyle.onStrokeWidthChange
    val onShapeLineStyleChange = shapeStyle.onLineStyleChange
    val onShapeOpacityChange = shapeStyle.onOpacityChange
    val onShapeFilledChange = shapeStyle.onFilledChange
    val onShapeFontChange = shapeStyle.onFontChange
    val onShapeShowNameChange = shapeStyle.onShowNameChange
    val onEditShapeName = shapeStyle.onEditName
    val onShapeNameTextSizeChange = shapeStyle.onNameTextSizeChange
    val onShapeNameColorChange = shapeStyle.onNameColorChange
    val onShapeNameBoldChange = shapeStyle.onNameBoldChange
    val onShapeColorChangeGlobal = shapeStyle.onColorChangeGlobal
    val onShapeNameColorChangeGlobal = shapeStyle.onNameColorChangeGlobal
    val textColorArgb = textStyle.colorArgb
    val textFont = textStyle.font
    val textSize = textStyle.size
    val textBold = textStyle.bold
    val textOpacity = textStyle.opacity
    val onTextColorChange = textStyle.onColorChange
    val onTextFontChange = textStyle.onFontChange
    val onTextSizeChange = textStyle.onSizeChange
    val onTextBoldChange = textStyle.onBoldChange
    val onTextOpacityChange = textStyle.onOpacityChange
    val onEditSelectedText = textStyle.onEditSelectedText
    val onTextColorChangeGlobal = textStyle.onColorChangeGlobal
    val dsoGalaxyColorArgb = dsoGlobalColors.galaxyColorArgb
    val onDsoGalaxyColorChangeGlobal = dsoGlobalColors.onGalaxyColorChangeGlobal
    val dsoGlobularColorArgb = dsoGlobalColors.globularColorArgb
    val onDsoGlobularColorChangeGlobal = dsoGlobalColors.onGlobularColorChangeGlobal
    val dsoOpenClusterColorArgb = dsoGlobalColors.openClusterColorArgb
    val onDsoOpenClusterColorChangeGlobal = dsoGlobalColors.onOpenClusterColorChangeGlobal
    val dsoNebulaColorArgb = dsoGlobalColors.nebulaColorArgb
    val onDsoNebulaColorChangeGlobal = dsoGlobalColors.onNebulaColorChangeGlobal
    val dsoOtherColorArgb = dsoGlobalColors.otherColorArgb
    val onDsoOtherColorChangeGlobal = dsoGlobalColors.onOtherColorChangeGlobal
    val reticleColorArgb = dsoGlobalColors.reticleColorArgb
    val onReticleColorChangeGlobal = dsoGlobalColors.onReticleColorChangeGlobal
    val maskToolActive = maskPanel.toolActive
    val maskEraseMode = maskPanel.eraseMode
    val maskBrushFraction = maskPanel.brushFraction
    val maskBrushHardness = maskPanel.brushHardness
    val maskPresent = maskPanel.present
    val onMaskToolToggle = maskPanel.onToolToggle
    val onMaskEraseModeChange = maskPanel.onEraseModeChange
    val onMaskBrushFractionChange = maskPanel.onBrushFractionChange
    val onMaskBrushHardnessChange = maskPanel.onBrushHardnessChange
    val onClearMask = maskPanel.onClear
    val eraserActive = eraserPanel.active
    val eraseBrushFraction = eraserPanel.brushFraction
    val eraseBrushHardness = eraserPanel.brushHardness
    val eraseMaskPresent = eraserPanel.maskPresent
    val onToggleEraser = eraserPanel.onToggle
    val onEraseBrushChange = eraserPanel.onBrushChange
    val onEraseBrushHardnessChange = eraserPanel.onBrushHardnessChange
    val onClearErase = eraserPanel.onClear
    val scrollState = rememberScrollState()
    // Slider-Scroll-Erhalt, PRO PANEL: Beim Ziehen eines Sliders wird der restliche Inhalt
    // ausgeblendet, wodurch die Scroll-Position sonst auf 0 geklemmt wird (Popup springt nach oben).
    // Daher: solange NICHT gezogen wird, die Position laufend merken; beim Loslassen zuerst
    // wiederherstellen, dann weiter mitschreiben (ein einziger Effekt -> kein Wettlauf zwischen
    // Sichern und Wiederherstellen). Wichtig: EIGENE Position je [panel] (statt ein gemeinsamer Wert
    // für alle Tabs) -- sonst "leckt" die Scroll-Position eines Tabs (z.B. Beschriften) beim
    // Tab-Wechsel in ein anderes, unterschiedlich langes Panel. Effekt-Key = panel (statt scrollState,
    // das über die Panel-Lebensdauer hinweg dieselbe Instanz bleibt) -> läuft bei jedem Tab-Wechsel neu an.
    // NICHT remember{} (Bug gefunden per Nutzertest): dieses Popup verlässt beim Tab-Wechsel
    // nachweislich die Composition -- s. menuTabMemory-Kommentar weiter unten ("...überlebt, dass der
    // Menü-Inhalt beim Schließen die Composition verlässt, dort verworfenes rememberSaveable fällt
    // sonst auf 0 zurück"), exakt dasselbe Problem, hier mit demselben Muster gelöst: ein
    // PROZESSWEITER Store (editorPanelScrollMemory, Top-Level außerhalb jeder Composable) statt
    // remember{}, das genau diesen Tab-Wechsel nicht übersteht.
    val sliderDraggingNow = LocalSliderDragging.current
    LaunchedEffect(panel, sliderDraggingNow) {
        if (sliderDraggingNow) return@LaunchedEffect
        scrollState.scrollTo(editorPanelScrollMemory[panel] ?: 0)
        snapshotFlow { scrollState.value }.collect { editorPanelScrollMemory[panel] = it }
    }
    val maxSheetHeight = when (panel) {
        EditorPanel.Image -> 118.dp
        EditorPanel.Automatic -> 300.dp
        EditorPanel.Mask -> 260.dp
        EditorPanel.Annotate -> 460.dp
        EditorPanel.Constellation -> 260.dp
        EditorPanel.Shapes -> 260.dp
        EditorPanel.Text -> 236.dp
        EditorPanel.Export -> 320.dp
        EditorPanel.Settings -> 220.dp
    }
    // „Peek": Popup-Höhe auf einen Bildschirm-Anteil deckeln, damit lange Panels überlaufen und der
    // letzte Regler angeschnitten ist (Hinweis: weiter swipebar). Kurze Panels passen ganz hinein.
    val screenCap = (LocalConfiguration.current.screenHeightDp * 0.55f).dp
    val effectiveMax = minOf(maxSheetHeight, screenCap)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = effectiveMax),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                // Gleiche großzügige Innenabstände wie der "Objekt platzieren"-Dialog.
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        HideWhileSliderDrag {
            // Kein X: normale Menü-Popups schließen per Tipp ins Bild (Scrim) / Navigationsleiste.
            MenuHeader(title = panelTitle(panel))
        }
        when (panel) {
            EditorPanel.Image -> ImagePanel(onPickImage)
            // Export läuft jetzt durch dasselbe Scaffold (Kopfzeile + Padding) wie alle Popups
            // (kein Sonderpfad mehr) -> einheitliche Optik.
            EditorPanel.Export -> ExportSheet(
                onShare = onShareExport,
                onSave = onSaveExport,
                onShareDiagnostic = onShareAppDiagnostic,
                onExportSolveCrop = onExportSolveCrop,
            )
            EditorPanel.Mask -> MaskPanel(
                maskToolActive = maskToolActive,
                maskEraseMode = maskEraseMode,
                maskBrushFraction = maskBrushFraction,
                maskBrushHardness = maskBrushHardness,
                maskPresent = maskPresent,
                onMaskToolToggle = onMaskToolToggle,
                onMaskEraseModeChange = onMaskEraseModeChange,
                onMaskBrushFractionChange = onMaskBrushFractionChange,
                onMaskBrushHardnessChange = onMaskBrushHardnessChange,
                onClearMask = onClearMask,
                eraserActive = eraserActive,
                eraseBrushFraction = eraseBrushFraction,
                eraseBrushHardness = eraseBrushHardness,
                eraseMaskPresent = eraseMaskPresent,
                onToggleEraser = onToggleEraser,
                onEraseBrushChange = onEraseBrushChange,
                onEraseBrushHardnessChange = onEraseBrushHardnessChange,
                onClearErase = onClearErase,
            )
            EditorPanel.Settings -> SettingsPanel(
                solverChoice = astapSolverChoice,
                novaApiKey = novaApiKey,
                isBusy = astapOperationState is AstapOperationState.Solving ||
                    astapOperationState is AstapOperationState.SolvingOnline,
                onSolverChoiceChange = onAstapSolverChoiceChange,
                onNovaApiKeyChange = onNovaApiKeyChange,
                leftHandedDrawing = leftHandedDrawing,
                onLeftHandedDrawingChange = onLeftHandedDrawingChange,
                starDisplayCap = starDisplayCap,
                onStarDisplayCapChange = onStarDisplayCapChange,
                exportImageFormat = exportImageFormat,
                onExportImageFormatChange = onExportImageFormatChange,
                canExportSolution = canExportSolution,
                onExportSolution = onExportSolution,
                onImportSolution = onImportSolution,
            )
            EditorPanel.Automatic -> {
                val usesNova = astapSolverChoice == AstapSolverChoice.NovaOnline
                val isBusy = astapOperationState is AstapOperationState.Solving ||
                    astapOperationState is AstapOperationState.SolvingOnline
                Button(onClick = onOpenCalibration, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (solveTileCount > 0) {
                            stringResource(R.string.open_astrometry_calibration_with_tiles, solveTileCount)
                        } else {
                            stringResource(R.string.action_open_astrometry_calibration)
                        },
                    )
                }
                HideWhileSliderDrag { HorizontalDivider() }
                // Schnell-Lösen: ganzes Bild als Einzelbild mit dem aktuellen Solver (aus Einstellungen).
                SolveActionSection(
                    operationState = astapOperationState,
                    usesNova = usesNova,
                    novaApiKey = novaApiKey,
                    isBusy = isBusy,
                    deepSkyAvailable = deepSkyAvailable,
                    onSolve = onStartAstapSolve,
                    onCancelSolve = onCancelAstapSolve,
                )
            }
            EditorPanel.Annotate -> AnnotatePanel(
                hasSolution = deepSkyAvailable,
                deepSkyObjects = deepSkyObjects,
                selections = annotateSelections,
                onApplyConstellations = onApplyConstellations,
                onApplyDeepSky = onApplyDeepSky,
                onApplyStars = onApplyStars,
                onConstellationNamesChanged = onConstellationNamesChanged,
                eraserActive = eraserActive,
                eraseBrushFraction = eraseBrushFraction,
                eraseMaskPresent = eraseMaskPresent,
                onToggleEraser = onToggleEraser,
                onEraseBrushChange = onEraseBrushChange,
                onClearErase = onClearErase,
                showConstellationAnchors = showConstellationAnchors,
                constellationColorArgb = constellationColorArgb,
                constellationFont = constellationFont,
                constellationLineStyle = constellationLineStyle,
                constellationStrokeWidth = constellationStrokeWidth,
                constellationAnchorRadiusRatio = constellationAnchorRadiusRatio,
                constellationOpacity = constellationOpacity,
                constellationNameTextSize = constellationNameTextSize,
                onToggleConstellationAnchorsGlobal = onToggleConstellationAnchorsGlobal,
                onConstellationColorChangeGlobal = onConstellationColorChangeGlobal,
                onConstellationFontChangeGlobal = onConstellationFontChangeGlobal,
                onConstellationLineStyleChangeGlobal = onConstellationLineStyleChangeGlobal,
                onConstellationStrokeWidthChangeGlobal = onConstellationStrokeWidthChangeGlobal,
                onConstellationAnchorRadiusChangeGlobal = onConstellationAnchorRadiusChangeGlobal,
                onConstellationOpacityChangeGlobal = onConstellationOpacityChangeGlobal,
                onConstellationNameTextSizeChangeGlobal = onConstellationNameTextSizeChangeGlobal,
                layerDrawOrder = layerDrawOrder,
                onLayerDrawOrderChange = onLayerDrawOrderChange,
                dsoGalaxyColorArgb = dsoGalaxyColorArgb,
                onDsoGalaxyColorChangeGlobal = onDsoGalaxyColorChangeGlobal,
                dsoGlobularColorArgb = dsoGlobularColorArgb,
                onDsoGlobularColorChangeGlobal = onDsoGlobularColorChangeGlobal,
                dsoOpenClusterColorArgb = dsoOpenClusterColorArgb,
                onDsoOpenClusterColorChangeGlobal = onDsoOpenClusterColorChangeGlobal,
                dsoNebulaColorArgb = dsoNebulaColorArgb,
                onDsoNebulaColorChangeGlobal = onDsoNebulaColorChangeGlobal,
                dsoOtherColorArgb = dsoOtherColorArgb,
                onDsoOtherColorChangeGlobal = onDsoOtherColorChangeGlobal,
                shapeColorArgb = shapeColorArgb,
                onShapeColorChangeGlobal = onShapeColorChangeGlobal,
                shapeNameColorArgb = shapeNameColorArgb,
                onShapeNameColorChangeGlobal = onShapeNameColorChangeGlobal,
                reticleColorArgb = reticleColorArgb,
                onReticleColorChangeGlobal = onReticleColorChangeGlobal,
                textColorArgb = textColorArgb,
                onTextColorChangeGlobal = onTextColorChangeGlobal,
            )
            EditorPanel.Constellation -> ConstellationPanel(
                selectedConstellation = selectedConstellation,
                showConstellationAnchors = showConstellationAnchors,
                colorArgb = constellationColorArgb,
                font = constellationFont,
                lineStyle = constellationLineStyle,
                strokeWidth = constellationStrokeWidth,
                anchorRadiusRatio = constellationAnchorRadiusRatio,
                opacity = constellationOpacity,
                showName = constellationShowName,
                nameTextSize = constellationNameTextSize,
                onOpenSkyPicker = onOpenSkyPicker,
                onAddConstellation = onAddConstellation,
                onStartFisheyeAlign = onStartFisheyeAlign,
                onRevertAstrometry = onRevertAstrometry,
                fisheyeFovLongDeg = fisheyeFovLongDeg,
                onFisheyeFovChange = onFisheyeFovChange,
                onToggleConstellationAnchors = onToggleConstellationAnchors,
                onColorChange = onConstellationColorChange,
                onFontChange = onConstellationFontChange,
                onLineStyleChange = onConstellationLineStyleChange,
                onStrokeWidthChange = onConstellationStrokeWidthChange,
                onAnchorRadiusChange = onConstellationAnchorRadiusChange,
                onShowNameChange = onConstellationShowNameChange,
                onNameTextSizeChange = onConstellationNameTextSizeChange,
                onOpacityChange = onConstellationOpacityChange,
            )
            EditorPanel.Shapes -> ShapesPanel(
                selectedTool = selectedTool,
                colorArgb = shapeColorArgb,
                strokeWidth = shapeStrokeWidth,
                lineStyle = shapeLineStyle,
                opacity = shapeOpacity,
                filled = shapeFilled,
                isReticle = shapeIsReticle,
                font = shapeFont,
                showName = shapeShowName,
                nameText = shapeNameText,
                nameTextSize = shapeNameTextSize,
                nameColorArgb = shapeNameColorArgb,
                nameBold = shapeNameBold,
                onToolSelected = onToolSelected,
                onColorChange = onShapeColorChange,
                onFontChange = onShapeFontChange,
                onStrokeWidthChange = onShapeStrokeWidthChange,
                onLineStyleChange = onShapeLineStyleChange,
                onOpacityChange = onShapeOpacityChange,
                onFilledChange = onShapeFilledChange,
                onShowNameChange = onShapeShowNameChange,
                onEditName = onEditShapeName,
                onNameTextSizeChange = onShapeNameTextSizeChange,
                onNameColorChange = onShapeNameColorChange,
                onNameBoldChange = onShapeNameBoldChange,
            )
            EditorPanel.Text -> TextPanel(
                selectedTool = selectedTool,
                colorArgb = textColorArgb,
                font = textFont,
                textSize = textSize,
                textBold = textBold,
                opacity = textOpacity,
                onToolSelected = onToolSelected,
                onEditSelectedText = onEditSelectedText,
                onColorChange = onTextColorChange,
                onFontChange = onTextFontChange,
                onTextSizeChange = onTextSizeChange,
                onTextBoldChange = onTextBoldChange,
                onOpacityChange = onTextOpacityChange,
            )
            EditorPanel.Export -> Unit
        }
        }
        VerticalScrollIndicator(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 3.dp, top = 10.dp, bottom = 10.dp),
        )
    }
}

// Dünner Scroll-Indikator (rechts) als Swipe-Hinweis für scrollbare Popups.
@Composable
private fun VerticalScrollIndicator(scrollState: ScrollState, modifier: Modifier = Modifier) {
    if (scrollState.maxValue <= 0) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    Canvas(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight(),
    ) {
        val trackHeight = size.height
        val content = trackHeight + scrollState.maxValue
        val thumbHeight = (trackHeight * (trackHeight / content)).coerceAtLeast(24.dp.toPx())
        val travel = (trackHeight - thumbHeight).coerceAtLeast(0f)
        val progress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()
        val thumbTop = travel * progress.coerceIn(0f, 1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, thumbTop),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f, size.width / 2f),
        )
    }
}

/**
 * Einheitliche, dynamisch mitwachsende Popup-Kopfzeile: zentrierte Pille, die mit dem Inhalt wächst
 * (`wrapContentWidth`). Minimal nur Undo/Redo bzw. Titel; mit mehr Inhalt dehnt sie sich zentriert
 * nach links/rechts aus. Undo/Redo erscheint nur, wenn der Kontext eins hat (canUndo/canRedo != null).
 */
@Composable
private fun MenuHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    // Einheitliche Popup-Kopfzeile im Stil des "Objekt platzieren"-Dialogs (M3 AlertDialog):
    // linksbündiger Titel in headlineSmall. KEIN X bei normalen Menü-Popups (Schließen per Tipp ins
    // Bild / über die Navigationsleiste). onClose (X) nur, wo es kein Tap-zu gibt (Kalibrierungs-Karte).
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

/** Eine kontextuelle Aktion in der globalen Aktions-Bubble (z. B. „Anwenden", „Fertig verschieben"). */
internal data class BarAction(val label: String, val onClick: () -> Unit)

/**
 * Globale Aktions-Bubble (oben-mittig, für ALLE Funktionen): links Vor/Zurück (kontextabhängig
 * verdrahtet – Overlays/Kacheln/Feinjustierung), rechts kontextuelle Aktionen (Anwenden/Fertig
 * verschieben/Abbrechen) + laufende Info-Texte. Ohne Aktion/Info schrumpft sie auf die kompakte
 * Vor/Zurück-Pille. Mehrere Infos werden abwechselnd (3-s-Takt) durchgeblättert; lange Texte laufen
 * (Marquee). Ersetzt frühere freischwebende Leisten, das separate Solve-Banner UND den grünen
 * „Fertig verschieben"-Balken. Vor/Zurück sind bewusst nicht ausgegraut.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun GlobalActionBar(
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
    infoTexts: List<String> = emptyList(),
    actions: List<BarAction> = emptyList(),
    enabled: Boolean = true,
) {
    var infoIndex by remember { mutableStateOf(0) }
    LaunchedEffect(infoTexts.size) {
        infoIndex = 0
        if (infoTexts.size >= 2) {
            while (true) {
                kotlinx.coroutines.delay(3000)
                infoIndex = (infoIndex + 1) % infoTexts.size
            }
        }
    }
    val info = if (infoTexts.isEmpty()) null else infoTexts[infoIndex % infoTexts.size]
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        // Inhaltsfarbe explizit: bei `surface.copy(alpha=…)` findet contentColorFor keine Übereinstimmung
        // und fiele auf den umgebenden LocalContentColor (über schwarzem Hintergrund = SCHWARZ) zurück
        // -> Vor/Zurück-Icons wären unsichtbar. Mit onSurface immer korrekt kontrastiert.
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onUndo, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.action_undo))
            }
            IconButton(onClick = onRedo, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.action_redo))
            }
            if (info != null) {
                Text(
                    info,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    // Kein „…": weight nimmt die Restbreite, basicMarquee scrollt den KOMPLETTEN Text.
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp, end = 4.dp)
                        .basicMarquee(),
                )
            }
            actions.forEach { action ->
                // Kein Umbruch: der Text läuft (Marquee) und bleibt lesbar, statt zweizeilig zu werden.
                TextButton(onClick = action.onClick) {
                    Text(action.label, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/**
 * Gemeinsame Popup-Hülle für ALLE Menü-Popups („eine Logik"): unten verankerter `Dialog` mit
 * antippbarem Scrim (schließt), M3-Optik (surfaceContainerHigh, 28dp), über der Bottom-Bar. Beim
 * Slider-Ziehen wird die Karte transparent und der Dialog-Dimmer 0 (Bild voll sichtbar); der aktive
 * Slider bleibt, der Rest blendet sich via `HideWhileSliderDrag` aus. `content` bringt eigene
 * Kopfzeile/Scroll mit (z. B. `EditorPanelSheet`). [sliderDragging] ist hochgezogen, damit der
 * Editor-Canvas das Ziehen kennt (Pinsel-Vorschau); Popups ohne Slider lassen die Defaults.
 */
@Composable
internal fun AppBottomPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sliderDragging: Boolean = false,
    onSliderDraggingChange: (Boolean) -> Unit = {},
    // Default unverändert (alle bisherigen Menüs: Zurück-Taste schließt normal). Die Astrometrie-
    // Kalibrierung setzt dies auf false, weil sie eine EIGENE BackHandler-Logik hat, die zwischen
    // „minimieren" (Kachel platzieren) und „verlassen" unterscheidet -> keine Doppel-Behandlung.
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = dismissOnBackPress),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(sliderDragging) {
            dialogWindow?.setDimAmount(if (sliderDragging) 0f else 0.32f)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = if (sliderDragging) RectangleShape else RoundedCornerShape(28.dp),
                color = if (sliderDragging) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
                // Inhaltsfarbe explizit: contentColorFor kennt surfaceContainerHigh NICHT und fiele sonst
                // auf den umgebenden LocalContentColor zurück (über schwarzem Feinjustier-Bg = SCHWARZ)
                // -> Texte/Icons unsichtbar. onSurface ist auf surfaceContainerHigh immer lesbar.
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (sliderDragging) 0.dp else 6.dp,
                modifier = modifier
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp, start = 8.dp, end = 8.dp)
                    .fillMaxWidth()
                    // Klicks im Popup sollen den Scrim NICHT auslösen (= nicht schließen).
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {},
            ) {
                CompositionLocalProvider(
                    LocalSliderDrag provides { dragging: Boolean -> onSliderDraggingChange(dragging) },
                    LocalSliderDragging provides sliderDragging,
                ) {
                    content()
                }
            }
        }
    }
}

// Merkt sich je Menü den zuletzt gewählten Tab über Schließen/Öffnen hinweg (Nutzerwunsch: nicht
// jedes Mal neu durchklicken). Prozessweiter Snapshot-Store; überlebt, dass der Menü-Inhalt beim
// Schließen die Composition verlässt (dort verworfenes rememberSaveable fällt sonst auf 0 zurück).
private val menuTabMemory = mutableStateMapOf<String, Int>()

// Dasselbe Prinzip wie menuTabMemory, für die Scroll-Position je EditorPanel (s. EditorPanelSheet) --
// überlebt aus demselben Grund den Panel-/Tab-Wechsel (remember{} dort wurde per Nutzertest als
// unzureichend erkannt: die Position sprang beim Zurückwechseln wieder auf 0).
private val editorPanelScrollMemory = mutableStateMapOf<EditorPanel, Int>()

/** Drop-in für `rememberSaveable { mutableStateOf(0) }`, aber pro [key] persistent über Menü-Öffnen/Schließen. */
@Composable
private fun rememberMenuTab(key: String): MutableState<Int> {
    val state = remember(key) { mutableStateOf(menuTabMemory[key] ?: 0) }
    LaunchedEffect(state.value) { menuTabMemory[key] = state.value }
    return state
}

/** Einheitliche Bereichs-Umschaltung als native M3-Tabs (ersetzt die abgeschnittenen Segment-Buttons). */
@Composable
private fun AppSegmentTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Scrollbar + inhaltsbreite Tabs: jeder Tab ist so breit wie sein VOLLER Text -> nichts wird
    // abgeschnitten (fixe gleichbreite PrimaryTabRow schnitt „Feinjustierung"/„Projektion" ab).
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0)),
        modifier = modifier,
        containerColor = Color.Transparent,
        edgePadding = 0.dp,
    ) {
        options.forEachIndexed { index, label ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                text = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun panelTitle(panel: EditorPanel): String = when (panel) {
    EditorPanel.Image -> stringResource(R.string.panel_image)
    EditorPanel.Automatic -> stringResource(R.string.panel_align)
    EditorPanel.Mask -> stringResource(R.string.panel_mask)
    EditorPanel.Annotate -> stringResource(R.string.panel_annotate)
    EditorPanel.Constellation -> stringResource(R.string.panel_constellation)
    EditorPanel.Shapes -> stringResource(R.string.panel_shapes)
    EditorPanel.Text -> stringResource(R.string.panel_text)
    EditorPanel.Export -> stringResource(R.string.panel_export)
    EditorPanel.Settings -> stringResource(R.string.panel_settings)
}

private fun editorPanelForOverlay(kind: OverlayKind): EditorPanel = when (kind) {
    OverlayKind.Constellation -> EditorPanel.Constellation
    OverlayKind.Ellipse, OverlayKind.Rectangle, OverlayKind.Freehand -> EditorPanel.Shapes
    OverlayKind.Text -> EditorPanel.Text
}

private fun editorToolForOverlay(kind: OverlayKind): EditorTool = when (kind) {
    OverlayKind.Constellation -> EditorTool.Constellation
    OverlayKind.Ellipse -> EditorTool.Ellipse
    OverlayKind.Rectangle -> EditorTool.Rectangle
    OverlayKind.Text -> EditorTool.Text
    OverlayKind.Freehand -> EditorTool.Draw
}

// Runder Aktions-Button am Objekt (Langdruck): Löschen/Bearbeiten. Per offset am Objekt platziert.
@Composable
private fun ObjectActionButton(
    screenPos: Offset,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val halfPx = with(LocalDensity.current) { 24.dp.toPx() }
    Box(
        modifier = Modifier
            .offset { IntOffset((screenPos.x - halfPx).roundToInt(), (screenPos.y - halfPx).roundToInt()) }
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

// Verschiebe-Griff (Strich + Knopf, diagonal zur Bildmitte) — gleiche Logik wie das Fähnchen der
// Feinausrichtung. Ziehen am Knopf verschiebt das Objekt (Delta -> Bildkoordinaten).
@Composable
private fun MoveModeDoneButton(modifier: Modifier = Modifier, onDone: () -> Unit) {
    // Verschiebe-Modus beenden: gut sichtbare grüne Pille oben-mittig (zuverlässiger als ein Tipp).
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 12.dp)
            .clickable(onClick = onDone),
        shape = RoundedCornerShape(50),
        color = Color(0xFF2E7D32),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            Text(
                text = stringResource(R.string.done_moving_button),
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// Tap-to-place-Hinweis: pink pulsierender Bildrand + zentrale Pille „Objektposition auswählen".
// Ohne Pointer-Handler -> Tipps gehen durch zum EditorCanvas darunter.
@Composable
private fun PlacementHintOverlay(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
    )
    val pink = Color(0xFFFF4FD8)
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 6.dp.toPx()
            drawRect(
                color = pink.copy(alpha = pulse),
                topLeft = Offset(w / 2f, w / 2f),
                size = Size(size.width - w, size.height - w),
                style = Stroke(width = w),
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(50),
            color = pink.copy(alpha = 0.88f),
            shadowElevation = 4.dp,
        ) {
            Text(
                text = stringResource(R.string.tap_to_place_object),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ActionPillButton(
    contentDescription: String,
    onClick: () -> Unit,
    // Aus dem (bild-abgeleiteten) Theme getönt -> Menü-Icons erhalten je nach Bild verschiedene Farben.
    restingTint: Color? = null,
    icon: @Composable (Color) -> Unit,
) {
    val tint = restingTint ?: MaterialTheme.colorScheme.onSurfaceVariant
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
            icon(tint)
        }
    }
}

@Composable
private fun PanelButton(
    panel: EditorPanel,
    activePanel: EditorPanel?,
    contentDescription: String,
    onPanelSelected: (EditorPanel) -> Unit,
    // Ruhefarbe aus dem (bild-abgeleiteten) Theme; aktiv bleibt onPrimaryContainer für klaren Kontrast.
    restingTint: Color? = null,
    icon: @Composable (Color) -> Unit,
) {
    val selected = activePanel == panel
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else (restingTint ?: MaterialTheme.colorScheme.onSurfaceVariant)
    IconButton(
        onClick = { onPanelSelected(panel) },
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
            icon(tint)
        }
    }
}

@Composable
private fun DashedRectangleIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        drawRoundRect(
            color = tint,
            topLeft = Offset(4.dp.toPx(), 5.dp.toPx()),
            size = Size(16.dp.toPx(), 14.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx()), 0f),
            ),
        )
    }
}

@Composable
private fun UrsaMinorIcon(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val catalogPoints = listOf(
            -123.9853f to 77.7945f,
            -115.6238f to 75.7553f,
            -129.8179f to 71.8340f,
            -137.3236f to 74.1555f,
            -123.9853f to 77.7945f,
            -108.5073f to 82.0373f,
            -96.9458f to 86.5865f,
            37.9545f to 89.2641f,
        )
        val polarPoints = catalogPoints.map { (raDegrees, decDegrees) ->
            val angle = raDegrees / 180f * PI.toFloat()
            val radiusFromPole = (90f - decDegrees).coerceAtLeast(0.1f)
            Offset(
                x = sin(angle) * radiusFromPole,
                y = -cos(angle) * radiusFromPole,
            )
        }
        val minX = polarPoints.minOf { it.x }
        val maxX = polarPoints.maxOf { it.x }
        val minY = polarPoints.minOf { it.y }
        val maxY = polarPoints.maxOf { it.y }
        val padding = 3.dp.toPx()
        val scale = min(
            (size.width - padding * 2f) / (maxX - minX).coerceAtLeast(1f),
            (size.height - padding * 2f) / (maxY - minY).coerceAtLeast(1f),
        )
        val fittedSize = Size((maxX - minX) * scale, (maxY - minY) * scale)
        val origin = Offset(
            x = (size.width - fittedSize.width) / 2f - minX * scale,
            y = (size.height - fittedSize.height) / 2f - minY * scale,
        )
        val points = polarPoints.map { point -> origin + point * scale }
        points.zipWithNext().forEach { (start, end) ->
            drawLine(tint, start, end, strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        }
        points.forEachIndexed { index, point ->
            val radius = if (index == points.lastIndex) 2.35.dp.toPx() else 2.05.dp.toPx()
            drawCircle(tint, radius = radius, center = point)
        }
    }
}

@Composable
private fun ImagePanel(onPickImage: () -> Unit) {
    Row(
        modifier = Modifier.padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onPickImage,
            leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
            label = { Text(stringResource(R.string.action_load_new_image)) },
        )
    }
}

// Gemeinsame Liste für Sprachauswahl (SettingsPanel) UND Region-Info-Fallback-Sprache -- jede Sprache in
// ihrem eigenen Endonym (Eigenbezeichnung), nicht übersetzt. Als Funktion (nicht top-level val), damit
// stringResource(R.string.language_system) bei jedem Sprachwechsel neu ausgewertet wird statt einmalig
// beim Laden der Klasse.
@Composable
private fun appLanguageChoices(): List<Pair<AppLang, String>> = listOf(
    AppLang.System to stringResource(R.string.language_system),
    AppLang.German to "Deutsch",
    AppLang.English to "English",
    AppLang.Chinese to "中文",
    AppLang.Spanish to "Español",
    AppLang.Russian to "Русский",
    AppLang.Arabic to "العربية",
    AppLang.Japanese to "日本語",
)

/** Einstellungen-Menü: Plate-Solver-Backend (ASTAP/Nova) + API-Schlüssel. */
@Composable
private fun SettingsPanel(
    solverChoice: AstapSolverChoice,
    novaApiKey: String,
    isBusy: Boolean,
    onSolverChoiceChange: (AstapSolverChoice) -> Unit,
    onNovaApiKeyChange: (String) -> Unit,
    leftHandedDrawing: Boolean,
    onLeftHandedDrawingChange: (Boolean) -> Unit,
    starDisplayCap: Int,
    onStarDisplayCapChange: (Int) -> Unit,
    exportImageFormat: ExportImageFormat,
    onExportImageFormatChange: (ExportImageFormat) -> Unit,
    canExportSolution: Boolean,
    onExportSolution: () -> Unit,
    onImportSolution: () -> Unit,
) {
    var showIndexDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Sprachauswahl: System folgen oder fest auf eine der 7 unterstützten Sprachen stellen (reaktiv
        // über AppLocale). Jede Sprache zeigt ihren eigenen Namen in eigener Schrift (Endonym), nicht
        // übersetzt -- Nutzer müssen ihre Sprache erkennen können, unabhängig von der aktuellen UI-Sprache.
        Text(
            text = stringResource(R.string.label_language),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val current = AppLocale.current
        val languageChoices = appLanguageChoices()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languageChoices, key = { it.first.name }) { (lang, label) ->
                FilterChip(
                    selected = current == lang,
                    onClick = { AppLocale.set(context, lang) },
                    label = { Text(label) },
                )
            }
        }
        // Region-Info-Fallback-Sprache: nur relevant, wenn Wikipedia in der App-Sprache nichts liefert
        // (dann diese Sprache, sonst zuletzt immer Englisch) -- "System" ergibt hier keinen Sinn, daher
        // ohne diese Option.
        Text(
            text = stringResource(R.string.label_region_info_fallback_lang),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.region_info_fallback_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fallbackLang = AppLocale.fallbackLanguage
        val fallbackChoices = languageChoices.filter { it.first != AppLang.System }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fallbackChoices, key = { it.first.name }) { (lang, label) ->
                FilterChip(
                    selected = fallbackLang == lang,
                    onClick = { AppLocale.setFallbackLanguage(context, lang) },
                    label = { Text(label) },
                )
            }
        }
        HorizontalDivider()
        // Zeichnen-Werkzeug (Zweifinger-Stift): Rechts-/Linkshänder spiegelt den Greif-Versatz +
        // das Stift-Bild auf die andere Seite der Spitze.
        Text(
            text = stringResource(R.string.label_drawing),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !leftHandedDrawing,
                onClick = { onLeftHandedDrawingChange(false) },
                label = { Text(stringResource(R.string.label_right_handed)) },
            )
            FilterChip(
                selected = leftHandedDrawing,
                onClick = { onLeftHandedDrawingChange(true) },
                label = { Text(stringResource(R.string.label_left_handed)) },
            )
        }
        HorizontalDivider()
        // Anzeige-Deckel für "Alle Sterne bis Magnitude" (s. AstapOverlayMapper.createStarOverlays
        // maxStars): bewusst hier statt im Katalog-bearbeiten-Menü (Nutzerwunsch 2026-08-22) -- ist keine
        // bildspezifische Anzeige-Einstellung wie Magnitude/Chips, sondern eine einmalige, geräteweite
        // Performance-Abwägung ("wie viele Sterne verträgt mein Handy noch flüssig").
        Text(
            text = stringResource(R.string.label_star_display_cap),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.star_display_cap_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        var starDisplayCapDraft by remember { mutableStateOf(starDisplayCap.toFloat()) }
        SettingSlider(
            label = stringResource(R.string.star_display_cap_value, starDisplayCapDraft.roundToInt()),
            value = starDisplayCapDraft,
            valueRange = 0f..10000f,
            onValueChange = { starDisplayCapDraft = it },
            onValueChangeFinished = { onStarDisplayCapChange(starDisplayCapDraft.roundToInt()) },
        )
        HorizontalDivider()
        // Bilddateiformat für den "mit Hintergrund"-Export (Nutzerwunsch 2026-08-24): JPEG schreibt
        // zusätzlich eingebettete 360°-XMP-Metadaten (Google-Photo-Sphere-Standard), damit externe
        // Galerie-Apps (z.B. Samsung) das exportierte Bild als Panorama erkennen -- geht nur bei JPEG,
        // nicht bei PNG. Der "nur Overlay"-Export bleibt davon unabhängig immer PNG (Transparenz).
        Text(
            text = stringResource(R.string.label_export_image_format),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.export_image_format_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = exportImageFormat == ExportImageFormat.Png,
                onClick = { onExportImageFormatChange(ExportImageFormat.Png) },
                label = { Text(stringResource(R.string.label_export_format_png)) },
            )
            FilterChip(
                selected = exportImageFormat == ExportImageFormat.Jpeg,
                onClick = { onExportImageFormatChange(ExportImageFormat.Jpeg) },
                label = { Text(stringResource(R.string.label_export_format_jpeg)) },
            )
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.label_plate_solver),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        SolverBackendSection(solverChoice, novaApiKey, isBusy, onSolverChoiceChange, onNovaApiKeyChange)
        HorizontalDivider()
        // Index-Kataloge für den lokalen Offline-Solver (schmalere Felder herunterladen).
        OutlinedButton(onClick = { showIndexDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CloudDownload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.label_index_catalogues))
        }
        HorizontalDivider()
        // Astrometrie-Lösung exportieren/laden (Nutzerwunsch 2026-08-25): "damit ich das nicht noch mal
        // neu lösen muss", wenn dasselbe Foto später erneut geöffnet wird. Bewusst ein einfaches
        // Export/Import über den System-Dateidialog (SAF) statt einer eigenen Verwaltungsoberfläche --
        // der Nutzer wählt Ort UND Namen der Datei selbst, mehrere Speicherstände sind dadurch einfach
        // mehrere selbst benannte Dateien. Export ist deaktiviert, wenn keine (oder eine Mesh-korrigierte/
        // restkorrigierte, s. SolutionExport.isExportable) Lösung vorliegt.
        Text(
            text = stringResource(R.string.label_solution_export_import),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (!canExportSolution) {
            Text(
                stringResource(R.string.label_solution_not_exportable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportSolution, enabled = canExportSolution, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_export_solution))
            }
            OutlinedButton(onClick = onImportSolution, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_import_solution))
            }
        }
    }
    if (showIndexDialog) {
        IndexCatalogDialog(onDismiss = { showIndexDialog = false })
    }
}

/** Dialog: herunterladbare astrometry.net-Index-Pakete für den lokalen Offline-Solver. */
@Composable
private fun IndexCatalogDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.label_index_catalogues)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.index_bundled_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AstrometryIndexPackage.entries.forEach { pkg -> IndexPackageRow(pkg) }
            }
        },
    )
}

/** Eine Paket-Zeile im Index-Dialog: Titel/Abdeckung/Größe + Laden/Abbrechen/Löschen + Fortschritt. */
@Composable
private fun IndexPackageRow(pkg: AstrometryIndexPackage) {
    val context = LocalContext.current
    val downloader = remember(context) { AstrometryIndexDownloader(context) }
    // Zustand kommt aus dem prozessweiten Manager -> überlebt das Schließen/Wiederöffnen des Dialogs.
    val completed = AstrometryIndexDownloadManager.completedVersion.intValue
    val installed = remember(pkg.id, completed) { downloader.isInstalled(pkg) }
    val progress = AstrometryIndexDownloadManager.progress[pkg.id]
    val error = AstrometryIndexDownloadManager.errors[pkg.id]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(pkg.titleResId), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(pkg.coverageResId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.download_size_label, formatBytes(pkg.approxBytes)),
            style = MaterialTheme.typography.labelMedium,
        )
        val p = progress
        when {
            p != null -> {
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { AstrometryIndexDownloadManager.cancel(pkg) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.cancel_with_percent, (p * 100).roundToInt()))
                }
            }
            installed -> {
                Text(
                    stringResource(R.string.label_installed_offline_usable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = { AstrometryIndexDownloadManager.remove(context, pkg) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_delete)) }
            }
            else -> {
                Button(
                    onClick = { AstrometryIndexDownloadManager.start(context, pkg) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_download)) }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
    } else {
        String.format(java.util.Locale.US, "%.0f MB", mb)
    }
}

/** Beschriftungs-Radierer (wischt Sternbilder/Marker im Vordergrund weg). */
@Composable
private fun AnnotationEraserSection(
    eraserActive: Boolean,
    eraseBrushFraction: Float,
    eraseBrushHardness: Float,
    eraseMaskPresent: Boolean,
    onToggleEraser: () -> Unit,
    onEraseBrushChange: (Float) -> Unit,
    onEraseBrushHardnessChange: (Float) -> Unit,
    onClearErase: () -> Unit,
) {
    // Der Beschriftungs-Radierer ist automatisch aktiv, solange dieser Tab offen ist (kein Aktivier-Chip).
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HideWhileSliderDrag {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_label_eraser),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (eraseMaskPresent) {
                    TextButton(onClick = onClearErase) {
                        Text(stringResource(R.string.action_reset))
                    }
                }
            }
        }
        SettingSlider(
            label = stringResource(R.string.label_eraser_brush_size),
            value = eraseBrushFraction,
            valueRange = 0.006f..0.2f,
            onValueChange = onEraseBrushChange,
        )
        SettingSlider(
            label = stringResource(R.string.label_eraser_soft_hard),
            value = eraseBrushHardness,
            valueRange = 0f..1f,
            onValueChange = onEraseBrushHardnessChange,
        )
    }
}

/** Maskieren-Menü: Vordergrund-Maskierpinsel (Solven) + Beschriftungs-Radierer (Overlays). */
@Composable
private fun MaskPanel(
    maskToolActive: Boolean,
    maskEraseMode: Boolean,
    maskBrushFraction: Float,
    maskBrushHardness: Float,
    maskPresent: Boolean,
    onMaskToolToggle: () -> Unit,
    onMaskEraseModeChange: (Boolean) -> Unit,
    onMaskBrushFractionChange: (Float) -> Unit,
    onMaskBrushHardnessChange: (Float) -> Unit,
    onClearMask: () -> Unit,
    eraserActive: Boolean,
    eraseBrushFraction: Float,
    eraseBrushHardness: Float,
    eraseMaskPresent: Boolean,
    onToggleEraser: () -> Unit,
    onEraseBrushChange: (Float) -> Unit,
    onEraseBrushHardnessChange: (Float) -> Unit,
    onClearErase: () -> Unit,
) {
    // Wie die Astrometrie-Kalibrierung: zwei Tabs statt gestapelter Abschnitte (gleiches Schalt-Menü).
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Persistent über Menü-Schließen/Öffnen (zuletzt bedienten Tab merken).
        var maskTab by rememberMenuTab("mask")
        // Menüpunkt aktiv ⇒ Werkzeug automatisch aktiv: „Vordergrund" aktiviert den Maskenpinsel,
        // „Beschriftung" den Beschriftungs-Radierer. Kein separater Aktivier-Chip mehr nötig; damit
        // ist auch nur die Maske des aktiven Tabs sichtbar (Canvas rendert je Werkzeug).
        LaunchedEffect(maskTab) {
            if (maskTab == 0) onMaskToolToggle() else onToggleEraser()
        }
        HideWhileSliderDrag {
            AppSegmentTabs(
                options = listOf(stringResource(R.string.label_foreground), stringResource(R.string.label_labels)),
                selectedIndex = maskTab,
                onSelect = { maskTab = it },
            )
        }
        if (maskTab == 0) {
            ForegroundMaskSection(
                maskToolActive, maskEraseMode, maskBrushFraction, maskBrushHardness, maskPresent,
                onMaskToolToggle, onMaskEraseModeChange, onMaskBrushFractionChange,
                onMaskBrushHardnessChange, onClearMask,
            )
        } else {
            AnnotationEraserSection(
                eraserActive, eraseBrushFraction, eraseBrushHardness, eraseMaskPresent,
                onToggleEraser, onEraseBrushChange, onEraseBrushHardnessChange, onClearErase,
            )
        }
    }
}

@Composable
private fun StarsPanel(
    selectedTool: EditorTool,
    showDetectedStars: Boolean,
    starDetectionSensitivity: Float,
    isDetecting: Boolean,
    onToolSelected: (EditorTool) -> Unit,
    onDetectStars: () -> Unit,
    onToggleStars: () -> Unit,
    onStarDetectionSensitivityChange: (Float) -> Unit,
) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HideWhileSliderDrag {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AssistChip(
                    onClick = onDetectStars,
                    enabled = !isDetecting,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    label = { Text(if (isDetecting) stringResource(R.string.status_searching) else stringResource(R.string.action_detect_stars)) },
                )
            }
            item {
                FilterChip(
                    selected = showDetectedStars,
                    onClick = onToggleStars,
                    leadingIcon = {
                        Icon(
                            if (showDetectedStars) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.action_show)) },
                )
            }
            item {
                FilterChip(
                    selected = selectedTool == EditorTool.AddStar,
                    onClick = { onToolSelected(EditorTool.AddStar) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_add_star)) },
                )
            }
            item {
                FilterChip(
                    selected = selectedTool == EditorTool.DeleteStar,
                    onClick = { onToolSelected(EditorTool.DeleteStar) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    label = { Text(stringResource(R.string.action_delete_star)) },
                )
            }
        }
        }
        SettingSlider(
            label = stringResource(R.string.label_sensitivity),
            value = starDetectionSensitivity,
            valueRange = 0f..1f,
            onValueChange = onStarDetectionSensitivityChange,
        )
    }
}

private fun formatDegrees(value: Float): String {
    val decimals = if (value < 10f) 2 else 1
    return String.format(Locale.getDefault(), "%.${decimals}f°", value)
}

/** Solver-Konfiguration (FOV-Quelle, Equipment, Mosaik, Zielobjekt, DB-Anzeige) – ASTAP-Einzelbild/Mosaik. */
@Composable
private fun SolverConfigSection(
    captureSettings: AstapCaptureSettings,
    exifFieldOfView: AstapExifFieldOfView?,
    equipmentFieldOfView: AstapFieldOfView,
    fieldOfViewDegrees: Float,
    // Nutzerwunsch 2026-08-20 (ASTAP entfernt): Kamera-/Objektiv-Profil bestimmt weiterhin
    // [fieldOfViewDegrees], das AUCH Lokal (offline) und Nova (online) als Such-RADIUS um ein
    // eingetipptes Zielobjekt nutzen (s. startSingleImageSolve, targetSkyPoint) -- deshalb für BEIDE
    // sichtbar, nicht mehr hinter einem "usesAstap"-Schalter versteckt.
    targetObjectQuery: String,
    targetObjectResolved: Boolean?,
    isBusy: Boolean,
    onCaptureSettingsChange: (AstapCaptureSettings) -> Unit,
    onTargetObjectQueryChange: (String) -> Unit,
) {
    val selectedCamera = AstapEquipmentCatalog.camera(captureSettings.cameraProfileId)
    var focalText by remember {
        mutableStateOf(captureSettings.focalLengthMm.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() })
    }
    var megapixelText by remember {
        mutableStateOf(captureSettings.sensorMegapixels.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() })
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // FOV-/Equipment-Optionen (Mosaik-Raster mit ASTAP entfernt, 2026-08-20 -- nur noch Einzelbild).
        if (captureSettings.captureType != AstapCaptureType.Fisheye) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (captureSettings.captureType == AstapCaptureType.Single) {
                item {
                    FilterChip(
                        selected = captureSettings.fovSource == AstapFovSource.AutomaticExif,
                        onClick = {
                            onCaptureSettingsChange(
                                captureSettings.copy(fovSource = AstapFovSource.AutomaticExif),
                            )
                        },
                        label = { Text(stringResource(R.string.label_exif_automatic)) },
                    )
                }
            }
            item {
                FilterChip(
                    selected = captureSettings.fovSource == AstapFovSource.EquipmentProfile,
                    onClick = {
                        onCaptureSettingsChange(
                            captureSettings.copy(fovSource = AstapFovSource.EquipmentProfile),
                        )
                    },
                    label = { Text(stringResource(R.string.label_equipment_profile)) },
                )
            }
            item {
                FilterChip(
                    selected = captureSettings.fovSource == AstapFovSource.Manual,
                    onClick = {
                        onCaptureSettingsChange(
                            captureSettings.copy(fovSource = AstapFovSource.Manual),
                        )
                    },
                    label = { Text(stringResource(R.string.label_manual)) },
                )
            }
        }

        if (captureSettings.fovSource != AstapFovSource.Manual) {
            Text(
                text = stringResource(R.string.label_sensor_size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AstapEquipmentCatalog.cameras, key = { it.id }) { camera ->
                    FilterChip(
                        selected = camera.id == selectedCamera.id,
                        onClick = {
                            onCaptureSettingsChange(
                                captureSettings.copy(cameraProfileId = camera.id),
                            )
                        },
                        label = {
                            Text(stringResource(camera.nameResId))
                        },
                    )
                }
            }

            OutlinedTextField(
                value = focalText,
                onValueChange = { txt ->
                    focalText = txt
                    txt.replace(',', '.').toFloatOrNull()?.let {
                        if (it > 0f) onCaptureSettingsChange(captureSettings.copy(focalLengthMm = it))
                    }
                },
                label = { Text(stringResource(R.string.label_focal_length_mm)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = megapixelText,
                onValueChange = { txt ->
                    megapixelText = txt
                    txt.replace(',', '.').toFloatOrNull()?.let {
                        if (it > 0f) onCaptureSettingsChange(captureSettings.copy(sensorMegapixels = it))
                    }
                },
                label = { Text(stringResource(R.string.label_megapixels)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AstapTileOrientation.entries.forEach { orientation ->
                    item(orientation.name) {
                        FilterChip(
                            selected = captureSettings.tileOrientation == orientation,
                            onClick = {
                                onCaptureSettingsChange(
                                    captureSettings.copy(tileOrientation = orientation),
                                )
                            },
                            label = {
                                Text(
                                    when (orientation) {
                                        AstapTileOrientation.Landscape ->
                                            stringResource(R.string.label_tile_landscape)
                                        AstapTileOrientation.Portrait ->
                                            stringResource(R.string.label_tile_portrait)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (captureSettings.fovSource == AstapFovSource.Manual) {
            SettingSlider(
                label = stringResource(
                    R.string.vertical_fov_label,
                    formatDegrees(captureSettings.manualVerticalFovDegrees),
                ),
                value = captureSettings.manualVerticalFovDegrees,
                valueRange = 0.3f..179f,
                onValueChange = {
                    onCaptureSettingsChange(
                        captureSettings.copy(manualVerticalFovDegrees = it),
                    )
                },
            )
        } else {
            val effectiveField = if (
                captureSettings.captureType == AstapCaptureType.Single &&
                captureSettings.fovSource == AstapFovSource.AutomaticExif &&
                exifFieldOfView != null
            ) {
                exifFieldOfView.fieldOfView
            } else {
                equipmentFieldOfView
            }
            Text(
                text = stringResource(
                    R.string.calculated_fov_uses,
                    formatDegrees(effectiveField.horizontalDegrees),
                    formatDegrees(effectiveField.verticalDegrees),
                    formatDegrees(fieldOfViewDegrees),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = targetObjectQuery,
            onValueChange = onTargetObjectQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.label_target_object_optional)) },
            placeholder = { Text("M31, NGC 7000, Rigel ...") },
            singleLine = true,
            enabled = !isBusy,
            supportingText = {
                val hint = when (targetObjectResolved) {
                    true -> stringResource(R.string.target_object_found_hint)
                    false -> stringResource(R.string.target_object_not_found_hint)
                    null -> stringResource(R.string.target_object_optional_hint)
                }
                Text(hint, style = MaterialTheme.typography.labelSmall)
            },
        )
        } // Ende FOV-/Equipment-Block (nicht bei Fisheye, s.o.)
    }
}

/** Lösen/Download-Button + Fortschritt/Status + Diagnose. Wiederverwendbar (Ausrichten & Kalibrierung). */
@Composable
private fun SolveActionSection(
    operationState: AstapOperationState,
    usesNova: Boolean,
    novaApiKey: String,
    isBusy: Boolean,
    deepSkyAvailable: Boolean,
    onSolve: () -> Unit,
    onCancelSolve: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onSolve,
            enabled = !isBusy && (!usesNova || novaApiKey.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (usesNova) {
                    stringResource(R.string.label_solve_online_astrometry_net)
                } else {
                    stringResource(R.string.action_solve_image_automatically)
                },
            )
        }

        when (operationState) {
            AstapOperationState.Idle -> Unit
            // Unit 6: expliziter Abbruch ist KEIN Fehler -- keine Meldung, UI kehrt genau wie bei Idle
            // sauber in den Vor-Solve-Zustand zurück (der Zustand selbst bleibt intern trotzdem von
            // Idle unterschieden, s. AstapOperationState-Deklaration).
            AstapOperationState.Cancelled -> Unit
            AstapOperationState.Solving -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    stringResource(R.string.detecting_matching_stars),
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onCancelSolve,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_cancel_solve))
                }
            }
            is AstapOperationState.SolvingOnline -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = operationState.statusText,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onCancelSolve,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // SolvingOnline gilt für Nova (online) UND Lokal (offline). „Online" nur bei Nova.
                    Text(
                        if (usesNova) {
                            stringResource(R.string.action_cancel_online_solve)
                        } else {
                            stringResource(R.string.action_cancel_solve)
                        },
                    )
                }
            }
            is AstapOperationState.Solved -> {
                Text(
                    text = stringResource(
                        R.string.solved_constellations_drawn,
                        operationState.constellationCount,
                        stringResource(R.string.panel_annotate),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            is AstapOperationState.Failure -> {
                Text(
                    text = operationState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (deepSkyAvailable && !isBusy) {
            Text(
                text = stringResource(
                    R.string.solution_ready_annotate_hint,
                    stringResource(R.string.panel_annotate),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Solver-Backend (ASTAP/Nova) + API-Schlüssel. Eigenständig für das Einstellungen-Menü. */
@Composable
private fun SolverBackendSection(
    solverChoice: AstapSolverChoice,
    novaApiKey: String,
    isBusy: Boolean,
    onSolverChoiceChange: (AstapSolverChoice) -> Unit,
    onNovaApiKeyChange: (String) -> Unit,
) {
    val context = LocalContext.current
    // Ist der lokale Offline-Solver einsatzbereit (native Lib + gebündelte Indizes, API28+)? Reine
    // Existenzprüfung (isSupported), lädt die native Lib NICHT in den Hauptprozess (Unit 6, s.
    // LocalAstrometryNative-KDoc).
    val localAvailable = remember(context) {
        LocalAstrometryNative.isSupported(context) && AstrometryIndexManager.hasBundledIndexes(context)
    }
    val usesNova = solverChoice == AstapSolverChoice.NovaOnline
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ASTAP entfernt (Nutzerwunsch 2026-08-20, nie genutzt) -- die frühere "Astrometry.net vs.
        // ASTAP"-Ebene-1-Auswahl ist raus, nur noch der Offline/Online-Umschalter für astrometry.net.
        AppSegmentTabs(
            options = listOf(stringResource(R.string.label_offline_local), stringResource(R.string.label_online)),
            selectedIndex = if (usesNova) 1 else 0,
            onSelect = { idx ->
                onSolverChoiceChange(
                    if (idx == 0) AstapSolverChoice.LocalAstrometry else AstapSolverChoice.NovaOnline,
                )
            },
        )
        // Hinweistext nur bei Online (API-Key/Upload); der frühere „Offline…"-Text entfällt.
        if (usesNova) {
            Text(
                text = stringResource(R.string.nova_online_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!localAvailable && !usesNova) {
            Text(
                text = stringResource(R.string.local_solver_unavailable_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (usesNova) {
            OutlinedTextField(
                value = novaApiKey,
                onValueChange = onNovaApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.label_api_key)) },
                singleLine = true,
                enabled = !isBusy,
            )
        }
    }
}

/** Vordergrund-Maskierpinsel. Eigenständig für das Maskieren-Menü. */
@Composable
private fun ForegroundMaskSection(
    maskToolActive: Boolean,
    maskEraseMode: Boolean,
    maskBrushFraction: Float,
    maskBrushHardness: Float,
    maskPresent: Boolean,
    onMaskToolToggle: () -> Unit,
    onMaskEraseModeChange: (Boolean) -> Unit,
    onMaskBrushFractionChange: (Float) -> Unit,
    onMaskBrushHardnessChange: (Float) -> Unit,
    onClearMask: () -> Unit,
) {
    // Der Maskenpinsel ist automatisch aktiv, solange dieser Tab offen ist (kein „Maske malen"-Chip).
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HideWhileSliderDrag {
            Text(
                text = stringResource(R.string.action_mask_foreground),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // „Radierer" schaltet nur zwischen Malen und Radieren (Werkzeug bleibt aktiv).
                FilterChip(
                    selected = maskEraseMode,
                    onClick = { onMaskEraseModeChange(!maskEraseMode) },
                    label = { Text(stringResource(R.string.label_eraser)) },
                )
                if (maskPresent) {
                    TextButton(onClick = onClearMask) {
                        Text(stringResource(R.string.action_clear_mask))
                    }
                }
            }
        }
        SettingSlider(
            label = stringResource(R.string.label_brush_size),
            value = maskBrushFraction,
            valueRange = 0.004f..0.15f,
            onValueChange = onMaskBrushFractionChange,
        )
        SettingSlider(
            label = stringResource(R.string.label_brush_soft_hard),
            value = maskBrushHardness,
            valueRange = 0f..1f,
            onValueChange = onMaskBrushHardnessChange,
        )
    }
}

@Composable
private fun AnnotatePanel(
    hasSolution: Boolean,
    deepSkyObjects: List<DeepSkyObject>,
    selections: AnnotateSelections,
    onApplyConstellations: () -> Unit,
    onApplyDeepSky: (Boolean) -> Unit,
    onApplyStars: (Boolean) -> Unit,
    onConstellationNamesChanged: () -> Unit,
    eraserActive: Boolean = false,
    eraseBrushFraction: Float = 0.06f,
    eraseMaskPresent: Boolean = false,
    onToggleEraser: () -> Unit = {},
    onEraseBrushChange: (Float) -> Unit = {},
    onClearErase: () -> Unit = {},
    // Globale Sternbild-Stil-Regler (Nutzerwunsch 2026-08-20): dieselben Einstellungen wie im
    // Long-Press-Editor eines einzelnen Sternbilds (ConstellationPanel), hier aber IMMER auf ALLE
    // Sternbilder zugleich wirkend -- der Long-Press-Editor bearbeitet dagegen ausschließlich das
    // ausgewählte Sternbild, keine Vermischung mehr über einen Umschalter.
    showConstellationAnchors: Boolean = false,
    constellationColorArgb: Long = 0xFFFFFFFF,
    constellationFont: OverlayFont = OverlayFont.SansSerif,
    constellationLineStyle: OverlayLineStyle = OverlayLineStyle.Solid,
    constellationStrokeWidth: Float = 1f,
    constellationAnchorRadiusRatio: Float = 0.018f,
    constellationOpacity: Float = 1f,
    constellationNameTextSize: Float = 48f,
    onToggleConstellationAnchorsGlobal: () -> Unit = {},
    onConstellationColorChangeGlobal: (Long) -> Unit = {},
    onConstellationFontChangeGlobal: (OverlayFont) -> Unit = {},
    onConstellationLineStyleChangeGlobal: (OverlayLineStyle) -> Unit = {},
    onConstellationStrokeWidthChangeGlobal: (Float) -> Unit = {},
    onConstellationAnchorRadiusChangeGlobal: (Float) -> Unit = {},
    onConstellationOpacityChangeGlobal: (Float) -> Unit = {},
    onConstellationNameTextSizeChangeGlobal: (Float) -> Unit = {},
    layerDrawOrder: List<DrawLayer> = DEFAULT_DRAW_LAYER_ORDER,
    onLayerDrawOrderChange: (List<DrawLayer>) -> Unit = {},
    // Globale Standardfarben (Katalog bearbeiten -> Farben, Nutzerwunsch 2026-08-20): DSO-Typ-Gruppen
    // haben hier -- anders als Sternbilder oben -- KEINEN eigenen Long-Press-Einzelbearbeitungs-Weg
    // (DSOs sind Katalog-Objekte, keine Nutzer-Overlays), deshalb wirken diese 5 IMMER auf alle Objekte
    // der jeweiligen Gruppe. Formen/Formen-Namen/Kometenmarker/Text setzen dagegen nur den DEFAULT für
    // künftig neu platzierte Objekte (analog zu shapeColorArgb selbst), keine rückwirkende Änderung
    // bestehender Overlays -- das entspricht dem heutigen Verhalten außerhalb eines offenen
    // Long-Press-Editors.
    dsoGalaxyColorArgb: Long = 0xFF3FDDF5,
    dsoGlobularColorArgb: Long = 0xFFA6F05A,
    dsoOpenClusterColorArgb: Long = 0xFFFFD28A,
    dsoNebulaColorArgb: Long = 0xFF76FF03,
    dsoOtherColorArgb: Long = 0xFFFFD77E,
    onDsoGalaxyColorChangeGlobal: (Long) -> Unit = {},
    onDsoGlobularColorChangeGlobal: (Long) -> Unit = {},
    onDsoOpenClusterColorChangeGlobal: (Long) -> Unit = {},
    onDsoNebulaColorChangeGlobal: (Long) -> Unit = {},
    onDsoOtherColorChangeGlobal: (Long) -> Unit = {},
    shapeColorArgb: Long = 0xFFFFD28A,
    onShapeColorChangeGlobal: (Long) -> Unit = {},
    shapeNameColorArgb: Long = 0xFFFFFFFF,
    onShapeNameColorChangeGlobal: (Long) -> Unit = {},
    reticleColorArgb: Long = 0xFFFFD28A,
    onReticleColorChangeGlobal: (Long) -> Unit = {},
    textColorArgb: Long = 0xFFFFFFFF,
    onTextColorChangeGlobal: (Long) -> Unit = {},
) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Segmente: nur das aktive zeigt seine Regler -> aufgeräumt. Persistent über Menü-Schließen/Öffnen
        // (Nutzerwunsch: zuletzt bedienten Menüpunkt merken, nicht neu durchklicken).
        var segment by rememberMenuTab("annotate")
        val segments = listOf(
            stringResource(R.string.label_constellations),
            stringResource(R.string.label_deep_sky),
            stringResource(R.string.label_stars),
            stringResource(R.string.label_grid),
            stringResource(R.string.label_milky_way),
            stringResource(R.string.label_image_effects),
            stringResource(R.string.label_layers),
            stringResource(R.string.label_colors),
        )
        HideWhileSliderDrag {
            AppSegmentTabs(options = segments, selectedIndex = segment, onSelect = { segment = it })
        }
        // Bildeffekte wirken aufs Basisbild selbst, unabhängig von einer Astrometrie-Lösung ->
        // als einziges Segment VOR dem hasSolution-Gate (sonst könnte man ein frisches, noch
        // ungelöstes Foto nicht weichzeichnen/invertieren).
        if (segment == 5) {
            ImageEffectsAnnotationSection(selections = selections)
            return@Column
        }
        // Schichten-Reihenfolge ist wie Bildeffekte eine reine Anzeige-Präferenz, keine Lösung nötig.
        if (segment == 6) {
            LayerOrderAnnotationSection(layerDrawOrder = layerDrawOrder, onLayerDrawOrderChange = onLayerDrawOrderChange)
            return@Column
        }
        // Standardfarben sind wie Schichten/Bildeffekte eine reine Anzeige-Präferenz, keine Lösung nötig.
        if (segment == 7) {
            ColorDefaultsAnnotationSection(
                selections = selections,
                constellationColorArgb = constellationColorArgb,
                onConstellationColorChangeGlobal = onConstellationColorChangeGlobal,
                dsoGalaxyColorArgb = dsoGalaxyColorArgb,
                onDsoGalaxyColorChangeGlobal = onDsoGalaxyColorChangeGlobal,
                dsoGlobularColorArgb = dsoGlobularColorArgb,
                onDsoGlobularColorChangeGlobal = onDsoGlobularColorChangeGlobal,
                dsoOpenClusterColorArgb = dsoOpenClusterColorArgb,
                onDsoOpenClusterColorChangeGlobal = onDsoOpenClusterColorChangeGlobal,
                dsoNebulaColorArgb = dsoNebulaColorArgb,
                onDsoNebulaColorChangeGlobal = onDsoNebulaColorChangeGlobal,
                dsoOtherColorArgb = dsoOtherColorArgb,
                onDsoOtherColorChangeGlobal = onDsoOtherColorChangeGlobal,
                shapeColorArgb = shapeColorArgb,
                onShapeColorChangeGlobal = onShapeColorChangeGlobal,
                shapeNameColorArgb = shapeNameColorArgb,
                onShapeNameColorChangeGlobal = onShapeNameColorChangeGlobal,
                reticleColorArgb = reticleColorArgb,
                onReticleColorChangeGlobal = onReticleColorChangeGlobal,
                textColorArgb = textColorArgb,
                onTextColorChangeGlobal = onTextColorChangeGlobal,
            )
            return@Column
        }
        if (!hasSolution) {
            Text(
                text = stringResource(R.string.solve_align_first_hint, stringResource(R.string.panel_align)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        when (segment) {
            0 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HideWhileSliderDrag {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selections.constellationsEnabled,
                        onClick = {
                            selections.constellationsEnabled = !selections.constellationsEnabled
                            onApplyConstellations()
                        },
                        leadingIcon = {
                            Icon(
                                if (selections.constellationsEnabled) Icons.Default.Visibility
                                else Icons.Default.VisibilityOff,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(R.string.action_draw_constellations)) },
                    )
                    FilterChip(
                        selected = selections.showConstellationNames,
                        onClick = {
                            selections.showConstellationNames = !selections.showConstellationNames
                            onConstellationNamesChanged()
                        },
                        label = { Text(stringResource(R.string.label_names)) },
                    )
                    FilterChip(
                        selected = showConstellationAnchors,
                        onClick = onToggleConstellationAnchorsGlobal,
                        leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null) },
                        label = { Text(stringResource(R.string.label_anchors)) },
                    )
                }
                }
                // Dieselben Stil-Regler wie im Long-Press-Editor eines einzelnen Sternbilds
                // (ConstellationPanel), hier aber immer auf ALLE Sternbilder wirkend -- s.
                // AnnotatePanel-Parameterkommentar oben.
                Text(
                    text = stringResource(R.string.label_style_applies_all),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HideWhileSliderDrag {
                    ColorPickerField(selected = constellationColorArgb, onColorChange = onConstellationColorChangeGlobal)
                    FontChoiceRow(selected = constellationFont, onFontChange = onConstellationFontChangeGlobal)
                    ConstellationLineStyleSelector(selected = constellationLineStyle, onSelected = onConstellationLineStyleChangeGlobal)
                }
                SettingSlider(
                    stringResource(R.string.label_line_width),
                    constellationStrokeWidth,
                    0.2f..40f,
                    onConstellationStrokeWidthChangeGlobal,
                )
                SettingSlider(
                    stringResource(R.string.label_anchor_size),
                    constellationAnchorRadiusRatio,
                    0.018f..0.075f,
                    onConstellationAnchorRadiusChangeGlobal,
                )
                SettingSlider(
                    stringResource(R.string.label_name_size),
                    constellationNameTextSize,
                    14f..240f,
                    onConstellationNameTextSizeChangeGlobal,
                )
                SettingSlider(
                    stringResource(R.string.label_opacity),
                    constellationOpacity,
                    0.15f..1f,
                    onConstellationOpacityChangeGlobal,
                )
            }
            1 -> DeepSkyAnnotationSection(
                selections = selections,
                deepSkyObjects = deepSkyObjects,
                onApply = onApplyDeepSky,
            )
            2 -> StarAnnotationSection(selections = selections, onApply = onApplyStars)
            3 -> GridAnnotationSection(selections = selections)
            else -> MilkyWayAnnotationSection(selections = selections)
        }
    }
}

@Composable
private fun DeepSkyAnnotationSection(
    selections: AnnotateSelections,
    deepSkyObjects: List<DeepSkyObject>,
    onApply: (Boolean) -> Unit,
) {
    HideWhileSliderDrag {
    Text(
        text = stringResource(R.string.label_objects_type),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selections.galaxies,
                onClick = { selections.galaxies = !selections.galaxies; onApply(true) },
                label = { Text(stringResource(R.string.label_galaxies)) },
            )
        }
        item {
            FilterChip(
                selected = selections.nebulae,
                onClick = { selections.nebulae = !selections.nebulae; onApply(true) },
                label = { Text(stringResource(R.string.label_nebulae)) },
            )
        }
        item {
            FilterChip(
                selected = selections.clusters,
                onClick = { selections.clusters = !selections.clusters; onApply(true) },
                label = { Text(stringResource(R.string.label_clusters)) },
            )
        }
        item {
            FilterChip(
                selected = selections.others,
                onClick = { selections.others = !selections.others; onApply(true) },
                label = { Text(stringResource(R.string.label_other)) },
            )
        }
    }

    Text(
        text = stringResource(R.string.action_show_catalogues),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(DeepSkyCatalogGroup.entries, key = { it.key }) { group ->
            FilterChip(
                selected = group in selections.catalogs,
                onClick = {
                    if (group in selections.catalogs) selections.catalogs.remove(group)
                    else selections.catalogs.add(group)
                    onApply(true)
                },
                label = { Text(stringResource(group.labelResId)) },
            )
        }
    }
    // Regler-Geltungsbereich: je Regler EIGENER Schalter (Helligkeit/Deckkraft unabhängig
    // voneinander), EIN Wert für alle aktiven Kataloge, oder pro Katalog einzeln.
    if (selections.catalogs.isNotEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_mag_range_all_catalogs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = selections.dsoMagRangeAppliesToAll,
                onCheckedChange = { selections.dsoMagRangeAppliesToAll = it; onApply(true) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.label_opacity_all_catalogs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = selections.dsoOpacityAppliesToAll,
                onCheckedChange = { selections.dsoOpacityAppliesToAll = it; onApply(true) },
            )
        }
        // Welchen Katalog steuern die Pro-Katalog-Regler? (nur relevant, solange MINDESTENS einer
        // der beiden Regler oben NICHT "alle Kataloge" ist -- sonst gäbe es nichts zum Auswählen.)
        if (!selections.dsoMagRangeAppliesToAll || !selections.dsoOpacityAppliesToAll) {
            Text(
                text = stringResource(R.string.label_mag_slider_controls),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selections.catalogs.toList(), key = { it.key }) { group ->
                    FilterChip(
                        selected = group == selections.dsoSelectedCatalog,
                        // Performance-Fix 2026-09-02: wählt nur AUS, welchen Katalog die Regler darunter
                        // steuern -- ändert für sich genommen nichts Sichtbares, braucht daher KEINEN
                        // Resync (vorher unnötig onApply(false), löste die volle Pipeline aus).
                        onClick = { selections.dsoSelectedCatalog = group },
                        label = { Text(stringResource(group.labelResId)) },
                    )
                }
            }
        }
    }
    FilterChip(
        selected = selections.deepSkyShowNames,
        onClick = { selections.deepSkyShowNames = !selections.deepSkyShowNames; onApply(true) },
        label = { Text(stringResource(R.string.label_names)) },
    )
    FontChoiceRow(
        selected = selections.deepSkyFont,
        // Performance-Fix 2026-09-02: reine Zustandsschreibung -- ein reaktiver
        // LaunchedEffect(annotate.deepSkyFont) in StarMapperApp() wendet die Änderung live auf die
        // bestehenden DSO-Overlays an, ohne die teure Pipeline erneut auszulösen (s. dort).
        onFontChange = { selections.deepSkyFont = it },
    )
    TextButton(onClick = { selections.resetDeepSky(); onApply(true) }) {
        Text(stringResource(R.string.action_reset_catalogues))
    }
    }

    // Objekt-Suche: gezielt einzelne Objekte finden und "anpinnen" -- diese ignorieren Kategorie-,
    // Katalog- UND Helligkeits-Regler komplett (s. AstapOverlayMapper.createDeepSkyOverlays
    // pinnedIds), unabhängig davon, ob ihr Katalog überhaupt aktiv ist.
    HideWhileSliderDrag {
    var dsoSearchQuery by remember { mutableStateOf("") }
    Text(
        text = stringResource(R.string.label_dso_search),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = dsoSearchQuery,
        onValueChange = { dsoSearchQuery = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("M31, NGC 7000, ...") },
        singleLine = true,
        trailingIcon = if (dsoSearchQuery.isNotBlank()) {
            {
                IconButton(onClick = { dsoSearchQuery = "" }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_search))
                }
            }
        } else {
            null
        },
    )
    if (dsoSearchQuery.isNotBlank()) {
        val results = remember(dsoSearchQuery, deepSkyObjects) {
            searchDeepSkyObjects(dsoSearchQuery, deepSkyObjects)
        }
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.dso_search_no_results),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                results.forEach { dso ->
                    val pinned = dso.id in selections.pinnedDsoIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (pinned) selections.pinnedDsoIds.remove(dso.id) else selections.pinnedDsoIds.add(dso.id)
                                onApply(true)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (pinned) Icons.Default.Check else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            dso.properDisplayName(AppLocale.resolvedLanguageTag),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
    if (selections.pinnedDsoIds.isNotEmpty()) {
        Text(
            text = stringResource(R.string.label_dso_pinned),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selections.pinnedDsoIds.toList(), key = { it }) { id ->
                InputChip(
                    selected = true,
                    onClick = { selections.pinnedDsoIds.remove(id); onApply(true) },
                    label = {
                        Text(
                            deepSkyObjects.firstOrNull { it.id == id }
                                ?.properDisplayName(AppLocale.resolvedLanguageTag) ?: id,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_remove_pinned_dso),
                            modifier = Modifier.size(16.dp),
                        )
                    },
                )
            }
        }
    }
    }

    // Helligkeits-BEREICH (Unter-/Obergrenze): entweder EIN Bereich für alle Kataloge, oder pro
    // gewähltem Katalog einzeln (s. dsoMagRangeAppliesToAll-Schalter oben). Live: Slider-Ziehen
    // aktualisiert sofort (ohne Undo-Flut); Loslassen = ein Schritt.
    run {
        if (selections.dsoMagRangeAppliesToAll) {
            val magRange = selections.dsoGlobalMagRange
            SettingRangeSlider(
                label = stringResource(
                    R.string.magnitude_range_all,
                    "%.1f".format(magRange.start),
                    "%.1f".format(magRange.endInclusive),
                ),
                value = magRange,
                valueRange = DSO_MAG_SLIDER_MIN..DSO_MAG_SLIDER_MAX,
                // Performance-Fix 2026-09-02: WÄHREND des Ziehens nur den lokalen Wert aktualisieren
                // (Thumb/Label folgen sofort über Compose-Recomposition) -- die tatsächliche Neufilterung
                // (candidateCount ändert sich) läuft erst beim Loslassen, hintergrund-berechnet, s.
                // requestDeepSkySync().
                onValueChange = { selections.dsoGlobalMagRange = it },
                onValueChangeFinished = { onApply(true) },
                steps = 199,
            )
        } else {
            val selectedCatalog = selections.dsoSelectedCatalog
            val catalogLabel = stringResource(selectedCatalog.labelResId)
            val magRange = selections.dsoMagRangeOf(selectedCatalog)
            SettingRangeSlider(
                label = stringResource(
                    R.string.magnitude_range_catalog,
                    catalogLabel,
                    "%.1f".format(magRange.start),
                    "%.1f".format(magRange.endInclusive),
                ),
                value = magRange,
                valueRange = DSO_MAG_SLIDER_MIN..DSO_MAG_SLIDER_MAX,
                onValueChange = { selections.dsoCatalogMagRange[selectedCatalog] = it },
                onValueChangeFinished = { onApply(true) },
                steps = 199,
            )
        }
    }

    // Deckkraft: entweder EIN Wert für alle Kataloge, oder pro gewähltem Katalog einzeln (s.
    // dsoOpacityAppliesToAll-Schalter oben) -- unabhängig vom Helligkeits-Regler-Schalter.
    // Performance-Fix 2026-09-02: reine Zustandsschreibung -- ein reaktiver LaunchedEffect in
    // StarMapperApp() wendet die Änderung live auf die DSO-Overlays an (kein Katalogfilter/Best-Known/
    // WCS/Kollisions-Rerun).
    run {
        if (selections.dsoOpacityAppliesToAll) {
            SettingSlider(
                label = stringResource(
                    R.string.opacity_percent_all,
                    "%.0f".format(selections.dsoGlobalOpacity * 100),
                ),
                value = selections.dsoGlobalOpacity,
                valueRange = 0.1f..1f,
                // Performance-Fix 2026-09-02: reine Zustandsschreibung -- ein reaktiver
                // LaunchedEffect(annotate.dsoGlobalOpacity, ...) in StarMapperApp() wendet die Änderung
                // live auf die bestehenden DSO-Overlays an.
                onValueChange = { selections.dsoGlobalOpacity = it },
            )
        } else {
            val selectedCatalog = selections.dsoSelectedCatalog
            val catalogLabel = stringResource(selectedCatalog.labelResId)
            val opacityValue = selections.dsoOpacityOf(selectedCatalog)
            SettingSlider(
                label = stringResource(
                    R.string.opacity_percent_catalog,
                    catalogLabel,
                    "%.0f".format(opacityValue * 100),
                ),
                value = opacityValue,
                valueRange = 0.1f..1f,
                onValueChange = { selections.dsoCatalogOpacity[selectedCatalog] = it },
            )
        }
    }

    // Best Known V1 (Nutzer-Vorgabe 2026-09-01): reduziert den sichtbaren Bestand auf eine kuratierte
    // Liste (Messier/Caldwell/bekannte Nicht-Katalog-Ziele) und ersetzt nur den Anzeigenamen, sofern
    // eine international/deutsch bekannte Bezeichnung hinterlegt ist. Wirkt unabhängig vom Größenfilter
    // unten (s. AnnotateSelections.bestKnownEnabled-KDoc).
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_best_known),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = selections.bestKnownEnabled,
            onCheckedChange = { selections.bestKnownEnabled = it; onApply(true) },
        )
    }

    // Mindestgröße (Anteil der kürzeren Bildseite), jetzt WCS-basiert auf die ECHTE projizierte
    // Katalog-Winkelgröße geprüft (Best-Known-Vorgabe Abschnitt I-P) statt auf die Render-/Anzeigegröße
    // -- s. AstapOverlayMapper.createDeepSkyOverlays minNaturalSizePercent. Linker Anschlag (0) =
    // "Alle Größen" (Filter AUS); sonst diskrete 0.1-%-Schritte, echte Quantisierung s.
    // quantizeDsoMinSizePercent (nicht nur die Anzeige gerundet).
    val dsoSizeValueLabel = if (selections.dsoMinSizePercent <= 0f) {
        stringResource(R.string.label_dso_min_size_all)
    } else {
        "%.1f %%".format(selections.dsoMinSizePercent)
    }
    // Performance-Fix 2026-09-02: WÄHREND des Ziehens nur den (quantisierten) lokalen Wert aktualisieren
    // -- Thumb/Prozent-Label folgen dadurch sofort/flüssig. Die eigentliche Neufilterung (die sichtbare
    // Objektmenge ändert sich) läuft erst beim Loslassen, hintergrund-berechnet, s. requestDeepSkySync().
    SettingSlider(
        label = stringResource(R.string.label_dso_min_size_in_image, dsoSizeValueLabel),
        value = selections.dsoMinSizePercent,
        valueRange = 0f..DSO_MIN_SIZE_PERCENT_MAX,
        steps = 29,
        onValueChange = { selections.dsoMinSizePercent = quantizeDsoMinSizePercent(it) },
        onValueChangeFinished = { onApply(true) },
    )

    // Schriftgröße der Objektnamen (Callout-Beschriftung). Performance-Fix 2026-09-02: reine
    // Zustandsschreibung -- ein reaktiver LaunchedEffect(annotate.deepSkyNameSize) in StarMapperApp()
    // wendet die Änderung live auf die bestehenden DSO-Overlays an, kein Pipeline-Rerun.
    SettingSlider(
        label = stringResource(R.string.name_size_label, selections.deepSkyNameSize.roundToInt()),
        value = selections.deepSkyNameSize,
        valueRange = 14f..90f,
        onValueChange = { selections.deepSkyNameSize = it },
    )

    // Punkt 5 (Nutzer-Vorgabe 2026-08-30): direkte Liniendicke-Steuerung für DSO-Konturen, per Schalter
    // optional -- solange deaktiviert, bleibt das bisherige feste Verhalten (Default 2.5) unverändert.
    // Performance-Fix 2026-09-02: sowohl Schalter als auch Regler sind reine Zustandsschreibungen -- ein
    // reaktiver LaunchedEffect in StarMapperApp() wendet die Änderung live auf die DSO-Overlays an.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_deepsky_stroke_width_enabled),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = selections.deepSkyStrokeWidthEnabled,
            onCheckedChange = { selections.deepSkyStrokeWidthEnabled = it },
        )
    }
    if (selections.deepSkyStrokeWidthEnabled) {
        SettingSlider(
            label = stringResource(R.string.label_line_width),
            value = selections.deepSkyStrokeWidth,
            valueRange = 0.2f..40f,
            onValueChange = { selections.deepSkyStrokeWidth = it },
        )
    }

    // Punkt 6 (Nutzer-Vorgabe 2026-08-30): Beschriftungs-Deckkraft optional an die Objekt-Deckkraft
    // koppeln (bisher für DSOs immer fest 1.0, unabhängig vom Deckkraft-Regler des Objekts selbst).
    // Performance-Fix 2026-09-02: direkte 1:1-Feldzuweisung -- sofort/live, kein Pipeline-Rerun.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.label_deepsky_name_opacity_linked),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = selections.deepSkyNameOpacityLinked,
            onCheckedChange = { selections.deepSkyNameOpacityLinked = it },
        )
    }
}

@Composable
private fun StarAnnotationSection(
    selections: AnnotateSelections,
    onApply: (Boolean) -> Unit,
) {
    val tycho2Context = LocalContext.current
    val tycho2Completed = Tycho2DownloadManager.completedVersion.intValue
    // Datei-Existenz-Check (billig) -- neu ausgewertet, sobald ein Download/Löschen abgeschlossen ist.
    val tycho2Installed = remember(tycho2Completed) { Tycho2Store.isInstalled(tycho2Context) }
    HideWhileSliderDrag {
    Text(
        text = stringResource(R.string.label_stars),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selections.starNamed,
                onClick = { selections.starNamed = !selections.starNamed; onApply(true) },
                label = { Text(stringResource(R.string.label_fixed_stars)) },
            )
        }
        item {
            FilterChip(
                selected = selections.starConstellation,
                onClick = { selections.starConstellation = !selections.starConstellation; onApply(true) },
                label = { Text(stringResource(R.string.label_constellation_stars)) },
            )
        }
        item {
            FilterChip(
                selected = selections.starAll,
                onClick = { selections.starAll = !selections.starAll; onApply(true) },
                label = { Text(stringResource(R.string.label_all_to_mag)) },
            )
        }
    }
    }
    if (selections.starAll) {
        SettingSlider(
            label = stringResource(
                R.string.star_magnitude_limit_label,
                "%.1f".format(selections.starMagnitude),
            ),
            value = selections.starMagnitude,
            valueRange = if (tycho2Installed) 2f..14f else 2f..8f,
            onValueChange = { selections.starMagnitude = it; onApply(false) },
            onValueChangeFinished = { onApply(true) },
        )
        Tycho2CatalogRow(tycho2Installed)
    }
    // Deckkraft der Sternnamen/-punkte. Beim Ziehen nur den Wert ändern, erst beim Loslassen die
    // Sterne neu generieren -> bleibt performant (keine Regeneration pro Tick).
    SettingSlider(
        label = stringResource(
            R.string.name_opacity_percent,
            "%.0f".format(selections.starNameOpacity * 100),
        ),
        value = selections.starNameOpacity,
        valueRange = 0.1f..1f,
        onValueChange = { selections.starNameOpacity = it },
        onValueChangeFinished = { onApply(true) },
    )
    // Globale Stern-Beschriftung (0.11.0): Punkte-Toggle, Größe, Farbe, Schriftart. Größe nur bei
    // Slider-Release regenerieren (wie Deckkraft); Punkte/Farbe/Schriftart sofort übernehmen.
    HideWhileSliderDrag {
        FilterChip(
            selected = selections.starShowDots,
            onClick = { selections.starShowDots = !selections.starShowDots; onApply(true) },
            leadingIcon = {
                Icon(
                    if (selections.starShowDots) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.label_star_dots)) },
        )
    }
    SettingSlider(
        label = stringResource(R.string.star_name_size_label, selections.starNameSize.roundToInt()),
        value = selections.starNameSize,
        valueRange = 14f..60f,
        onValueChange = { selections.starNameSize = it },
        onValueChangeFinished = { onApply(true) },
    )
    HideWhileSliderDrag {
        Text(
            text = stringResource(R.string.label_name_colour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorPickerField(
            selected = selections.starNameColorArgb,
            onColorChange = { selections.starNameColorArgb = it; onApply(true) },
        )
        Text(
            text = stringResource(R.string.label_font),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FontChoiceRow(
            selected = selections.starFont,
            onFontChange = { selections.starFont = it; onApply(true) },
        )
    }
}

/** Kompakte Zeile für den optionalen Tycho-2-Tiefkatalog-Download (s. Tycho2Store/-Downloader/
 *  -Converter/-DownloadManager) -- erweitert den Sternregler oben von Mag. 8 auf Mag. 14. Zwei
 *  Fortschrittsphasen wie beim Manager (Download, dann Verarbeitung), Stil 1:1 von IndexPackageRow
 *  übernommen. */
@Composable
private fun Tycho2CatalogRow(installed: Boolean) {
    val context = LocalContext.current
    val downloadProgress = Tycho2DownloadManager.downloadProgress.value
    val convertProgress = Tycho2DownloadManager.convertProgress.value
    val downloadError = Tycho2DownloadManager.downloadError.value
    val convertError = Tycho2DownloadManager.convertError.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.label_tycho2_catalog), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.tycho2_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            downloadProgress != null -> {
                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = { Tycho2DownloadManager.cancel() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.cancel_with_percent, (downloadProgress * 100).roundToInt())) }
            }
            convertProgress != null -> {
                Text(
                    stringResource(R.string.tycho2_processing_label, (convertProgress * 100).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
                LinearProgressIndicator(progress = { convertProgress }, modifier = Modifier.fillMaxWidth())
            }
            installed -> {
                Text(
                    stringResource(R.string.label_installed_offline_usable),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = { Tycho2DownloadManager.remove(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_delete)) }
            }
            else -> {
                Button(
                    onClick = { Tycho2DownloadManager.start(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_download)) }
            }
        }
        (downloadError ?: convertError)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

// Koordinatennetz (0.10.5): RA/Dec-Gradnetz nach dem Lösen. Reines State-Toggling — der Editor liest
// den State reaktiv und zeichnet das Netz als eigenen Pass (kein Overlay, keine Regeneration).
@Composable
private fun GridAnnotationSection(selections: AnnotateSelections) {
    HideWhileSliderDrag {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selections.gridEnabled,
                onClick = { selections.gridEnabled = !selections.gridEnabled },
                leadingIcon = {
                    Icon(
                        if (selections.gridEnabled) Icons.Default.Visibility
                        else Icons.Default.VisibilityOff,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(R.string.action_draw_grid)) },
            )
            FilterChip(
                selected = selections.gridShowLabels,
                onClick = { selections.gridShowLabels = !selections.gridShowLabels },
                label = { Text(stringResource(R.string.label_grid_numbers)) },
            )
        }
    }
    SettingSlider(
        label = stringResource(R.string.label_line_thickness),
        value = selections.gridThickness,
        valueRange = 0.0004f..0.004f,
        onValueChange = { selections.gridThickness = it },
    )
    SettingSlider(
        label = stringResource(R.string.label_density_coarse_fine),
        value = selections.gridDensity,
        valueRange = 0.3f..3f,
        onValueChange = { selections.gridDensity = it },
    )
    SettingSlider(
        label = stringResource(R.string.grid_opacity_percent, "%.0f".format(selections.gridOpacity * 100)),
        value = selections.gridOpacity,
        valueRange = 0.1f..1f,
        onValueChange = { selections.gridOpacity = it },
    )
    HideWhileSliderDrag {
        Text(
            text = stringResource(R.string.label_colour),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorPickerField(
            selected = selections.gridColorArgb,
            onColorChange = { selections.gridColorArgb = it },
        )
    }
}

@Composable
private fun MilkyWayAnnotationSection(selections: AnnotateSelections) {
    HideWhileSliderDrag {
        FilterChip(
            selected = selections.milkyWayEnabled,
            onClick = { selections.milkyWayEnabled = !selections.milkyWayEnabled },
            leadingIcon = {
                Icon(
                    if (selections.milkyWayEnabled) Icons.Default.Visibility
                    else Icons.Default.VisibilityOff,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(R.string.action_draw_milky_way)) },
        )
    }
    SettingSlider(
        label = stringResource(R.string.grid_opacity_percent, "%.0f".format(selections.milkyWayOpacity * 100)),
        value = selections.milkyWayOpacity,
        valueRange = 0.1f..1f,
        onValueChange = { selections.milkyWayOpacity = it },
    )
}

@Composable
private fun DrawLayer.displayName(): String = when (this) {
    DrawLayer.Constellation -> stringResource(R.string.label_constellations)
    DrawLayer.Objects -> stringResource(R.string.label_objects)
    DrawLayer.Star -> stringResource(R.string.label_stars)
    DrawLayer.MilkyWay -> stringResource(R.string.label_milky_way)
    DrawLayer.Graticule -> stringResource(R.string.label_grid)
}

/** Weist [layer] Platz [position] zu (0 = unterste/zuerst gezeichnet). War [layer] bereits auf einem
 *  ANDEREN Platz, tauschen beide Plätze ihren Inhalt -- die Liste bleibt dadurch immer eine gültige
 *  Permutation aller [DrawLayer]-Werte (kein Duplikat, keine Lücke möglich). */
private fun List<DrawLayer>.withLayerAtPosition(position: Int, layer: DrawLayer): List<DrawLayer> {
    if (this[position] == layer) return this
    val result = toMutableList()
    val oldPosition = indexOf(layer)
    result[oldPosition] = result[position]
    result[position] = layer
    return result
}

// Schichten-Reihenfolge (Nutzerwunsch 2026-08-20): 5 nummerierte Plätze, je eine Typ-Auswahl statt
// Drag-and-Drop oder Auf/Ab-Pfeilen (kein Präzedenzfall in der App für Reorder-UI) -- der Nutzer legt
// für jeden Platz explizit fest, welche Schicht dort einsortiert ist.
@Composable
private fun LayerOrderAnnotationSection(
    layerDrawOrder: List<DrawLayer>,
    onLayerDrawOrderChange: (List<DrawLayer>) -> Unit,
) {
    Text(
        text = stringResource(R.string.label_layer_order_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        layerDrawOrder.forEachIndexed { position, currentLayer ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val suffix = when (position) {
                    0 -> " " + stringResource(R.string.label_layer_slot_bottom)
                    layerDrawOrder.lastIndex -> " " + stringResource(R.string.label_layer_slot_top)
                    else -> ""
                }
                Text(
                    text = stringResource(R.string.label_layer_slot, position + 1) + suffix,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DrawLayer.entries, key = { it.name }) { layer ->
                        FilterChip(
                            selected = currentLayer == layer,
                            onClick = { onLayerDrawOrderChange(layerDrawOrder.withLayerAtPosition(position, layer)) },
                            label = { Text(layer.displayName()) },
                        )
                    }
                }
            }
        }
    }
}

// Globale Standardfarben (Katalog bearbeiten -> Farben, Nutzerwunsch 2026-08-20): 12 explizite Zeilen
// statt eines datengetriebenen Loops -- die Kategorien haben technisch heterogene State-/Callback-
// Typen (5 DSO-Gruppen wirken auf alle bestehenden Objekte, die übrigen nur als Default fürs
// Neu-Platzieren), genau wie Segment 0 (Sternbilder) seine Regler auch explizit auflistet.
@Composable
private fun ColorDefaultsAnnotationSection(
    selections: AnnotateSelections,
    constellationColorArgb: Long,
    onConstellationColorChangeGlobal: (Long) -> Unit,
    dsoGalaxyColorArgb: Long,
    onDsoGalaxyColorChangeGlobal: (Long) -> Unit,
    dsoGlobularColorArgb: Long,
    onDsoGlobularColorChangeGlobal: (Long) -> Unit,
    dsoOpenClusterColorArgb: Long,
    onDsoOpenClusterColorChangeGlobal: (Long) -> Unit,
    dsoNebulaColorArgb: Long,
    onDsoNebulaColorChangeGlobal: (Long) -> Unit,
    dsoOtherColorArgb: Long,
    onDsoOtherColorChangeGlobal: (Long) -> Unit,
    shapeColorArgb: Long,
    onShapeColorChangeGlobal: (Long) -> Unit,
    shapeNameColorArgb: Long,
    onShapeNameColorChangeGlobal: (Long) -> Unit,
    reticleColorArgb: Long,
    onReticleColorChangeGlobal: (Long) -> Unit,
    textColorArgb: Long,
    onTextColorChangeGlobal: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorDefaultRow(stringResource(R.string.label_galaxies), dsoGalaxyColorArgb, onDsoGalaxyColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_globular_clusters), dsoGlobularColorArgb, onDsoGlobularColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_open_clusters), dsoOpenClusterColorArgb, onDsoOpenClusterColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_nebulae), dsoNebulaColorArgb, onDsoNebulaColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_other_dsos), dsoOtherColorArgb, onDsoOtherColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_constellations), constellationColorArgb, onConstellationColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_stars), selections.starNameColorArgb) { selections.starNameColorArgb = it }
        ColorDefaultRow(stringResource(R.string.label_grid), selections.gridColorArgb) { selections.gridColorArgb = it }
        ColorDefaultRow(stringResource(R.string.panel_shapes), shapeColorArgb, onShapeColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_name_colour), shapeNameColorArgb, onShapeNameColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_comet_marker), reticleColorArgb, onReticleColorChangeGlobal)
        ColorDefaultRow(stringResource(R.string.label_text), textColorArgb, onTextColorChangeGlobal)
    }
}

@Composable
private fun ColorDefaultRow(label: String, colorArgb: Long, onColorChange: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        ColorPickerField(selected = colorArgb, onColorChange = onColorChange)
    }
}

@Composable
private fun ImageEffectsAnnotationSection(selections: AnnotateSelections) {
    HideWhileSliderDrag {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selections.imageGrayscale,
                onClick = { selections.imageGrayscale = !selections.imageGrayscale },
                label = { Text(stringResource(R.string.label_black_white)) },
            )
            FilterChip(
                selected = selections.imageInverted,
                onClick = { selections.imageInverted = !selections.imageInverted },
                label = { Text(stringResource(R.string.action_invert)) },
            )
        }
    }
    SettingSlider(
        label = stringResource(
            R.string.blur_intensity_percent,
            "%.0f".format(selections.imageBlurIntensity * 100),
        ),
        value = selections.imageBlurIntensity,
        valueRange = 0f..1f,
        onValueChange = { selections.imageBlurIntensity = it },
    )
}

@Composable
private fun ConstellationPanel(
    selectedConstellation: ConstellationPattern,
    showConstellationAnchors: Boolean,
    colorArgb: Long,
    font: OverlayFont,
    lineStyle: OverlayLineStyle,
    strokeWidth: Float,
    anchorRadiusRatio: Float,
    opacity: Float,
    showName: Boolean,
    nameTextSize: Float,
    onOpenSkyPicker: () -> Unit,
    onAddConstellation: () -> Unit,
    onStartFisheyeAlign: () -> Unit,
    onRevertAstrometry: () -> Unit,
    fisheyeFovLongDeg: Float,
    onFisheyeFovChange: (Float) -> Unit,
    onToggleConstellationAnchors: () -> Unit,
    onColorChange: (Long) -> Unit,
    onFontChange: (OverlayFont) -> Unit,
    onLineStyleChange: (OverlayLineStyle) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onAnchorRadiusChange: (Float) -> Unit,
    onShowNameChange: (Boolean) -> Unit,
    onNameTextSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HideWhileSliderDrag {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = showConstellationAnchors,
                    onClick = onToggleConstellationAnchors,
                    leadingIcon = { Icon(Icons.Default.RadioButtonUnchecked, contentDescription = null) },
                    label = { Text(stringResource(R.string.label_anchors)) },
                )
            }
            item {
                FilterChip(
                    selected = showName,
                    onClick = { onShowNameChange(!showName) },
                    label = { Text(stringResource(R.string.label_names)) },
                )
            }
        }
        // "Alle"-Umschalter entfernt (Nutzerwunsch 2026-08-20): dieser Long-Press-Editor bearbeitet
        // IMMER nur das ausgewählte Sternbild. Globale Änderungen an ALLEN Sternbildern laufen jetzt
        // ausschließlich über das Katalog-bearbeiten-Menü -> klare Trennung statt eines Umschalters,
        // der hier leicht übersehen werden konnte.
        Text(
            text = stringResource(R.string.label_style_applies_selected),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorPickerField(selected = colorArgb, onColorChange = onColorChange)
        FontChoiceRow(selected = font, onFontChange = onFontChange)
        ConstellationLineStyleSelector(selected = lineStyle, onSelected = onLineStyleChange)
        }
        SettingSlider(stringResource(R.string.label_line_width), strokeWidth, 0.2f..40f, onStrokeWidthChange)
        SettingSlider(stringResource(R.string.label_anchor_size), anchorRadiusRatio, 0.018f..0.075f, onAnchorRadiusChange)
        SettingSlider(stringResource(R.string.label_name_size), nameTextSize, 14f..240f, onNameTextSizeChange)
        SettingSlider(stringResource(R.string.label_opacity), opacity, 0.15f..1f, onOpacityChange)
    }
}

@Composable
private fun ShapesPanel(
    selectedTool: EditorTool,
    colorArgb: Long,
    font: OverlayFont,
    strokeWidth: Float,
    lineStyle: OverlayLineStyle,
    opacity: Float,
    filled: Boolean,
    isReticle: Boolean,
    showName: Boolean,
    nameText: String,
    nameTextSize: Float,
    nameColorArgb: Long,
    nameBold: Boolean,
    onToolSelected: (EditorTool) -> Unit,
    onColorChange: (Long) -> Unit,
    onFontChange: (OverlayFont) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onLineStyleChange: (OverlayLineStyle) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onFilledChange: (Boolean) -> Unit,
    onShowNameChange: (Boolean) -> Unit,
    onEditName: () -> Unit,
    onNameTextSizeChange: (Float) -> Unit,
    onNameColorChange: (Long) -> Unit,
    onNameBoldChange: (Boolean) -> Unit,
) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HideWhileSliderDrag {
            ColorPickerField(selected = colorArgb, onColorChange = onColorChange)
            // Fadenkreuz/Komet-Marker brauchen weder Linienstil (Pinsel etc.) noch Füllung noch einen
            // Namens-Schwänzchen -- alles zusammen im bestehenden isReticle-Block ausgeblendet.
            if (!isReticle) {
                LineStyleSelector(selected = lineStyle, onSelected = onLineStyleChange)
                FilterChip(
                    selected = filled,
                    onClick = { onFilledChange(!filled) },
                    label = { Text(stringResource(R.string.label_fill)) },
                )
                FilterChip(
                    selected = showName,
                    onClick = {
                        val turningOn = !showName
                        onShowNameChange(turningOn)
                        // Erstes Aktivieren ohne vorhandenen Text: sofort Texteingabe öffnen, statt
                        // einen leeren Namen anzuzeigen und den Bearbeiten-Button suchen zu lassen.
                        if (turningOn && nameText.isBlank()) onEditName()
                    },
                    label = { Text(stringResource(R.string.action_show_name)) },
                )
                if (showName) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEditName) {
                            Text(stringResource(R.string.action_edit_text))
                        }
                        FilterChip(
                            selected = nameBold,
                            onClick = { onNameBoldChange(!nameBold) },
                            label = { Text(if (nameBold) stringResource(R.string.label_bold) else stringResource(R.string.label_thin)) },
                        )
                    }
                    ColorPickerField(selected = nameColorArgb, onColorChange = onNameColorChange)
                    FontChoiceRow(selected = font, onFontChange = onFontChange)
                }
            }
        }
        SettingSlider(stringResource(R.string.label_line_width), strokeWidth, 0.2f..40f, onStrokeWidthChange)
        SettingSlider(stringResource(R.string.label_opacity), opacity, 0.15f..1f, onOpacityChange)
        if (!isReticle && showName) {
            SettingSlider(stringResource(R.string.label_name_size), nameTextSize, 14f..240f, onNameTextSizeChange)
        }
    }
}

@Composable
private fun TextPanel(
    selectedTool: EditorTool,
    colorArgb: Long,
    font: OverlayFont,
    textSize: Float,
    textBold: Boolean,
    opacity: Float,
    onToolSelected: (EditorTool) -> Unit,
    onEditSelectedText: () -> Unit,
    onColorChange: (Long) -> Unit,
    onFontChange: (OverlayFont) -> Unit,
    onTextSizeChange: (Float) -> Unit,
    onTextBoldChange: (Boolean) -> Unit,
    onOpacityChange: (Float) -> Unit,
) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HideWhileSliderDrag {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = textBold,
                        onClick = { onTextBoldChange(!textBold) },
                        label = { Text(if (textBold) stringResource(R.string.label_bold) else stringResource(R.string.label_thin_alt)) },
                    )
                }
            }
            ColorPickerField(selected = colorArgb, onColorChange = onColorChange)
            FontChoiceRow(selected = font, onFontChange = onFontChange)
            Text(
                stringResource(R.string.text_resize_hint),
            )
        }
        SettingSlider(stringResource(R.string.label_opacity), opacity, 0.15f..1f, onOpacityChange)
    }
}

// Schriftart-Auswahl (0.11.0): Sans/Serif/Mono — für Text-Overlays und Sternnamen.
@Composable
private fun FontChoiceRow(selected: OverlayFont, onFontChange: (OverlayFont) -> Unit) {
    val fonts = listOf(
        OverlayFont.SansSerif to stringResource(R.string.font_sans),
        OverlayFont.Serif to stringResource(R.string.font_serif),
        OverlayFont.SerifMonospace to stringResource(R.string.font_serif_mono),
        OverlayFont.Cursive to stringResource(R.string.font_cursive),
        OverlayFont.Casual to stringResource(R.string.font_casual),
        OverlayFont.LeagueScript to stringResource(R.string.font_league_script),
        OverlayFont.Galada to stringResource(R.string.font_galada),
        OverlayFont.Smooch to stringResource(R.string.font_smooch),
        OverlayFont.Estonia to stringResource(R.string.font_estonia),
        OverlayFont.Waterfall to stringResource(R.string.font_waterfall),
        OverlayFont.Whisper to stringResource(R.string.font_whisper),
        OverlayFont.Cherish to stringResource(R.string.font_cherish),
        OverlayFont.TwinkleStar to stringResource(R.string.font_twinkle_star),
        OverlayFont.KolkerBrush to stringResource(R.string.font_kolker_brush),
        OverlayFont.WaterBrush to stringResource(R.string.font_water_brush),
        OverlayFont.Splash to stringResource(R.string.font_splash),
        OverlayFont.Freehand to stringResource(R.string.font_freehand),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(fonts, key = { it.first.name }) { (font, label) ->
            FilterChip(
                selected = selected == font,
                onClick = { onFontChange(font) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LineStyleSelector(selected: OverlayLineStyle, onSelected: (OverlayLineStyle) -> Unit) {
    val labels = listOf(
        OverlayLineStyle.Solid to stringResource(R.string.line_style_solid),
        OverlayLineStyle.Dashed to stringResource(R.string.line_style_dashed),
        OverlayLineStyle.Dotted to stringResource(R.string.line_style_dotted),
        OverlayLineStyle.HandDrawn to stringResource(R.string.line_style_hand),
        OverlayLineStyle.Brush to stringResource(R.string.line_style_brush),
        OverlayLineStyle.Marker to stringResource(R.string.line_style_marker),
        OverlayLineStyle.Chalk to stringResource(R.string.line_style_chalk),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels, key = { it.first.name }) { (style, label) ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelected(style) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ConstellationLineStyleSelector(selected: OverlayLineStyle, onSelected: (OverlayLineStyle) -> Unit) {
    val labels = listOf(
        OverlayLineStyle.Solid to stringResource(R.string.line_style_solid),
        OverlayLineStyle.Brush to stringResource(R.string.line_style_brush),
        OverlayLineStyle.Marker to stringResource(R.string.line_style_marker),
        OverlayLineStyle.Chalk to stringResource(R.string.line_style_chalk),
        OverlayLineStyle.HandDrawn to stringResource(R.string.line_style_hand),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels, key = { it.first.name }) { (style, label) ->
            FilterChip(
                selected = selected == style,
                onClick = { onSelected(style) },
                label = { Text(label) },
            )
        }
    }
}

// Meldet „Slider wird gezogen" an die Editor-Popup-Hülle (blendet das Menü aus, zeigt das Bild).
// Default = No-Op -> Slider außerhalb der Editor-Popups (Sky-Picker/Referenz) lösen NICHTS aus.
private val LocalSliderDrag = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

// True, während IRGENDEIN Editor-Popup-Slider gezogen wird -> Nicht-Slider-Inhalte ausblenden.
private val LocalSliderDragging = staticCompositionLocalOf { false }

// Blendet seinen Inhalt aus, solange ein Slider gezogen wird (nur der aktive Slider bleibt sichtbar).
// Inhalt wird in den umgebenden Column emittiert (keine ColumnScope-Modifier im Inhalt nötig).
@Composable
private fun HideWhileSliderDrag(content: @Composable () -> Unit) {
    // Beim Slider-Ziehen NICHT aus dem Layout entfernen (sonst kollabiert das Popup und der aktive
    // Slider springt an eine andere Stelle) -> nur unsichtbar schalten, der Platz bleibt erhalten.
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.alpha(if (LocalSliderDragging.current) 0f else 1f),
    ) { content() }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    // 0 (Default) = bisheriges kontinuierliches Verhalten, unverändert für alle bestehenden Aufrufer.
    // > 0 = Anzahl der Zwischenschritte (Compose-Slider-Semantik) -- rein visuelle Rastung/Tickmarken;
    // die tatsächliche Quantisierung übernimmt weiterhin der jeweilige onValueChange-Aufrufer selbst
    // (s. DSO-Mindestgrößen-Regler), damit der gespeicherte Wert unabhängig von der Slider-Bibliothek
    // garantiert exakt gerastert ist.
    steps: Int = 0,
) {
    val sliderDrag = LocalSliderDrag.current
    // Wird ein Slider gezogen, bleibt NUR der aktive sichtbar; inaktive blenden sich aus
    // (der aktive bleibt gemountet -> Geste läuft weiter).
    var thisDragging by remember { mutableStateOf(false) }
    // Inaktive Slider beim Ziehen NICHT entfernen (sonst verrutscht der aktive) -> nur unsichtbar
    // schalten, der Platz bleibt erhalten, damit der aktive Slider an Ort und Stelle bleibt.
    val hiddenInactive = LocalSliderDragging.current && !thisDragging
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        // Aktiver Slider schwebt beim Ziehen über dem Bild -> dezenter Hintergrund für Lesbarkeit.
        modifier = (if (thisDragging) {
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        } else {
            Modifier
        }).alpha(if (hiddenInactive) 0f else 1f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                thisDragging = true
                sliderDrag(true)
                onValueChange(it)
            },
            onValueChangeFinished = {
                thisDragging = false
                sliderDrag(false)
                onValueChangeFinished()
            },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

/** Mechanisches Pendant zu [SettingSlider] (identisches Drag-Hide-Chrome), aber mit zwei Griffen
 *  (Unter-/Obergrenze) für einen Helligkeitsbereich statt eines einzelnen Deckels. */
@Composable
private fun SettingRangeSlider(
    label: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    steps: Int = 0,
) {
    val sliderDrag = LocalSliderDrag.current
    var thisDragging by remember { mutableStateOf(false) }
    val hiddenInactive = LocalSliderDragging.current && !thisDragging
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = (if (thisDragging) {
            Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        } else {
            Modifier
        }).alpha(if (hiddenInactive) 0f else 1f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RangeSlider(
            value = value.start.coerceIn(valueRange.start, valueRange.endInclusive)..
                value.endInclusive.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                thisDragging = true
                sliderDrag(true)
                onValueChange(it)
            },
            onValueChangeFinished = {
                thisDragging = false
                sliderDrag(false)
                onValueChangeFinished()
            },
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun SkyPickerDialog(
    selected: ConstellationPattern,
    mirrorX: Boolean,
    mirrorY: Boolean,
    catalog: List<ConstellationPattern>,
    milkyWayLayers: List<MilkyWayLayer>,
    skyStars: List<CatalogStar>,
    deepSkyObjects: List<DeepSkyObject>,
    d3Settings: D3CatalogSettings,
    onDismiss: () -> Unit,
    onPreviewSelect: (ConstellationPattern) -> Unit,
    onToggleMirrorX: () -> Unit,
    onToggleMirrorY: () -> Unit,
    onD3SettingsChange: (D3CatalogSettings) -> Unit,
    onPlace: (ConstellationPattern) -> Unit,
) {
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp - 28.dp).coerceAtLeast(420.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxDialogHeight)
                .padding(14.dp),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 6.dp,
        ) {
            SkyPickerSheet(
                selected = selected,
                mirrorX = mirrorX,
                mirrorY = mirrorY,
                catalog = catalog,
                milkyWayLayers = milkyWayLayers,
                skyStars = skyStars,
                deepSkyObjects = deepSkyObjects,
                d3Settings = d3Settings,
                onPreviewSelect = onPreviewSelect,
                onToggleMirrorX = onToggleMirrorX,
                onToggleMirrorY = onToggleMirrorY,
                onD3SettingsChange = onD3SettingsChange,
                onPlace = onPlace,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun SkyPickerSheet(
    selected: ConstellationPattern,
    mirrorX: Boolean,
    mirrorY: Boolean,
    catalog: List<ConstellationPattern>,
    milkyWayLayers: List<MilkyWayLayer>,
    skyStars: List<CatalogStar>,
    deepSkyObjects: List<DeepSkyObject>,
    d3Settings: D3CatalogSettings,
    onPreviewSelect: (ConstellationPattern) -> Unit,
    onToggleMirrorX: () -> Unit,
    onToggleMirrorY: () -> Unit,
    onD3SettingsChange: (D3CatalogSettings) -> Unit,
    onPlace: (ConstellationPattern) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val letters = remember(catalog) {
        catalog.mapNotNull { it.localizedName().firstOrNull()?.uppercaseChar() }.distinct().sorted()
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.label_virtual_sky),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = mirrorX,
                    onClick = onToggleMirrorX,
                    label = { Text(stringResource(R.string.action_mirror_x)) },
                )
            }
            item {
                FilterChip(
                    selected = mirrorY,
                    onClick = onToggleMirrorY,
                    label = { Text(stringResource(R.string.action_mirror_y)) },
                )
            }
        }
        SkySphere(
            selected = selected,
            mirrorX = mirrorX,
            mirrorY = mirrorY,
            catalog = catalog,
            milkyWayLayers = milkyWayLayers,
            skyStars = skyStars,
            deepSkyObjects = deepSkyObjects,
            d3Settings = d3Settings,
            onPreviewSelect = onPreviewSelect,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(letters, key = { it }) { letter ->
                    AssistChip(
                        onClick = {
                            val index = catalog.indexOfFirst { it.localizedName().startsWith(letter.toString(), ignoreCase = true) }
                            if (index >= 0) {
                                onPreviewSelect(catalog[index])
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        label = { Text(letter.toString()) },
                    )
                }
            }
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(catalog, key = { it.id }) { pattern ->
                    ConstellationCard(
                        pattern = pattern,
                        selected = pattern.id == selected.id,
                        onClick = { onPreviewSelect(pattern) },
                    )
                }
            }
            Button(
                onClick = { onPlace(selected) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.place_object_button, selected.localizedName()))
            }
        }
    }
}

@Composable
private fun D3CatalogSettingsPanel(
    settings: D3CatalogSettings,
    starCount: Int,
    deepSkyCount: Int,
    onSettingsChange: (D3CatalogSettings) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = settings.showStars,
                        onClick = { onSettingsChange(settings.copy(showStars = !settings.showStars)) },
                        label = { Text(stringResource(R.string.label_stars)) },
                    )
                }
                item {
                    FilterChip(
                        selected = settings.showDeepSkyObjects,
                        onClick = { onSettingsChange(settings.copy(showDeepSkyObjects = !settings.showDeepSkyObjects)) },
                        label = { Text("DSO") },
                    )
                }
                item {
                    FilterChip(
                        selected = settings.showMilkyWay,
                        onClick = { onSettingsChange(settings.copy(showMilkyWay = !settings.showMilkyWay)) },
                        label = { Text(stringResource(R.string.label_milky_way_alt)) },
                    )
                }
                item {
                    FilterChip(
                        selected = settings.showBackgroundConstellations,
                        onClick = {
                            onSettingsChange(
                                settings.copy(showBackgroundConstellations = !settings.showBackgroundConstellations),
                            )
                        },
                        label = { Text(stringResource(R.string.label_all_constellations)) },
                    )
                }
                item {
                    FilterChip(
                        selected = settings.showConstellationStarPoints,
                        onClick = {
                            onSettingsChange(
                                settings.copy(showConstellationStarPoints = !settings.showConstellationStarPoints),
                            )
                        },
                        label = { Text(stringResource(R.string.label_star_points)) },
                    )
                }
                item {
                    FilterChip(
                        selected = settings.showConstellationNames,
                        onClick = {
                            onSettingsChange(
                                settings.copy(showConstellationNames = !settings.showConstellationNames),
                            )
                        },
                        label = { Text(stringResource(R.string.label_names)) },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = stringResource(
                        R.string.star_limit_with_count,
                        "%.1f".format(settings.starMagnitudeLimit),
                        starCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.starMagnitudeLimit.coerceIn(1f, 6f),
                    onValueChange = { onSettingsChange(settings.copy(starMagnitudeLimit = it)) },
                    valueRange = 1f..6f,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = stringResource(
                        R.string.dso_limit_with_count,
                        "%.1f".format(settings.deepSkyMagnitudeLimit),
                        deepSkyCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.deepSkyMagnitudeLimit.coerceIn(1f, 20f),
                    onValueChange = { onSettingsChange(settings.copy(deepSkyMagnitudeLimit = it)) },
                    valueRange = 1f..20f,
                )
            }
        }
    }
}

@Composable
private fun SkySphere(
    selected: ConstellationPattern,
    mirrorX: Boolean,
    mirrorY: Boolean,
    catalog: List<ConstellationPattern>,
    milkyWayLayers: List<MilkyWayLayer>,
    skyStars: List<CatalogStar>,
    deepSkyObjects: List<DeepSkyObject>,
    d3Settings: D3CatalogSettings,
    onPreviewSelect: (ConstellationPattern) -> Unit,
    modifier: Modifier,
) {
    // Orientierung des virtuellen Sternhimmels als FREIE Rotationsmatrix (Arcball). Fingerbewegungen
    // multiplizieren inkrementelle Bildschirm-Achsen-Rotationen davor -> der Himmel folgt IMMER dem
    // Finger, egal wie die Kugel gerade steht (kein Euler-Gimbal, kein Pol-Umschlag, kein Anschlag).
    var skyRot by remember { mutableStateOf(Mat3.IDENTITY) }
    var skyZoom by remember { mutableFloatStateOf(1f) }
    val visibleSkyStars = remember(skyStars, d3Settings.starMagnitudeLimit) {
        skyStars.filter { it.magnitude <= d3Settings.starMagnitudeLimit }
    }
    val visibleDeepSkyObjects = remember(deepSkyObjects, d3Settings.deepSkyMagnitudeLimit, d3Settings.showDeepSkyObjects) {
        if (d3Settings.showDeepSkyObjects) {
            deepSkyObjects.filter { it.magnitude?.let { magnitude -> magnitude <= d3Settings.deepSkyMagnitudeLimit } == true }
        } else {
            emptyList()
        }
    }
    // Zwei-Finger-Roll: Vorzeichen an die Spiegelung anpassen (wie bisher), damit die Drehung dem
    // Finger folgt. Der Arcball-Schwenk braucht durch die Bildschirm-Achsen-Rotation KEINE Korrektur.
    val rollDirection = if (mirrorX != mirrorY) -1f else 1f
    // Sensitivität mit Zoom skalieren: reingezoomt (skyZoom groß) ⇒ kleinere Bewegung pro Pixel
    // ⇒ langsameres, präziseres Schwenken; rausgezoomt ⇒ zügiger. Niedrigere Clamp-Untergrenze,
    // damit der Zoom-Effekt bis zum Maximalzoom spürbar bleibt.
    val skyPanSensitivity = (0.0045f / skyZoom).coerceIn(0.0011f, 0.0050f)
    val skyTransformPanSensitivity = (0.004f / skyZoom).coerceIn(0.0009f, 0.0045f)
    val skyTouchSlop = LocalViewConfiguration.current.touchSlop

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF070913))
            .pointerInput(catalog, mirrorX, mirrorY) {
                awaitEachGesture {
                    val gestureStartedNanos = System.nanoTime()
                    val startZoom = skyZoom
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var previousSingle = down.position
                    var previousCentroid: Offset? = null
                    var previousDistance = 0f
                    var previousAngle = 0f
                    var hadMultiTouch = false
                    var lastPoint = down.position
                    var maxSingleDrag = 0f
                    var pointerEventCount = 0
                    var maxPointerCount = 1
                    var multiTouchFrames = 0
                    var zoomUpdateCount = 0
                    var rollUpdateCount = 0

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        pointerEventCount++
                        event.changes.forEach { it.consume() }
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        maxPointerCount = max(maxPointerCount, pressed.size)
                        if (pressed.size < 2) {
                            if (hadMultiTouch) {
                                previousSingle = pressed.first().position
                                continue
                            }
                            previousCentroid = null
                            previousDistance = 0f
                            val change = pressed.firstOrNull() ?: continue
                            val panChange = change.position - previousSingle
                            previousSingle = change.position
                            lastPoint = change.position
                            maxSingleDrag = max(maxSingleDrag, (change.position - down.position).getDistance())
                            // Arcball: horizontale Fingerbewegung dreht um die Bildschirm-Y-Achse, vertikale
                            // um die Bildschirm-X-Achse; VOR die aktuelle Orientierung multipliziert -> der Punkt
                            // unter dem Finger folgt exakt, unabhängig von Lage/Roll/Spiegelung. Kein Clamp
                            // (kein „Anschlag"), kein Pol-Umschlag.
                            val aY = panChange.x * skyPanSensitivity
                            val aX = panChange.y * skyPanSensitivity
                            skyRot = Mat3.rotY(aY) * Mat3.rotX(aX) * skyRot
                            continue
                        } else {
                            previousSingle = pressed[0].position
                        }

                        if (pressed.size >= 2) {
                            hadMultiTouch = true
                            multiTouchFrames++
                            val first = pressed[0].position
                            val second = pressed[1].position
                            val vector = second - first
                            val centroid = (first + second) / 2f
                            val distance = vector.getDistance().coerceAtLeast(1f)
                            val angle = atan2(vector.y, vector.x)
                            val oldCentroid = previousCentroid
                            if (oldCentroid != null && previousDistance > 0f) {
                                val panChange = centroid - oldCentroid
                                val zoomChange = (distance / previousDistance).coerceIn(0.78f, 1.28f)
                                val rotationChange = wrapAngle(angle - previousAngle)
                                // Zwei-Finger: Arcball-Schwenk (Bildschirm-Achsen) + Roll um die Sichtachse
                                // (Bildschirm-Z) + Zoom. Alles inkrementell vor die Orientierung multipliziert.
                                val aY = panChange.x * skyTransformPanSensitivity
                                val aX = panChange.y * skyTransformPanSensitivity
                                skyRot = Mat3.rotZ(rotationChange * rollDirection) *
                                    Mat3.rotY(aY) * Mat3.rotX(aX) * skyRot
                                skyZoom = (skyZoom * zoomChange).coerceIn(0.65f, 2.35f)
                                if (abs(zoomChange - 1f) > 0.002f) zoomUpdateCount++
                                if (abs(rotationChange) > 0.002f) rollUpdateCount++
                            }
                            previousCentroid = centroid
                            previousDistance = distance
                            previousAngle = angle
                        }
                    }
                    if (!hadMultiTouch && maxSingleDrag < skyTouchSlop) {
                        val radius = min(size.width, size.height) * 0.44f * skyZoom
                        val center = Offset(size.width / 2f, size.height / 2f)
                        selectConstellationAt(
                            tap = lastPoint,
                            catalog = catalog,
                            rot = skyRot,
                            mirrorX = mirrorX,
                            mirrorY = mirrorY,
                            radius = radius,
                            center = center,
                            maxDistance = 34f,
                        )?.let(onPreviewSelect)
                    }
                    AppDiagnostics.record(
                        "sky_gesture_finished durationMs=${(System.nanoTime() - gestureStartedNanos) / 1_000_000L} " +
                            "events=$pointerEventCount maxPointers=$maxPointerCount multiFrames=$multiTouchFrames " +
                            "zoomUpdates=$zoomUpdateCount rollUpdates=$rollUpdateCount " +
                            "dragPx=${String.format(Locale.US, "%.2f", maxSingleDrag)} " +
                            "zoom=${String.format(Locale.US, "%.4f", startZoom)}->" +
                            String.format(Locale.US, "%.4f", skyZoom) +
                            " canvas=${size.width}x${size.height}",
                    )
                }
            },
    ) {
        val radius = min(size.width, size.height) * 0.44f * skyZoom
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(Color(0xFF11182A), radius, center)
        drawCircle(Color(0xFF31415E), radius, center, style = Stroke(width = 1.dp.toPx()))
        if (d3Settings.showMilkyWay) {
            drawMilkyWayLayers(milkyWayLayers, skyRot, radius, center, mirrorX, mirrorY)
        }
        if (d3Settings.showStars) {
            drawCatalogStars(visibleSkyStars, skyRot, radius, center, mirrorX, mirrorY)
        }
        if (d3Settings.showDeepSkyObjects) {
            drawDeepSkyObjects(visibleDeepSkyObjects, skyRot, radius, center, mirrorX, mirrorY, AppLocale.resolvedLanguageTag)
        }

        catalog.forEach { pattern ->
            if (!d3Settings.showBackgroundConstellations && pattern.id != selected.id) return@forEach
            val color = if (pattern.id == selected.id) Color(0xFFFFD166) else Color(0xFF90CAF9).copy(alpha = 0.55f)
            val projectedPoints = pattern.stars.map { star ->
                projectSkyPoint(star, skyRot, radius, center, mirrorX, mirrorY)
            }
            pattern.edges.forEach { (a, b) ->
                val start = projectedPoints.getOrNull(a)
                val end = projectedPoints.getOrNull(b)
                if (start != null && end != null) {
                    drawLine(color, start, end, strokeWidth = if (pattern.id == selected.id) 3.dp.toPx() else 1.3.dp.toPx())
                }
            }
            if (d3Settings.showConstellationStarPoints) {
                projectedPoints.forEach { point ->
                    if (point != null) drawCircle(color = color, radius = if (pattern.id == selected.id) 3.4.dp.toPx() else 2.dp.toPx(), center = point)
                }
            }
            if (d3Settings.showConstellationNames) {
                drawConstellationName(
                    pattern = pattern,
                    projectedPoints = projectedPoints,
                    selected = pattern.id == selected.id,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellationName(
    pattern: ConstellationPattern,
    projectedPoints: List<Offset?>,
    selected: Boolean,
) {
    val points = projectedPoints.filterNotNull()
    if (points.isEmpty()) return
    val center = Offset(
        x = points.sumOf { it.x.toDouble() }.toFloat() / points.size,
        y = points.sumOf { it.y.toDouble() }.toFloat() / points.size,
    )
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = if (selected) android.graphics.Color.argb(230, 255, 209, 102) else android.graphics.Color.argb(142, 210, 226, 255)
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textSize = if (selected) 13.dp.toPx() else 10.dp.toPx()
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText(pattern.localizedName(), center.x, center.y - 8.dp.toPx(), paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMilkyWayLayers(
    layers: List<MilkyWayLayer>,
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean,
    mirrorY: Boolean,
) {
    if (layers.isEmpty()) return
    layers.forEach { layer ->
        val fill = milkyWayLayerFill(layer.level)
        val outline = milkyWayLayerOutline(layer.level)
        layer.polygons.forEach { polygon ->
            polygon.rings.forEach { ring ->
                ring.visibleRuns(rot, radius, center, mirrorX, mirrorY).forEach { run ->
                    if (run.size >= 3) {
                        val path = Path().apply {
                            moveTo(run.first().x, run.first().y)
                            run.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(path, fill)
                        drawPath(
                            path = path,
                            color = outline,
                            style = Stroke(width = 0.45.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

private fun milkyWayLayerFill(level: Int): Color {
    val alpha = when (level) {
        1 -> 0.090f
        2 -> 0.122f
        3 -> 0.155f
        4 -> 0.190f
        else -> 0.230f
    }
    return Color(0xFFE8EEFF).copy(alpha = alpha)
}

private fun milkyWayLayerOutline(level: Int): Color {
    val alpha = (0.036f + level * 0.012f).coerceAtMost(0.088f)
    return Color(0xFFD6E1FF).copy(alpha = alpha)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCatalogStars(
    stars: List<CatalogStar>,
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean,
    mirrorY: Boolean,
) {
    if (stars.isEmpty()) return
    stars.asReversed().forEach { star ->
        val point = projectSkyPoint(star.point, rot, radius, center, mirrorX, mirrorY) ?: return@forEach
        val brightness = ((6f - star.magnitude) / 7.45f).coerceIn(0.08f, 1f)
        val starRadius = (0.75f + brightness * 2.65f).dp.toPx()
        drawCircle(
            color = starColor(star.bv).copy(alpha = 0.40f + brightness * 0.48f),
            radius = starRadius,
            center = point,
        )
        if (star.magnitude <= 2.2f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = starRadius * 1.75f,
                center = point,
                style = Stroke(width = 0.8.dp.toPx()),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDeepSkyObjects(
    objects: List<DeepSkyObject>,
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean,
    mirrorY: Boolean,
    lang: String = "en",
) {
    if (objects.isEmpty()) return
    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(180, 210, 232, 255)
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        textSize = 8.dp.toPx()
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    objects.asReversed().forEach { deepSky ->
        val point = projectSkyPoint(deepSky.point, rot, radius, center, mirrorX, mirrorY) ?: return@forEach
        val magnitude = deepSky.magnitude ?: return@forEach
        val brightness = ((20f - magnitude) / 20f).coerceIn(0.06f, 1f)
        val markerRadius = (1.4f + brightness * 4.2f).dp.toPx()
        val color = deepSkyColor(deepSky.type).copy(alpha = 0.40f + brightness * 0.38f)
        drawDeepSkyMarker(point, markerRadius, color, deepSky.type)
        if (magnitude <= 8f) {
            drawContext.canvas.nativeCanvas.drawText(
                deepSky.properDisplayName(lang),
                point.x + markerRadius + 3.dp.toPx(),
                point.y - 2.dp.toPx(),
                labelPaint,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDeepSkyMarker(
    center: Offset,
    radius: Float,
    color: Color,
    type: String,
) {
    when {
        type in galaxyTypes -> {
            drawOval(
                color = color,
                topLeft = Offset(center.x - radius * 1.55f, center.y - radius * 0.72f),
                size = Size(radius * 3.1f, radius * 1.44f),
                style = Stroke(width = 0.8.dp.toPx()),
            )
            drawCircle(color.copy(alpha = color.alpha * 0.55f), radius * 0.35f, center)
        }
        type in nebulaTypes -> {
            drawCircle(color.copy(alpha = color.alpha * 0.40f), radius * 1.25f, center)
            drawCircle(color, radius, center, style = Stroke(width = 0.8.dp.toPx()))
        }
        else -> {
            drawCircle(color.copy(alpha = color.alpha * 0.55f), radius * 0.55f, center)
            drawCircle(color, radius, center, style = Stroke(width = 0.75.dp.toPx()))
        }
    }
}

private val galaxyTypes = setOf("g", "s", "s0", "e", "i")
private val nebulaTypes = setOf("bn", "dn", "pn", "snr", "sfr", "rn")

private fun deepSkyColor(type: String): Color {
    return when {
        type in galaxyTypes -> Color(0xFFB7C8FF)
        type in nebulaTypes -> Color(0xFF7BE7D6)
        type == "gc" -> Color(0xFFFFD77E)
        type == "oc" -> Color(0xFFFFE7A3)
        else -> Color(0xFFD7E3FF)
    }
}

private fun starColor(bv: Float?): Color {
    val value = bv ?: return Color.White
    return when {
        value < -0.05f -> Color(0xFFAED6FF)
        value < 0.35f -> Color(0xFFD7E8FF)
        value < 0.85f -> Color(0xFFFFF7DE)
        value < 1.35f -> Color(0xFFFFD8A8)
        else -> Color(0xFFFFB083)
    }
}

private fun List<SkyPoint>.visibleRuns(
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean,
    mirrorY: Boolean,
): List<List<Offset>> {
    val runs = mutableListOf<MutableList<Offset>>()
    var current = mutableListOf<Offset>()
    forEach { point ->
        val projected = projectSkyPoint(point, rot, radius, center, mirrorX, mirrorY)
        if (projected == null) {
            if (current.size >= 2) runs += current
            current = mutableListOf()
        } else {
            val previous = current.lastOrNull()
            if (previous != null && previous.distanceTo(projected) > radius * 1.15f) {
                if (current.size >= 2) runs += current
                current = mutableListOf(projected)
            } else {
                current += projected
            }
        }
    }
    if (current.size >= 2) runs += current
    return runs
}

@Composable
private fun ConstellationCard(pattern: ConstellationPattern, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(176.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Nur die gewählte Sprache: Name via localizedName(), Hemisphäre via localizedLabel().
            // Der lateinische Katalogname wird als kleiner, sprachneutraler Zusatz NUR gezeigt, wenn er
            // sich vom Anzeigenamen unterscheidet (in EN ist localizedName == name -> kein Dublett).
            Text(pattern.localizedName(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (pattern.name != pattern.localizedName()) {
                Text(pattern.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = pattern.hemisphere.localizedLabel(),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun ExportSheet(
    onShare: (ExportScale, Boolean, ExportProjectionMode, Int) -> Unit,
    onSave: (ExportScale, Boolean, ExportProjectionMode, Int) -> Unit,
    onShareDiagnostic: () -> Unit,
    onExportSolveCrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableStateOf(ExportScale.Small) }
    var includeBackground by remember { mutableStateOf(true) }
    var projectionMode by remember { mutableStateOf(ExportProjectionMode.Flat) }
    // Reines Qualitäts-EXPERIMENT (Nutzer-Vorgabe 2026-08-30) -- NUR bei Spherical360 sichtbar/wirksam,
    // s. SphericalOverlayRenderer.drawTextMesh-KDoc. 1 = aus (Default, unverändertes Verhalten).
    var ssaaFactor by remember { mutableStateOf(1) }
    // Komplett selbstständig (kein onRunGpuSmokeTest-Parameter mehr) -- ein zusätzlicher Parameter an
    // dieser Stelle hatte die ohnehin randvolle StarMapperApp()-Funktion über die 64KB-Bytecode-Grenze
    // getrieben (MethodTooLargeException). ExportSheet holt sich Scope/Context selbst, kostet dadurch
    // nur das eigene (viel kleinere) Methodenbudget.
    val gpuSmokeTestScope = rememberCoroutineScope()
    val gpuSmokeTestContext = LocalContext.current

    // Kopfzeile + Außenabstand kommen vom gemeinsamen Popup-Scaffold (EditorPanelSheet) -> kein
    // eigener Titel/Padding mehr, damit Export wie alle anderen Popups aussieht.
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppSegmentTabs(
            options = ExportScale.entries.map { option ->
                when (option) {
                    ExportScale.Small -> stringResource(R.string.label_small)
                    ExportScale.Medium -> stringResource(R.string.label_medium)
                    ExportScale.Original -> stringResource(R.string.label_original)
                }
            },
            selectedIndex = ExportScale.entries.indexOf(scale).coerceAtLeast(0),
            onSelect = { scale = ExportScale.entries[it] },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = includeBackground,
                onClick = { includeBackground = true },
                label = { Text(stringResource(R.string.label_image_plus_overlay)) },
            )
            FilterChip(
                selected = !includeBackground,
                onClick = { includeBackground = false },
                label = { Text(stringResource(R.string.label_overlay_only)) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = projectionMode == ExportProjectionMode.Flat,
                onClick = { projectionMode = ExportProjectionMode.Flat },
                label = { Text(stringResource(R.string.label_export_projection_flat)) },
            )
            FilterChip(
                selected = projectionMode == ExportProjectionMode.Spherical360,
                onClick = { projectionMode = ExportProjectionMode.Spherical360 },
                label = { Text(stringResource(R.string.label_export_projection_spherical)) },
            )
        }
        if (projectionMode == ExportProjectionMode.Spherical360) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.label_ssaa_experiment), style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { factor ->
                        FilterChip(
                            selected = ssaaFactor == factor,
                            onClick = { ssaaFactor = factor },
                            label = { Text("${factor}×") },
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onShare(scale, includeBackground, projectionMode, ssaaFactor) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_share_png))
        }
        OutlinedButton(
            onClick = { onSave(scale, includeBackground, projectionMode, ssaaFactor) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save_to_photos))
        }
        TextButton(
            onClick = onExportSolveCrop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.CropSquare, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_save_solved_crop))
        }
        TextButton(
            onClick = onShareDiagnostic,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_share_app_diagnostics))
        }
        // Reine Vorstufe für den geplanten GPU-Sphere-Space-Renderpfad (Nutzer-Vorgabe 2026-08-30,
        // Umsetzungsschritt 2: EGL/FBO-Smoke-Test) -- betrifft den eigentlichen Export in keiner Weise,
        // rein diagnostisch (Ergebnis landet in der App-Diagnose).
        TextButton(
            onClick = {
                gpuSmokeTestScope.launch {
                    val result = withContext(Dispatchers.Default) { GpuSmokeTest.run() }
                    AppDiagnostics.record(
                        "gpu_smoke_test success=${result.success} " +
                            "glesVersionRequested=${result.glesVersionRequested} " +
                            "glVersion=${result.glVersion} glVendor=${result.glVendor} glRenderer=${result.glRenderer} " +
                            "extensionCount=${result.extensionCount} seamlessCubemap=${result.seamlessCubemapExtensionPresent} " +
                            result.samplePixels.entries.joinToString(" ") { (name, argb) -> "sample_$name=#${"%08X".format(argb)}" } +
                            (result.failureReason?.let { " failureReason=$it" } ?: ""),
                    )
                    Toast.makeText(
                        gpuSmokeTestContext,
                        gpuSmokeTestContext.getString(
                            if (result.success) R.string.toast_gpu_smoke_test_success else R.string.toast_gpu_smoke_test_failed,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_run_gpu_smoke_test))
        }
    }
}

@Composable
private fun TextOverlayDialog(initialText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_free_text)) },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.label_label_singular)) },
                // Mehrzeilig: Enter = Zeilenumbruch.
                singleLine = false,
                minLines = 1,
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.action_place))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ManualHintDialog(
    initialText: String,
    referenceCatalogStars: List<CatalogStar>,
    skyCatalogStars: List<CatalogStar>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(initialText) { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_manual_hint)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.manual_hint_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.label_star_name)) },
                    singleLine = true,
                )
                // Live-Vorschläge je getippten Buchstaben (Nutzerwunsch) -- dasselbe Muster wie "Stern
                // suchen & platzieren" in FisheyeAlignScreen.kt: Antippen wählt den Katalog-Stern
                // eindeutig aus UND bestätigt sofort (kein zusätzlicher Tap auf "Übernehmen" nötig, da
                // der Name exakt aus dem Katalog stammt und findStarByName ihn daher sicher wiederfindet).
                val query = value.trim()
                if (query.isNotEmpty()) {
                    // Bewusst NICHT nur properName (Eigenname) durchsuchen: auf dem Foto wird JEDER
                    // Stern mit Beschriftung gezeigt, auch ohne Eigenname über die Katalogbezeichnung
                    // (Bayer/Flamsteed/Variable/HIP, s. name/localizedNames -- dieselben Felder, die
                    // findStarByName unten auch schon durchsucht). Vorher fielen alle nur-Katalog-
                    // benannten, aber sichtbaren Sterne aus den Vorschlägen komplett heraus (Nutzer-
                    // befund 2026-08-17: "nicht alle angezeigten Sterne lassen sich finden").
                    val matches = (referenceCatalogStars + skyCatalogStars)
                        .asSequence()
                        .filter { star ->
                            val candidates = listOf(star.properName, star.name) +
                                star.localizedProperNames.values + star.localizedNames.values
                            candidates.any { it.isNotBlank() && it.contains(query, ignoreCase = true) }
                        }
                        // Über id statt properName entdoppeln -- mehrere Katalog-benannte (kein
                        // Eigenname) Sterne hätten sonst alle dasselbe leere Dedup-Merkmal und
                        // kollabierten fälschlich zu einem einzigen Vorschlag.
                        .distinctBy { it.id }
                        .sortedBy { it.magnitude }
                        .take(6)
                        .toList()
                    matches.forEach { star ->
                        val label = star.displayName(AppLocale.resolvedLanguageTag)
                        TextButton(
                            onClick = { onConfirm(label) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            Row {
                if (initialText.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * Klartext-Qualitätseinstufung aus dem Median-Reprojektionsfehler (Bildpixel) einer Kachel gegen
 * den gemeinsamen Fit — [ratio] ist der Fehler geteilt durch TileConsistency.MIN_ERROR_FRACTION *
 * lange Bildkante, also dieselbe Skala, ab der eine Kachel real als Ausreißer geflaggt würde.
 */
@Composable
private fun tileQualityLabel(ratio: Double): String = when {
    ratio < 0.25 -> stringResource(R.string.quality_excellent)
    ratio < 0.5 -> stringResource(R.string.quality_very_good)
    ratio < 1.0 -> stringResource(R.string.quality_good)
    ratio < 2.0 -> stringResource(R.string.quality_poor)
    else -> stringResource(R.string.quality_very_poor)
}

/**
 * Klartext-Einstufung der echten, unabhängigen Pro-Kachel-Solve-Genauigkeit ([rmsPx], s.
 * tileOwnRmsById) -- EIGENE, für diese Kennzahl passende absolute Schwellen (nicht tileQualityLabels
 * relative MIN_ERROR_FRACTION-Skala, die für Falschtreffer-Erkennung mit hunderten Pixeln kalibriert
 * ist). Startwerte, mit dem Nutzer abgestimmt, AUSDRÜCKLICH UNVERIFIZIERT gegen echte Gerätefälle.
 */
// Ab hier gilt eine Kachel-RMS als "schlecht" (s. tileOwnAccuracyLabel) -- dieselbe Schwelle markiert
// jetzt auch die neue Warnmarkierung im Editor-Canvas (s. EditorCanvas' Kachel-Zeichen-Schleife),
// damit Popup-Einstufung und Warnsymbol nie unabhängig voneinander verstellt werden können.
private const val TILE_WEAK_RMS_THRESHOLD_PX = 10.0

@Composable
private fun tileOwnAccuracyLabel(rmsPx: Double): String = when {
    rmsPx < 3.0 -> stringResource(R.string.quality_excellent)
    rmsPx < 6.0 -> stringResource(R.string.quality_very_good)
    rmsPx < TILE_WEAK_RMS_THRESHOLD_PX -> stringResource(R.string.quality_good)
    rmsPx < 20.0 -> stringResource(R.string.quality_poor)
    else -> stringResource(R.string.quality_very_poor)
}

/** "≈ 25 Lichtjahre" / "≈ 5.610 Lichtjahre" -- kurz und gerundet, Tausendertrennung ab 1000. */
private fun formatLightYears(lightYears: Double, german: Boolean): String {
    val locale = if (german) Locale.GERMANY else Locale.US
    val rounded = if (lightYears < 1000) {
        String.format(locale, "%.1f", lightYears)
    } else {
        String.format(locale, "%,.0f", lightYears)
    }
    return if (german) "≈ $rounded Lichtjahre" else "≈ $rounded light-years"
}

/** Serialisiert genau das, was für [entry] sichtbar ist, als sauberen Klartext-Block für die Zwischenablage. */
private fun buildRegionInfoCopyText(
    context: Context,
    entry: RegionInfoEntry,
    summary: WikipediaSummary?,
    facts: WikidataFacts?,
    german: Boolean,
): String {
    val lines = mutableListOf(entry.title, entry.subtitle)
    entry.facts.forEach { lines += "${it.label}: ${it.value}" }
    facts?.distanceLightYears?.let {
        lines += "${context.getString(R.string.label_distance)}: ${formatLightYears(it, german)}"
    }
    facts?.spectralType?.let {
        lines += "${context.getString(R.string.label_spectral_type)}: $it"
    }
    if (summary != null) {
        lines += ""
        lines += summary.extract
        lines += if (facts != null) {
            context.getString(R.string.source_wikipedia_wikidata)
        } else {
            context.getString(R.string.source_wikipedia)
        }
    }
    return lines.joinToString("\n")
}

/**
 * „Region-Info": Wissenswertes zu den AKTUELL sichtbaren (Katalog-)Beschriftungen im Bild -- keine
 * eigene FOV-Logik, reine Aufbereitung von [overlays] (s. [RegionInfoMatcher]). Strukturierte Fakten
 * erscheinen sofort (offline); Wikipedia-Kurztexte und Wikidata-Zusatzfakten laden nebenläufig nach,
 * wo ein Versuch sich lohnt.
 */
@Composable
private fun RegionInfoDialog(
    overlays: List<AnnotationOverlay>,
    deepSkyObjects: List<DeepSkyObject>,
    catalogStars: List<CatalogStar>,
    lang: String,
    onDismiss: () -> Unit,
) {
    // Nur für die (bewusst weiterhin zweisprachigen, s. Plan) Wikipedia-Prosa-Formatierungen unten --
    // die strukturierten Fakten selbst kommen bereits sprachfertig aus RegionInfoMatcher(lang).
    val german = lang == "de"
    // NICHT mit `overlays` selbst keyen (bleibt über Recompositions hinweg dieselbe
    // SnapshotStateList-Identität) -- über eine abgeleitete, wertbasierte Signatur, damit sich die
    // Liste live mitändert, wenn der Nutzer bei offenem Dialog Katalog-Regler verstellt.
    val overlaySignature = overlays.joinToString("|") { "${it.id}:${it.layer}:${it.text}" }
    val entries = remember(overlaySignature, deepSkyObjects, catalogStars, lang) {
        RegionInfoMatcher.buildEntries(overlays, deepSkyObjects, catalogStars, lang)
    }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Mehrfachauswahl fuer den kombinierten Kopieren-Button unten (Nutzerwunsch: einzelne Objekte
    // gezielt auswaehlen, z.B. fuer einen Social-Media-Post per KI). Ueber cacheKey statt Index, damit
    // eine Auswahl stabil bleibt, auch wenn sich die Liste durch Katalog-Regler waehrend offenem Dialog
    // umsortiert.
    var selectedCacheKeys by remember { mutableStateOf(setOf<String>()) }
    // Suchfeld (Nutzerwunsch: bei dicht belegten Feldern schnell ein einzelnes Objekt finden) --
    // filtert nur die ANZEIGE, entries selbst (und damit Auswahl/Fetch-Effect unten) bleibt
    // unveraendert, damit eine bereits getroffene Auswahl beim Weitertippen/Loeschen der Suche nicht
    // verloren geht. entry.title enthaelt seit dem Klarname-Fix bereits die Katalogbezeichnung in
    // Klammern (z.B. "Herznebel (IC 1805)"), eine Suche nach der Katalogbezeichnung findet das Objekt
    // also ueber denselben Titel-Abgleich mit.
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(entries, searchQuery) {
        if (searchQuery.isBlank()) entries else entries.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    // Reihenfolge: aktuelle App-Sprache -> eingestellte Fallback-Sprache -> immer zuletzt Englisch,
    // dedupliziert (z.B. wenn die Fallback-Sprache bereits Englisch ist).
    val langChain = remember(lang) {
        listOf(lang, AppLocale.tagFor(AppLocale.fallbackLanguage), "en").distinct()
    }

    // Nutzerwunsch 2026-08-20: KEIN automatischer Massenabruf mehr beim Öffnen -- bei einem dicht
    // belegten Feld wurden dadurch zu viele gleichzeitige Wikipedia-Abrufe angestoßen (u.a. durch den
    // neuen Volltextsuche-Fallback selbst noch teurer geworden, s. WikipediaSummaryService), was sich
    // wie "ich drücke auf Info laden, es passiert nichts" anfühlte -- der eine Tastendruck landete in
    // einer Warteschlange voller automatisch gestarteter Abrufe. Jetzt lädt AUSSCHLIESSLICH ein
    // gezielter Tastendruck auf "Info laden" genau EIN Objekt -- weniger Systemlast, kein unsichtbares
    // Warten. [loadingKeys] hält, für welche Objekte GERADE (durch Antippen) ein Abruf läuft, rein
    // lokaler UI-Zustand (unabhängig vom Dialog-Cache in WikipediaSummaryService).
    var loadingKeys by remember { mutableStateOf(setOf<String>()) }
    fun loadInfo(entry: RegionInfoEntry) {
        if (entry.cacheKey in loadingKeys) return
        loadingKeys = loadingKeys + entry.cacheKey
        scope.launch {
            try {
                WikipediaSummaryService.ensureFetchedChain(context, entry.cacheKey, entry.wikipediaTitleCandidates, langChain)
                // Wikidata-Fakten NUR nach einem inhaltlich geprüften Wikipedia-Treffer (Themen-Gate
                // in WikipediaSummaryService.fetchSummary bereits bestanden) -- ohne Treffer gibt es
                // auch keine Q-ID, also automatisch dieselbe Absicherung wie beim Kurztext.
                if (entry.attemptWikidataFacts) {
                    val qid = WikipediaSummaryService.bestResult(entry.cacheKey, langChain)?.wikibaseItem
                    if (!qid.isNullOrBlank()) {
                        WikidataFactsService.ensureFetched(qid)
                    }
                }
            } finally {
                loadingKeys = loadingKeys - entry.cacheKey
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_region_info)) },
        text = {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.region_info_no_entries),
                )
            } else {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.region_info_search_hint)) },
                        singleLine = true,
                        trailingIcon = if (searchQuery.isNotBlank()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear_search))
                                }
                            }
                        } else {
                            null
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredEntries.isEmpty()) {
                        Text(
                            stringResource(R.string.region_info_no_search_results),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            stringResource(R.string.region_info_wikipedia_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        filteredEntries.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Spacer(modifier = Modifier.height(if (index == 0) 8.dp else 0.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = entry.cacheKey in selectedCacheKeys,
                                onCheckedChange = { checked ->
                                    selectedCacheKeys = if (checked) {
                                        selectedCacheKeys + entry.cacheKey
                                    } else {
                                        selectedCacheKeys - entry.cacheKey
                                    }
                                },
                            )
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    val summary = WikipediaSummaryService.bestResult(entry.cacheKey, langChain)
                                    val facts = summary?.wikibaseItem?.let { WikidataFactsService.results[it] }
                                    val text = buildRegionInfoCopyText(context, entry, summary, facts, german)
                                    clipboardManager.setText(AnnotatedString(text))
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy_this_object),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Herkunft des Populärnamens (z.B. "Stellarium") -- nur bei Namen ohne
                        // unabhängige Zweitquelle gesetzt (s. RegionInfoMatcher.buildDeepSkyEntry),
                        // damit unabhängig bestätigte und nur einfach belegte Namen unterscheidbar
                        // bleiben (Nutzerwunsch 2026-08-19).
                        if (entry.nameSource.isNotBlank()) {
                            Text(
                                stringResource(R.string.region_info_name_source, entry.nameSource),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        entry.facts.forEach { fact ->
                            Text("${fact.label}: ${fact.value}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.wikipediaTitleCandidates.isNotEmpty()) {
                            val hasResult = WikipediaSummaryService.chainComplete(entry.cacheKey, langChain)
                            val summary = WikipediaSummaryService.bestResult(entry.cacheKey, langChain)
                            val isLoading = entry.cacheKey in loadingKeys
                            Spacer(modifier = Modifier.height(4.dp))
                            when {
                                summary != null -> {
                                    Text(summary.extract, style = MaterialTheme.typography.bodyMedium)
                                    // Wikidata-Fakten hängen an derselben Q-ID wie der bereits geprüfte
                                    // Wikipedia-Treffer -- automatisch von Teil 1 (Themen-Gate) mitgeschützt.
                                    val wikidataFacts = if (entry.attemptWikidataFacts) {
                                        summary.wikibaseItem?.let { WikidataFactsService.results[it] }
                                    } else {
                                        null
                                    }
                                    wikidataFacts?.distanceLightYears?.let { ly ->
                                        Text(
                                            "${stringResource(R.string.label_distance)}: ${formatLightYears(ly, german)}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    wikidataFacts?.spectralType?.let { type ->
                                        Text(
                                            "${stringResource(R.string.label_spectral_type)}: $type",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = if (summary.pageUrl != null) {
                                            Modifier.clickable {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(summary.pageUrl)),
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ) {
                                        Text(
                                            if (wikidataFacts != null) {
                                                stringResource(R.string.source_wikipedia_wikidata)
                                            } else {
                                                stringResource(R.string.source_wikipedia)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (summary.pageUrl != null) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                        if (summary.pageUrl != null) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = stringResource(R.string.action_open_source),
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                // hasResult && summary == null -> Versuch lief, kein Artikel gefunden.
                                // Sichtbar markiert (Nutzerbefund 2026-08-19: "lädt nicht zuverlässig" -- ohne
                                // diese Zeile war dieser Zustand optisch nicht von "noch nicht versucht" zu
                                // unterscheiden, weil hier zuvor gar nichts gerendert wurde).
                                hasResult -> Text(
                                    stringResource(R.string.region_info_no_wikipedia_article),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                isLoading -> Text(
                                    stringResource(R.string.status_loading_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // Weder Ergebnis noch gerade am Laden -> nichts wurde bisher angefragt (kein
                                // automatischer Massenabruf mehr, s. Kommentar oben) -- Antippen startet
                                // GENAU EINEN gezielten Abruf für dieses Objekt.
                                else -> Text(
                                    stringResource(R.string.action_load_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { loadInfo(entry) },
                                )
                            }
                        }
                    }
                    }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = entries.filter { it.cacheKey in selectedCacheKeys }
                    // Jeder Objekt-Block bleibt exakt der bereits bewährte Einzel-Kopiertext (Titel
                    // zuerst), nur die Titelzeile bekommt ein "• " voran (Nutzerwunsch: schöner als
                    // eine "---"-Trennlinie). Die Leerzeile zwischen den Blöcken (joinToString) macht
                    // die eigentliche Arbeit fürs klare Untereinander-Stehen beim Einfügen UND fürs
                    // KI-Erkennen einzelner Absätze -- der Punkt obendrauf ist die zusätzliche, vom
                    // Nutzer gewünschte optische Klarheit.
                    val combined = selected.joinToString("\n\n") { entry ->
                        val summary = WikipediaSummaryService.bestResult(entry.cacheKey, langChain)
                        val facts = summary?.wikibaseItem?.let { WikidataFactsService.results[it] }
                        "• " + buildRegionInfoCopyText(context, entry, summary, facts, german)
                    }
                    clipboardManager.setText(AnnotatedString(combined))
                },
                enabled = selectedCacheKeys.isNotEmpty(),
            ) {
                Text("${stringResource(R.string.action_copy_selection)} (${selectedCacheKeys.size})")
            }
        },
    )
}

@Composable
private fun TileInfoDialog(
    tile: SolveTile,
    medianErrorPx: Double?,
    ownAccuracy: TileOwnAccuracy?,
    minError: Double,
    centerRaDeg: Float?,
    centerDecDeg: Float?,
    fovDeg: Float?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_tile_info)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.label_status_colon) + when (tile.status) {
                        SolveTileStatus.Pending -> stringResource(R.string.label_pending)
                        SolveTileStatus.Solving -> stringResource(R.string.status_being_solved)
                        SolveTileStatus.Solved -> stringResource(R.string.label_solved)
                        SolveTileStatus.Failed -> stringResource(R.string.label_failed)
                        SolveTileStatus.Suspect -> stringResource(R.string.label_suspect_excluded)
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.label_dewarp_used) +
                        if (tile.dewarp) stringResource(R.string.action_yes) else stringResource(R.string.action_no),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.label_solved_with_hint) +
                        when (tile.solvedWithHint) {
                            true -> stringResource(R.string.action_yes)
                            false -> stringResource(R.string.label_no_blind)
                            null -> "–"
                        },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.label_solve_duration) +
                        (tile.solveDurationMs?.let { "%.1f s".format(it / 1000.0) } ?: "–"),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.label_center_radec) +
                        if (centerRaDeg != null && centerDecDeg != null) {
                            "%.1f° / %+.1f°".format(centerRaDeg, centerDecDeg)
                        } else {
                            "–"
                        },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.label_fov_long_edge) +
                        (fovDeg?.let { "%.2f°".format(it) } ?: "–"),
                )
                Spacer(modifier = Modifier.height(4.dp))
                val qualityText = when {
                    tile.status != SolveTileStatus.Solved && tile.status != SolveTileStatus.Suspect ->
                        "–"
                    medianErrorPx == null -> stringResource(R.string.tile_quality_not_assessable)
                    else -> tileQualityLabel(medianErrorPx / minError)
                }
                Text(stringResource(R.string.label_consistency_overall) + qualityText)
                Spacer(modifier = Modifier.height(4.dp))
                // Anders als die Konsistenz oben: kein Vergleich mit den anderen Kacheln, sondern ein
                // absoluter Genauigkeitswert für DIESE Kachel -- bevorzugt aus den echten, von
                // astrometry.net selbst verifizierten .corr-Solve-Treffern, sonst Kreuzmatch der
                // Kachel-eigenen WCS gegen echte, unabhängig erkannte Sterne (s.
                // solveAllTiles()/tileOwnRmsById).
                val ownRmsText = when {
                    tile.status != SolveTileStatus.Solved && tile.status != SolveTileStatus.Suspect ->
                        "–"
                    ownAccuracy == null -> stringResource(R.string.tile_own_accuracy_no_refs)
                    ownAccuracy.rmsPx == null -> stringResource(
                        R.string.tile_own_accuracy_too_few_refs,
                        ownAccuracy.matchCount,
                        TileConsistency.MIN_TILE_OWN_RMS_MATCHES,
                    )
                    else -> "${tileOwnAccuracyLabel(ownAccuracy.rmsPx!!)} (%.1f px, ".format(ownAccuracy.rmsPx) +
                        stringResource(R.string.stars_count_close_paren, ownAccuracy.matchCount)
                }
                Text(stringResource(R.string.label_own_solve_accuracy) + ownRmsText)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun ConstellationReferenceDialog(
    pattern: ConstellationPattern,
    skyStars: List<CatalogStar>,
    deepSkyObjects: List<DeepSkyObject>,
    milkyWayLayers: List<MilkyWayLayer>,
    mirrorX: Boolean,
    mirrorY: Boolean,
    onDismiss: () -> Unit,
) {
    var referenceZoom by remember(pattern.id, mirrorX, mirrorY) { mutableFloatStateOf(1f) }
    var referencePan by remember(pattern.id, mirrorX, mirrorY) { mutableStateOf(Offset.Zero) }
    var referenceRoll by remember(pattern.id, mirrorX, mirrorY) { mutableFloatStateOf(0f) }
    var showReferenceStarNames by remember(pattern.id) { mutableStateOf(true) }
    var showReferenceDeepSkyObjects by remember(pattern.id) { mutableStateOf(true) }
    val referenceStarMagnitudeMax = remember(skyStars) {
        (skyStars.maxOfOrNull { it.magnitude } ?: 6f).coerceIn(6f, 8f)
    }
    var referenceStarMagnitudeLimit by remember(pattern.id, referenceStarMagnitudeMax) {
        mutableFloatStateOf(7f.coerceAtMost(referenceStarMagnitudeMax))
    }
    var referenceDeepSkyLimit by remember(pattern.id) { mutableFloatStateOf(12f) }
    val referenceStarCount = remember(skyStars, referenceStarMagnitudeLimit) {
        skyStars.count { it.magnitude <= referenceStarMagnitudeLimit }
    }
    val referenceDeepSkyCount = remember(deepSkyObjects, referenceDeepSkyLimit) {
        deepSkyObjects.count { it.magnitude?.let { magnitude -> magnitude <= referenceDeepSkyLimit } == true }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.catalog_reference_title, pattern.localizedName()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = showReferenceStarNames,
                            onClick = { showReferenceStarNames = !showReferenceStarNames },
                            label = { Text(stringResource(R.string.label_star_names)) },
                        )
                    }
                    item {
                        FilterChip(
                            selected = showReferenceDeepSkyObjects,
                            onClick = { showReferenceDeepSkyObjects = !showReferenceDeepSkyObjects },
                            label = { Text("DSO") },
                        )
                    }
                }
                SettingSlider(
                    label = stringResource(
                        R.string.star_limit_with_count,
                        "%.1f".format(referenceStarMagnitudeLimit),
                        referenceStarCount,
                    ),
                    value = referenceStarMagnitudeLimit,
                    valueRange = 1f..referenceStarMagnitudeMax,
                    onValueChange = { referenceStarMagnitudeLimit = it },
                )
                SettingSlider(
                    label = stringResource(
                        R.string.dso_limit_with_count,
                        "%.1f".format(referenceDeepSkyLimit),
                        referenceDeepSkyCount,
                    ),
                    value = referenceDeepSkyLimit,
                    valueRange = 1f..20f,
                    onValueChange = { referenceDeepSkyLimit = it },
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF070913))
                        .pointerInput(pattern.id, mirrorX, mirrorY) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var previousSingle = down.position
                                var previousCentroid: Offset? = null
                                var previousDistance = 0f
                                var previousAngle = 0f
                                var hadMultiTouch = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size < 2) {
                                        if (hadMultiTouch) {
                                            pressed.firstOrNull()?.consume()
                                            continue
                                        }
                                        val change = pressed.first()
                                        val delta = change.position - previousSingle
                                        previousSingle = change.position
                                        referencePan += delta
                                        change.consume()
                                        continue
                                    }
                                    hadMultiTouch = true
                                    val first = pressed[0].position
                                    val second = pressed[1].position
                                    val vector = second - first
                                    val centroid = (first + second) / 2f
                                    val distance = vector.getDistance().coerceAtLeast(1f)
                                    val angle = atan2(vector.y, vector.x)
                                    val oldCentroid = previousCentroid
                                    if (oldCentroid != null && previousDistance > 0f) {
                                        referencePan += centroid - oldCentroid
                                        referenceZoom = (referenceZoom * (distance / previousDistance).coerceIn(0.78f, 1.28f))
                                            .coerceIn(0.55f, 6.5f)
                                        referenceRoll = wrapAngle(referenceRoll + wrapAngle(angle - previousAngle))
                                        event.changes.forEach { it.consume() }
                                    }
                                    previousCentroid = centroid
                                    previousDistance = distance
                                    previousAngle = angle
                                    previousSingle = pressed[0].position
                                }
                            }
                        },
                ) {
                    drawConstellationReference(
                        pattern = pattern,
                        skyStars = skyStars,
                        deepSkyObjects = deepSkyObjects,
                        milkyWayLayers = milkyWayLayers,
                        mirrorX = mirrorX,
                        mirrorY = mirrorY,
                        referenceZoom = referenceZoom,
                        referencePan = referencePan,
                        referenceRoll = referenceRoll,
                        showStarNames = showReferenceStarNames,
                        starMagnitudeLimit = referenceStarMagnitudeLimit,
                        showDeepSkyObjects = showReferenceDeepSkyObjects,
                        deepSkyMagnitudeLimit = referenceDeepSkyLimit,
                    )
                }
                Text(
                    text = stringResource(R.string.rendered_offline_d3_catalog),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

private data class ReferenceBounds(
    val minRa: Float,
    val maxRa: Float,
    val minDec: Float,
    val maxDec: Float,
    val anchorRa: Float,
) {
    val raSpan: Float get() = (maxRa - minRa).coerceAtLeast(1f)
    val decSpan: Float get() = (maxDec - minDec).coerceAtLeast(1f)
}

private fun referenceBounds(pattern: ConstellationPattern): ReferenceBounds {
    val anchor = pattern.stars.firstOrNull()?.raHours?.times(15f) ?: 0f
    val ras = pattern.stars.map { unwrapRaDegrees(it.raHours * 15f, anchor) }
    val decs = pattern.stars.map { it.decDegrees }
    val minRa = ras.minOrNull() ?: 0f
    val maxRa = ras.maxOrNull() ?: 1f
    val minDec = decs.minOrNull() ?: -1f
    val maxDec = decs.maxOrNull() ?: 1f
    val raMargin = max(7f, (maxRa - minRa) * 0.42f)
    val decMargin = max(7f, (maxDec - minDec) * 0.42f)
    return ReferenceBounds(
        minRa = minRa - raMargin,
        maxRa = maxRa + raMargin,
        minDec = (minDec - decMargin).coerceAtLeast(-90f),
        maxDec = (maxDec + decMargin).coerceAtMost(90f),
        anchorRa = anchor,
    )
}

private fun unwrapRaDegrees(raDegrees: Float, anchorRaDegrees: Float): Float {
    var value = raDegrees
    while (value - anchorRaDegrees > 180f) value -= 360f
    while (value - anchorRaDegrees < -180f) value += 360f
    return value
}

private fun ReferenceBounds.contains(point: SkyPoint): Boolean {
    val ra = unwrapRaDegrees(point.raDegrees, anchorRa)
    return ra in minRa..maxRa && point.decDegrees in minDec..maxDec
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.referenceMap(
    point: SkyPoint,
    bounds: ReferenceBounds,
    padding: Float,
    mirrorX: Boolean,
    mirrorY: Boolean,
): Offset {
    val rawX = padding + (unwrapRaDegrees(point.raDegrees, bounds.anchorRa) - bounds.minRa) / bounds.raSpan * (size.width - padding * 2f)
    val rawY = padding + (bounds.maxDec - point.decDegrees) / bounds.decSpan * (size.height - padding * 2f)
    val x = if (mirrorX) size.width - rawX else rawX
    val y = if (mirrorY) size.height - rawY else rawY
    return Offset(x, y)
}

private fun transformReferencePoint(
    point: Offset,
    canvasSize: Size,
    zoom: Float,
    pan: Offset,
    roll: Float,
): Offset {
    val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
    return center + rotateOffset((point - center) * zoom, roll) + pan
}

private fun Offset.isNearCanvas(canvasSize: Size, margin: Float): Boolean {
    return x >= -margin &&
        x <= canvasSize.width + margin &&
        y >= -margin &&
        y <= canvasSize.height + margin
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellationReference(
    pattern: ConstellationPattern,
    skyStars: List<CatalogStar>,
    deepSkyObjects: List<DeepSkyObject>,
    milkyWayLayers: List<MilkyWayLayer>,
    mirrorX: Boolean,
    mirrorY: Boolean,
    referenceZoom: Float,
    referencePan: Offset,
    referenceRoll: Float,
    showStarNames: Boolean,
    starMagnitudeLimit: Float,
    showDeepSkyObjects: Boolean,
    deepSkyMagnitudeLimit: Float,
) {
    val bounds = referenceBounds(pattern)
    val padding = 22.dp.toPx()
    fun map(point: SkyPoint): Offset {
        return transformReferencePoint(
            referenceMap(point, bounds, padding, mirrorX, mirrorY),
            size,
            referenceZoom,
            referencePan,
            referenceRoll,
        )
    }
    drawRect(Color(0xFF070913))
    milkyWayLayers.forEach { layer ->
        val fill = milkyWayLayerFill(layer.level)
        val outline = milkyWayLayerOutline(layer.level)
        val margin = max(size.width, size.height) * 0.65f
        val segmentLimit = max(size.width, size.height) * max(0.70f, referenceZoom * 0.34f)
        layer.polygons.forEach { polygon ->
            polygon.rings.forEach { ring ->
                val run = mutableListOf<Offset>()
                fun flushRun() {
                    if (run.size >= 3) {
                        val path = Path().apply {
                            moveTo(run.first().x, run.first().y)
                            run.drop(1).forEach { lineTo(it.x, it.y) }
                            close()
                        }
                        drawPath(path, fill)
                        drawPath(path, outline, style = Stroke(width = 0.45.dp.toPx()))
                    }
                    run.clear()
                }
                ring.forEach { point ->
                    val current = map(point)
                    val previous = run.lastOrNull()
                    val visible = current.isNearCanvas(size, margin)
                    val connected = previous == null || previous.distanceTo(current) < segmentLimit
                    if (visible && connected) {
                        run += current
                    } else {
                        flushRun()
                        if (visible) run += current
                    }
                }
                flushRun()
            }
        }
    }
    skyStars.asReversed().forEach { star ->
        if (star.magnitude > starMagnitudeLimit) return@forEach
        val point = map(star.point)
        if (!point.isNearCanvas(size, 28.dp.toPx())) return@forEach
        val brightness = ((starMagnitudeLimit + 0.75f - star.magnitude) / (starMagnitudeLimit + 1.4f)).coerceIn(0.08f, 1f)
        drawCircle(
            color = starColor(star.bv).copy(alpha = 0.46f + brightness * 0.48f),
            radius = (0.7f + brightness * 2.8f).dp.toPx(),
            center = point,
        )
    }
    if (showDeepSkyObjects) {
        drawReferenceDeepSkyObjects(
            objects = deepSkyObjects,
            map = { point -> map(point) },
            magnitudeLimit = deepSkyMagnitudeLimit,
            referenceZoom = referenceZoom,
            lang = AppLocale.resolvedLanguageTag,
        )
    }
    val patternPoints = pattern.stars.map { star ->
        map(SkyPoint(star.raHours * 15f, star.decDegrees))
    }
    pattern.edges.forEach { (a, b) ->
        val start = patternPoints.getOrNull(a) ?: return@forEach
        val end = patternPoints.getOrNull(b) ?: return@forEach
        val endpoints = trimmedLineEndpoints(start, end, 8.dp.toPx()) ?: return@forEach
        drawLine(
            color = Color(0xFF8FD8FF),
            start = endpoints.first,
            end = endpoints.second,
            strokeWidth = 2.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
    patternPoints.forEach { point ->
        drawCircle(Color(0xFF8FD8FF), 5.dp.toPx(), point, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.38f), 2.dp.toPx(), point)
    }
    if (showStarNames) {
        val catalogNamePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 218, 232, 255)
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
            textSize = (9.dp.toPx() * referenceZoom.coerceIn(1f, 1.45f)).coerceAtMost(13.dp.toPx())
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
        val labelLang = AppLocale.resolvedLanguageTag
        skyStars.forEach { star ->
            val starLabel = star.displayName(labelLang)
            if (star.magnitude > starMagnitudeLimit || starLabel.isBlank()) return@forEach
            val point = map(star.point)
            if (!point.isNearCanvas(size, 36.dp.toPx())) return@forEach
            drawContext.canvas.nativeCanvas.drawText(starLabel, point.x + 5.dp.toPx(), point.y - 4.dp.toPx(), catalogNamePaint)
        }
        val anchorNamePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(235, 235, 248, 255)
            textAlign = android.graphics.Paint.Align.LEFT
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textSize = 10.5.dp.toPx()
            setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
        }
        pattern.stars.forEachIndexed { index, star ->
            if (star.name.isNotBlank()) {
                val point = patternPoints.getOrNull(index) ?: return@forEachIndexed
                drawContext.canvas.nativeCanvas.drawText(star.name, point.x + 7.dp.toPx(), point.y - 5.dp.toPx(), anchorNamePaint)
            }
        }
    }
    val center = Offset(
        x = patternPoints.sumOf { it.x.toDouble() }.toFloat() / patternPoints.size,
        y = patternPoints.sumOf { it.y.toDouble() }.toFloat() / patternPoints.size,
    )
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(230, 255, 255, 255)
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        textSize = 16.dp.toPx()
        setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText(pattern.localizedName(), center.x, center.y - 14.dp.toPx(), paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReferenceDeepSkyObjects(
    objects: List<DeepSkyObject>,
    map: (SkyPoint) -> Offset,
    magnitudeLimit: Float,
    referenceZoom: Float,
    lang: String = "en",
) {
    if (objects.isEmpty()) return
    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(205, 215, 234, 255)
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
        textSize = 9.dp.toPx()
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    val margin = 32.dp.toPx()
    val labelMagnitudeLimit = if (referenceZoom >= 2.4f) 15f else 11f
    objects.asReversed().forEach { deepSky ->
        val magnitude = deepSky.magnitude ?: return@forEach
        if (magnitude > magnitudeLimit) return@forEach
        val point = map(deepSky.point)
        if (!point.isNearCanvas(size, margin)) return@forEach
        val brightness = ((20f - magnitude) / 20f).coerceIn(0.07f, 1f)
        val markerRadius = (1.7f + brightness * 4.8f).dp.toPx()
        val color = deepSkyColor(deepSky.type).copy(alpha = 0.48f + brightness * 0.38f)
        drawDeepSkyMarker(point, markerRadius, color, deepSky.type)
        if (magnitude <= labelMagnitudeLimit) {
            drawContext.canvas.nativeCanvas.drawText(
                deepSky.properDisplayName(lang),
                point.x + markerRadius + 4.dp.toPx(),
                point.y - 3.dp.toPx(),
                labelPaint,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOverlay(
    overlay: AnnotationOverlay,
    viewport: ImageViewport,
    selected: Boolean,
    editing: Boolean,
    showConstellationAnchors: Boolean,
    pulse: Float? = null,
) {
    // Wird das Overlay gerade gezogen, pink pulsieren lassen (sonst die eingestellte Farbe).
    val overlayColor = if (pulse != null) {
        Color(0xFFFF4FD8).copy(alpha = (0.6f + 0.4f * pulse).coerceIn(0.05f, 1f))
    } else when (overlay.kind) {
        OverlayKind.Constellation -> Color(overlay.colorArgb).copy(alpha = overlay.opacity.coerceIn(0.05f, 1f))
        OverlayKind.Text -> Color(overlay.colorArgb).copy(alpha = overlay.opacity.coerceIn(0.05f, 1f))
        else -> Color(overlay.colorArgb).copy(alpha = overlay.opacity.coerceIn(0.05f, 1f))
    }
    val strokeWidth = imageStrokeToScreen(overlay.strokeWidth, viewport)

    when (overlay.kind) {
        OverlayKind.Constellation -> {
            val points = overlay.constellationImagePoints().map { viewport.imageToScreen(it) }
            if (editing) {
                drawConstellationGlow(overlay, viewport, overlayColor, showConstellationAnchors)
            }
            val imageMinDim = min(viewport.imageWidth, viewport.imageHeight).toFloat()
            val anchorRadiusImage = OverlayGeometry.constellationAnchorRadius(overlay, imageMinDim)
            val anchorRadius = anchorRadiusImage * viewport.scale
            // Trim-Lücke in Bild-px berechnen (gleiche Formel wie Export) und in Bildschirm-px umrechnen.
            val lineTrimGap = OverlayGeometry.constellationLineTrimGap(
                anchorRadiusImage,
                OverlayGeometry.strokeWidth(overlay.strokeWidth),
                showConstellationAnchors,
                overlay.anchorRadiusRatio,
            ) * viewport.scale
            fun drawEdge(edgePts: List<Offset>, edgeIndex: Int) {
                if (edgePts.size < 2) return
                when (overlay.lineStyle) {
                    OverlayLineStyle.Solid, OverlayLineStyle.Dashed -> {
                        // Als EINE durchgezogene Polylinie zeichnen (ein Path) statt Einzelsegmente mit
                        // Round-Cap — sonst „perlt" eine durchgezogene gekrümmte Linie optisch zu Punkten.
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(edgePts[0].x, edgePts[0].y)
                        for (k in 1 until edgePts.size) path.lineTo(edgePts[k].x, edgePts[k].y)
                        drawPath(
                            path = path,
                            color = overlayColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round,
                                pathEffect = if (overlay.lineStyle == OverlayLineStyle.Dashed) {
                                    PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 4f, strokeWidth * 2.4f))
                                } else {
                                    null
                                },
                            ),
                        )
                    }
                    OverlayLineStyle.Dotted -> drawDottedPolyline(edgePts, overlayColor, strokeWidth)
                    else -> StrokeRenderer.draw(
                        canvas = drawContext.canvas.nativeCanvas,
                        points = edgePts,
                        baseWidth = strokeWidth,
                        colorArgb = overlayColor.toArgbInt(),
                        style = overlay.lineStyle,
                        seed = overlay.id * 1000L + edgeIndex,
                        taper = true,
                    )
                }
            }
            val edgePolylines = overlay.edgePolylines
            if (edgePolylines != null) {
                // Gekrümmte Kanten (Fisheye/Panorama): vorprojizierte Großkreis-Polylinien. Trimmen
                // ZUERST in Bild-px (beide Enden sind echte Sternanker; Trimmen und die reine
                // Verschiebung+Uniform-Skalierung von imageToScreen vertauschen -- affine Abbildung
                // erhält Streckenverhältnisse), DANACH an der Bild-Naht aufteilen
                // (OverlayGeometry.splitPolylineAtSeam) -- die neuen Schnittpunkte sind keine Anker
                // und dürfen nicht nochmal angeschnitten werden.
                edgePolylines.forEachIndexed { edgeIndex, poly ->
                    if (poly.size < 2) return@forEachIndexed
                    val trimmed = trimPolylineEnds(poly, lineTrimGap / viewport.scale)
                    // GOLDEN-RUECKBAU 2026-09-03: Naht-Aufteilung NUR bei periodischer Projektion
                    // (overlay.seamAware, s. dessen KDoc). Bei einem normalen Einzelfoto verschob
                    // splitPolylineAtSeam Kantenstuecke ausserhalb [0,imageWidth] um +/-imageWidth
                    // zurueck INS Bild -- Golden clippte sie schlicht weg.
                    val pieces = if (overlay.seamAware) {
                        OverlayGeometry.splitPolylineAtSeam(trimmed, viewport.imageWidth)
                    } else {
                        listOf(trimmed)
                    }
                    pieces.forEach { piece ->
                        if (piece.size >= 2) drawEdge(piece.map { viewport.imageToScreen(it) }, edgeIndex)
                    }
                }
            } else {
                overlay.constellation?.edges.orEmpty().forEachIndexed { edgeIndex, (a, b) ->
                    val endpoints = trimmedLineEndpoints(points[a], points[b], lineTrimGap) ?: return@forEachIndexed
                    drawEdge(listOf(endpoints.first, endpoints.second), edgeIndex)
                }
            }
            if (overlay.showName) {
                drawConstellationOverlayName(overlay, viewport, overlayColor)
            }
            if (showConstellationAnchors) {
                // Bild-px-Formel (wie Export) -> Ankerring im Editor identisch zum Export,
                // unabhängig von der Zoomstufe.
                val anchorStroke = (OverlayGeometry.anchorStrokeWidth(anchorRadiusImage) * viewport.scale)
                    .coerceAtLeast(1f)
                val anchorAlpha = if (editing || selected) 0.82f else 0.56f
                points.forEach {
                    drawCircle(
                        color = overlayColor.copy(alpha = anchorAlpha),
                        radius = anchorRadius,
                        center = it,
                        style = Stroke(width = anchorStroke),
                    )
                    // Mittelpunkt-Markierung nur als Editier-Hilfe (im Export nicht vorhanden).
                    if (editing || selected) {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.32f),
                            radius = (anchorRadius * 0.22f).coerceAtLeast(1.2f),
                            center = it,
                        )
                    }
                }
            }
        }
        OverlayKind.Ellipse -> {
            val center = viewport.imageToScreen(overlay.center)
            val screenSize = Size(overlay.size.width * viewport.scale, overlay.size.height * viewport.scale)
            // markerRing=false: ringlos. Sternmarker (Star-Ebene) -> kleiner gefüllter Punkt, damit
            // auch namenlose "alle bis mag"-Sterne sichtbar bleiben; sonst (z.B. ringlose Form) nichts.
            if (overlay.reticle != null) {
                drawReticleOverlay(overlay, center, screenSize, overlayColor, strokeWidth)
            } else if (overlay.markerRing) {
                if (overlay.filled) {
                    withTransform({ rotate(overlay.rotationDegrees, center) }) {
                        drawOval(
                            color = overlayColor,
                            topLeft = Offset(center.x - screenSize.width / 2f, center.y - screenSize.height / 2f),
                            size = screenSize,
                        )
                    }
                }
                if (editing) {
                    drawStyledOval(
                        center,
                        screenSize,
                        overlay.rotationDegrees,
                        overlayColor.copy(alpha = (overlayColor.alpha * 0.28f).coerceIn(0f, 1f)),
                        strokeWidth + 10.dp.toPx(),
                        overlay.lineStyle,
                        overlay.id,
                    )
                }
                drawStyledOval(center, screenSize, overlay.rotationDegrees, overlayColor, strokeWidth, overlay.lineStyle, overlay.id)
            } else if (overlay.layer == AnnotationLayer.Star && overlay.markerDot) {
                val dotRadius = (min(screenSize.width, screenSize.height) * 0.34f).coerceAtLeast(1.5f)
                drawCircle(color = overlayColor, radius = dotRadius, center = center)
            }
            if (overlay.showName && overlay.text.isNotBlank()) {
                drawShapeNameLabel(overlay, center, screenSize, overlayColor, viewport)
            }
        }
        OverlayKind.Rectangle -> {
            val center = viewport.imageToScreen(overlay.center)
            val screenSize = Size(overlay.size.width * viewport.scale, overlay.size.height * viewport.scale)
            if (overlay.filled) {
                withTransform({ rotate(overlay.rotationDegrees, center) }) {
                    drawRect(
                        color = overlayColor,
                        topLeft = Offset(center.x - screenSize.width / 2f, center.y - screenSize.height / 2f),
                        size = screenSize,
                    )
                }
            }
            if (editing) {
                drawStyledRect(
                    center,
                    screenSize,
                    overlay.rotationDegrees,
                    overlayColor.copy(alpha = (overlayColor.alpha * 0.28f).coerceIn(0f, 1f)),
                    strokeWidth + 10.dp.toPx(),
                    overlay.lineStyle,
                    overlay.id,
                )
            }
            drawStyledRect(center, screenSize, overlay.rotationDegrees, overlayColor, strokeWidth, overlay.lineStyle, overlay.id)
            if (overlay.showName && overlay.text.isNotBlank()) {
                drawShapeNameLabel(overlay, center, screenSize, overlayColor, viewport)
            }
        }
        OverlayKind.Freehand -> {
            val center = viewport.imageToScreen(overlay.center)
            val screenSize = Size(overlay.size.width * viewport.scale, overlay.size.height * viewport.scale)
            // denormalizedFreehandPoints liefert bereits gedrehte, absolute Bild-SEGMENTE -> nur noch
            // in Bildschirm-px umrechnen (kein withTransform/rotate nötig, anders als Ellipse/Rechteck).
            val screenSegments = OverlayGeometry.denormalizedFreehandPoints(overlay)
                .map { segment -> segment.map { viewport.imageToScreen(it) } }
            // Füllung nur bei GENAU einem Segment (typischer Fall: ohne Lücke durchgezeichnet) -- bei
            // mehreren Segmenten (Lücken durch den Radiergummi) ist eine geschlossene Fläche nicht
            // sinnvoll definiert, es wird dann nur gestrichelt/gezeichnet.
            val singleSegment = screenSegments.singleOrNull()?.takeIf { it.size >= 2 }
            val canFill = overlay.filled && singleSegment != null
            if (canFill && singleSegment != null) {
                val fillPath = Path().apply {
                    moveTo(singleSegment[0].x, singleSegment[0].y)
                    singleSegment.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(fillPath, color = overlayColor)
            }
            fun strokeFreehandSegment(points: List<Offset>, strokeColor: Color, width: Float) {
                // Geschlossen (Start=Ende) nur bei filled -- passend zum taper-Flag/Export.
                val strokePoints = if (canFill) points + points.first() else points
                if (overlay.lineStyle != OverlayLineStyle.Solid && overlay.lineStyle != OverlayLineStyle.Dashed) {
                    drawStyledPolyline(strokePoints, strokeColor, width, overlay.lineStyle, overlay.id, taper = !canFill)
                } else {
                    val strokePath = Path().apply {
                        moveTo(strokePoints[0].x, strokePoints[0].y)
                        strokePoints.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        strokePath,
                        color = strokeColor,
                        // Rund statt Standard-Butt/Miter (Nutzeranforderung): eine Freihand-Linie ist
                        // kein geometrisches Rechteck/Kreis mit gewollt scharfen Ecken -- ohne Round
                        // wirken Strichenden/Kurven-Knicke kantig, WYSIWYG-Bruch zur Live-Vorschau
                        // (drawStyledPolyline rundet bereits).
                        style = Stroke(
                            width = width,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = pathEffectFor(overlay.lineStyle, width),
                        ),
                    )
                }
            }
            screenSegments.forEach { points ->
                if (points.size >= 2) {
                    if (editing) {
                        strokeFreehandSegment(
                            points,
                            overlayColor.copy(alpha = (overlayColor.alpha * 0.28f).coerceIn(0f, 1f)),
                            strokeWidth + 10.dp.toPx(),
                        )
                    }
                    strokeFreehandSegment(points, overlayColor, strokeWidth)
                }
            }
            if (overlay.showName && overlay.text.isNotBlank()) {
                drawShapeNameLabel(overlay, center, screenSize, overlayColor, viewport)
            }
        }
        OverlayKind.Text -> {
            val center = viewport.imageToScreen(overlay.center)
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(overlay.rotationDegrees, center.x, center.y)
            // Schatten-Alpha an die Deckkraft koppeln, sonst bleibt der schwarze Schatten voll deckend
            // und der Text wirkt bei niedriger Opazität DUNKEL statt durchsichtig.
            val textShadowAlpha = (overlay.opacity.coerceIn(0f, 1f) * 255f).roundToInt()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = overlayColor.toArgbInt()
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = overlay.font.toTypeface(overlay.textBold)
                textSize = OverlayGeometry.textOverlaySize(overlay) * viewport.scale
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.argb(textShadowAlpha, 0, 0, 0))
            }
            // Mehrzeilig: Zeilen an \n trennen, vertikal zentriert um die Mitte zeichnen.
            val lines = overlay.text.split("\n")
            val lineHeight = paint.fontSpacing
            val firstBaseline = center.y - (paint.ascent() + paint.descent()) / 2f -
                (lines.size - 1) * lineHeight / 2f
            lines.forEachIndexed { i, line ->
                drawContext.canvas.nativeCanvas.drawText(line, center.x, firstBaseline + i * lineHeight, paint)
            }
            drawContext.canvas.nativeCanvas.restore()
        }
    }

    if (editing) drawEditingControls(overlay, viewport)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShapeNameLabel(
    overlay: AnnotationOverlay,
    center: Offset,
    screenSize: Size,
    overlayColor: Color,
    viewport: ImageViewport,
) {
    // Sternnamen (Star-Ebene) folgen dem Deckkraft-Regler; andere Marker-Namen bleiben voll deckend.
    // Punkt 6 (Nutzer-Vorgabe 2026-08-30): overlay.nameOpacityLinked koppelt die Beschriftung
    // zusätzlich an die Objekt-Deckkraft, unabhängig von der Ebene (bisher NUR für Star fest verdrahtet).
    val nameAlpha = if (overlay.layer == AnnotationLayer.Star || overlay.nameOpacityLinked) overlay.opacity.coerceIn(0f, 1f) else 1f
    // Schatten-Alpha an die Namens-Deckkraft koppeln (sonst wirkt der Name bei niedriger Deckkraft dunkel).
    val shadowAlpha = (nameAlpha * 255f).roundToInt()
    // Callout (Führungslinie + rotierender Name): bisher nur automatisch platzierte DSO-Marker,
    // jetzt auch nutzerplatzierte Ellipsen/Rechtecke (labelAngleDeg/labelLeaderPx per Zieh-Griff
    // gesetzt statt algorithmisch). Sternnamen (layer==Star) bleiben beim alten Fallback (fixer
    // Text ohne Linie) -- unveraendertes Verhalten dort.
    val isCallout = overlay.layer == AnnotationLayer.DeepSky ||
        (
            overlay.layer == null &&
                (overlay.kind == OverlayKind.Ellipse || overlay.kind == OverlayKind.Rectangle || overlay.kind == OverlayKind.Freehand)
            )
    // Nur im Callout-Zweig eine eigene Namensfarbe -- der alte Fallback-Zweig (Sternnamen) behält
    // die Form-/Marker-Farbe wie bisher.
    val textColor = if (isCallout) Color(overlay.nameColorArgb) else overlayColor
    // DSO-/Sternnamen bleiben fett (Altverhalten); nutzerplatzierte Formen respektieren den neuen
    // Fett/Duenn-Regler (textBold), da dort erstmals eine Wahlmoeglichkeit dafuer existiert.
    val useBold = if (overlay.layer == null) overlay.textBold else true
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.copy(alpha = nameAlpha).toArgbInt()
        textAlign = android.graphics.Paint.Align.LEFT
        typeface = overlay.font.toTypeface(bold = useBold)
        textSize = OverlayGeometry.markerNameTextSize(overlay) * viewport.scale
        setShadowLayer(5f, 0f, 0f, android.graphics.Color.argb(shadowAlpha, 0, 0, 0))
    }
    if (isCallout) {
        // Callout: kurze Führungslinie vom Kreisrand zum Namen (mit Lücke), Name rotiert um das Objekt.
        val layout = OverlayGeometry.markerLabelLayout(overlay)
        val s = viewport.scale
        val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = overlayColor.copy(alpha = nameAlpha).toArgbInt()
            strokeWidth = (OverlayGeometry.strokeWidth(overlay.strokeWidth) * s * 0.6f).coerceAtLeast(1f)
            style = android.graphics.Paint.Style.STROKE
        }
        drawContext.canvas.nativeCanvas.drawLine(
            center.x + layout.edge.x * s,
            center.y + layout.edge.y * s,
            center.x + layout.lineEnd.x * s,
            center.y + layout.lineEnd.y * s,
            linePaint,
        )
        paint.textAlign = when (layout.align) {
            OverlayGeometry.LabelAlign.Left -> android.graphics.Paint.Align.LEFT
            OverlayGeometry.LabelAlign.Right -> android.graphics.Paint.Align.RIGHT
            OverlayGeometry.LabelAlign.Center -> android.graphics.Paint.Align.CENTER
        }
        val nameX = center.x + layout.nameAnchor.x * s
        val nameY = center.y + layout.nameAnchor.y * s + paint.textSize * 0.35f
        drawContext.canvas.nativeCanvas.drawText(overlay.text, nameX, nameY, paint)
    } else {
        val nameX = center.x + screenSize.width / 2f + OverlayGeometry.MARKER_NAME_GAP * viewport.scale
        val nameY = center.y + paint.textSize * 0.35f
        drawContext.canvas.nativeCanvas.drawText(overlay.text, nameX, nameY, paint)
    }
}

private fun imageStrokeToScreen(strokeWidth: Float, viewport: ImageViewport): Float {
    // Bild-px-Strichbreite (wie Export) * viewport.scale -> WYSIWYG. Kleiner Bildschirm-Boden
    // gegen Sub-Pixel-Verschwinden beim extremen Rauszoomen.
    return (OverlayGeometry.strokeWidth(strokeWidth) * viewport.scale).coerceAtLeast(1f)
}

// „Markieren"-Fadenkreuz im Editor zeichnen (Screen-Koordinaten, um die Mitte gedreht).
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReticleOverlay(
    overlay: AnnotationOverlay,
    center: Offset,
    screenSize: Size,
    color: Color,
    strokeWidth: Float,
) {
    // Fadenkreuz bleibt quadratisch -> alle Schenkel gleich lang, kein Stauchen bei Größenänderung.
    val half = min(screenSize.width, screenSize.height) / 2f
    val hw = half
    val hh = half
    val gapX = half * 0.32f
    val gapY = half * 0.32f
    val rad = overlay.rotationDegrees * (PI.toFloat() / 180f)
    fun p(dx: Float, dy: Float): Offset = center + rotateOffset(Offset(dx, dy), rad)
    fun seg(ax: Float, ay: Float, bx: Float, by: Float) {
        drawLine(color = color, start = p(ax, ay), end = p(bx, by), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
    when (overlay.reticle) {
        ReticleStyle.OpenCrosshair -> {
            seg(-hw, 0f, -gapX, 0f)
            seg(gapX, 0f, hw, 0f)
            seg(0f, -hh, 0f, -gapY)
            seg(0f, gapY, 0f, hh)
        }
        ReticleStyle.CometMarker -> {
            seg(gapX, 0f, hw, 0f)
            seg(0f, gapY, 0f, hh)
        }
        null -> {}
    }
}

/** Kürzt eine Polylinie an BEIDEN Enden um [gap] (Bildschirm-px) – für die Lücke zum Stern/Ring. */
private fun trimPolylineEnds(points: List<Offset>, gap: Float): List<Offset> {
    if (gap <= 0f || points.size < 2) return points
    val fromStart = trimPolylineStart(points, gap)
    return trimPolylineStart(fromStart.reversed(), gap).reversed()
}

private fun trimPolylineStart(points: List<Offset>, gap: Float): List<Offset> {
    if (points.size < 2) return points
    var remaining = gap
    for (i in 0 until points.size - 1) {
        val seg = points[i + 1] - points[i]
        val len = seg.getDistance()
        if (len <= 0f) continue
        if (len >= remaining) {
            val newFirst = points[i] + seg * (remaining / len)
            val rest = ArrayList<Offset>(points.size - i)
            rest += newFirst
            rest.addAll(points.subList(i + 1, points.size))
            return rest
        }
        remaining -= len
    }
    return listOf(points.last())
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellationGlow(
    overlay: AnnotationOverlay,
    viewport: ImageViewport,
    color: Color,
    anchorsVisible: Boolean,
) {
    // Bild-px statt Bildschirm-px (anders als früher): erst trimmen/an der Naht aufteilen, dann PRO
    // Teilstück in Bildschirmkoordinaten umrechnen -- sonst würde eine über die 360°-Naht laufende
    // Kante hier weiterhin als eine lange Querlinie über den ganzen Bildschirm gezeichnet, selbst wenn
    // der Haupt-Render (s. drawEdge oben) sie schon korrekt aufteilt.
    val imagePoints = overlay.constellationImagePoints()
    val glowStrokeWidth = imageStrokeToScreen(overlay.strokeWidth, viewport) + 11.dp.toPx()
    // Gleiche Bild-px-Geometrie wie der Haupt-Render (Editier-Hilfe).
    val imageMinDim = min(viewport.imageWidth, viewport.imageHeight).toFloat()
    val anchorRadiusImage = OverlayGeometry.constellationAnchorRadius(overlay, imageMinDim)
    val glowTrimGapImage = OverlayGeometry.constellationLineTrimGap(
        anchorRadiusImage,
        OverlayGeometry.strokeWidth(overlay.strokeWidth),
        anchorsVisible,
        overlay.anchorRadiusRatio,
    ) + 5.5.dp.toPx() / viewport.scale
    overlay.constellation?.edges.orEmpty().forEach { (a, b) ->
        val endpoints = trimmedLineEndpoints(imagePoints[a], imagePoints[b], glowTrimGapImage) ?: return@forEach
        // GOLDEN-RUECKBAU 2026-09-03: s. drawOverlay -- Naht-Aufteilung nur bei periodischer Projektion.
        val glowPieces = if (overlay.seamAware) {
            OverlayGeometry.splitPolylineAtSeam(listOf(endpoints.first, endpoints.second), viewport.imageWidth)
        } else {
            listOf(listOf(endpoints.first, endpoints.second))
        }
        glowPieces.forEach { piece ->
            if (piece.size < 2) return@forEach
            drawLine(
                color = color.copy(alpha = (color.alpha * 0.24f).coerceIn(0f, 1f)),
                start = viewport.imageToScreen(piece[0]),
                end = viewport.imageToScreen(piece[1]),
                strokeWidth = glowStrokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Bounding-Box (Bild-px, [l,t,r,b]) des tatsächlich gezeichneten Sternbild-Namens -- exakt dieselbe
 * Anker-/Positionsherleitung wie [drawConstellationOverlayName] selbst, als Hindernis für die
 * DSO-Namensplatzierung in syncDeepSkyLayer (Nutzerbefund 2026-08-27: "Kollisionsmechanik funktioniert
 * auf DSO-Objekte, aber nicht auf Sternbildnamen" -- Ursache war die vorher genutzte
 * OverlayGeometry.nonCalloutNameLabelBoundingBoxOrNull, die eine feste Position rechts vom Marker
 * annimmt, für Sternbilder aber nie zutrifft, da center/size dort die Bounding-Box des GANZEN
 * Linienmusters ist, nicht die Namensposition). null, wenn kein Name gezeichnet wird.
 */
private fun constellationNameObstacleBox(overlay: AnnotationOverlay, imageWidth: Float, imageHeight: Float): FloatArray? {
    val pattern = overlay.constellation ?: return null
    if (!overlay.showName) return null
    val anchor = overlay.constellationNameAnchor(imageWidth, imageHeight) ?: return null
    val nameImg = OverlayGeometry.constellationNameTextSize(overlay)
    val clearImg = OverlayGeometry.constellationAnchorRadius(overlay, min(imageWidth, imageHeight)) +
        OverlayGeometry.strokeWidth(overlay.strokeWidth) + OverlayGeometry.CONSTELLATION_NAME_GAP
    val labelX = anchor.x.coerceIn(nameImg, (imageWidth - nameImg).coerceAtLeast(nameImg))
    var baselineY = anchor.y - clearImg
    if (baselineY - nameImg < 0f) baselineY = anchor.y + clearImg + nameImg
    baselineY = baselineY.coerceIn(nameImg, (imageHeight - nameImg * 0.3f).coerceAtLeast(nameImg))
    val text = pattern.localizedName()
    val textW = text.length * nameImg * 0.55f
    val textH = nameImg * 1.1f
    val l = labelX - textW / 2f
    return floatArrayOf(l, baselineY - textH, l + textW, baselineY + textH * 0.2f)
}

// Bildschirm-Trefferprüfung für den Sternbild-Namen (Ziel: EditorGestureTarget.ConstellationNameHandle)
// -- dieselbe Box wie constellationNameObstacleBox, nur nach Bildschirm-px umgerechnet + mit etwas
// Toleranz (padding), passend zu den übrigen Zieh-Griffen (handleHitRadius).
private fun constellationNameHitTest(
    overlay: AnnotationOverlay,
    screenPoint: Offset,
    viewport: ImageViewport,
    padding: Float,
): Boolean {
    val box = constellationNameObstacleBox(overlay, viewport.imageWidth.toFloat(), viewport.imageHeight.toFloat()) ?: return false
    val corner1 = viewport.imageToScreen(Offset(box[0], box[1]))
    val corner2 = viewport.imageToScreen(Offset(box[2], box[3]))
    val left = min(corner1.x, corner2.x) - padding
    val right = max(corner1.x, corner2.x) + padding
    val top = min(corner1.y, corner2.y) - padding
    val bottom = max(corner1.y, corner2.y) + padding
    return screenPoint.x in left..right && screenPoint.y in top..bottom
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawConstellationOverlayName(
    overlay: AnnotationOverlay,
    viewport: ImageViewport,
    color: Color,
) {
    val pattern = overlay.constellation ?: return
    val imgW = viewport.imageWidth.toFloat()
    val imgH = viewport.imageHeight.toFloat()
    // Anker nur aus den im Bild liegenden Sternen -> Name am sichtbaren Körper; gefaltete/off-field
    // Sternbilder bekommen GAR KEINEN Namen mehr (behebt den überlappenden Klumpen am linken Rand).
    val anchor = overlay.constellationNameAnchor(imgW, imgH) ?: return
    val nameImg = OverlayGeometry.constellationNameTextSize(overlay)
    val imageMinDim = min(viewport.imageWidth, viewport.imageHeight).toFloat()
    // Abstand zu Ankern/Linien: Ankerradius + Strichbreite + Grundabstand (Bild-px).
    val clearImg = OverlayGeometry.constellationAnchorRadius(overlay, imageMinDim) +
        OverlayGeometry.strokeWidth(overlay.strokeWidth) + OverlayGeometry.CONSTELLATION_NAME_GAP
    // Bevorzugt oberhalb des obersten sichtbaren Ankers, sonst darunter; X/Y zur Sicherheit ins Bild.
    val labelX = anchor.x.coerceIn(nameImg, (imgW - nameImg).coerceAtLeast(nameImg))
    var baselineY = anchor.y - clearImg
    if (baselineY - nameImg < 0f) {
        baselineY = anchor.y + clearImg + nameImg
    }
    baselineY = baselineY.coerceIn(nameImg, (imgH - nameImg * 0.3f).coerceAtLeast(nameImg))
    val screen = viewport.imageToScreen(Offset(labelX, baselineY))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        // Beschriftung immer voll deckend (Objekt-Opazität beeinflusst den Namen NICHT).
        this.color = color.copy(alpha = 1f).toArgbInt()
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = overlay.font.toTypeface(bold = true)
        textSize = nameImg * viewport.scale
        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
    }
    drawContext.canvas.nativeCanvas.drawText(pattern.localizedName(), screen.x, screen.y, paint)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEditingControls(
    overlay: AnnotationOverlay,
    viewport: ImageViewport,
) {
    if (overlay.kind != OverlayKind.Constellation) {
        val corners = overlaySelectionCorners(overlay, viewport)
        val rotationHandle = overlayRotationHandleCenter(overlay, viewport, 38.dp.toPx())
        val topCenter = (corners[0] + corners[1]) / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.58f),
            start = topCenter,
            end = rotationHandle,
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = 5.8.dp.toPx(),
            center = rotationHandle,
        )
        drawCircle(
            color = Color(0xFF536697),
            radius = 5.8.dp.toPx(),
            center = rotationHandle,
            style = Stroke(width = 1.6.dp.toPx()),
        )
        corners.forEach { corner ->
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = 4.5.dp.toPx(),
                center = corner,
            )
            drawCircle(
                color = Color(0xFF536697),
                radius = 4.5.dp.toPx(),
                center = corner,
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        // Zieh-Griff am Ende der Namens-Führungslinie (die Linie selbst zeichnet drawShapeNameLabel
        // bereits, s. dort -- hier nur der Handle-Punkt, gleicher Stil wie Resize-/Rotate-Handles).
        val isShapeLikeOverlay = overlay.kind == OverlayKind.Ellipse ||
            overlay.kind == OverlayKind.Rectangle ||
            overlay.kind == OverlayKind.Freehand
        if (isShapeLikeOverlay && overlay.showName) {
            val labelHandle = labelHandleScreenPoint(overlay, viewport)
            drawCircle(
                color = Color.White.copy(alpha = 0.90f),
                radius = 5.8.dp.toPx(),
                center = labelHandle,
            )
            drawCircle(
                color = Color(0xFF536697),
                radius = 5.8.dp.toPx(),
                center = labelHandle,
                style = Stroke(width = 1.6.dp.toPx()),
            )
        }
    }
    val canvasSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
    if (overlay.kind == OverlayKind.Constellation) {
        val referenceCenter = fixedReferenceHandleCenter(canvasSize, 42.dp.toPx(), 56.dp.toPx())
        drawCircle(
            color = Color(0xFF5B74B7).copy(alpha = 0.96f),
            radius = 14.dp.toPx(),
            center = referenceCenter,
        )
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            textSize = 19.dp.toPx()
        }
        val baseline = referenceCenter.y - (paint.ascent() + paint.descent()) / 2f
        drawContext.canvas.nativeCanvas.drawText("?", referenceCenter.x, baseline, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyledOval(
    center: Offset,
    size: Size,
    rotationDegrees: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: OverlayLineStyle,
    seed: Long,
) {
    if (lineStyle != OverlayLineStyle.Solid && lineStyle != OverlayLineStyle.Dashed) {
        drawStyledPolyline(ovalPolyline(center, size, rotationDegrees), color, strokeWidth, lineStyle, seed)
        return
    }
    withTransform({ rotate(rotationDegrees, center) }) {
        drawOval(
            color = color,
            topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f),
            size = size,
            style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle, strokeWidth)),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyledRect(
    center: Offset,
    size: Size,
    rotationDegrees: Float,
    color: Color,
    strokeWidth: Float,
    lineStyle: OverlayLineStyle,
    seed: Long,
) {
    if (lineStyle != OverlayLineStyle.Solid && lineStyle != OverlayLineStyle.Dashed) {
        drawStyledPolyline(rectPolyline(center, size, rotationDegrees), color, strokeWidth, lineStyle, seed)
        return
    }
    withTransform({ rotate(rotationDegrees, center) }) {
        drawRect(
            color = color,
            topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f),
            size = size,
            style = Stroke(width = strokeWidth, pathEffect = pathEffectFor(lineStyle, strokeWidth)),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStyledPolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    lineStyle: OverlayLineStyle,
    seed: Long,
    // false (Standard) = geschlossene Form (Ellipse-/Rechteck-Kontur, Aufrufer schließt den
    // Punktzug bereits selbst -- s. ovalPolyline/rectPolyline). true = offene Linie mit spitz
    // auslaufenden Enden (z. B. offen gezeichnete Freihand-Umrisse, s. OverlayKind.Freehand).
    taper: Boolean = false,
) {
    if (points.size < 2) return
    when (lineStyle) {
        OverlayLineStyle.Dotted -> drawDottedPolyline(points, color, strokeWidth)
        OverlayLineStyle.Brush, OverlayLineStyle.HandDrawn, OverlayLineStyle.Marker, OverlayLineStyle.Chalk ->
            // Gleicher Render-Kern wie Export -> WYSIWYG.
            StrokeRenderer.draw(
                canvas = drawContext.canvas.nativeCanvas,
                points = points,
                baseWidth = strokeWidth,
                colorArgb = color.toArgbInt(),
                style = lineStyle,
                seed = seed,
                taper = taper,
            )
        else -> points.zipWithNext().forEach { (start, end) ->
            drawLine(color, start, end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDottedPolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    val radius = (strokeWidth * 0.48f).coerceAtLeast(1.4f)
    val spacing = (strokeWidth * 3.0f).coerceAtLeast(7f)
    var carry = 0f
    points.zipWithNext().forEach { (start, end) ->
        val segment = end - start
        val length = segment.getDistance()
        if (length <= 0.001f) return@forEach
        var distance = spacing - carry
        while (distance <= length) {
            val t = distance / length
            drawCircle(
                color = color,
                radius = radius,
                center = start + segment * t,
            )
            distance += spacing
        }
        carry = (length - (distance - spacing)).coerceIn(0f, spacing)
    }
}

private fun pathEffectFor(lineStyle: OverlayLineStyle, strokeWidth: Float): PathEffect? = when (lineStyle) {
    OverlayLineStyle.Solid -> null
    OverlayLineStyle.Dashed -> PathEffect.dashPathEffect(floatArrayOf(strokeWidth * 4f, strokeWidth * 2.4f), 0f)
    OverlayLineStyle.Dotted -> null
    OverlayLineStyle.HandDrawn, OverlayLineStyle.Brush, OverlayLineStyle.Marker, OverlayLineStyle.Chalk -> null
}

private fun ovalPolyline(center: Offset, size: Size, rotationDegrees: Float): List<Offset> {
    val radians = rotationDegrees * PI.toFloat() / 180f
    val segments = 96
    return (0..segments).map { index ->
        val angle = index.toFloat() / segments * 2f * PI.toFloat()
        val local = Offset(cos(angle) * size.width / 2f, sin(angle) * size.height / 2f)
        center + rotateOffset(local, radians)
    }
}

private fun rectPolyline(center: Offset, size: Size, rotationDegrees: Float): List<Offset> {
    val radians = rotationDegrees * PI.toFloat() / 180f
    val halfWidth = size.width / 2f
    val halfHeight = size.height / 2f
    val corners = listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
        Offset(-halfWidth, -halfHeight),
    )
    val points = mutableListOf<Offset>()
    corners.zipWithNext().forEach { (start, end) ->
        val steps = 24
        for (step in 0 until steps) {
            val t = step / steps.toFloat()
            val local = start + (end - start) * t
            points += center + rotateOffset(local, radians)
        }
    }
    points += center + rotateOffset(corners.first(), radians)
    return points
}

private fun Color.toArgbInt(): Int {
    val alphaInt = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
    val redInt = (red.coerceIn(0f, 1f) * 255f).roundToInt()
    val greenInt = (green.coerceIn(0f, 1f) * 255f).roundToInt()
    val blueInt = (blue.coerceIn(0f, 1f) * 255f).roundToInt()
    return alphaInt shl 24 or (redInt shl 16) or (greenInt shl 8) or blueInt
}

private data class ImageViewport(
    val imageWidth: Int,
    val imageHeight: Int,
    val scale: Float,
    val offset: Offset,
) {
    fun imageToScreen(point: Offset): Offset = offset + point * scale
    fun screenToImage(point: Offset): Offset = (point - offset) / scale
    fun screenDeltaToImage(delta: Offset): Offset = delta / scale

    companion object {
        fun from(canvasSize: IntSize, imageWidth: Int, imageHeight: Int, zoom: Float, pan: Offset): ImageViewport {
            val canvasWidth = canvasSize.width.toFloat().coerceAtLeast(1f)
            val canvasHeight = canvasSize.height.toFloat().coerceAtLeast(1f)
            val baseScale = min(canvasWidth / imageWidth, canvasHeight / imageHeight)
            val baseOffset = Offset(
                x = (canvasWidth - imageWidth * baseScale) / 2f,
                y = (canvasHeight - imageHeight * baseScale) / 2f,
            )
            return ImageViewport(
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                scale = baseScale * zoom,
                offset = baseOffset + pan,
            )
        }
    }
}

private enum class ResizeCorner {
    TopLeft,
    TopRight,
    BottomRight,
    BottomLeft,
}

// Welche Art Geste ein SolveTile-/Overlay-Update auslöst -- an jeder Aufrufstelle bereits genau
// bekannt, daher explizit durchgereicht statt aus dem Ergebnis erraten. `onUpdateSolveTile` ignoriert
// dies inzwischen komplett (Kacheln sind rein ansichts-lokal, s. Plan Nachtrag 3); `onUpdateOverlay`
// braucht nur noch `AnchorPoint`, um verankerte Sternbild-Anker-Ziehgesten in Tiny Sky zu sperren --
// alle anderen Overlay-Bearbeitungen in Tiny Sky laufen inzwischen unbedingt durch (s.
// snapshotTinySkyGeometryIfActive), die übrigen Werte existieren nur noch für Aufrufer-Klarheit.
private enum class GeometryEditKind { Move, Resize, Rotate, AnchorPoint, FieldOnly, ConstellationName }

/**
 * Grund eines `syncConstellationLayer()`-Aufrufs (Nutzer-Auftrag 2026-09-03).
 *
 * Nötig, weil `AnnotationOverlay.anchorOverrides` ZWEI Bedeutungen in einem Feld trägt: die von
 * `createConstellationOverlays` final projizierten Sternpositionen UND (nach einer Ziehgeste) die
 * manuelle Korrektur des Nutzers. Aus `prior`/`fresh` allein lässt sich nicht entscheiden, welche der
 * beiden vorliegt -- insbesondere ist `prior.constellation?.stars == fresh.constellation?.stars` KEIN
 * gültiges Kriterium: dieselbe Sternliste bedeutet nicht dieselben Pixelpositionen, zwei Projektionen
 * können exakt dieselben Sterne behalten und trotzdem jeden einzelnen woanders abbilden. Auch
 * `edgePolylines == null` taugt nicht als Marker für "manuell bearbeitet", da
 * `createConstellationOverlays` diesen Zustand bei jeder nicht-Panorama-Lösung selbst erzeugt.
 * Deshalb wird der Grund vom Aufrufer mitgegeben statt abgeleitet.
 */
private enum class ConstellationSyncReason {
    /** Die aktive Lösung/Projektion hat sich geändert (Solve, Reprojektion, Projektionswechsel,
     *  Solution-Import, Kalibrierungs-Wiederherstellung, Feinjustierung) -> frische Anker gewinnen. */
    GeometryChanged,

    /** Reiner Darstellungs-Resync bei UNVERÄNDERTER Geometrie (Sprachwechsel, Sichtbarkeits- oder
     *  Namensschalter) -> vorhandene (ggf. manuell korrigierte) Anker bleiben erhalten. */
    VisualOnly,
}

// --- Kachel-Ausrichtung (Multi-Region-Solve) -----------------------------------------------------
// Mehrere kleine Bereiche werden plate-gesolved; aus ihren WCS werden verteilte Anker (Pixel↔Himmel)
// abgeleitet und per FisheyeRefiner.calibrateFromReferences zu einem globalen Fisheye-Fit kombiniert,
// mit dem die Feinausrichtung schon vorausgerichtet startet. Getrennt vom ASTAP-Mosaik-Aufnahmemodus.
// Suspect = gelöst, aber die Anker widersprechen den übrigen Kacheln massiv (stiller
// Nova-Falschtreffer, siehe TileConsistency) -> orange, vom Fit/Mosaik ausgeschlossen,
// beim nächsten "Kacheln lösen" automatisch neu versucht (zählt als offen).
// internal (nicht private): SolutionExport.kt (Export/Import-Feature) liest diese Kachel-Struktur
// von außerhalb dieser Datei -- reine Sichtbarkeits-Erweiterung, keine Verhaltensänderung.
internal enum class SolveTileStatus { Pending, Solving, Solved, Failed, Suspect }

// Maximale Zeilenzahl im Solve-Live-Ticker (ältere werden abgeschnitten).
private const val MAX_TICKER_LINES = 40

/**
 * Live-Ticker für das Lösen: kompakter, automatisch mitscrollender Verlauf (letzte ~4 Zeilen sichtbar).
 * Newest unten voll deckend, ältere gedimmt. Leer -> unsichtbar (Progressbar signalisiert Aktivität).
 */
/** Log des Live-Tickers als scrollbarer Dialog (Zeitstempel + Meldung des letzten Solve-Laufs). */
@Composable
private fun SolveLogDialog(lines: List<Pair<String, String>>, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
        title = { Text(stringResource(R.string.label_solver_log)) },
        text = {
            if (lines.isEmpty()) {
                Text(
                    stringResource(R.string.label_no_log_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    lines.forEach { (stamp, msg) ->
                        Text(
                            "$stamp  $msg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun SolveTicker(
    lines: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onShowMessage: (String) -> Unit = {},
) {
    if (lines.isEmpty()) return
    val scrollState = rememberScrollState()
    LaunchedEffect(lines.size) { scrollState.animateScrollTo(scrollState.maxValue) }
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 84.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            lines.forEachIndexed { i, (stamp, msg) ->
                val isNewest = i == lines.lastIndex
                val alpha = if (isNewest) 1f else 0.5f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(msg) { detectTapGestures(onLongPress = { onShowMessage(msg) }) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Zeitstempel bleibt fix stehen.
                    Text(
                        stamp,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.75f),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Nur die Info läuft (Marquee) und wird komplett ausgeschrieben (kein „…").
                    Text(
                        msg,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        overflow = if (isNewest) TextOverflow.Clip else TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        modifier = Modifier
                            .weight(1f)
                            .then(if (isNewest) Modifier.basicMarquee() else Modifier),
                    )
                }
            }
        }
    }
}

// Welcher Bildquelle (und damit welchem Pixel-/Projektionsraum) center/size/rotationDegrees UND wcs
// einer Kachel angehören. Native = wie bisher immer. TinySky = Kachel wurde gezeichnet/gelöst, während
// die Tiny-Sky-Ansicht aktiv war -- ihre Geometrie bleibt UNVERÄNDERT in Tiny-Sky-Pixeln (kein
// convertOverlayGeometry mehr für Kacheln, s. Plan Nachtrag 1), nur das Löse-ERGEBNIS (anchors) wird
// exakt punktweise nach nativen Koordinaten umgerechnet, bevor es in die globale Lösung einfließt.
internal enum class TileSourceSpace { Native, TinySky }

internal data class SolveTile(
    val id: Long,
    val center: Offset,
    val size: Size,
    val rotationDegrees: Float = 0f,
    val status: SolveTileStatus = SolveTileStatus.Pending,
    val wcs: com.codex.starmapper.processing.WcsSolution? = null,
    // s. TileSourceSpace oben. Default Native -> bestehende (rein native) Nutzung bleibt exakt gleich.
    val sourceSpace: TileSourceSpace = TileSourceSpace.Native,
    // De-Warp-WUNSCH pro Kachel (vom Nutzer gesetzt): soll DIESE Kachel vor dem Solve entzerrt
    // werden? Beim Platzieren erbt sie den globalen Default (dewarpEnabled). Der Solve-Loop
    // entzerrt genau die Kacheln mit dewarpRequested = true (statt eines globalen Schalters).
    val dewarpRequested: Boolean = false,
    // De-Warp-ERGEBNIS: entzerrt gelöste Kacheln tragen ihre exakten Anker direkt (statt einer
    // Crop-WCS) und merken sich, dass sie entzerrt gelöst wurden (≈-Markierung an gelösten Kacheln).
    val dewarp: Boolean = false,
    val anchors: List<Pair<Offset, Vec3>>? = null,
    // NUR für Tiny-Sky-Kacheln (sonst immer null): die eigene, beim Solve gewonnene lokale WCS --
    // gültig ausschließlich im TINY-SKY-Pixelraum dieser Kachel (anders als [wcs], das für Tiny-Sky-
    // Kacheln bewusst null bleibt, weil es dort für die NATIVE Fit-Integration missverstanden werden
    // könnte). Dient EINZIG als Positions-Hinweis-Quelle für andere, noch ungelöste Tiny-Sky-Kacheln
    // in der Nähe (s. solveAllTiles()'s nearestTileResult) -- ein Nachbar-in-Tiny-Sky-Hinweis bleibt
    // sonst strukturell unerreichbar, da die Spender-Suche bisher ausschließlich `wcs != null` prüfte
    // und Tiny-Sky-Kacheln dieses Feld nie füllen (Nutzerbefund 2026-08-25: "Kacheln bekommen falsche
    // Hinweise, suchen zu lange" -- der schon einmal reparierte Zielrechteck-Umrechnungsfehler aus
    // Plan Nachtrag 12 war nicht die einzige Ursache).
    val tinySkyLocalWcs: com.codex.starmapper.processing.WcsSolution? = null,
    // Manueller Positions-Hinweis (Nutzer tippt einen bekannten Sternnamen in der Nähe der Kachel
    // ein): wird beim Solve dieser Kachel VOR der priorFit-Extrapolation geprüft und hat Vorrang,
    // falls der Name im Katalog auflösbar ist (siehe astapSolveJob, rawHint-Berechnung).
    val manualHintStarName: String? = null,
    // Für das Kachel-Info-Popup: wurde beim (letzten) Solve dieser Kachel ein Positions-Hinweis
    // tatsächlich verwendet (rawHint != null bzw. De-Warp-Patch-Hinweis), oder blind gelöst?
    // null = noch nie gelöst.
    val solvedWithHint: Boolean? = null,
    // Dauer des letzten Solve-Versuchs dieser Kachel in Millisekunden (nur der reine Solver-Aufruf,
    // ohne De-Warp-Patch-Vorlauf). null = noch nie gelöst.
    val solveDurationMs: Long? = null,
)

/** Ergebnis der Anker-Sammlung über alle gelösten (nicht verdächtigen) Kacheln für solveAllTiles(). */
private data class TileFitAnchors(
    val anchors: List<Pair<Offset, Vec3>>,
    val weights: List<Double>,
    val tileWcs: List<TileWcs>,
    // Wie tileWcs, aber mit der Kachel-ID korreliert (tileWcs allein trägt keine ID) -> Grundlage
    // für tileOwnRmsById (Kreuzmatch der Kachel-eigenen WCS gegen echte Ganzbild-Blobs weiter unten).
    val idToTileWcs: List<Pair<Long, TileWcs>>,
    val outliers: List<TileConsistency.Outlier>,
    // Median-Reprojektionsfehler je Kachel (auch Ausreißer) -> Grundlage der Qualitäts-Einstufung
    // im Kachel-Info-Popup.
    val quality: Map<Long, Double>,
    // 1:1 zu weights' Herkunft (beide aus good.map{it.second.size}) -- Gruppierung derselben
    // anchors-Liste nach Herkunfts-Kachel für FisheyeRefiner.fitMesh()s Leave-One-Tile-Out-
    // Kreuzvalidierung (s. dortiger Kommentar). Getrennt von weights, weil Stimmgewicht und
    // Kachel-Gruppierung zwei unterschiedliche Dinge sind, die zufällig aus derselben Quelle kommen.
    val groupSizes: List<Int>,
    // 1:1 zu groupSizes/groupReliability (aus good.map{it.first.id}) -- NICHT dieselbe Liste/Reihenfolge
    // wie idToTileWcs (das nur Kacheln mit erfolgreich abgeleiteter eigener WCS enthält, s. dortiger
    // Kommentar); nur fürs Diagnose-Log gebraucht, welche Kachel-ID zu welchem Gewicht gehört.
    val tileIds: List<Long>,
    // 1:1 zu groupSizes -- Kachel-Zuverlässigkeits-Gewicht (TileConsistency.tileReliabilityWeights),
    // bereits IN weights eingerechnet, aber zusätzlich separat gehalten für FisheyeRefiner.fitMesh()s
    // groupWeights (dieselbe Größe wie groupSizes, nicht wie die flache weights-Liste).
    val groupReliability: List<Double>,
    // Kachel-Überlappungs-Widerspruch (TileConsistency.overlapDisagreement) -- eines der beiden
    // Signale hinter groupReliability, zusätzlich für die neue Kachel-Warnmarkierung gebraucht.
    val overlapAccuracy: Map<Long, TileConsistency.OverlapAccuracy>,
)

/**
 * Wählt zwischen dem Gewinner der starren 6-Modell-Auswahl ([rigid], s. calibratePanorama) und dem
 * neuen [PanoProjectionKind.Mesh]-Kandidaten ([mesh], s. FisheyeRefiner.fitMesh) -- KEIN einfacher
 * "<"-Vergleich: mesh.rms ist ehrlich per Leave-One-Tile-Out kreuzvalidiert, rigid.rms ist ein
 * optimistischer In-Sample-Restfehler (s. Kommentar an fitMesh) -- ein direkter Vergleich wäre also
 * strenger gegen Mesh, als es dessen tatsächliche Vorhersagequalität verdient. Da Mesh (seit dem
 * Korrektur-Redesign, s. CorrectedProjection) strukturell nie schlechter als [rigid] allein ist,
 * ist diese Marge kein Schutz mehr vor einem KAPUTTEN Mesh (das kann per Konstruktion nicht mehr
 * passieren) -- sondern reine Stabilität: kein Hin-und-Her zwischen Modellarten bei knappen/
 * verrauschten Unterschieden, wenn der ehrlich gemessene Vorsprung im Rauschen der beiden
 * unterschiedlichen Bewertungsmethoden verschwindet.
 */
private const val MESH_SUBSTITUTION_MARGIN = 0.8

/**
 * Gelockerte Marge fuer die ZYLINDRISCH/PERIODISCHE Modellfamilie (Equirectangular/Cylindrical/
 * Mercator) auf einem Bild, das KEIN echtes 2:1-Vollpanorama ist (Geraetebefund 2026-09-04,
 * Diagnose 43/44): dort gewann Mercator bei EXAKTEM Gleichstand (`solvetiles_candidates Mercator=19,4
 * Mesh=19,4`), war aber sichtbar falsch, waehrend Mesh visuell korrekt lag.
 *
 * Warum ein Wert > 1 hier vertretbar und kein "Mesh gewinnt immer" ist:
 * - `mesh.rms` ist per Leave-One-Tile-Out KREUZVALIDIERT, `rigid.rms` ein optimistischer
 *   In-Sample-Restfehler (s. KDoc oben) -- bei gleicher Zahl ist Mesh also faktisch bereits besser.
 * - Mesh ist seit dem Korrektur-Redesign ein Layer UEBER genau diesem `rigid`-Gewinner
 *   (`CorrectedProjection`, Baseline = rigid) und kann strukturell nicht schlechter als seine eigene
 *   Baseline sein. Die 0.8-Marge war laut eigenem KDoc bereits "kein Schutz mehr vor einem KAPUTTEN
 *   Mesh ... sondern reine Stabilitaet".
 * - Ein echtes Vollpanorama ist ausgenommen (s. `meshSubstitutionMargin`): dort ist die zylindrische
 *   Familie die richtige Wahl, und ein Wechsel auf `CorrectedProjection` wuerde `horizontalPeriodPx()`
 *   verlieren und damit die eingefrorene Gradnetz-/Perioden-Mechanik veraendern.
 * Fisheye/Stereographic/Rectilinear (u.a. der Golden-Fall) behalten unveraendert 0.8.
 */
private const val MESH_SUBSTITUTION_MARGIN_PERIODIC = 1.02

/** Marge fuer [pickCalibration]: gelockert nur fuer die periodische Familie auf einem Nicht-2:1-Bild. */
private fun meshSubstitutionMargin(
    rigidKind: PanoProjectionKind,
    imageWidth: Int,
    imageHeight: Int,
): Double {
    val periodicFamily = rigidKind == PanoProjectionKind.Equirectangular ||
        rigidKind == PanoProjectionKind.Cylindrical ||
        rigidKind == PanoProjectionKind.Mercator
    if (!periodicFamily) return MESH_SUBSTITUTION_MARGIN
    // Gleiche Toleranz wie FisheyeRefiner.FULL_PANORAMA_ASPECT_TOLERANCE (dort private).
    val fullPanorama = imageHeight > 0 &&
        kotlin.math.abs(imageWidth.toDouble() / imageHeight.toDouble() - 2.0) <= 0.02
    return if (fullPanorama) MESH_SUBSTITUTION_MARGIN else MESH_SUBSTITUTION_MARGIN_PERIODIC
}

private fun pickCalibration(
    rigid: FisheyeRefiner.PanoCalibration?,
    mesh: FisheyeRefiner.PanoCalibration?,
    imageWidth: Int,
    imageHeight: Int,
): FisheyeRefiner.PanoCalibration? = when {
    mesh == null -> rigid
    rigid == null -> mesh
    mesh.rms < rigid.rms * meshSubstitutionMargin(rigid.kind, imageWidth, imageHeight) -> mesh
    else -> rigid
}

/**
 * Gebündelter Kalibrierungs-Zustand (Anzeige-Lösung + Projektionswahl + Feinjustier-Hilfsfelder) für
 * EINEN atomaren Undo/Redo-Schritt bzw. den "direkt nach dem Solve"-Wiederherstellungspunkt. Ein
 * Bündel statt Einzel-Felder, damit ein Snapshot/Restore nie einzelne Felder vergisst (das war die
 * Ursache der Kachel-Undo/Redo-Veraltungslücke: nur solveTiles wurde gesichert, nicht die daraus
 * abgeleitete Anzeige-Lösung).
 */
private data class CalibrationSnapshot(
    val lastSolvedWcs: WcsSolutionLike?,
    val originalSolvedWcs: WcsSolutionLike?,
    val singleSolveWcs: WcsSolutionLike?,
    val singleImageSolved: Boolean,
    val panoSolveAnchors: List<Pair<Offset, Vec3>>,
    val panoSolveWeights: List<Double>,
    val fisheyeConfirmedAnchorDirs: List<Vec3>,
    val lastSolvedTileWcs: List<TileWcs>,
    val dewarpUsedInLastSolve: Boolean,
    val fisheyeBaseFit: PanoramaWcsSolution?,
    val fisheyeAlignSeed: PanoramaWcsSolution?,
    val fisheyeAlignRefs: List<AlignRefSnapshot>?,
    val alignProjectionKind: PanoProjectionKind,
    val alignProjectionRms: Double?,
    val panoProjectionChoice: String,
    val constellationsEnabled: Boolean,
    val customAlignFit: PanoramaWcsSolution?,
    val customAlignRms: Double?,
    val customAlignKind: PanoProjectionKind?,
    val richMeshFit: PanoramaWcsSolution?,
    val richMeshRms: Double?,
    // Manuelle Projektionskandidaten (Nutzer-Auftrag 2026-09-03) -- gehören zum Kalibrierungsstand und
    // müssen deshalb bei Undo/Redo bzw. "Original-Astrometrie wiederherstellen" mit zurückspringen,
    // sonst zeigte das Projektionsmenü Verfügbarkeiten eines anderen Solve-Stands an.
    val richMeshAutoEligible: Boolean = false,
    val richMeshRejectReason: String? = null,
    val sparseMeshFit: PanoramaWcsSolution? = null,
    val sparseMeshRms: Double? = null,
    val projectionCandidateRms: Map<PanoProjectionKind, Double?> = emptyMap(),
)

/** Kachel-Undo/Redo-Snapshot: Kacheln + ihre Qualitätswerte + die zugehörige Kalibrierung — alle drei
 *  atomar zusammen, damit nach einem Kachel-Undo/Redo die Anzeige-Lösung nie von den (wiederhergestellten)
 *  Kacheln abweicht. [ownRms]/[corrRefs] ebenso mitgeführt (statt sie bei jedem Undo/Redo zu leeren):
 *  sie gehören exakt zum WCS-Stand DIESES Snapshots, ein Undo/Redo stellt so automatisch wieder genau
 *  die dazu passenden Werte her -- sonst wären nach jedem Undo (auch harmlosen) ALLE bereits gelösten
 *  Kacheln ohne "Eigene Solve-Genauigkeit", bis jede einzeln neu gelöst wird (Gerätebeleg 2026-07-27:
 *  ein einziges Undo direkt vor dem Diagnose-Export leerte 54/54 Kacheln mit zuvor bis zu 420 echten
 *  .corr-Treffern je Kachel). */
private data class TileEditSnapshot(
    val tiles: List<SolveTile>,
    val quality: Map<Long, Double>,
    val calibration: CalibrationSnapshot,
    val ownRms: Map<Long, TileOwnAccuracy> = emptyMap(),
    val corrRefs: Map<Long, List<Pair<Offset, Vec3>>> = emptyMap(),
    // Wie ownRms mitgeführt (gleicher Grund, s. Klassenkommentar) -- Überlappungs-Widerspruch statt
    // eigener Solve-Genauigkeit.
    val overlapRms: Map<Long, TileConsistency.OverlapAccuracy> = emptyMap(),
)

/**
 * Untersuchungsauftrag 2026-08-31 (dritte Runde: "warum verlieren TinySky-Kacheln 9 von 10 100% ihrer
 * .corr-Korrespondenzen als Outlier"): rein additive Detail-Diagnose für die ersten [budget] `.corr`-
 * Referenzen EINER Tiny-Sky-Kachel, KEINE Verhaltensänderung an Solve/Validierung selbst. Ziel: im
 * Log-Output selbst zweifelsfrei belegen, in welchem Koordinatenraum [wcs] (die gerade gefittete,
 * kachel-EIGENE Lösung, IMMER ein reines [WcsSolution]/TAN aus [LocalAstrometrySolver.solve], NIE ein
 * periodisches Panorama-Modell) tatsächlich vorhersagt -- crop-lokal (Raum des gelösten Bitmaps selbst)
 * oder bereits nativ.
 *
 * [rawRefs] = [LocalAstrometrySolver.LocalSolveResult.corrRefs] VOR jeder Transformation (crop-lokal,
 * 0-basiert -- die "-1"-FITS-Korrektur ist darin bereits enthalten, das IST bereits "raw field_x/field_y"
 * im Sinne des Auftrags, nur schon 0-indiziert). Ruft [TileDeWarp.transformTinySkyRefsToNative] pro
 * EINZELNER Referenz separat auf (statt der ggf. kürzeren, wegen `mapNotNull` nicht mehr index-parallelen
 * Batch-Liste) -- reine Wiederverwendung der echten Produktionslogik, keine Parallel-Implementierung.
 *
 * Vier Vergleichsvarianten pro Referenz: (1) die semantisch korrekte Prüfung crop-lokale Ist-Position vs.
 * crop-lokale WCS-Vorhersage (das, was der begleitende Fix jetzt tatsächlich nutzt); (2) die BISHERIGE,
 * verdächtige Prüfung native Ist-Position vs. crop-lokale Vorhersage OHNE Offset (das, was der alte Code
 * verglich); (3)+(4) dieselbe native Ist-Position gegen die crop-lokale Vorhersage MIT addiertem bzw.
 * entferntem Crop-Offset (rx,ry) -- klärt empirisch, ob ein simpler Offset-Versatz die Erklärung ist,
 * unabhängig von der bereits in (1) belegten eigentlichen Ursache. `wcsPredCropLocalRefAware` ist bei
 * einem reinen TAN-[wcs] IMMER identisch zu `wcsPredCropLocal` (kein periodischer Zweig vorhanden) --
 * bestätigt im Log-Output direkt, dass der bereits früher angebrachte seam-/branch-bewusste Fix in
 * [RichCorrMesh.rejectResidualOutliers] für DIESE (Stage-1-TinySky-)Prüfung strukturell wirkungslos ist,
 * da er nur bei periodischen (Cylindrical-family) Projektionen überhaupt einen Unterschied machen kann.
 */
private fun logTinySkyCorrValidationDetail(
    label: String,
    rawRefs: List<Pair<Offset, Vec3>>,
    rx: Int,
    ry: Int,
    rh: Int,
    overviewProjection: PanoramaProjection,
    nativeProjection: PanoramaProjection,
    wcs: WcsSolutionLike,
    budget: Int,
) {
    fun fmtPx(o: Offset?): String = if (o == null) "null" else "(${"%.2f".format(o.x)},${"%.2f".format(o.y)})"
    fun residual(a: Offset?, b: Offset?): String {
        if (a == null || b == null) return "null"
        return "%.2f".format(hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()))
    }
    val offsetPx = Offset(rx.toFloat(), ry.toFloat())
    for (i in 0 until minOf(budget, rawRefs.size)) {
        val (cropLocalActualPx, dir) = rawRefs[i]
        val (raDeg, decDeg) = vectorToRaDec(dir)
        val sky = SkyPoint(raDeg.toFloat(), decDeg.toFloat())
        val nativeTransformedActualPx = TileDeWarp.transformTinySkyRefsToNative(
            listOf(cropLocalActualPx to dir), rx, ry, overviewProjection, nativeProjection,
        ).firstOrNull()?.first
        val wcsPredCropLocal = wcs.skyToImage(sky, rh)
        val wcsPredCropLocalRefAware = wcs.skyToImage(sky, rh, cropLocalActualPx)
        val wcsPredPlusOffset = wcsPredCropLocal?.let { it + offsetPx }
        val wcsPredMinusOffset = wcsPredCropLocal?.let { it - offsetPx }
        AppDiagnostics.record(
            "corr_pipeline_detail label=$label idx=$i " +
                "cropLocalActualPx=${fmtPx(cropLocalActualPx)} offX=$rx offY=$ry " +
                "nativeTransformedActualPx=${fmtPx(nativeTransformedActualPx)} " +
                "wcsPredCropLocal=${fmtPx(wcsPredCropLocal)} wcsPredCropLocalRefAware=${fmtPx(wcsPredCropLocalRefAware)} " +
                "residual_cropLocalActual_vs_wcsPredCropLocal=${residual(cropLocalActualPx, wcsPredCropLocal)} " +
                "residual_nativeActual_vs_wcsPredCropLocal_noOffset=${residual(nativeTransformedActualPx, wcsPredCropLocal)} " +
                "residual_nativeActual_vs_wcsPredPlusOffset=${residual(nativeTransformedActualPx, wcsPredPlusOffset)} " +
                "residual_nativeActual_vs_wcsPredMinusOffset=${residual(nativeTransformedActualPx, wcsPredMinusOffset)} " +
                "raDeg=${"%.4f".format(raDeg)} decDeg=${"%.4f".format(decDeg)}",
        )
    }
}

/**
 * Untersuchungsauftrag 2026-08-31 (vierte Runde, Stage 2): rein additive Diagnose, KEINE Verhaltens-
 * änderung an `RichCorrMeshProjection`/Outlier-Schwellen. Stage 1 gilt laut Nutzer als repariert bestätigt
 * (TinySky-`.corr`-Punkte überleben jetzt in großer Zahl die kachel-eigene Validierung). Der neue Befund:
 * `rich_corr_mesh_validate` verwarf DANACH trotzdem 920/920 Referenzen mit Residuen um ~2355-2362px,
 * `seamBranchSuspect=false`, `group0` zeigte eine nahezu KONSTANTE Translation (~+2340px X, +315px Y).
 *
 * Direkt im Code gefunden (nicht nur vermutet): [FisheyeRefiner.globalizeTileCorrRefs] addiert
 * UNBEDINGT `tw.tileOffsetX/Y` auf jede `.corr`-Position, unter der (für native Kacheln korrekten)
 * Annahme, sie sei kachel-lokal. Für eine Tiny-Sky-Kachel ist [tileCorrRefsById]'s Eintrag dank des
 * Stufe-1-Fixes aber bereits eine NATIVE Position (`TileDeWarp.transformTinySkyRefsToNative`, exakt
 * dieselbe geometrische Kette wie [TileDeWarp.tinySkyAnchorsFor], das OHNE jede weitere Offset-Addition
 * direkt in `tile.anchors` landet -- die Quelle von SparseAnchorMesh). `tw.tileOffsetX/Y` ist für eine
 * Tiny-Sky-Kachel zudem gar nicht der Kachel-Ursprung, sondern nur eine grobe, für [MosaicWcsSolution]s
 * Fallback-Buchhaltung gedachte native Bounding-Box-Näherung (`convertOverlayGeometry`, s. TileWcs-
 * Konstruktionsstelle) -- ihre zusätzliche Addition erklärt exakt eine KONSTANTE Translation pro Kachel/
 * Gruppe in genau der beobachteten Größenordnung. `seamBranchSuspect=false` passt dazu: eine reine
 * Translation ist keine periodische Ast-Verwechslung. Begleitend behoben (s. [TileWcs.corrRefsAlreadyNative]-
 * KDoc + [FisheyeRefiner.globalizeTileCorrRefs]s neue Fallunterscheidung).
 *
 * Protokolliert pro echtem, bereits akzeptiertem `.corr`-Punkt (max. [budgetPerTile] je Tiny-Sky-Kachel):
 * [corrNativePx] (der jetzt korrekte, in [tileCorrRefsById] gespeicherte Wert -- das, was NACH dem Fix
 * tatsächlich in `corrGlobalRefs` landet); [sparseAnchorEquivalentNativePx] (der nächstgelegene `tile.
 * anchors`-Punkt DERSELBEN Kachel -- kein exakt derselbe Messpunkt, aber dieselbe Transformationskette,
 * bestätigt empirisch, dass beide Pfade im selben nativen Koordinatenraum landen); [wouldBeCorruptedByOldOffsetPx]
 * (was `globalizeTileCorrRefs` VOR diesem Fix geliefert hätte -- zum direkten Abgleich gegen die vom
 * Nutzer bereits beobachteten ~+2340/+315px); `tileOffsetX`/`tileOffsetY` (die Panorama-Baseline-fremden,
 * nur für Mosaik-Fallback gedachten Werte, die den alten Fehler verursachten); `baselineExpectedPxMainBranch`/
 * `baselineExpectedPxRefAware`/`baselineResidualPx` (Soll-Position/Residuum gegen [baseline], referenz-
 * bewusst -- deckt einen ZUSÄTZLICHEN, unabhängigen Wrap/Branch-Fehler ab, falls doch einer existiert,
 * ohne ihn vorab anzunehmen); RA/Dec; `group` (Index in [idToTileWcs], identisch zur Gruppen-Reihenfolge,
 * die [FisheyeRefiner.corrRefGroupSizesAndWeights] für `rich_corr_mesh_validate`s `group0`/`group1`/...
 * verwendet).
 */
private fun logStage2CorrGlobalizationDetail(
    idToTileWcs: List<Pair<Long, TileWcs>>,
    tileCorrRefsById: Map<Long, List<Pair<Offset, Vec3>>>,
    tilesSnapshot: List<SolveTile>,
    baseline: WcsSolutionLike,
    imageHeight: Int,
    budgetPerTile: Int,
) {
    fun fmtPx(o: Offset?): String = if (o == null) "null" else "(${"%.2f".format(o.x)},${"%.2f".format(o.y)})"
    for ((groupIndex, entry) in idToTileWcs.withIndex()) {
        val (tileId, tw) = entry
        if (!tw.corrRefsAlreadyNative) continue
        val corrRefs = tileCorrRefsById[tileId] ?: continue
        if (corrRefs.isEmpty()) continue
        val sparseAnchors = tilesSnapshot.firstOrNull { it.id == tileId }?.anchors ?: emptyList()
        for (i in 0 until minOf(budgetPerTile, corrRefs.size)) {
            val (corrNativePx, dir) = corrRefs[i]
            val (raDeg, decDeg) = vectorToRaDec(dir)
            val sky = SkyPoint(raDeg.toFloat(), decDeg.toFloat())
            val nearestAnchorPx = sparseAnchors.minByOrNull {
                hypot((it.first.x - corrNativePx.x).toDouble(), (it.first.y - corrNativePx.y).toDouble())
            }?.first
            val wouldBeCorrupted = Offset(corrNativePx.x + tw.tileOffsetX, corrNativePx.y + tw.tileOffsetY)
            val baselineMainBranch = baseline.skyToImage(sky, imageHeight)
            val baselineRefAware = baseline.skyToImage(sky, imageHeight, corrNativePx)
            val baselineResidualPx = baselineRefAware?.let {
                hypot((corrNativePx.x - it.x).toDouble(), (corrNativePx.y - it.y).toDouble())
            }
            AppDiagnostics.record(
                "corr_globalize_detail tile=$tileId group=$groupIndex idx=$i " +
                    "corrNativePx=${fmtPx(corrNativePx)} " +
                    "sparseAnchorEquivalentNativePx=${nearestAnchorPx?.let { fmtPx(it) } ?: "n/a(no_anchors)"} " +
                    "wouldBeCorruptedByOldOffsetPx=${fmtPx(wouldBeCorrupted)} " +
                    "tileOffsetX=${tw.tileOffsetX} tileOffsetY=${tw.tileOffsetY} " +
                    "baselineExpectedPxMainBranch=${fmtPx(baselineMainBranch)} " +
                    "baselineExpectedPxRefAware=${fmtPx(baselineRefAware)} " +
                    "baselineResidualPx=${baselineResidualPx?.let { "%.2f".format(it) } ?: "null"} " +
                    "raDeg=${"%.4f".format(raDeg)} decDeg=${"%.4f".format(decDeg)}",
            )
        }
    }
}

/**
 * Anker einer gelösten Kachel (Bildpixel <-> äquatoriale Richtung): entzerrte (De-Warp) tragen
 * sie direkt (tile.anchors), normale werden aus ihrer Crop-WCS per 3x3-Gitter abgetastet.
 * Geteilt von Solve-Auswertung und Panorama-Seed-Refresh.
 */
private fun tileAnchorPairs(tile: SolveTile, imgW: Int, imgH: Int): List<Pair<Offset, Vec3>> {
    tile.anchors?.let { return it }
    val w = tile.wcs ?: return emptyList()
    val bb = solveTileBoundingBox(tile)
    val rx = bb.left.roundToInt().coerceIn(0, imgW - 1)
    val ry = bb.top.roundToInt().coerceIn(0, imgH - 1)
    val rw = bb.width.roundToInt().coerceIn(64, imgW - rx)
    val rh = bb.height.roundToInt().coerceIn(64, imgH - ry)
    val list = ArrayList<Pair<Offset, Vec3>>(9)
    for (gx in 0..2) for (gy in 0..2) {
        val px = rw * (gx + 0.5) / 3.0
        val py = rh * (gy + 0.5) / 3.0
        val sky = w.imageToSky(px, py, rh)
        list += Offset((rx + px).toFloat(), (ry + py).toFloat()) to
            raDecToVector(sky.raDegrees.toDouble(), sky.decDegrees.toDouble())
    }
    return list
}

/** Bild-Eckpunkte (TL,TR,BR,BL) der (ggf. rotierten) Kachel. */
private fun solveTileImageCorners(tile: SolveTile): List<Offset> {
    val hw = tile.size.width / 2f
    val hh = tile.size.height / 2f
    val rad = tile.rotationDegrees * PI.toFloat() / 180f
    return listOf(
        Offset(-hw, -hh), Offset(hw, -hh), Offset(hw, hh), Offset(-hw, hh),
    ).map { tile.center + rotateOffset(it, rad) }
}

private fun solveTileScreenCorners(tile: SolveTile, viewport: ImageViewport): List<Offset> =
    solveTileImageCorners(tile).map { viewport.imageToScreen(it) }

/** Achsenparallele Bounding-Box (Bild-px) — der tatsächlich gelöste Ausschnitt. */
private fun solveTileBoundingBox(tile: SolveTile): ImageBounds {
    val pts = solveTileImageCorners(tile)
    return ImageBounds(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
}

private fun solveTileRotateHandleCenter(tile: SolveTile, viewport: ImageViewport, offsetPx: Float): Offset {
    val rad = tile.rotationDegrees * PI.toFloat() / 180f
    val imageOffset = rotateOffset(Offset(0f, -tile.size.height / 2f - offsetPx / viewport.scale), rad)
    return viewport.imageToScreen(tile.center + imageOffset)
}

/** Treffer auf einem Eck-Griff (Resize) der Kachel? */
private fun solveTileResizeHit(tile: SolveTile, screenPoint: Offset, viewport: ImageViewport, radius: Float): Boolean =
    solveTileScreenCorners(tile, viewport).any { it.distanceTo(screenPoint) <= radius }

private fun pointInsideSolveTile(imagePoint: Offset, tile: SolveTile, padding: Float): Boolean {
    val rad = -tile.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(imagePoint - tile.center, rad)
    return abs(local.x) <= tile.size.width / 2f + padding && abs(local.y) <= tile.size.height / 2f + padding
}

/** Oberste Kachel unter dem Bildschirmpunkt (zuletzt hinzugefügte zuerst). */
private fun solveTileAtScreen(
    screenPoint: Offset, tiles: List<SolveTile>, viewport: ImageViewport, hitRadiusPx: Float,
    activeTileSpace: TileSourceSpace,
): SolveTile? {
    val imagePoint = viewport.screenToImage(screenPoint)
    val pad = hitRadiusPx / viewport.scale
    return tiles.asReversed().firstOrNull {
        it.sourceSpace == activeTileSpace && pointInsideSolveTile(imagePoint, it, pad)
    }
}

/** Symmetrisches Resize um das Zentrum aus dem aktuellen Ziehpunkt (wie beim Rechteck-Overlay). */
private fun resizeSolveTile(tile: SolveTile, screenPoint: Offset, viewport: ImageViewport): SolveTile {
    val imagePoint = viewport.screenToImage(screenPoint)
    val rad = -tile.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(imagePoint - tile.center, rad)
    val w = (abs(local.x) * 2f).coerceAtLeast(48f)
    val h = (abs(local.y) * 2f).coerceAtLeast(48f)
    return tile.copy(size = Size(w, h))
}

private fun rotateSolveTile(tile: SolveTile, screenPoint: Offset, viewport: ImageViewport): SolveTile {
    val imagePoint = viewport.screenToImage(screenPoint)
    val angle = atan2(imagePoint.y - tile.center.y, imagePoint.x - tile.center.x)
    var deg = angle * 180f / PI.toFloat() + 90f
    val nearest = Math.round(deg / 15f) * 15f
    if (abs(deg - nearest) <= 5f) deg = nearest
    return tile.copy(rotationDegrees = deg)
}

private sealed class EditorGestureTarget {
    data object FreeImage : EditorGestureTarget()
    data class OverlayBody(val id: Long) : EditorGestureTarget()
    data class ConstellationAnchor(val id: Long, val anchorIndex: Int) : EditorGestureTarget()
    data class ResizeHandle(val id: Long, val corner: ResizeCorner) : EditorGestureTarget()
    data class RotateHandle(val id: Long) : EditorGestureTarget()
    // Zieh-Griff am Ende der Namens-Führungslinie (Ellipse/Rectangle/Freehand mit showName=true).
    data class LabelHandle(val id: Long) : EditorGestureTarget()
    // Sternbild-NAME selbst (unabhängig vom Linienmuster) -- Ziehen versetzt nur den Namen
    // (AnnotationOverlay.constellationNameOffset), Antippen ohne Ziehen setzt ihn zurück.
    data class ConstellationNameHandle(val id: Long) : EditorGestureTarget()
    data class FixedDelete(val id: Long) : EditorGestureTarget()
    data class FixedReference(val id: Long) : EditorGestureTarget()
}

private sealed class EditorGestureState {
    data object Idle : EditorGestureState()
    data object Pressed : EditorGestureState()
    data object ImagePan : EditorGestureState()
    data object MultiTouchViewport : EditorGestureState()
    data class OverlayMoveAfterLongPress(val id: Long) : EditorGestureState()
    data class AnchorDrag(val id: Long, val anchorIndex: Int) : EditorGestureState()
    data class ResizeDrag(val id: Long, val corner: ResizeCorner) : EditorGestureState()
    data class RotateDrag(val id: Long) : EditorGestureState()
}

private fun resolveEditorGestureTarget(
    screenPoint: Offset,
    canvasSize: IntSize,
    overlays: List<AnnotationOverlay>,
    viewport: ImageViewport,
    selectedOverlayId: Long?,
    editingOverlayId: Long?,
    showConstellationAnchors: Boolean,
    handleHitRadius: Float,
    fixedControlInsetPx: Float,
    fixedControlSpacingPx: Float,
    fixedControlHitRadiusPx: Float,
    rotationHandleOffsetPx: Float,
): EditorGestureTarget {
    val editingOverlay = editingOverlayId
        ?.takeIf { it == selectedOverlayId }
        ?.let { id -> overlays.firstOrNull { it.id == id } }

    if (editingOverlay != null) {
        if (editingOverlay.kind == OverlayKind.Constellation) {
            if (editingOverlay.showName && constellationNameHitTest(editingOverlay, screenPoint, viewport, handleHitRadius)) {
                return EditorGestureTarget.ConstellationNameHandle(editingOverlay.id)
            }
            if (showConstellationAnchors) {
                nearestConstellationAnchor(editingOverlay, screenPoint, viewport, handleHitRadius)?.let { anchorIndex ->
                    return EditorGestureTarget.ConstellationAnchor(editingOverlay.id, anchorIndex)
                }
            }
        } else {
            nearestResizeCorner(editingOverlay, screenPoint, viewport, handleHitRadius)?.let { corner ->
                return EditorGestureTarget.ResizeHandle(editingOverlay.id, corner)
            }
            if (overlayRotationHandleCenter(editingOverlay, viewport, rotationHandleOffsetPx).distanceTo(screenPoint) <= handleHitRadius) {
                return EditorGestureTarget.RotateHandle(editingOverlay.id)
            }
            if (
                (
                    editingOverlay.kind == OverlayKind.Ellipse ||
                        editingOverlay.kind == OverlayKind.Rectangle ||
                        editingOverlay.kind == OverlayKind.Freehand
                    ) &&
                editingOverlay.showName &&
                labelHandleScreenPoint(editingOverlay, viewport).distanceTo(screenPoint) <= handleHitRadius
            ) {
                return EditorGestureTarget.LabelHandle(editingOverlay.id)
            }
        }

        if (
            editingOverlay.kind == OverlayKind.Constellation &&
            isNearFixedReferenceHandle(screenPoint, canvasSize, fixedControlInsetPx, fixedControlSpacingPx, fixedControlHitRadiusPx)
        ) {
            return EditorGestureTarget.FixedReference(editingOverlay.id)
        }
    }

    return findOverlayAtScreen(screenPoint, overlays, viewport, handleHitRadius)
        ?.let { EditorGestureTarget.OverlayBody(it) }
        ?: EditorGestureTarget.FreeImage
}

private fun nearestConstellationAnchor(
    overlay: AnnotationOverlay,
    screenPoint: Offset,
    viewport: ImageViewport,
    maxDistance: Float,
): Int? {
    return overlay.constellationImagePoints()
        .mapIndexed { index, point -> index to viewport.imageToScreen(point).distanceTo(screenPoint) }
        .filter { (_, distance) -> distance <= maxDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun nearestDetectedStar(
    imagePoint: Offset,
    stars: List<DetectedStar>,
    viewport: ImageViewport,
    maxScreenDistance: Float,
): Offset? {
    val screenPoint = viewport.imageToScreen(imagePoint)
    return stars
        .map { star -> star.offset to viewport.imageToScreen(star.offset).distanceTo(screenPoint) }
        .filter { (_, distance) -> distance <= maxScreenDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun nearestDetectedStarIndex(
    imagePoint: Offset,
    stars: List<DetectedStar>,
    viewport: ImageViewport,
    maxScreenDistance: Float,
): Int? {
    val screenPoint = viewport.imageToScreen(imagePoint)
    return stars
        .mapIndexed { index, star -> index to viewport.imageToScreen(star.offset).distanceTo(screenPoint) }
        .filter { (_, distance) -> distance <= maxScreenDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun nearestResizeCorner(
    overlay: AnnotationOverlay,
    screenPoint: Offset,
    viewport: ImageViewport,
    maxDistance: Float,
): ResizeCorner? {
    return overlaySelectionCorners(overlay, viewport)
        .zip(ResizeCorner.entries)
        .map { (cornerPoint, corner) -> corner to cornerPoint.distanceTo(screenPoint) }
        .filter { (_, distance) -> distance <= maxDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun overlaySelectionCorners(overlay: AnnotationOverlay, viewport: ImageViewport): List<Offset> {
    val halfWidth = overlay.size.width / 2f
    val halfHeight = overlay.size.height / 2f
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    return listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
    ).map { corner -> viewport.imageToScreen(overlay.center + rotateOffset(corner, radians)) }
}

// Bildschirmposition des Zieh-Griffs am Ende der Namens-Führungslinie (s. LabelHandle).
private fun labelHandleScreenPoint(overlay: AnnotationOverlay, viewport: ImageViewport): Offset {
    val layout = OverlayGeometry.markerLabelLayout(overlay)
    return viewport.imageToScreen(overlay.center + layout.lineEnd)
}

private fun overlayRotationHandleCenter(
    overlay: AnnotationOverlay,
    viewport: ImageViewport,
    offsetPx: Float,
): Offset {
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    val imageOffset = rotateOffset(Offset(0f, -overlay.size.height / 2f - offsetPx / viewport.scale), radians)
    return viewport.imageToScreen(overlay.center + imageOffset)
}

private fun fixedReferenceHandleCenter(canvasSize: IntSize, insetPx: Float, spacingPx: Float): Offset {
    return Offset(canvasSize.width - insetPx - spacingPx, insetPx)
}

private fun isNearFixedReferenceHandle(
    screenPoint: Offset,
    canvasSize: IntSize,
    insetPx: Float,
    spacingPx: Float,
    maxDistance: Float,
): Boolean = fixedReferenceHandleCenter(canvasSize, insetPx, spacingPx).distanceTo(screenPoint) <= maxDistance


private data class ImageBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private fun overlayImageBounds(overlay: AnnotationOverlay): ImageBounds {
    val points = when (overlay.kind) {
        OverlayKind.Constellation -> overlay.constellationImagePoints().ifEmpty { overlayImageCorners(overlay) }
        else -> overlayImageCorners(overlay)
    }
    return ImageBounds(
        left = points.minOf { it.x },
        top = points.minOf { it.y },
        right = points.maxOf { it.x },
        bottom = points.maxOf { it.y },
    )
}

private fun overlayImageCorners(overlay: AnnotationOverlay): List<Offset> {
    val halfWidth = overlay.size.width / 2f
    val halfHeight = overlay.size.height / 2f
    val radians = overlay.rotationDegrees * PI.toFloat() / 180f
    return listOf(
        Offset(-halfWidth, -halfHeight),
        Offset(halfWidth, -halfHeight),
        Offset(halfWidth, halfHeight),
        Offset(-halfWidth, halfHeight),
    ).map { corner -> overlay.center + rotateOffset(corner, radians) }
}

private fun resizeOverlayFromScreenPoint(
    overlay: AnnotationOverlay,
    screenPoint: Offset,
    viewport: ImageViewport,
    allOverlays: List<AnnotationOverlay> = emptyList(),
): AnnotationOverlay {
    val imagePoint = viewport.screenToImage(screenPoint)
    val radians = -overlay.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(imagePoint - overlay.center, radians)
    val w = (abs(local.x) * 2f).coerceAtLeast(24f)
    val h = (abs(local.y) * 2f).coerceAtLeast(24f)
    if (overlay.kind == OverlayKind.Text) {
        // Text: die Höhe ist die Schriftgröße (bis bildgroß). Rasten: zuerst an die Größe anderer
        // Text-Overlays angleichen (schnell vereinheitlichen), sonst auf ein grobes 10-px-Raster.
        var th = h
        val others = allOverlays
            .filter { it.id != overlay.id && it.kind == OverlayKind.Text }
            .map { it.size.height }
        val nearest = others.minByOrNull { abs(it - th) }
        th = if (nearest != null && abs(nearest - th) <= th * 0.08f) {
            nearest
        } else {
            (Math.round(th / 10f) * 10f).coerceAtLeast(8f)
        }
        return overlay.copy(size = Size(th * 5f, th))
    }
    // Einrasten auf 1:1, wenn nahe quadratisch (innerhalb kleiner Toleranz).
    val snapped = if (h > 0f && (w / h) in 0.92f..1.08f) {
        val s = (w + h) / 2f
        Size(s, s)
    } else {
        Size(w, h)
    }
    return overlay.copy(size = snapped)
}

private fun rotateOverlayFromScreenPoint(
    overlay: AnnotationOverlay,
    screenPoint: Offset,
    viewport: ImageViewport,
): AnnotationOverlay {
    val imagePoint = viewport.screenToImage(screenPoint)
    val angle = atan2(imagePoint.y - overlay.center.y, imagePoint.x - overlay.center.x)
    var rotationDegrees = angle * 180f / PI.toFloat() + 90f
    // Einrasten auf 15°-Raster (0/15/30/45/…), wenn nahe (±5°) -> saubere Winkel möglich.
    val step = 15f
    val nearest = Math.round(rotationDegrees / step) * step
    if (abs(rotationDegrees - nearest) <= 5f) rotationDegrees = nearest
    return overlay.copy(rotationDegrees = rotationDegrees)
}

private fun translateOverlay(overlay: AnnotationOverlay, imageDelta: Offset): AnnotationOverlay {
    return overlay.copy(
        center = overlay.center + imageDelta,
        anchorOverrides = overlay.anchorOverrides.mapValues { (_, point) -> point + imageDelta },
        // Gelöste (Fisheye-)Sternbilder zeichnen ihre Linien aus den absoluten edgePolylines;
        // ohne Mitverschieben blieben die Linien stehen, während Anker/Name/Glow wandern.
        edgePolylines = overlay.edgePolylines?.map { poly -> poly.map { it + imageDelta } },
    )
}

private fun selectConstellationAt(
    tap: Offset,
    catalog: List<ConstellationPattern>,
    rot: Mat3,
    mirrorX: Boolean,
    mirrorY: Boolean,
    radius: Float,
    center: Offset,
    maxDistance: Float,
): ConstellationPattern? {
    return catalog
        .mapNotNull { pattern ->
            val projected = pattern.stars.map { star -> projectSkyPoint(star, rot, radius, center, mirrorX, mirrorY) }
            val starDistance = projected.filterNotNull().minOfOrNull { it.distanceTo(tap) } ?: Float.MAX_VALUE
            val edgeDistance = pattern.edges.minOfOrNull { (a, b) ->
                val start = projected.getOrNull(a)
                val end = projected.getOrNull(b)
                if (start != null && end != null) distanceToSegment(tap, start, end) else Float.MAX_VALUE
            } ?: Float.MAX_VALUE
            val distance = min(starDistance, edgeDistance)
            if (distance <= maxDistance) pattern to distance else null
        }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.0001f) return point.distanceTo(start)
    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    val projection = Offset(start.x + dx * t, start.y + dy * t)
    return point.distanceTo(projection)
}

private fun rotateOffset(offset: Offset, radians: Float): Offset {
    val c = cos(radians)
    val s = sin(radians)
    return Offset(offset.x * c - offset.y * s, offset.x * s + offset.y * c)
}

private fun findOverlayAtScreen(
    screenPoint: Offset,
    overlays: List<AnnotationOverlay>,
    viewport: ImageViewport,
    hitRadiusPx: Float,
): Long? {
    val imagePoint = viewport.screenToImage(screenPoint)
    val imagePadding = hitRadiusPx / viewport.scale
    return overlays.asReversed().firstOrNull { overlay ->
        when (overlay.kind) {
            OverlayKind.Constellation -> {
                val points = overlay.constellationImagePoints().map { viewport.imageToScreen(it) }
                // Nutzer-Auftrag 2026-09-03: gegen die TATSÄCHLICH GEZEICHNETE Punktfolge prüfen.
                // Sobald `edgePolylines` existiert (jede PanoramaWcsSolution -- Fisheye, Mercator,
                // Mesh, ...), zeichnet drawOverlay gekrümmte Großkreis-Polylinien; der bisherige Test
                // gegen die GERADE Sehne zwischen zwei Ankern lag bei starker Krümmung weit neben der
                // sichtbaren Linie -- große, randnahe Sternbilder waren dadurch praktisch nicht
                // greifbar. Die Naht-Aufteilung wird identisch zum Zeichnen nachvollzogen
                // (overlay.seamAware -> splitPolylineAtSeam), sonst liefen Linie und Trefferfläche bei
                // 360°-Panoramen an der Naht auseinander. `trimPolylineEnds` wird bewusst NICHT
                // nachgebildet: ungetrimmt ist nur an den Ankerenden minimal großzügiger, und genau
                // dort greift ohnehin schon der Ankerpunkt-Treffer darüber.
                val polylines = overlay.edgePolylines
                val edgeHit = if (polylines != null) {
                    polylines.any { poly ->
                        if (poly.size < 2) return@any false
                        val pieces = if (overlay.seamAware) {
                            OverlayGeometry.splitPolylineAtSeam(poly, viewport.imageWidth)
                        } else {
                            listOf(poly)
                        }
                        pieces.any { piece ->
                            if (piece.size < 2) return@any false
                            piece.map { viewport.imageToScreen(it) }.zipWithNext().any { (a, b) ->
                                distanceToSegment(screenPoint, a, b) <= hitRadiusPx
                            }
                        }
                    }
                } else {
                    overlay.constellation?.edges.orEmpty().any { (a, b) ->
                        val start = points.getOrNull(a)
                        val end = points.getOrNull(b)
                        start != null && end != null && distanceToSegment(screenPoint, start, end) <= hitRadiusPx
                    }
                }
                points.any { it.distanceTo(screenPoint) <= hitRadiusPx } || edgeHit
            }
            OverlayKind.Text -> pointInsideOverlayBounds(imagePoint, overlay, padding = imagePadding)
            OverlayKind.Ellipse -> pointNearEllipseStroke(imagePoint, overlay, padding = imagePadding)
            OverlayKind.Rectangle -> pointNearRectangleStroke(imagePoint, overlay, padding = imagePadding)
            OverlayKind.Freehand -> pointNearFreehandOutline(imagePoint, overlay, padding = imagePadding)
        }
    }?.id
}

// Wie pointNearEllipseStroke/pointNearRectangleStroke: Treffer nur NAHE der Kontur, nicht überall
// innerhalb einer gefüllten Form (gleiche Konvention wie die übrigen Formen). Geschlossen (Start->Ende
// mitgezählt) nur bei filled=true -- passend zum taper-Flag beim Rendering (drawOverlay()).
private fun pointNearFreehandOutline(point: Offset, overlay: AnnotationOverlay, padding: Float): Boolean {
    val allSegments = OverlayGeometry.denormalizedFreehandPoints(overlay)
    // Füllung/geschlossene Kontur nur bei genau einem Segment -- spiegelt drawOverlay()s canFill.
    val canFill = overlay.filled && allSegments.size == 1 && (allSegments.firstOrNull()?.size ?: 0) >= 2
    return allSegments.any { points ->
        if (points.size < 2) return@any false
        val pairs = if (canFill) points.zipWithNext() + (points.last() to points.first()) else points.zipWithNext()
        pairs.any { (a, b) -> distanceToSegment(point, a, b) <= padding }
    }
}

private fun pointNearEllipseStroke(point: Offset, overlay: AnnotationOverlay, padding: Float): Boolean {
    val radians = -overlay.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(point - overlay.center, radians)
    val rx = (overlay.size.width / 2f).coerceAtLeast(1f)
    val ry = (overlay.size.height / 2f).coerceAtLeast(1f)
    val normalized = hypot(local.x / rx, local.y / ry)
    val tolerance = padding / min(rx, ry).coerceAtLeast(1f)
    return abs(normalized - 1f) <= tolerance
}

private fun pointNearRectangleStroke(point: Offset, overlay: AnnotationOverlay, padding: Float): Boolean {
    val radians = -overlay.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(point - overlay.center, radians)
    val halfWidth = overlay.size.width / 2f
    val halfHeight = overlay.size.height / 2f
    val insideOuter = abs(local.x) <= halfWidth + padding && abs(local.y) <= halfHeight + padding
    val insideInner = abs(local.x) < (halfWidth - padding).coerceAtLeast(0f) &&
        abs(local.y) < (halfHeight - padding).coerceAtLeast(0f)
    return insideOuter && !insideInner
}

private fun pointInsideOverlayBounds(point: Offset, overlay: AnnotationOverlay, padding: Float): Boolean {
    val radians = -overlay.rotationDegrees * PI.toFloat() / 180f
    val local = rotateOffset(point - overlay.center, radians)
    return abs(local.x) <= overlay.size.width / 2f + padding &&
        abs(local.y) <= overlay.size.height / 2f + padding
}

// 3x3-Rotationsmatrix (row-major) für die Arcball-Himmelsdrehung. Der Zustand des virtuellen
// Sternhimmels ist EINE freie Rotation (equ->Ansicht); Fingerbewegungen multiplizieren inkrementelle
// Bildschirm-Achsen-Rotationen davor, sodass der Himmel IMMER dem Finger folgt – ohne Euler-Gimbal,
// Pol-Umschlag oder Anschlag. tx/ty/tz vermeiden Allokationen beim Projizieren vieler Sterne.
private class Mat3(val m: FloatArray) {
    operator fun times(o: Mat3): Mat3 {
        val a = m
        val b = o.m
        val r = FloatArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                var s = 0f
                for (k in 0..2) s += a[i * 3 + k] * b[k * 3 + j]
                r[i * 3 + j] = s
            }
        }
        return Mat3(r)
    }

    fun tx(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z
    fun ty(x: Float, y: Float, z: Float) = m[3] * x + m[4] * y + m[5] * z
    fun tz(x: Float, y: Float, z: Float) = m[6] * x + m[7] * y + m[8] * z

    companion object {
        val IDENTITY = Mat3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))
        fun rotX(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c))
        }
        fun rotY(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c))
        }
        fun rotZ(a: Float): Mat3 {
            val c = cos(a); val s = sin(a)
            return Mat3(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        }
    }
}

private fun projectSkyPoint(
    star: com.codex.starmapper.domain.StarNode,
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean = false,
    mirrorY: Boolean = false,
): Offset? {
    return projectSkyPoint(
        point = SkyPoint(raDegrees = star.raHours * 15f, decDegrees = star.decDegrees),
        rot = rot,
        radius = radius,
        center = center,
        mirrorX = mirrorX,
        mirrorY = mirrorY,
    )
}

private fun projectSkyPoint(
    point: SkyPoint,
    rot: Mat3,
    radius: Float,
    center: Offset,
    mirrorX: Boolean = false,
    mirrorY: Boolean = false,
): Offset? {
    val ra = point.raDegrees / 180f * PI.toFloat()
    val dec = point.decDegrees / 180f * PI.toFloat()
    val mirrorXFactor = if (mirrorX) -1f else 1f
    val mirrorYFactor = if (mirrorY) -1f else 1f
    // Basisvektor (equ, gespiegelt) -> per Arcball-Matrix in die Ansicht drehen. Identität ergibt exakt
    // die frühere Startansicht (yaw=pitch=roll=0).
    val bx = cos(dec) * sin(ra) * mirrorXFactor
    val by = sin(dec) * mirrorYFactor
    val bz = cos(dec) * cos(ra)
    val vz = rot.tz(bx, by, bz)
    if (vz < -0.08f) return null
    val vx = rot.tx(bx, by, bz)
    val vy = rot.ty(bx, by, bz)
    val compression = 0.72f + vz.coerceAtLeast(0f) * 0.28f
    return center + Offset(vx * radius * compression, -vy * radius * compression)
}

private fun wrapAngle(value: Float): Float {
    val fullTurn = 2f * PI.toFloat()
    var wrapped = value % fullTurn
    if (wrapped > PI.toFloat()) wrapped -= fullTurn
    if (wrapped < -PI.toFloat()) wrapped += fullTurn
    return wrapped
}

// RecordingCanvas verweigert Bitmaps oberhalb eines gerätespezifischen Limits
// (garantiert sind nur 100 MB). Größere Bilder werden für die Anzeige
// herunterskaliert; die Originalmasse bleiben für die FOV-Berechnung erhalten.
private const val MAX_DISPLAY_BITMAP_BYTES = 100L * 1024L * 1024L
private const val MAX_DISPLAY_DIMENSION_PX = 6500

// Maximale lange Kante des Offscreen-Overlay-Caches (Speicher/Texturgrenze). Nahe der typischen
// Bildauflösung -> Overlays bleiben weitgehend scharf; weicher erst bei extremem Hineinzoomen.
// Betrifft NUR die Editor-Vorschau; der Export rendert weiterhin in voller Auflösung (1:1).
private const val OVERLAY_CACHE_MAX_LONG_EDGE = 4096

// Offscreen-Cache der statischen Overlays für die Editor-Vorschau.
private class OverlayCache(
    val image: ImageBitmap,
    val scale: Float,
    val width: Int,
    val height: Int,
)

private fun buildOverlayCache(bitmap: Bitmap): OverlayCache {
    val longEdge = max(bitmap.width, bitmap.height)
    val cacheScale = (OVERLAY_CACHE_MAX_LONG_EDGE.toFloat() / longEdge).coerceAtMost(1f)
    val cw = (bitmap.width * cacheScale).roundToInt().coerceAtLeast(1)
    val ch = (bitmap.height * cacheScale).roundToInt().coerceAtLeast(1)
    return OverlayCache(
        image = ImageBitmap(cw, ch, ImageBitmapConfig.Argb8888),
        scale = cacheScale,
        width = cw,
        height = ch,
    )
}

private data class LoadedEditorImage(
    val bitmap: Bitmap,
    val originalWidth: Int,
    val originalHeight: Int,
)

private fun displayScaleFor(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val byteScale = sqrt(
        MAX_DISPLAY_BITMAP_BYTES.toDouble() / (width.toDouble() * height.toDouble() * 4.0),
    )
    val dimensionScale = MAX_DISPLAY_DIMENSION_PX.toDouble() / max(width, height)
    return minOf(1.0, byteScale, dimensionScale).toFloat()
}

// Originalbild in VOLLER Auflösung dekodieren (ohne den Anzeige-Downscale aus loadEditorImage) –
// nur für den Original-Export. Software-Bitmap, da der Export-Canvas Software ist. Bei sehr großen
// Panoramen kann das scheitern (OOM) -> null; der Aufrufer fällt dann aufs Anzeige-Bitmap zurück.
private fun decodeOriginalBitmap(context: Context, uri: Uri?): Bitmap? {
    if (uri == null) return null
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                )
            }
        }
    } catch (t: Throwable) {
        AppDiagnostics.record("export_original_decode_failed msg=${t.message}")
        null
    }
}

// Größe der Original-Bilddatei in Byte (Nutzer-Auftrag 2026-09-02, "Originalexport"-Diagnose) -- über
// den FileDescriptor statt InputStream.available() (bei content://-Uris nicht zuverlässig die echte
// Gesamtgröße). null bei jedem Fehlschlag (z.B. Uri seit dem Laden ungültig geworden) statt Absturz.
private fun originalFileSizeBytes(context: Context, uri: Uri?): Long? {
    if (uri == null) return null
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull()
}

@Composable
private fun rememberEditorImageFromUri(uri: Uri?): LoadedEditorImage? {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<LoadedEditorImage?>(null) }

    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            uri?.let { loadEditorImage(context, it) }
        }
    }

    return image
}

private fun loadEditorImage(context: Context, uri: Uri): LoadedEditorImage? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            var originalWidth = 0
            var originalHeight = 0
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                originalWidth = info.size.width
                originalHeight = info.size.height
                val scale = displayScaleFor(originalWidth, originalHeight)
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (originalWidth * scale).roundToInt().coerceAtLeast(1),
                        (originalHeight * scale).roundToInt().coerceAtLeast(1),
                    )
                }
            }
            LoadedEditorImage(
                bitmap = bitmap,
                originalWidth = if (originalWidth > 0) originalWidth else bitmap.width,
                originalHeight = if (originalHeight > 0) originalHeight else bitmap.height,
            )
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val originalWidth = bounds.outWidth
            val originalHeight = bounds.outHeight
            val options = BitmapFactory.Options()
            if (originalWidth > 0 && originalHeight > 0) {
                var sampleSize = 1
                while (displayScaleFor(originalWidth / sampleSize, originalHeight / sampleSize) < 1f) {
                    sampleSize *= 2
                }
                options.inSampleSize = sampleSize
            }
            val bitmap = context.contentResolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            LoadedEditorImage(
                bitmap = bitmap,
                originalWidth = if (originalWidth > 0) originalWidth else bitmap.width,
                originalHeight = if (originalHeight > 0) originalHeight else bitmap.height,
            )
        }
    } catch (_: Exception) {
        null
    }
}

private fun Context.shareImage(uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.label_mapmysky_export)))
}

private fun Context.shareTextFile(uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_SUBJECT,
            getString(R.string.label_mapmysky_diagnostics),
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, getString(R.string.action_share_diagnostics)))
}
