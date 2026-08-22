package com.codex.starmapper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.codex.starmapper.R
import kotlin.math.roundToInt

/**
 * Drop-in-Ersatz für die frühere ColorChoiceRow (16 feste Presets, entfernt): kleine Farbkachel + Hex-
 * Text, Tap öffnet den vollen Photoshop-artigen Farbwähler ([ColorPickerPanel]) als [AppBottomPopup].
 * Identische Signatur zur alten ColorChoiceRow -> alle Aufrufstellen sind reine Umbenennungen.
 */
@Composable
internal fun ColorPickerField(selected: Long, onColorChange: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showPicker = true }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorSwatch(argb = selected, size = 32.dp)
        Spacer(Modifier.width(8.dp))
        Text("#" + argbToHexString(selected), style = MaterialTheme.typography.labelMedium)
    }
    if (showPicker) {
        AppBottomPopup(onDismiss = { showPicker = false }) {
            ColorPickerPanel(
                initialColorArgb = selected,
                onColorChange = onColorChange,
                onDone = { showPicker = false },
            )
        }
    }
}

@Composable
private fun ColorSwatch(argb: Long, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    )
}

/**
 * Photoshop-artiger Farbwähler: 2D-Sättigung/Helligkeit-Feld + Farbton-Leiste + H/S/B- und R/G/B-
 * Zahlenfelder + Hex-Feld, alle bidirektional synchron über den gemeinsamen hue/sat/bri-State. Live-
 * Apply (kein Cancel/OK, passend zu allen übrigen Reglern der App) -- Undo bleibt Sache der aufrufenden
 * Stelle. "current"-Kachel (Ausgangsfarbe beim Öffnen) ist antippbar und setzt zurück.
 */
@Composable
internal fun ColorPickerPanel(
    initialColorArgb: Long,
    onColorChange: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val seedHsv = remember(initialColorArgb) { hsvArrayOf(initialColorArgb) }
    var hue by remember { mutableFloatStateOf(seedHsv[0]) }
    var sat by remember { mutableFloatStateOf(seedHsv[1]) }
    var bri by remember { mutableFloatStateOf(seedHsv[2]) }
    val liveColorArgb = remember(hue, sat, bri) { argbFromHsv(hue, sat, bri) }

    // Einziger Propagations-Punkt nach außen -- jeder der 5 Eingabewege (2D-Feld, Farbton-Leiste, H/S/B-,
    // R/G/B-, Hex-Feld) schreibt NUR hue/sat/bri, keiner ruft onColorChange selbst auf.
    LaunchedEffect(liveColorArgb) { onColorChange(liveColorArgb) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(
                argb = initialColorArgb,
                size = 40.dp,
                modifier = Modifier.clickable { hue = seedHsv[0]; sat = seedHsv[1]; bri = seedHsv[2] },
            )
            ColorSwatch(argb = liveColorArgb, size = 40.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.label_color_current_new_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(220.dp),
        ) {
            SaturationBrightnessField(
                hue = hue,
                sat = sat,
                bri = bri,
                onChange = { s, b -> sat = s; bri = b },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            HueBar(
                hue = hue,
                onHueChange = { hue = it },
                modifier = Modifier.width(28.dp).fillMaxHeight(),
            )
        }
        HsbNumberFields(
            hue = hue,
            sat = sat,
            bri = bri,
            onHueChange = { hue = it },
            onSatChange = { sat = it },
            onBriChange = { bri = it },
        )
        RgbNumberFields(liveColorArgb) { r, g, b ->
            val hsv = hsvArrayOf(argbFromRgb(r, g, b))
            hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
        }
        HexField(liveColorArgb) { parsedArgb ->
            val hsv = hsvArrayOf(parsedArgb)
            hue = hsv[0]; sat = hsv[1]; bri = hsv[2]
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_done))
        }
    }
}

/** 2D-Feld: horizontal = Sättigung (0..1), vertikal = Helligkeit (1 oben .. 0 unten), für den aktuellen
 *  Farbton [hue]. Geste nach dem Vorbild der 3D-Himmelsvorschau (awaitEachGesture), hier vereinfacht auf
 *  einen einzelnen Finger (kein Pinch/Rotate nötig). Die Feldgröße wird explizit über [onSizeChanged]
 *  eingefangen statt sich auf eine implizite `size`-Auflösung innerhalb der Gesten-Scope zu verlassen. */
@Composable
private fun SaturationBrightnessField(
    hue: Float,
    sat: Float,
    bri: Float,
    onChange: (sat: Float, bri: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val pureHue = Color(argbFromHsv(hue, 1f, 1f))
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun report(pos: Offset) {
                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                        val s = (pos.x / w).coerceIn(0f, 1f)
                        val b = 1f - (pos.y / h).coerceIn(0f, 1f)
                        onChange(s, b)
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        change.consume()
                        report(change.position)
                    }
                }
            },
    ) {
        // Sättigung: weiß -> reiner Farbton (links -> rechts). Helligkeit: transparent -> schwarz (oben -> unten).
        drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cursor = Offset(sat * size.width, (1f - bri) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = cursor, style = Stroke(2.5.dp.toPx()))
        drawCircle(Color.Black, radius = 9.dp.toPx(), center = cursor, style = Stroke(1.dp.toPx()))
    }
}

/** Vertikale Farbton-Leiste (0..360°), voller Regenbogen-Verlauf. Gleiches Gesten-Skelett wie
 *  [SaturationBrightnessField], nur auf die y-Achse reduziert. */
@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val hueStops = remember {
        (0..6).map { i -> Color(argbFromHsv(i * 60f, 1f, 1f)) }
    }
    var boxHeight by remember { mutableStateOf(0) }
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .onSizeChanged { boxHeight = it.height }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun report(pos: Offset) {
                        val h = boxHeight.toFloat().coerceAtLeast(1f)
                        onHueChange((pos.y / h * 360f).coerceIn(0f, 360f))
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        change.consume()
                        report(change.position)
                    }
                }
            },
    ) {
        drawRect(Brush.verticalGradient(hueStops))
        val cursorY = (hue / 360f * size.height).coerceIn(0f, size.height)
        drawRect(
            Color.White,
            topLeft = Offset(0f, (cursorY - 1.5.dp.toPx()).coerceAtLeast(0f)),
            size = Size(size.width, 3.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )
    }
}

@Composable
private fun HsbNumberFields(
    hue: Float,
    sat: Float,
    bri: Float,
    onHueChange: (Float) -> Unit,
    onSatChange: (Float) -> Unit,
    onBriChange: (Float) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncedNumberField(
            label = "H", canonicalValue = hue.roundToInt().toString(), modifier = Modifier.weight(1f),
        ) { text -> text.toFloatOrNull()?.let { if (it in 0f..360f) onHueChange(it) } }
        SyncedNumberField(
            label = "S", canonicalValue = (sat * 100f).roundToInt().toString(), modifier = Modifier.weight(1f),
        ) { text -> text.toFloatOrNull()?.let { if (it in 0f..100f) onSatChange(it / 100f) } }
        SyncedNumberField(
            label = "B", canonicalValue = (bri * 100f).roundToInt().toString(), modifier = Modifier.weight(1f),
        ) { text -> text.toFloatOrNull()?.let { if (it in 0f..100f) onBriChange(it / 100f) } }
    }
}

@Composable
private fun RgbNumberFields(argb: Long, onRgbChange: (r: Int, g: Int, b: Int) -> Unit) {
    val r = redOf(argb)
    val g = greenOf(argb)
    val b = blueOf(argb)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SyncedNumberField(label = "R", canonicalValue = r.toString(), modifier = Modifier.weight(1f)) { text ->
            text.toIntOrNull()?.let { if (it in 0..255) onRgbChange(it, g, b) }
        }
        SyncedNumberField(label = "G", canonicalValue = g.toString(), modifier = Modifier.weight(1f)) { text ->
            text.toIntOrNull()?.let { if (it in 0..255) onRgbChange(r, it, b) }
        }
        SyncedNumberField(label = "B", canonicalValue = b.toString(), modifier = Modifier.weight(1f)) { text ->
            text.toIntOrNull()?.let { if (it in 0..255) onRgbChange(r, g, it) }
        }
    }
}

@Composable
private fun HexField(argb: Long, onHexChange: (Long) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(argbToHexString(argb)) }
    LaunchedEffect(argb, isFocused) { if (!isFocused) text = argbToHexString(argb) }
    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            text = newText
            hexStringToArgbOrNull(newText)?.let(onHexChange)
        },
        label = { Text("#") },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
    )
}

/** Zahlenfeld mit rohem Text-Puffer (roh tippen erlaubt, auch ungültige Zwischenzustände), das nur bei
 *  fehlendem Fokus vom kanonischen Wert überschrieben wird -- verhindert, dass externe Updates (aus
 *  anderen Eingabewegen desselben Farbwerts) das Tippen unterbrechen. Analog zum focalText-Muster in
 *  StarMapperApp.kt (roher Puffer + tolerantes Parsen), hier zusätzlich mit Fokus-Gate für echte
 *  bidirektionale Synchronisation, da hier mehrere Felder denselben kanonischen Wert speisen. */
@Composable
private fun SyncedNumberField(
    label: String,
    canonicalValue: String,
    modifier: Modifier = Modifier,
    onCommit: (String) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(canonicalValue) }
    LaunchedEffect(canonicalValue, isFocused) { if (!isFocused) text = canonicalValue }
    OutlinedTextField(
        value = text,
        onValueChange = { newText -> text = newText; onCommit(newText) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused },
    )
}

// --- Konvertierung ---------------------------------------------------------------------------------
// Die App speichert Farben durchgängig als POSITIVEN Long im Muster 0xFFRRGGBB (z.B. Default
// 0xFFFFFFFF) -- Compose Color(color: Long) akzeptiert dieses Format direkt (etabliert, s.
// Color(overlay.colorArgb) in StarMapperApp.kt), hier also NIE zusätzlich nach Int wandeln.
// android.graphics.Color.HSVToColor(...) (android.graphics, NICHT Compose) liefert dagegen einen Int,
// der bei Alpha=FF im Zweierkomplement NEGATIV ist -- naives `.toLong()` sign-extended auf
// 0xFFFFFFFFFFFFFFFF statt der erwarteten 0x00000000FFFFFFFF-Form. Deshalb nach JEDER Long-Verbreiterung
// mit `and 0xFFFFFFL` maskieren (extrahiert exakt die unteren 24 RGB-Bit, unabhängig von der
// Sign-Extension darüber).

internal fun argbFromHsv(h: Float, s: Float, v: Float): Long {
    val normalizedHue = ((h % 360f) + 360f) % 360f
    val intColor = android.graphics.Color.HSVToColor(floatArrayOf(normalizedHue, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return 0xFF000000L or (intColor.toLong() and 0xFFFFFFL)
}

internal fun hsvArrayOf(argb: Long): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(argb.toInt(), it) } // Long->Int: Truncation korrekt, keine Maskierung nötig

internal fun argbFromRgb(r: Int, g: Int, b: Int): Long =
    0xFF000000L or
        (r.coerceIn(0, 255).toLong() shl 16) or
        (g.coerceIn(0, 255).toLong() shl 8) or
        b.coerceIn(0, 255).toLong()

internal fun redOf(argb: Long): Int = ((argb shr 16) and 0xFFL).toInt()
internal fun greenOf(argb: Long): Int = ((argb shr 8) and 0xFFL).toInt()
internal fun blueOf(argb: Long): Int = (argb and 0xFFL).toInt()

internal fun argbToHexString(argb: Long): String = "%06X".format(argb and 0xFFFFFFL)

internal fun hexStringToArgbOrNull(hex: String): Long? {
    val clean = hex.removePrefix("#").trim()
    if (clean.length != 6 || clean.any { it !in "0123456789abcdefABCDEF" }) return null
    return 0xFF000000L or (clean.toLong(16) and 0xFFFFFFL)
}
