package dev.shugyo.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min

/** Draws the animal partners (fox, otter, tanuki, dragon, cat, dog, rabbit, eagle) from [PartnerData]. */
internal object PartnerArt {
    private class Shape(
        val kind: Char,
        val color: Color,
        val alpha: Float,
        val width: Float,
        val rot: Float,
        val px: Float,
        val py: Float,
        val oval: FloatArray?,
        val path: Path?,
    )

    private val cache = HashMap<Int, List<Shape>>()
    private val SilhouetteColor = Color(0xFF2E2925)

    val count get() = PartnerData.forms.size / 3

    private fun shapes(i: Int): List<Shape> = synchronized(cache) {
        cache.getOrPut(i) {
            PartnerData.forms[i].lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
                runCatching {
                    val f = line.split(';', limit = 8)
                    val kind = f[0][0]
                    val color = Color(0xFF000000L or f[1].toLong(16))
                    val data = f[7]
                    Shape(
                        kind, color, f[2].toFloat(), f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), f[6].toFloat(),
                        oval = if (kind == 'e') data.split(',').map { it.toFloat() }.toFloatArray() else null,
                        path = if (kind == 'e') null else PathParser().parsePathString(data).toPath(),
                    )
                }.getOrNull()
            }.toList()
        }
    }

    /** Whether partner [index] has a drawing. */
    fun has(index: Int) = index in 0 until count

    fun DrawScope.drawPartner(index: Int, stage: Int, silhouette: Boolean, flash: Float) {
        val i = index * 3 + (stage.coerceIn(1, 3) - 1)
        val list = shapes(i)
        val s = PartnerData.scale[i]
        val box = min(size.width, size.height)
        val k = box / 100f
        val ox = (size.width - box) / 2f
        val oy = (size.height - box) / 2f
        withTransform({
            translate(ox, oy)
            scale(k, k, Offset.Zero)
            scale(s, s, Offset(50f, 93f))
        }) {
            for (sh in list) drawShape(sh, silhouette, null)
            if (flash > 0f) {
                // A white flash over the solid parts when hit.
                for (sh in list) if (sh.alpha >= 0.9f) drawShape(sh, false, Color.White.copy(alpha = 0.35f * flash))
            }
        }
    }

    private fun DrawScope.drawShape(sh: Shape, silhouette: Boolean, tint: Color?) {
        val faint = sh.alpha < 0.5f
        if (silhouette && faint && sh.color != Color.Black) return
        val color = tint ?: if (silhouette && sh.color != Color.Black) SilhouetteColor else sh.color
        val alpha = if (tint != null) 1f else sh.alpha
        fun draw() {
            when (sh.kind) {
                'e' -> {
                    val o = sh.oval ?: return
                    drawOval(color, Offset(o[0] - o[2], o[1] - o[3]), Size(o[2] * 2, o[3] * 2), alpha = alpha)
                }
                's' -> drawPath(
                    sh.path ?: return, color, alpha = alpha,
                    style = Stroke(width = sh.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                else -> drawPath(sh.path ?: return, color, alpha = alpha)
            }
        }
        if (sh.rot != 0f) withTransform({ rotate(sh.rot, Offset(sh.px, sh.py)) }) { draw() } else draw()
    }
}
