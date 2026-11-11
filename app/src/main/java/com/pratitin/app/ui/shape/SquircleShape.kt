package com.pratitin.app.ui.shape

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Continuous-curvature "squircle" corners (the iOS / Figma corner-smoothing curve).
 *
 * Unlike a circular rounded rect, curvature ramps up gradually from the straight
 * edge, so there is no visible "kink" where the edge meets the arc. Each corner is
 * two cubic Béziers around a shorter circular arc (Figma's corner-smoothing model).
 *
 * @param radius corner radius
 * @param smoothing 0 = plain rounded rect, 0.6 = iOS-like, 1 = maximal
 */
@Immutable
class SquircleShape(
    private val radius: Dp = 24.dp,
    private val smoothing: Float = 0.6f,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        return Outline.Generic(squirclePath(size, r, smoothing))
    }

    override fun equals(other: Any?) = other is SquircleShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = radius.hashCode() * 31 + smoothing.hashCode()
}

/** Builds the squircle path. Public so canvases (calendar grid, shaders) can reuse it. */
fun squirclePath(size: Size, radiusPx: Float, smoothing: Float, path: Path = Path()): Path {
    path.reset()
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return path
    val budget = min(w, h) / 2f
    val r = min(radiusPx, budget)
    if (r <= 0.5f) {
        path.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)); return path
    }
    // --- Figma corner-smoothing parameters
    var s = smoothing.coerceIn(0f, 1f)
    var p = (1 + s) * r
    val maxS = budget / r - 1f
    s = min(s, maxS).coerceAtLeast(0f)
    p = min(p, budget)
    val arcMeasure = 90f * (1f - s)
    val arcLen = sin(rad(arcMeasure / 2f)) * r * sqrt(2f)
    val alpha = (90f - arcMeasure) / 2f
    val p3p4 = r * tan(rad(alpha / 2f))
    val beta = 45f * s
    val c = p3p4 * cos(rad(beta))
    val d = c * tan(rad(beta))
    val b = (p - arcLen - c - d) / 3f
    val a = 2f * b

    // Local corner frame: x runs along the incoming edge toward the corner,
    // y runs inward along the outgoing edge. Points are relative to the corner.
    val local = floatArrayOf(
        -p, 0f,                       // start of corner on incoming edge
        -p + a, 0f,                   // bezier 1, control 1
        -p + a + b, 0f,               // bezier 1, control 2
        -p + a + b + c, d,            // end of bezier 1 = arc start
        -d, p - a - b - c,            // arc end
        0f, p - a - b,                // bezier 2, control 1
        0f, p - a,                    // bezier 2, control 2
        0f, p,                        // end of corner on outgoing edge
    )
    // arc: centre at (-r, r) in local frame, from angle -90+alpha to -alpha (clockwise, y-down)
    val a1 = rad(-90f + alpha)
    val a2 = rad(-alpha)
    val k = 4f / 3f * tan((a2 - a1) / 4f)
    val arc = floatArrayOf(
        -r + r * (cos(a1) - k * sin(a1)), r + r * (sin(a1) + k * cos(a1)),
        -r + r * (cos(a2) + k * sin(a2)), r + r * (sin(a2) - k * cos(a2)),
    )

    // corner transforms: (cornerX, cornerY, ux, uy, vx, vy) — x along u, y along v
    val corners = arrayOf(
        floatArrayOf(w, 0f, 1f, 0f, 0f, 1f),    // top-right
        floatArrayOf(w, h, 0f, 1f, -1f, 0f),    // bottom-right
        floatArrayOf(0f, h, -1f, 0f, 0f, -1f),  // bottom-left
        floatArrayOf(0f, 0f, 0f, -1f, 1f, 0f),  // top-left
    )
    fun map(t: FloatArray, x: Float, y: Float) = Offset(t[0] + x * t[2] + y * t[4], t[1] + x * t[3] + y * t[5])

    corners.forEachIndexed { i, t ->
        val p0 = map(t, local[0], local[1])
        if (i == 0) path.moveTo(p0.x, p0.y) else path.lineTo(p0.x, p0.y)
        val c1 = map(t, local[2], local[3]); val c2 = map(t, local[4], local[5]); val e1 = map(t, local[6], local[7])
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, e1.x, e1.y)
        if (arcMeasure > 0.01f) {
            val ac1 = map(t, arc[0], arc[1]); val ac2 = map(t, arc[2], arc[3]); val e2 = map(t, local[8], local[9])
            path.cubicTo(ac1.x, ac1.y, ac2.x, ac2.y, e2.x, e2.y)
        }
        val c3 = map(t, local[10], local[11]); val c4 = map(t, local[12], local[13]); val e3 = map(t, local[14], local[15])
        path.cubicTo(c3.x, c3.y, c4.x, c4.y, e3.x, e3.y)
    }
    path.close()
    return path
}

private fun rad(deg: Float) = (deg * Math.PI / 180.0).toFloat()

/** Shared shape tokens so every surface uses the same corner language. */
object Squircles {
    val xs = SquircleShape(10.dp)
    val s = SquircleShape(14.dp)
    val m = SquircleShape(22.dp)
    val l = SquircleShape(30.dp)
    val xl = SquircleShape(40.dp)
    val pill = SquircleShape(100.dp, 0.8f)
}
