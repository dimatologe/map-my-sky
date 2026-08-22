package com.codex.starmapper.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun AnnotationOverlay.constellationImagePoints(): List<Offset> {
    val pattern = constellation ?: return emptyList()
    return pattern.stars.mapIndexed { index, star ->
        anchorOverrides[index] ?: normalizedPointToImage(star.x, star.y)
    }
}

/**
 * Anker für den Sternbild-NAMEN: X = Mittel der im Bild liegenden Anker, Y = oberster davon.
 * Anker außerhalb des Bildes (z. B. gefaltete/hinter der optischen Achse projizierte Sterne bei
 * Fisheye/Panorama) werden ignoriert, damit der Name am sichtbaren Sternbildkörper sitzt statt am
 * gemittelten Center oder – durch Klemmen – am linken Rand. Gibt null zurück, wenn zu wenige Anker
 * im Bild liegen -> der Name wird dann GAR NICHT gezeichnet (entfernt die falsch platzierten Namen).
 */
fun AnnotationOverlay.constellationNameAnchor(imageWidth: Float, imageHeight: Float): Offset? {
    val pts = constellationImagePoints()
    if (pts.size < 2) return null
    val margin = 0.03f * minOf(imageWidth, imageHeight)
    val inField = pts.filter {
        it.x >= -margin && it.x <= imageWidth + margin && it.y >= -margin && it.y <= imageHeight + margin
    }
    // Mindestens 2 Anker UND >= 40 % des Sternbildkörpers müssen im Bild sein, sonst kein Name.
    if (inField.size < 2 || inField.size * 5 < pts.size * 2) return null
    val cx = inField.fold(0f) { acc, o -> acc + o.x } / inField.size
    if (cx < 0f || cx > imageWidth) return null
    val top = inField.minOf { it.y }
    return Offset(cx, top)
}

fun AnnotationOverlay.normalizedPointToImage(x: Float, y: Float): Offset {
    val radians = rotationDegrees * PI.toFloat() / 180f
    val half = Size(size.width / 2f, size.height / 2f)
    val localX = (if (mirrorX) -x else x) * half.width
    val localY = (if (mirrorY) -y else y) * half.height
    val rotatedX = localX * cos(radians) - localY * sin(radians)
    val rotatedY = localX * sin(radians) + localY * cos(radians)
    return Offset(center.x + rotatedX, center.y + rotatedY)
}

fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)

fun trimmedLineEndpoints(start: Offset, end: Offset, gap: Float): Pair<Offset, Offset>? {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = hypot(dx, dy)
    if (length <= gap * 2f) return null
    val ux = dx / length
    val uy = dy / length
    return Offset(start.x + ux * gap, start.y + uy * gap) to Offset(end.x - ux * gap, end.y - uy * gap)
}
