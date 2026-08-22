package com.codex.starmapper.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.codex.starmapper.data.ConstellationCatalog
import com.codex.starmapper.domain.AnnotationOverlay
import com.codex.starmapper.domain.DetectedStar
import com.codex.starmapper.domain.EditorTool

internal class EditorSessionViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val imageUriState = mutableStateOf(
        savedStateHandle.get<String>(IMAGE_URI_KEY)?.let(Uri::parse),
    )
    val overlays = mutableStateListOf<AnnotationOverlay>()
    val solvePreviewOverlays = mutableStateListOf<AnnotationOverlay>()
    val solvePreviewStarsState = mutableStateOf<List<DetectedStar>>(emptyList())
    val selectedToolState = mutableStateOf(EditorTool.Move)
    val selectedOverlayIdState = mutableStateOf<Long?>(null)
    val nextOverlayIdState = mutableLongStateOf(1L)
    val selectedConstellationState = mutableStateOf(ConstellationCatalog.featured.first())
    val detectedStarsState = mutableStateOf<List<DetectedStar>>(emptyList())
    val showDetectedStarsState = mutableStateOf(false)
    val showConstellationAnchorsState = mutableStateOf(false)
    val solveMaskState = mutableStateOf<Bitmap?>(null)
    val solveMaskVersionState = mutableIntStateOf(0)
    // Radiergummi-Maske: blendet Beschriftungen unter dem Strich aus (Editor + Export).
    val annotationEraseMaskState = mutableStateOf<Bitmap?>(null)
    // COMMIT-Version: geht in den (teuren) Overlay-Cache-Schlüssel ein -> nur bei Strichende bumpen,
    // damit der Cache nicht pro Strich-Segment neu gerendert wird.
    val annotationEraseVersionState = mutableIntStateOf(0)
    // LIVE-Version: bumpt pro Strich-Segment und invalidiert NUR die billige, transluzente
    // Radierer-Vorschau (kein Overlay-Cache-Redraw) -> flüssiges Radieren.
    val annotationErasePreviewVersionState = mutableIntStateOf(0)

    // --- Undo/Redo (nur Overlays) ---
    private data class OverlaySnapshot(val overlays: List<AnnotationOverlay>, val nextId: Long)

    private val undoStack = ArrayDeque<OverlaySnapshot>()
    private val redoStack = ArrayDeque<OverlaySnapshot>()
    val canUndoState = mutableStateOf(false)
    val canRedoState = mutableStateOf(false)
    private var pendingSnapshot: OverlaySnapshot? = null
    private var pendingCommitted = false

    private fun snapshot() = OverlaySnapshot(overlays.toList(), nextOverlayIdState.longValue)

    private fun applySnapshot(target: OverlaySnapshot) {
        overlays.clear()
        overlays.addAll(target.overlays)
        nextOverlayIdState.longValue = target.nextId
        selectedOverlayIdState.value = null
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        canUndoState.value = undoStack.isNotEmpty()
        canRedoState.value = redoStack.isNotEmpty()
    }

    private fun pushSnapshot(snapshot: OverlaySnapshot) {
        undoStack.addLast(snapshot)
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        refreshHistoryFlags()
        pushGlobalKind(HistoryKind.Overlay)
    }

    /** Diskrete Aktion (Button/Dialog): vor der Mutation aufrufen. */
    fun recordHistory() = pushSnapshot(snapshot())

    /** Gestenbeginn: Zustand merken, aber noch nicht in den Stack legen. */
    fun beginInteraction() {
        pendingSnapshot = snapshot()
        pendingCommitted = false
    }

    /** Erste Mutation innerhalb der Geste legt genau einen Snapshot ab (Drag-Coalescing). */
    fun recordInteractionMutation() {
        val snapshot = pendingSnapshot ?: return
        if (!pendingCommitted) {
            pushSnapshot(snapshot)
            pendingCommitted = true
        }
    }

    fun endInteraction() {
        pendingSnapshot = null
        pendingCommitted = false
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(snapshot())
        applySnapshot(previous)
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(snapshot())
        applySnapshot(next)
        return true
    }

    // --- Undo/Redo NUR für die Beschriftungs-Radierer-Maske (separate, gedeckelte Bitmap-History) ---
    // Die Maske liegt in 1/4-Auflösung (SolveMask.SCALE) -> Kopien sind klein; Tiefe gedeckelt.
    private val eraseUndoStack = ArrayDeque<Bitmap?>()
    private val eraseRedoStack = ArrayDeque<Bitmap?>()
    val canEraseUndoState = mutableStateOf(false)
    val canEraseRedoState = mutableStateOf(false)

    private fun copyMask(b: Bitmap?): Bitmap? = b?.copy(b.config ?: Bitmap.Config.ARGB_8888, true)

    private fun refreshEraseFlags() {
        canEraseUndoState.value = eraseUndoStack.isNotEmpty()
        canEraseRedoState.value = eraseRedoStack.isNotEmpty()
    }

    /** Vor einem Radierer-Strich (Gestenbeginn) den aktuellen Maskenzustand sichern. */
    fun snapshotEraseMask() {
        eraseUndoStack.addLast(copyMask(annotationEraseMaskState.value))
        while (eraseUndoStack.size > MAX_MASK_HISTORY) eraseUndoStack.removeFirst()
        eraseRedoStack.clear()
        refreshEraseFlags()
        pushGlobalKind(HistoryKind.EraseMask)
    }

    /** @return true, wenn tatsächlich ein Schritt zurückgenommen wurde (false = Stack bereits leer). */
    fun eraseUndo(): Boolean {
        if (eraseUndoStack.isEmpty()) return false
        val previous = eraseUndoStack.removeLast()
        eraseRedoStack.addLast(copyMask(annotationEraseMaskState.value))
        annotationEraseMaskState.value = previous
        annotationEraseVersionState.intValue++
        refreshEraseFlags()
        return true
    }

    fun eraseRedo(): Boolean {
        if (eraseRedoStack.isEmpty()) return false
        val next = eraseRedoStack.removeLast()
        eraseUndoStack.addLast(copyMask(annotationEraseMaskState.value))
        annotationEraseMaskState.value = next
        annotationEraseVersionState.intValue++
        refreshEraseFlags()
        return true
    }

    // --- Undo/Redo NUR für die Vordergrund-Solve-Maske (analog zur Radierer-Maske) ---
    private val solveMaskUndoStack = ArrayDeque<Bitmap?>()
    private val solveMaskRedoStack = ArrayDeque<Bitmap?>()
    val canSolveMaskUndoState = mutableStateOf(false)
    val canSolveMaskRedoState = mutableStateOf(false)

    private fun refreshSolveMaskFlags() {
        canSolveMaskUndoState.value = solveMaskUndoStack.isNotEmpty()
        canSolveMaskRedoState.value = solveMaskRedoStack.isNotEmpty()
    }

    /** Vor einem Maskenpinsel-Strich (Gestenbeginn) den aktuellen Maskenzustand sichern. */
    fun snapshotSolveMask() {
        solveMaskUndoStack.addLast(copyMask(solveMaskState.value))
        while (solveMaskUndoStack.size > MAX_MASK_HISTORY) solveMaskUndoStack.removeFirst()
        solveMaskRedoStack.clear()
        refreshSolveMaskFlags()
        pushGlobalKind(HistoryKind.SolveMask)
    }

    /** @return true, wenn tatsächlich ein Schritt zurückgenommen wurde (false = Stack bereits leer). */
    fun solveMaskUndo(): Boolean {
        if (solveMaskUndoStack.isEmpty()) return false
        val previous = solveMaskUndoStack.removeLast()
        solveMaskRedoStack.addLast(copyMask(solveMaskState.value))
        solveMaskState.value = previous
        solveMaskVersionState.intValue++
        refreshSolveMaskFlags()
        return true
    }

    fun solveMaskRedo(): Boolean {
        if (solveMaskRedoStack.isEmpty()) return false
        val next = solveMaskRedoStack.removeLast()
        solveMaskUndoStack.addLast(copyMask(solveMaskState.value))
        solveMaskState.value = next
        solveMaskVersionState.intValue++
        refreshSolveMaskFlags()
        return true
    }

    // --- Undo/Redo NUR für die Zeichnen-Segmente (Zweifinger-Stift) --------------------------------
    // Fertige Segmente EINER laufenden Zeichnen-Sitzung, VOR dem endgültigen Commit ("Fertig
    // zeichnen" -> ein Overlay). Ein Strich (Finger-B-Halten, Zeichnen ODER Radieren) = ein
    // Undo-Schritt -- analog zu den Masken-Pinseln oben (snapshotXxxMask() bei Strichbeginn).
    val drawSegmentsState = mutableStateOf<List<List<Offset>>>(emptyList())
    private val drawUndoStack = ArrayDeque<List<List<Offset>>>()
    private val drawRedoStack = ArrayDeque<List<List<Offset>>>()
    val canDrawUndoState = mutableStateOf(false)
    val canDrawRedoState = mutableStateOf(false)

    private fun refreshDrawFlags() {
        canDrawUndoState.value = drawUndoStack.isNotEmpty()
        canDrawRedoState.value = drawRedoStack.isNotEmpty()
    }

    /** Vor jedem Strich (Finger B gedrückt, Zeichnen ODER Radieren) den aktuellen Stand sichern. */
    fun snapshotDrawSegments() {
        drawUndoStack.addLast(drawSegmentsState.value)
        while (drawUndoStack.size > MAX_HISTORY) drawUndoStack.removeFirst()
        drawRedoStack.clear()
        refreshDrawFlags()
        pushGlobalKind(HistoryKind.Draw)
    }

    fun drawUndo(): Boolean {
        if (drawUndoStack.isEmpty()) return false
        val previous = drawUndoStack.removeLast()
        drawRedoStack.addLast(drawSegmentsState.value)
        drawSegmentsState.value = previous
        refreshDrawFlags()
        return true
    }

    fun drawRedo(): Boolean {
        if (drawRedoStack.isEmpty()) return false
        val next = drawRedoStack.removeLast()
        drawUndoStack.addLast(drawSegmentsState.value)
        drawSegmentsState.value = next
        refreshDrawFlags()
        return true
    }

    /** Zeichnen-Sitzung committet ("Fertig zeichnen") -> Zwischenschritte sind jetzt bedeutungslos. */
    fun clearDrawHistory() {
        drawSegmentsState.value = emptyList()
        drawUndoStack.clear()
        drawRedoStack.clear()
        refreshDrawFlags()
    }

    // --- GLOBALER Undo/Redo-Verlauf ---------------------------------------------------------------
    // EIN durchgehender Verlauf ab Bildladen über ALLE Menüs (Overlays, Masken/Radierer, Kacheln),
    // statt getrennter, menü-gebundener Stacks. Die einzelnen (getesteten) Undo/Redo-Mechanismen
    // bleiben; hier wird nur die REIHENFOLGE der Schritte global geführt und beim Undo/Redo an den
    // richtigen Mechanismus dispatcht. Kacheln liegen außerhalb des ViewModels -> als externe Schritte
    // registriert und per Callback zurückgenommen/wiederhergestellt.
    enum class HistoryKind { Overlay, EraseMask, SolveMask, Tile, Calibration, Draw }

    private val globalUndoKinds = ArrayDeque<HistoryKind>()
    private val globalRedoKinds = ArrayDeque<HistoryKind>()
    val canGlobalUndoState = mutableStateOf(false)
    val canGlobalRedoState = mutableStateOf(false)

    private fun refreshGlobalFlags() {
        canGlobalUndoState.value = globalUndoKinds.isNotEmpty()
        canGlobalRedoState.value = globalRedoKinds.isNotEmpty()
    }

    private fun pushGlobalKind(kind: HistoryKind) {
        globalUndoKinds.addLast(kind)
        while (globalUndoKinds.size > MAX_HISTORY) globalUndoKinds.removeFirst()
        globalRedoKinds.clear()
        refreshGlobalFlags()
    }

    /** Externer (nicht im ViewModel liegender) Schritt, z. B. eine Kachel-Änderung. */
    fun recordExternalStep(kind: HistoryKind) = pushGlobalKind(kind)

    /**
     * Nimmt den ZULETZT gemachten Schritt zurück – egal in welchem Menü er passierte. Die pro-Art
     * Bitmap-Stacks (Masken) sind FLACHER gedeckelt (MAX_MASK_HISTORY) als der globale Verlauf
     * (MAX_HISTORY) — ein hier gezogener Kind-Eintrag kann also auf einen bereits geleerten Stack
     * treffen (Phantom-Schritt). Statt dann lautlos nichts zu tun (und den Schritt trotzdem als
     * "erledigt" auf den Redo-Verlauf zu schieben), wird der Phantom-Eintrag verworfen und mit dem
     * NÄCHSTEN (älteren) Eintrag weiterversucht, bis ein Schritt wirklich etwas zurücknimmt.
     */
    fun globalUndo(onTile: () -> Boolean, onCalibration: () -> Boolean) {
        while (true) {
            val kind = globalUndoKinds.removeLastOrNull() ?: return
            val didUndo = when (kind) {
                HistoryKind.Overlay -> undo()
                HistoryKind.EraseMask -> eraseUndo()
                HistoryKind.SolveMask -> solveMaskUndo()
                HistoryKind.Tile -> onTile()
                HistoryKind.Calibration -> onCalibration()
                HistoryKind.Draw -> drawUndo()
            }
            if (didUndo) {
                globalRedoKinds.addLast(kind)
                refreshGlobalFlags()
                return
            }
            // Phantom-Eintrag (Stack bereits erschöpft) -> verwerfen, nächsten Eintrag versuchen.
        }
    }

    /** Stellt den zuletzt zurückgenommenen Schritt wieder her (gleiche Phantom-Behandlung wie oben). */
    fun globalRedo(onTileRedo: () -> Boolean, onCalibrationRedo: () -> Boolean) {
        while (true) {
            val kind = globalRedoKinds.removeLastOrNull() ?: return
            val didRedo = when (kind) {
                HistoryKind.Overlay -> redo()
                HistoryKind.EraseMask -> eraseRedo()
                HistoryKind.SolveMask -> solveMaskRedo()
                HistoryKind.Tile -> onTileRedo()
                HistoryKind.Calibration -> onCalibrationRedo()
                HistoryKind.Draw -> drawRedo()
            }
            if (didRedo) {
                globalUndoKinds.addLast(kind)
                refreshGlobalFlags()
                return
            }
        }
    }

    fun replaceImage(uri: Uri): Boolean {
        if (imageUriState.value == uri) return false

        imageUriState.value = uri
        savedStateHandle[IMAGE_URI_KEY] = uri.toString()
        resetImageWork()
        return true
    }

    private fun resetImageWork() {
        overlays.clear()
        solvePreviewOverlays.clear()
        solvePreviewStarsState.value = emptyList()
        selectedOverlayIdState.value = null
        nextOverlayIdState.longValue = 1L
        detectedStarsState.value = emptyList()
        showDetectedStarsState.value = false
        solveMaskState.value = null
        solveMaskVersionState.intValue = 0
        annotationEraseMaskState.value = null
        annotationEraseVersionState.intValue = 0
        undoStack.clear()
        redoStack.clear()
        pendingSnapshot = null
        pendingCommitted = false
        eraseUndoStack.clear()
        eraseRedoStack.clear()
        solveMaskUndoStack.clear()
        solveMaskRedoStack.clear()
        drawSegmentsState.value = emptyList()
        drawUndoStack.clear()
        drawRedoStack.clear()
        globalUndoKinds.clear()
        globalRedoKinds.clear()
        refreshEraseFlags()
        refreshSolveMaskFlags()
        refreshDrawFlags()
        refreshHistoryFlags()
        refreshGlobalFlags()
    }

    private companion object {
        const val IMAGE_URI_KEY = "editor_image_uri"
        const val MAX_HISTORY = 50
        // An MAX_HISTORY angeglichen (war 12): Masken liegen bereits in 1/4-Auflösung (siehe
        // copyMask), Speicherkosten bleiben gering. Ein flacherer Wert hier als MAX_HISTORY
        // ließ den globalen Verlauf nach vielen Maskenstrichen "Phantom"-Schritte enthalten,
        // die globalUndo()/globalRedo() zwar überspringen (siehe dort), aber die Angleichung
        // reduziert zusätzlich, wie oft das überhaupt vorkommt.
        const val MAX_MASK_HISTORY = 50
    }
}
