package com.codex.starmapper.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.codex.starmapper.R
import com.codex.starmapper.domain.CatalogStar
import com.codex.starmapper.processing.FisheyeProjection
import com.codex.starmapper.processing.FisheyeRefiner
import com.codex.starmapper.processing.PanoProjectionKind
import com.codex.starmapper.processing.PanoramaWcsSolution
import com.codex.starmapper.processing.StarBoost
import com.codex.starmapper.processing.Vec3
import com.codex.starmapper.processing.raDecToVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min
import kotlin.math.roundToInt

private const val SQRT2 = 1.41421356f
private val PINK = Color(0xFFFF4FD8)

/** Eine Referenz: heller Namensstern + (vom Nutzer bestätigte) Bildposition. Beobachtbar. */
private class AlignRef(
    val star: CatalogStar,
    val dir: Vec3,
    imagePos: Offset,
    active: Boolean,
    deleted: Boolean = false,
) {
    var imagePos by mutableStateOf(imagePos)
    var active by mutableStateOf(active)
    var deleted by mutableStateOf(deleted)
}

private data class RefSnap(val imagePos: Offset, val active: Boolean, val deleted: Boolean)

/**
 * Persistierbarer Zustand EINER Referenz (über Verlassen/Wieder-Öffnen der Feinjustierung hinweg,
 * solange KEIN neues Solve/neue Kacheln). Vom Aufrufer gehalten und zurückgegeben.
 */
data class AlignRefSnapshot(
    val star: CatalogStar,
    val imagePos: Offset,
    val active: Boolean,
    val deleted: Boolean,
)

/** Ergebnis der Erkennungs-Phase einer Geste (null = Long-Press = Marker ziehen). */
private enum class AlignGesture { Tap, Pan, TwoFinger }

/**
 * Vollbild-Feinausrichtung. Bedienung: 1 Finger schieben = Bild verschieben (Pan), 2 Finger =
 * zoomen, langes Drücken auf den Zieh-Griff eines Markers = diesen verschieben (der Griff sitzt
 * versetzt neben dem Stern, damit der Finger den Stern NICHT verdeckt). Ein gezogener Marker
 * pulsiert pink. Marker, die nicht zugeordnet werden können, lassen sich löschen; Rückgängig/
 * Wiederholen und Reset stehen bereit. Layout berücksichtigt System-Leisten (status/navigation) und
 * passt sich an jede Bildschirmgröße an. „Anwenden" gibt die fertige Lösung zurück.
 */
@Composable
fun FisheyeAlignScreen(
    bitmap: Bitmap,
    seed: PanoramaWcsSolution,
    brightStars: List<CatalogStar>,
    // Voller Katalog benannter Sterne (zum Suchen + manuellen Platzieren; auch solche, die nicht geseedet sind).
    allNamedStars: List<CatalogStar> = emptyList(),
    detectedStars: List<Offset>,
    projectionKind: PanoProjectionKind = PanoProjectionKind.Fisheye,
    // Restfehler des Seeds (falls bekannt) -> "Anwenden" ohne jede Ziehung liefert trotzdem einen
    // sinnvollen RMS-Wert statt null.
    seedRms: Double? = null,
    onApply: (PanoramaWcsSolution, Double?) -> Unit,
    onClose: () -> Unit,
    // Beim Verlassen den aktuellen Fit sichern -> beim Wieder-Öffnen ist die letzte Ausrichtung da.
    onPersistFit: (PanoramaWcsSolution) -> Unit = {},
    // Gespeicherter Referenz-Zustand (null = frisch aus dem Seed aufbauen). Ermöglicht, dass die
    // Feinjustierung beim Wieder-Öffnen EXAKT so aussieht wie verlassen (ohne neues Solve).
    initialRefs: List<AlignRefSnapshot>? = null,
    onPersistRefs: (List<AlignRefSnapshot>) -> Unit = {},
) {
    val imageW = bitmap.width
    val imageH = bitmap.height
    val centralRadius = min(imageW, imageH) * 0.30f
    val markerMargin = min(imageW, imageH) * 0.10f // Marker bis ±10% außerhalb behalten (greifbar).
    val imageCenter = Offset(imageW / 2f, imageH / 2f)

    val density = LocalDensity.current
    val sideInsetPx = with(density) { 10.dp.toPx() }
    val handleLenPx = with(density) { 52.dp.toPx() }
    val ringRadiusPx = with(density) { 8.dp.toPx() }
    val knobRadiusPx = with(density) { 11.dp.toPx() }
    val grabRadiusPx = with(density) { 44.dp.toPx() }
    val snapRadiusPx = with(density) { 22.dp.toPx() } // kleiner, zoom-bewusster Einrast-Radius.

    // Referenz-Marker: entweder EXAKT der gespeicherte Zustand (Wieder-Öffnen ohne neues Solve) oder
    // frisch aus der Seed-Projektion (die hellsten Namenssterne an ihrer Seed-Position).
    val refs: SnapshotStateList<AlignRef> = remember(seed, brightStars, initialRefs) {
        if (initialRefs != null && initialRefs.isNotEmpty()) {
            initialRefs.map { s ->
                val dir = raDecToVector(s.star.point.raDegrees.toDouble(), s.star.point.decDegrees.toDouble())
                AlignRef(s.star, dir, s.imagePos, s.active, s.deleted)
            }.toMutableStateList()
        } else {
            brightStars.mapNotNull { star ->
                val p = seed.skyToImage(star.point, imageH) ?: return@mapNotNull null
                if (p.x < -markerMargin || p.x > imageW + markerMargin ||
                    p.y < -markerMargin || p.y > imageH + markerMargin
                ) {
                    return@mapNotNull null
                }
                val dir = raDecToVector(star.point.raDegrees.toDouble(), star.point.decDegrees.toDouble())
                val central = (p - imageCenter).getDistance() <= centralRadius
                AlignRef(star, dir, p, active = central)
            }.toMutableStateList()
        }
    }
    var currentFit by remember(seed) { mutableStateOf(seed) }
    var currentRms by remember(seed) { mutableStateOf(seedRms) }
    var dragIndex by remember { mutableStateOf(-1) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var dragKnobOffset by remember { mutableStateOf(Offset.Zero) }
    var snapPreview by remember { mutableStateOf<Offset?>(null) } // Snap-Ziel (Bild-Px) während Ziehen.
    var deleteMode by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    // Stern-Suche + manuelles Platzieren: gewählter Stern wartet auf einen Tipp ins Bild.
    var searchQuery by remember { mutableStateOf("") }
    var pendingPlaceStar by remember { mutableStateOf<CatalogStar?>(null) }

    // Sicht (Zoom/Pan): Bild->Bildschirm = offset + p*scale.
    var viewScale by remember(seed) { mutableFloatStateOf(0f) }
    var viewOffset by remember(seed) { mutableStateOf(Offset.Zero) }
    var baseScale by remember(seed) { mutableFloatStateOf(0f) }

    // Framing mit festen Rändern (oben Aktions-Bubble, unten Steuer-Popup/Reopen-Pille) -> robust,
    // unabhängig von gemessenen Leisten (die es nach dem Popup-Umbau nicht mehr gibt).
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var fitted by remember(seed) { mutableStateOf(false) }
    val topInsetPx = with(density) { 96.dp.toPx() }
    val bottomInsetPx = with(density) { 120.dp.toPx() }
    // Steuer-Popup sichtbar? Schließen (Tipp ins Freie/Back) lässt den Feinjustier-MODUS bestehen
    // (Sterne weiter ziehbar); eine Reopen-Pille holt die Steuerung zurück.
    var panelOpen by remember { mutableStateOf(true) }
    // Bild EINMAL als GPU-Hardware-Bitmap hochladen (statt pro Frame asImageBitmap() neu zu wrappen)
    // -> deutlich flüssigeres Pan/Zoom (GPU-Textur, kein Re-Upload je Frame). Fallback: Software-Bild.
    val displayImage = remember(bitmap) {
        (runCatching { bitmap.copy(Bitmap.Config.HARDWARE, false) }.getOrNull() ?: bitmap).asImageBitmap()
    }
    // "Sterne hervorheben": rechnet den glatten Untergrund (Lichtglocke/Airglow/LP-Verlauf) raus, damit
    // schwache Sterne auch in hellen Bereichen als Punkte sichtbar werden. NUR Anzeige (Solve/Export bleiben).
    // Einmal berechnet (off-thread) + gecacht; deckungsgleich, weil auf denselben Ziel-Rahmen skaliert.
    var starBoost by remember { mutableStateOf(false) }
    var boostImage by remember(bitmap) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(bitmap, starBoost) {
        if (starBoost && boostImage == null) {
            boostImage = withContext(Dispatchers.Default) {
                val enhanced = StarBoost.enhance(bitmap)
                val hw = runCatching { enhanced.copy(Bitmap.Config.HARDWARE, false) }.getOrNull()
                val img = (hw ?: enhanced).asImageBitmap()
                if (hw != null) enhanced.recycle() // Software-Zwischenbild frei, wenn GPU-Kopie genutzt wird
                img
            }
        }
    }
    val shownImage = if (starBoost) (boostImage ?: displayImage) else displayImage

    // Puls für den gerade gezogenen Marker (pink).
    val pulse by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
    )

    fun imageToScreen(p: Offset): Offset = viewOffset + p * viewScale
    fun screenToImage(s: Offset): Offset = if (viewScale != 0f) (s - viewOffset) / viewScale else s

    // Griff sitzt diagonal zur Bildmitte hin (bleibt sichtbar, egal wo der Marker liegt).
    fun knobOffsetFor(ringScreen: Offset): Offset {
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        val k = handleLenPx / SQRT2
        val dx = if (ringScreen.x > cx) -1f else 1f
        val dy = if (ringScreen.y > cy) -1f else 1f
        return Offset(dx * k, dy * k)
    }

    val history = remember(refs) {
        mutableStateListOf(refs.map { RefSnap(it.imagePos, it.active, it.deleted) })
    }
    var historyIndex by remember(refs) { mutableIntStateOf(0) }

    fun refit() {
        val active = refs.filter { it.active && !it.deleted }
        if (active.size >= 3) {
            // Mit dem beim Kachel-Lösen gewählten Modell fitten (Fisheye/Equirect/Zyl/Mercator).
            // enforceFullPanoramaPeriod=true: dieser manuelle Feinjustier-"Goldstandard" kann per
            // onApply direkt zu lastSolvedWcs werden (s. [[project_gradnetz_randbeschriftung]]
            // Runde-3-Aufrufstellen-Analyse) -- soll die 360°-Periode genauso respektieren wie der
            // automatische Kachel-Solve.
            FisheyeRefiner.calibratePanorama(
                active.map { it.imagePos to it.dir }, imageW, imageH, setOf(projectionKind),
                enforceFullPanoramaPeriod = true,
            )?.let { currentFit = it.solution; currentRms = it.rms }
        }
        for (r in refs) {
            if (!r.active && !r.deleted) currentFit.skyToImage(r.star.point, imageH)?.let { r.imagePos = it }
        }
    }

    // Eindeutiger Einrast-Kandidat (Bild-Px) zum Ziehpunkt – oder null (dann bleibt die
    // Nutzerposition exakt erhalten). Radius klein & zoom-bewusst; bei Mehrdeutigkeit KEIN Snap.
    fun snapCandidate(imagePoint: Offset): Offset? {
        if (detectedStars.isEmpty() || viewScale <= 0f) return null
        val radiusImg = snapRadiusPx / viewScale
        var best: Offset? = null
        var bestD = Float.MAX_VALUE
        var secondD = Float.MAX_VALUE
        for (b in detectedStars) {
            val d = (b - imagePoint).getDistance()
            if (d < bestD) { secondD = bestD; bestD = d; best = b } else if (d < secondD) { secondD = d }
        }
        if (best == null || bestD > radiusImg) return null
        // Nur bei EINDEUTIGEM Kandidaten: entweder nur einer im Radius, oder der nächste klar näher
        // als der zweitnächste (Ratio-Test). Sonst null -> Nutzerposition bleibt exakt erhalten.
        val unique = secondD > radiusImg || bestD <= 0.5f * secondD
        return if (unique) best else null
    }

    fun applySnap(snap: List<RefSnap>) {
        refs.forEachIndexed { i, r ->
            if (i < snap.size) {
                r.imagePos = snap[i].imagePos
                r.active = snap[i].active
                r.deleted = snap[i].deleted
            } else {
                // In diesem (älteren) Snapshot noch nicht vorhanden -> später manuell platziert: ausblenden.
                r.deleted = true
            }
        }
    }

    fun pushHistory() {
        while (history.lastIndex > historyIndex) history.removeAt(history.lastIndex)
        history.add(refs.map { RefSnap(it.imagePos, it.active, it.deleted) })
        historyIndex = history.lastIndex
    }

    fun undo() {
        if (historyIndex > 0) { historyIndex -= 1; applySnap(history[historyIndex]); refit() }
    }

    fun redo() {
        if (historyIndex < history.lastIndex) { historyIndex += 1; applySnap(history[historyIndex]); refit() }
    }

    fun reset() {
        applySnap(history[0])
        while (history.lastIndex > 0) history.removeAt(history.lastIndex)
        historyIndex = 0
        currentFit = seed
        currentRms = seedRms
    }

    fun deleteNearest(screenPoint: Offset) {
        var idx = -1
        var bestD = grabRadiusPx
        refs.forEachIndexed { i, r ->
            if (r.deleted) return@forEachIndexed
            val d = (imageToScreen(r.imagePos) - screenPoint).getDistance()
            if (d < bestD) { bestD = d; idx = i }
        }
        if (idx >= 0) { refs[idx].deleted = true; refit(); pushHistory() }
    }

    // Gesuchten Stern manuell an einer Bildposition setzen (aktiver Referenzstern -> fliesst in den Fit).
    // Ist der Stern schon vorhanden, wird er nur verschoben/reaktiviert (kein Duplikat).
    fun placeStar(star: CatalogStar, atImage: Offset) {
        val existing = refs.indexOfFirst {
            it.star.properName.isNotBlank() && it.star.properName == star.properName
        }
        if (existing >= 0) {
            refs[existing].imagePos = atImage
            refs[existing].active = true
            refs[existing].deleted = false
        } else {
            val dir = raDecToVector(star.point.raDegrees.toDouble(), star.point.decDegrees.toDouble())
            refs.add(AlignRef(star, dir, atImage, active = true))
        }
        refit()
        pushHistory()
    }

    LaunchedEffect(canvasSize) {
        if (!fitted && canvasSize.width > 0 && canvasSize.height > 0) {
            val fit = fitViewport(canvasSize, imageW, imageH, topInsetPx, bottomInsetPx, sideInsetPx)
            baseScale = fit.scale
            viewScale = fit.scale
            viewOffset = fit.offset
            fitted = true
        }
    }

    fun snapshotRefs(): List<AlignRefSnapshot> =
        refs.map { AlignRefSnapshot(it.star, it.imagePos, it.active, it.deleted) }

    // Aktuellen Fit + Referenz-Zustand sichern + Modus verlassen.
    fun exit() {
        onPersistRefs(snapshotRefs())
        onPersistFit(currentFit)
        onClose()
    }

    // Back: erst das Steuer-Popup schließen (Modus bleibt), sonst den Modus verlassen.
    BackHandler { if (panelOpen) panelOpen = false else exit() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop

                    suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.twoFinger(initial: PointerEvent) {
                        var prevC = centroid(initial.changes.filter { it.pressed })
                        var prevSpan = averageSpan(initial.changes.filter { it.pressed }, prevC)
                        initial.changes.forEach { if (it.pressed) it.consume() }
                        while (true) {
                            val event = awaitPointerEvent()
                            val pts = event.changes.filter { it.pressed }
                            if (pts.size < 2) return
                            val c = centroid(pts)
                            val span = averageSpan(pts, c)
                            viewOffset += (c - prevC)
                            if (prevSpan > 1f && span > 1f && viewScale > 0f) {
                                val target = (viewScale * (span / prevSpan))
                                    .coerceIn(baseScale * 0.8f, baseScale * 8f)
                                val applied = target / viewScale
                                viewOffset = c - (c - viewOffset) * applied
                                viewScale = target
                            }
                            prevC = c
                            prevSpan = span
                            pts.forEach { it.consume() }
                        }
                    }

                    suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.panLoop(id: PointerId, start: Offset) {
                        var prev = start
                        while (true) {
                            val event = awaitPointerEvent()
                            val pts = event.changes.filter { it.pressed }
                            if (pts.isEmpty()) return
                            if (pts.size >= 2) { twoFinger(event); return }
                            val change = event.changes.firstOrNull { it.id == id } ?: pts.first()
                            if (!change.pressed) return
                            viewOffset += (change.position - prev)
                            prev = change.position
                            change.consume()
                        }
                    }

                    // Nach Long-Press: den Marker am nächsten Zieh-Griff greifen und ziehen.
                    suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.dragMarkerLoop(id: PointerId, hold: Offset) {
                        var idx = -1
                        var bestD = grabRadiusPx
                        var capturedOff = Offset.Zero
                        refs.forEachIndexed { i, r ->
                            if (r.deleted) return@forEachIndexed
                            val ringS = imageToScreen(r.imagePos)
                            val off = knobOffsetFor(ringS)
                            val d = (ringS + off - hold).getDistance()
                            if (d < bestD) { bestD = d; idx = i; capturedOff = off }
                        }
                        if (idx < 0) {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) return
                            }
                        }
                        dragKnobOffset = capturedOff
                        dragIndex = idx
                        dragPos = hold
                        snapPreview = snapCandidate(screenToImage(hold - capturedOff))
                        while (true) {
                            val event = awaitPointerEvent()
                            val pts = event.changes.filter { it.pressed }
                            if (pts.isEmpty()) break
                            val change = event.changes.firstOrNull { it.id == id } ?: pts.first()
                            if (!change.pressed) break
                            dragPos = change.position
                            snapPreview = snapCandidate(screenToImage(dragPos - capturedOff))
                            change.consume()
                        }
                        // Loslassen: nur bei eindeutigem Kandidaten einrasten, sonst exakt dort lassen.
                        val ringImg = screenToImage(dragPos - capturedOff)
                        refs[idx].imagePos = snapCandidate(ringImg) ?: ringImg
                        refs[idx].active = true
                        snapPreview = null
                        dragIndex = -1
                        refit()
                        pushHistory()
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var lastPoint = down.position
                        var panStart = down.position
                        var twoFingerEvent: PointerEvent? = null
                        val outcome = withTimeoutOrNull(MOVE_LONG_PRESS_MS) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pts = event.changes.filter { it.pressed }
                                if (pts.isEmpty()) return@withTimeoutOrNull AlignGesture.Tap
                                if (pts.size >= 2) {
                                    twoFingerEvent = event
                                    return@withTimeoutOrNull AlignGesture.TwoFinger
                                }
                                val change = event.changes.firstOrNull { it.id == down.id } ?: pts.first()
                                lastPoint = change.position
                                if (!change.pressed) return@withTimeoutOrNull AlignGesture.Tap
                                if ((change.position - down.position).getDistance() >= slop) {
                                    panStart = change.position
                                    change.consume()
                                    return@withTimeoutOrNull AlignGesture.Pan
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            AlignGesture.Tap
                        }
                        when (outcome) {
                            null -> if (!deleteMode) dragMarkerLoop(down.id, lastPoint)
                            AlignGesture.Pan -> panLoop(down.id, panStart)
                            AlignGesture.TwoFinger -> twoFingerEvent?.let { twoFinger(it) }
                            AlignGesture.Tap -> {
                                val toPlace = pendingPlaceStar
                                if (toPlace != null) {
                                    placeStar(toPlace, screenToImage(lastPoint))
                                    pendingPlaceStar = null
                                } else if (deleteMode) {
                                    deleteNearest(lastPoint)
                                }
                            }
                        }
                    }
                },
        ) {
            if (viewScale <= 0f) return@Canvas
            drawImage(
                image = shownImage,
                dstOffset = IntOffset(viewOffset.x.roundToInt(), viewOffset.y.roundToInt()),
                dstSize = IntSize((imageW * viewScale).roundToInt(), (imageH * viewScale).roundToInt()),
            )
            // Vorschau: jeder (nicht gelöschte) Referenzstern per aktuellem Fit.
            for (r in refs) {
                if (r.deleted) continue
                // Nur beim radialen Fisheye-Modell Sterne hinter dem ~180°-Feld (θ>91°) verwerfen;
                // bei der zylindrischen Familie ist die ganze Kugel abbildbar (kein z-Cull).
                if (currentFit.projection is FisheyeProjection &&
                    (currentFit.rotEquToPano * r.dir).z < -0.0175
                ) {
                    continue
                }
                val p = currentFit.skyToImage(r.star.point, imageH) ?: continue
                drawCircle(
                    Color(0xFF70E1F5).copy(alpha = 0.7f), 3.2.dp.toPx(), imageToScreen(p),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 11.dp.toPx()
                setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
            }
            // Snap-Vorschau: zeigt während des Ziehens das eindeutige Einrast-Ziel (grüner Ring).
            snapPreview?.let { sp ->
                val s = imageToScreen(sp)
                drawCircle(Color(0xFF66FF99), ringRadiusPx * 1.7f, s, style = Stroke(width = 2.dp.toPx()))
            }
            // Marker: offener Stern-Ring + versetzter, GEFÜLLTER Zieh-Griff. Gezogen = pink pulsierend.
            refs.forEachIndexed { i, r ->
                if (r.deleted) return@forEachIndexed
                val dragging = i == dragIndex
                val ringS = if (dragging) dragPos - dragKnobOffset else imageToScreen(r.imagePos)
                val knobOff = if (dragging) dragKnobOffset else knobOffsetFor(ringS)
                val knobS = ringS + knobOff
                val col = if (dragging) {
                    PINK.copy(alpha = 0.6f + 0.4f * pulse)
                } else if (r.active) {
                    Color(0xFFFFD166)
                } else {
                    Color.White.copy(alpha = 0.85f)
                }
                if (dragging) {
                    drawCircle(PINK.copy(alpha = 0.25f * pulse), ringRadiusPx + 10.dp.toPx() * pulse, ringS)
                }
                // Verbindungslinie dockt am ÄUSSEREN Ringrand UND am Knopfrand an (läuft nicht hinein).
                val toKnob = knobS - ringS
                val len = toKnob.getDistance()
                if (len > 0.001f) {
                    val dir = toKnob / len
                    drawLine(col, ringS + dir * ringRadiusPx, knobS - dir * knobRadiusPx, strokeWidth = 2.dp.toPx())
                }
                // Zieh-Griff (Knopf): innen VOLL gefüllt (weiß; beim Ziehen pink) + dünne Statuskontur.
                drawCircle(if (dragging) PINK.copy(alpha = 0.85f) else Color.White, knobRadiusPx, knobS)
                drawCircle(col, knobRadiusPx, knobS, style = Stroke(width = 2.dp.toPx()))
                // Stern-Ring: offen/transparent (kein gefüllter Mittelpunkt) über dem echten Stern.
                drawCircle(col, ringRadiusPx, ringS, style = Stroke(width = 2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(
                    r.star.properDisplayName(AppLocale.resolvedLanguageTag), ringS.x + 10.dp.toPx(), ringS.y - 8.dp.toPx(), labelPaint,
                )
            }
        }

        // Globale Aktions-Box (oben-mittig, wie überall): Vor/Zurück für die Feinjustierung.
        GlobalActionBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 2.dp, start = 8.dp, end = 8.dp),
            onUndo = { undo() },
            onRedo = { redo() },
        )

        val activeCount = refs.count { it.active && !it.deleted }
        if (panelOpen) {
            // Steuerung als einheitliches Popup unten (gleiche Hülle wie alle Menüs). Tipp ins Freie
            // schließt nur das Popup, der Feinjustier-Modus bleibt aktiv (Sterne weiter ziehbar).
            AppBottomPopup(onDismiss = { panelOpen = false }) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.fisheye_title),
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconToggleButton(checked = deleteMode, onCheckedChange = { deleteMode = it }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.fisheye_delete_mode),
                                tint = if (deleteMode) PINK else Color.White,
                            )
                        }
                        IconButton(onClick = { showResetConfirm = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.fisheye_reset), tint = Color.White)
                        }
                        // Sterne hervorheben: Untergrund (Lichtglocke/Airglow/LP) rausrechnen -> Sterne sichtbar.
                        IconToggleButton(checked = starBoost, onCheckedChange = { starBoost = it }) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = stringResource(R.string.fisheye_boost_stars),
                                tint = if (starBoost) Color(0xFFFFD166) else Color.White,
                            )
                        }
                    }
                    Text(
                        if (deleteMode) {
                            stringResource(R.string.fisheye_delete_hint)
                        } else {
                            stringResource(R.string.fisheye_status_hint, activeCount)
                        },
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    // Stern suchen + manuell platzieren: Name eingeben, Treffer tippen -> Menü schließt,
                    // dann auf den Stern im Bild tippen. Nutzt den vollen Namens-Katalog (auch nicht-geseedete).
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.fisheye_search_label)) },
                        placeholder = { Text(stringResource(R.string.fisheye_search_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val query = searchQuery.trim()
                    if (query.isNotEmpty()) {
                        val matches = allNamedStars.asSequence()
                            .filter { star ->
                                star.properName.contains(query, ignoreCase = true) ||
                                    star.localizedProperNames.values.any { it.contains(query, ignoreCase = true) }
                            }
                            .distinctBy { it.properName }
                            .sortedBy { it.magnitude }
                            .take(6)
                            .toList()
                        if (matches.isEmpty()) {
                            Text(
                                stringResource(R.string.fisheye_no_star_found),
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            )
                        }
                        matches.forEach { star ->
                            TextButton(
                                onClick = {
                                    pendingPlaceStar = star
                                    searchQuery = ""
                                    deleteMode = false
                                    panelOpen = false // Menü zu -> Bild frei zum Antippen
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(
                                        R.string.fisheye_place_star,
                                        star.properDisplayName(AppLocale.resolvedLanguageTag),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { onPersistRefs(snapshotRefs()); onApply(currentFit, currentRms) },
                        enabled = activeCount >= 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.fisheye_apply))
                    }
                    TextButton(onClick = { exit() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.fisheye_exit))
                    }
                    Text(
                        stringResource(R.string.fisheye_menu_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        } else {
            // Reopen-Pille: holt die Steuerung zurück (Modus war die ganze Zeit aktiv).
            Button(
                onClick = { panelOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.fisheye_reopen_menu))
            }
        }

        // Platzier-Hinweis: gewählter Stern wartet auf einen Tipp ins Bild.
        pendingPlaceStar?.let { star ->
            Surface(
                color = Color(0xFF241E14),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 84.dp, start = 16.dp, end = 16.dp),
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFFFD166))
                    Text(
                        stringResource(
                            R.string.fisheye_tap_star_hint,
                            star.properDisplayName(AppLocale.resolvedLanguageTag),
                        ),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { pendingPlaceStar = null }) {
                        Text(stringResource(R.string.notif_cancel))
                    }
                }
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text(stringResource(R.string.fisheye_reset_confirm_title)) },
                text = {
                    Text(stringResource(R.string.fisheye_reset_confirm_text))
                },
                confirmButton = {
                    TextButton(onClick = { showResetConfirm = false; reset() }) {
                        Text(stringResource(R.string.fisheye_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text(stringResource(R.string.fisheye_no))
                    }
                },
            )
        }
    }
}

/** Schwerpunkt der gedrückten Finger (Bildschirmkoordinaten). */
private fun centroid(changes: List<PointerInputChange>): Offset {
    if (changes.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    for (c in changes) { x += c.position.x; y += c.position.y }
    return Offset(x / changes.size, y / changes.size)
}

/** Mittlerer Abstand der Finger zum Schwerpunkt (Maß für die Spreizung beim Zoomen). */
private fun averageSpan(changes: List<PointerInputChange>, center: Offset): Float {
    if (changes.isEmpty()) return 0f
    var s = 0f
    for (c in changes) s += (c.position - center).getDistance()
    return s / changes.size
}

private class FitViewport(val scale: Float, val offset: Offset)

private fun fitViewport(
    canvas: IntSize,
    imageW: Int,
    imageH: Int,
    topInset: Float,
    bottomInset: Float,
    sideInset: Float,
): FitViewport {
    val availW = (canvas.width - 2f * sideInset).coerceAtLeast(1f)
    val availH = (canvas.height - topInset - bottomInset).coerceAtLeast(1f)
    val scale = min(availW / imageW, availH / imageH)
    val offX = sideInset + (availW - imageW * scale) / 2f
    val offY = topInset + (availH - imageH * scale) / 2f
    return FitViewport(scale, Offset(offX, offY))
}
