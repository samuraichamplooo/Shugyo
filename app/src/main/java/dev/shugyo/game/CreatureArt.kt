package dev.shugyo.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val Ink = Color(0xFF2B2024)
private val Blush = Color(0xFFFF7FA0)
private val Band = Color(0xFFE63946)
private val BandDark = Color(0xFFC1121F)
private val Shade = Color(0xFF2E2925)

/**
 * A creature, drawn by the app (no pictures needed). [hitKey] and [attackKey] play a shake or a lunge each
 * time they change; [fainted] tips it over and fades it.
 */
@Composable
fun CreatureView(
    species: Species,
    modifier: Modifier = Modifier,
    stage: Int = 1,
    silhouette: Boolean = false,
    faceLeft: Boolean = false,
    hitKey: Int = 0,
    attackKey: Int = 0,
    fainted: Boolean = false,
    bob: Boolean = true,
) {
    val idle = rememberInfiniteTransition(label = "idle")
    val lift by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "lift")
    val shake = remember { Animatable(0f) }
    val lunge = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    val faint = remember { Animatable(0f) }
    LaunchedEffect(hitKey) {
        if (hitKey == 0) return@LaunchedEffect
        flash.snapTo(1f)
        for (v in listOf(-1f, 1f, -0.7f, 0.7f, -0.3f, 0f)) shake.animateTo(v, tween(45))
        flash.animateTo(0f, tween(250))
    }
    LaunchedEffect(attackKey) {
        if (attackKey == 0) return@LaunchedEffect
        lunge.animateTo(1f, tween(140))
        lunge.animateTo(0f, tween(220))
    }
    LaunchedEffect(fainted) { faint.animateTo(if (fainted) 1f else 0f, tween(500)) }

    Canvas(
        modifier.graphicsLayer {
            val w = size.width
            translationX = shake.value * w * 0.06f + lunge.value * w * 0.14f * (if (faceLeft) -1f else 1f)
            translationY = (if (bob && !fainted) -lift * w * 0.025f else 0f) + faint.value * w * 0.12f
            rotationZ = faint.value * 80f * (if (faceLeft) -1f else 1f)
            alpha = 1f - faint.value * 0.6f
        },
    ) {
        val mirror = faceLeft
        if (mirror) {
            withTransform({ scale(-1f, 1f, center) }) { drawCreature(species, stage, silhouette, flash.value) }
        } else {
            drawCreature(species, stage, silhouette, flash.value)
        }
    }
}

/** Draws [sp] in a 100×100 box scaled to fit, standing on the bottom. */
fun DrawScope.drawCreature(sp: Species, stage: Int, silhouette: Boolean, flash: Float = 0f) {
    val box = min(size.width, size.height)
    val ox = (size.width - box) / 2f
    val oy = (size.height - box) / 2f
    val s = if (stage <= 1) 0.86f else 1f
    val k = box / 100f
    fun X(v: Float) = ox + (50f + (v - 50f) * s) * k
    fun Y(v: Float) = oy + (92f + (v - 92f) * s) * k
    fun R(v: Float) = v * s * k

    val el = sp.element
    val main = if (silhouette) Shade else el.main
    val dark = if (silhouette) Shade else el.dark
    val light = if (silhouette) Shade else el.light
    val accent = if (silhouette) Shade else el.accent

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, c: Color, a: Float = 1f) =
        drawOval(c, Offset(X(cx) - R(rx), Y(cy) - R(ry)), Size(R(rx) * 2, R(ry) * 2), alpha = a)

    fun path(build: Path.() -> Unit) = Path().apply(build)
    fun Path.m(x: Float, y: Float) = moveTo(X(x), Y(y))
    fun Path.l(x: Float, y: Float) = lineTo(X(x), Y(y))
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(X(x1), Y(y1), X(x2), Y(y2), X(x), Y(y))
    fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticBezierTo(X(x1), Y(y1), X(x), Y(y))
    fun fill(p: Path, c: Color, a: Float = 1f) = drawPath(p, c, alpha = a)
    fun line(p: Path, c: Color, w: Float) =
        drawPath(p, c, style = Stroke(width = R(w), cap = StrokeCap.Round, join = StrokeJoin.Round))

    val cx = 50f
    val (cy, rx, ry) = when (sp.body) {
        1 -> Triple(60f, 24f, 30f)
        2 -> Triple(66f, 32f, 22f)
        3 -> Triple(62f, 26f, 27f)
        else -> Triple(62f, 27f, 26f)
    }
    val top = cy - ry
    val bot = cy + ry

    // Ground shadow, and a glow once fully evolved.
    drawOval(Color.Black, Offset(X(24f), Y(88f)), Size(R(52f), R(8f)), alpha = 0.22f)
    if (stage >= 3 && !silhouette) {
        oval(cx, cy, rx + 12, ry + 12, accent, 0.16f)
        oval(cx, cy, rx + 6, ry + 6, accent, 0.18f)
    }

    // Tail (behind the body).
    when (sp.tail) {
        1 -> {
            oval(cx + rx + 1, cy + 9, 8.5f, 8.5f, main)
            oval(cx + rx + 2, cy + 8, 4.5f, 4.5f, light)
        }
        2 -> {
            fill(path { m(cx + rx - 8, cy + 14); c(cx + rx + 10, cy + 12, cx + rx + 18, cy - 2, cx + rx + 14, cy - 18); c(cx + rx + 8, cy - 6, cx + rx, cy - 2, cx + rx - 8, cy + 2); close() }, accent)
            fill(path { m(cx + rx - 4, cy + 10); c(cx + rx + 6, cy + 8, cx + rx + 11, cy - 1, cx + rx + 10, cy - 9); c(cx + rx + 5, cy - 2, cx + rx, cy + 1, cx + rx - 5, cy + 4); close() }, light)
        }
        3 -> {
            fill(path { m(cx + rx - 6, cy + 12); c(cx + rx + 4, cy - 4, cx + rx + 16, cy - 8, cx + rx + 20, cy - 6); c(cx + rx + 18, cy + 6, cx + rx + 8, cy + 14, cx + rx - 6, cy + 12); close() }, accent)
            line(path { m(cx + rx - 4, cy + 11); l(cx + rx + 17, cy - 5) }, dark, 1.2f)
        }
        4 -> fill(path { m(cx + rx - 8, cy + 2); l(cx + rx + 14, cy - 12); l(cx + rx + 10, cy + 16); close() }, dark)
    }

    // Ears, horns and the like.
    for (sx in listOf(-1f, 1f)) {
        when (sp.ears) {
            0 -> {
                oval(cx + sx * rx * 0.62f, top + 7, 8.5f, 8.5f, main)
                oval(cx + sx * rx * 0.62f, top + 7, 4.5f, 4.5f, light)
            }
            1 -> {
                fill(path { m(cx + sx * rx * 0.78f, top + 14); l(cx + sx * rx * 0.62f, top - 13); l(cx + sx * rx * 0.12f, top + 5); close() }, main)
                fill(path { m(cx + sx * rx * 0.66f, top + 10); l(cx + sx * rx * 0.58f, top - 6); l(cx + sx * rx * 0.3f, top + 6); close() }, light)
            }
            2 -> fill(path { m(cx + sx * 6, top + 5); c(cx + sx * 10, top - 6, cx + sx * 20, top - 12, cx + sx * 24, top - 14); c(cx + sx * 21, top - 6, cx + sx * 17, top + 2, cx + sx * 15, top + 8); close() }, accent)
            3 -> {
                line(path { m(cx + sx * 7, top + 3); q(cx + sx * 10, top - 10, cx + sx * 17, top - 15) }, dark, 2.2f)
                oval(cx + sx * 17, top - 15, 4f, 4f, accent)
            }
            5 -> {
                fill(path { m(cx + sx * 5, top + 6); c(cx + sx * 4, top - 14, cx + sx * 10, top - 26, cx + sx * 15, top - 24); c(cx + sx * 19, top - 20, cx + sx * 16, top - 4, cx + sx * 15, top + 6); close() }, main)
                fill(path { m(cx + sx * 8, top + 3); c(cx + sx * 8, top - 10, cx + sx * 11, top - 19, cx + sx * 13.5f, top - 18); c(cx + sx * 15, top - 12, cx + sx * 13, top - 2, cx + sx * 12.5f, top + 4); close() }, light)
            }
        }
    }
    if (sp.ears == 4) {
        val stem = if (silhouette) Shade else Color(0xFF2E7D45)
        line(path { m(cx, top + 3); l(cx, top - 8) }, stem, 2.2f)
        fill(path { m(cx, top - 8); c(cx - 4, top - 16, cx - 14, top - 16, cx - 16, top - 11); c(cx - 10, top - 6, cx - 4, top - 6, cx, top - 8); close() }, if (silhouette) Shade else Color(0xFF59C173))
        fill(path { m(cx, top - 8); c(cx + 4, top - 18, cx + 13, top - 19, cx + 15, top - 15); c(cx + 10, top - 9, cx + 4, top - 8, cx, top - 8); close() }, if (silhouette) Shade else Color(0xFF7ED98F))
    }

    // Feet and body.
    oval(cx - 12, bot - 1, 8f, 4.5f, dark)
    oval(cx + 12, bot - 1, 8f, 4.5f, dark)
    if (sp.body == 3) {
        fill(path { m(cx, top); c(cx + 16, top, cx + rx - 4, cy - 6, cx + rx, cy + 8); c(cx + rx + 2, bot - 4, cx + 12, bot, cx, bot); c(cx - 12, bot, cx - rx - 2, bot - 4, cx - rx, cy + 8); c(cx - rx + 4, cy - 6, cx - 16, top, cx, top); close() }, main)
    } else {
        oval(cx, cy, rx, ry, main)
    }
    if (silhouette) return
    oval(cx, cy + ry * 0.32f, rx * 0.56f, ry * 0.5f, light)

    // Markings.
    when (sp.pattern) {
        1 -> {
            oval(cx - rx * 0.62f, cy - ry * 0.2f, 3.2f, 3.2f, dark, 0.35f)
            oval(cx - rx * 0.75f, cy + ry * 0.05f, 2.4f, 2.4f, dark, 0.35f)
            oval(cx + rx * 0.66f, cy - ry * 0.12f, 3f, 3f, dark, 0.35f)
        }
        2 -> for (dx in listOf(-7f, 0f, 7f)) line(path { m(cx + dx - 3, top + 2.5f); q(cx + dx, top + 8, cx + dx + 3, top + 2.5f) }, dark, 2f)
        3 -> fill(path { m(cx, top + 5); l(cx + 4, top + 10); l(cx, top + 15); l(cx - 4, top + 10); close() }, accent)
    }

    // The dojo headband (partners wear one).
    if (sp.headband) {
        val y1 = cy - ry * 0.66f
        val y2 = cy - ry * 0.46f
        fun hx(y: Float) = rx * sqrt(max(0f, 1f - ((y - cy) / ry) * ((y - cy) / ry)))
        val a = hx(y1)
        val b = hx(y2)
        fill(path { m(cx - a, y1); q(cx, y1 - 4, cx + a, y1); l(cx + b, y2); q(cx, y2 - 4, cx - b, y2); close() }, Band)
        val kx = cx + b - 1
        val ky = (y1 + y2) / 2
        fill(path { m(kx, ky - 1); q(kx + 9, ky - 8, kx + 14, ky - 5); q(kx + 8, ky, kx + 1, ky + 2); close() }, Band)
        fill(path { m(kx, ky + 1); q(kx + 8, ky + 3, kx + 11, ky + 10); q(kx + 5, ky + 7, kx, ky + 3); close() }, BandDark)
    }

    // Face.
    val fy = cy - ry * 0.08f
    oval(cx - 17, fy + 7, 4f, 2.6f, Blush, 0.55f)
    oval(cx + 17, fy + 7, 4f, 2.6f, Blush, 0.55f)
    for (sx in listOf(-1f, 1f)) {
        val ex = cx + sx * 9.5f
        when (sp.eyes) {
            2 -> line(path { m(ex - 4, fy + 1.5f); q(ex, fy - 4, ex + 4, fy + 1.5f) }, Ink, 2.4f)
            3 -> {
                oval(ex, fy, 5f, 6f, Ink)
                oval(ex + 1.5f, fy - 2, 1.9f, 1.9f, Color.White)
                oval(ex - 1.5f, fy + 2.2f, 1f, 1f, Color.White)
            }
            else -> {
                oval(ex, fy, 4f, 5f, Ink)
                oval(ex + 1.3f, fy - 1.7f, 1.5f, 1.5f, Color.White)
            }
        }
        if (sp.eyes == 1) {
            fill(path { m(ex - sx * 5, fy - 8.5f); l(ex + sx * 3.5f, fy - 6); l(ex + sx * 3.2f, fy - 4.6f); l(ex - sx * 5.2f, fy - 7); close() }, Ink)
        }
    }
    val my = fy + 8.5f
    when (sp.mouth) {
        1 -> {
            line(path { m(cx - 4, my); q(cx, my + 4, cx + 4, my) }, Ink, 1.8f)
            fill(path { m(cx + 1.2f, my + 1.3f); l(cx + 3, my + 0.8f); l(cx + 2.2f, my + 3.4f); close() }, Color.White)
        }
        2 -> {
            oval(cx, my + 1.2f, 3f, 3.2f, Ink)
            oval(cx, my + 2.6f, 1.8f, 1.3f, Blush)
        }
        else -> line(path { m(cx - 3.5f, my); q(cx, my + 3.5f, cx + 3.5f, my) }, Ink, 1.8f)
    }

    // Evolved forms: a chest gem, then a crown.
    if (stage >= 2) {
        val gy = cy + ry * 0.14f
        fill(path { m(cx, gy); l(cx + 3, gy + 4); l(cx, gy + 8); l(cx - 3, gy + 4); close() }, accent)
    }
    if (stage >= 3) {
        for (dx in listOf(-8f, 0f, 8f)) {
            val h = if (dx == 0f) 10f else 7f
            fill(path { m(cx + dx - 3, top + 1); l(cx + dx, top - h); l(cx + dx + 3, top + 1); close() }, accent)
        }
    }
    if (flash > 0f) oval(cx, cy, rx, ry, Color.White, 0.55f * flash)
}
