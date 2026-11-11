package com.pratitin.app.ui.heritage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pratitin.app.ui.theme.LocalPalette
import kotlin.math.abs

/*
 * Original vector ornaments inspired by Khmer decorative art:
 *  - Kbach Phni Tes (ក្បាច់ភ្ញីទេស) flame-leaf scroll for borders and dividers
 *  - Prasat towers in the quincunx layout of Angkor Wat, with moat reflection
 *  - Chhouk (ឈូក) lotus, symbol of purity, used as markers and the bloom loader
 * Everything is drawn with Paths, so it is resolution-independent and theme-tinted.
 */

// ------------------------------------------------------------------ kbach border

/** One flame-leaf unit, normalised to a w×h box, pointing right. */
private fun kbachUnit(w: Float, h: Float): Path = Path().apply {
    val cy = h / 2f
    moveTo(0f, cy)
    cubicTo(w * 0.22f, cy - h * 0.62f, w * 0.62f, cy - h * 0.58f, w * 0.74f, cy - h * 0.12f)
    cubicTo(w * 0.80f, cy + h * 0.14f, w * 0.64f, cy + h * 0.30f, w * 0.52f, cy + h * 0.12f)
    cubicTo(w * 0.47f, cy + h * 0.02f, w * 0.52f, cy - h * 0.10f, w * 0.60f, cy - h * 0.06f)
    cubicTo(w * 0.48f, cy - h * 0.30f, w * 0.26f, cy - h * 0.08f, w * 0.18f, cy + h * 0.10f)
    cubicTo(w * 0.12f, cy + h * 0.22f, w * 0.05f, cy + h * 0.12f, 0f, cy)
    close()
}

/** Repeating kbach scroll along a horizontal band — mirrored pairs meet at a jewel. */
fun DrawScope.drawKbachBand(color: Color, unit: Float = 22.dp.toPx(), alpha: Float = 0.8f) {
    val h = size.height
    val leaf = kbachUnit(unit, h)
    val cy = h / 2f
    val count = (size.width / (unit * 2f)).toInt().coerceAtLeast(1)
    val used = count * unit * 2f
    val start = (size.width - used) / 2f
    drawLine(color.copy(alpha = alpha * 0.35f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f)
    for (i in 0 until count) {
        val x = start + i * unit * 2f
        translate(x, 0f) { drawPath(leaf, color.copy(alpha = alpha)) }
        translate(x + unit * 2f, 0f) { scale(-1f, 1f, pivot = Offset.Zero) { drawPath(leaf, color.copy(alpha = alpha * 0.85f)) } }
        // jewel where two leaves meet
        val jx = x + unit
        val j = Path().apply {
            moveTo(jx, cy - h * 0.22f); lineTo(jx + h * 0.14f, cy); lineTo(jx, cy + h * 0.22f); lineTo(jx - h * 0.14f, cy); close()
        }
        drawPath(j, color.copy(alpha = alpha))
    }
}

@Composable
fun KbachDivider(modifier: Modifier = Modifier, height: Dp = 14.dp, color: Color = LocalPalette.current.gold, alpha: Float = 0.7f) {
    Canvas(modifier.fillMaxWidth().height(height)) { drawKbachBand(color, unit = height.toPx() * 1.5f, alpha = alpha) }
}

/** Corner flourish for hero cards (top-left orientation; rotate for others). */
fun DrawScope.drawCornerFlourish(color: Color, s: Float) {
    val p = Path().apply {
        moveTo(0f, s); cubicTo(0f, s * 0.4f, s * 0.4f, 0f, s, 0f)
        moveTo(s * 0.18f, s * 0.86f); cubicTo(s * 0.2f, s * 0.48f, s * 0.48f, s * 0.2f, s * 0.86f, s * 0.18f)
    }
    drawPath(p, color, style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
    val bud = Path().apply {
        moveTo(s * 0.28f, s * 0.28f)
        cubicTo(s * 0.36f, s * 0.14f, s * 0.5f, s * 0.12f, s * 0.42f, s * 0.3f)
        cubicTo(s * 0.36f, s * 0.42f, s * 0.2f, s * 0.42f, s * 0.28f, s * 0.28f)
        close()
    }
    drawPath(bud, color)
}

fun DrawScope.drawAllCorners(color: Color, s: Float) {
    val w = size.width; val h = size.height
    drawCornerFlourish(color, s)
    translate(w, 0f) { rotate(90f, pivot = Offset.Zero) { drawCornerFlourish(color, s) } }
    translate(w, h) { rotate(180f, pivot = Offset.Zero) { drawCornerFlourish(color, s) } }
    translate(0f, h) { rotate(270f, pivot = Offset.Zero) { drawCornerFlourish(color, s) } }
}

// ------------------------------------------------------------------ prasat skyline

/**
 * A single lotus-bud tower (prasat). The left outline is generated as points —
 * stepped plinth, then corbelled tiers that bulge and step inward, then the finial —
 * and mirrored for the right side, so the tower is always perfectly symmetric.
 */
private fun Path.tower(cx: Float, base: Float, w: Float, h: Float) {
    val pts = ArrayList<Offset>(48)
    val hw = w / 2f
    pts += Offset(-hw * 1.24f, 0f); pts += Offset(-hw * 1.24f, -h * 0.05f)
    pts += Offset(-hw * 1.1f, -h * 0.05f); pts += Offset(-hw * 1.1f, -h * 0.12f)
    pts += Offset(-hw, -h * 0.12f); pts += Offset(-hw, -h * 0.26f)
    val tiers = 6
    val top = h * 0.9f
    var y = h * 0.26f
    val tierH = (top - y) / tiers
    for (i in 0 until tiers) {
        val t0 = i / tiers.toFloat()
        val t1 = (i + 1) / tiers.toFloat()
        val w0 = hw * (1f - 0.78f * t0 * t0) * (1f - 0.12f * t0)
        val w1 = hw * (1f - 0.78f * t1 * t1) * (1f - 0.12f * t1)
        pts += Offset(-w0 * 1.04f, -(y + tierH * 0.15f))       // bulge (corbel)
        pts += Offset(-w0 * 0.98f, -(y + tierH * 0.7f))
        pts += Offset(-w1 * 1.02f, -(y + tierH))               // ledge
        y += tierH
    }
    pts += Offset(-hw * 0.09f, -h * 0.95f)
    pts += Offset(0f, -h)                                      // finial tip
    moveTo(cx + pts[0].x, base + pts[0].y)
    for (i in 1 until pts.size) lineTo(cx + pts[i].x, base + pts[i].y)
    for (i in pts.size - 2 downTo 0) lineTo(cx - pts[i].x, base + pts[i].y)
    close()
}

/** Angkor-style skyline: five towers in quincunx, galleries, causeway and palms. */
fun buildSkyline(size: Size): Path {
    val w = size.width
    val base = size.height
    val unit = w / 10f
    val p = Path()
    // outer gallery + causeway platform
    p.moveTo(0f, base); p.lineTo(0f, base - unit * 0.5f)
    p.lineTo(w * 0.12f, base - unit * 0.5f); p.lineTo(w * 0.12f, base - unit * 0.78f)
    p.lineTo(w * 0.88f, base - unit * 0.78f); p.lineTo(w * 0.88f, base - unit * 0.5f)
    p.lineTo(w, base - unit * 0.5f); p.lineTo(w, base); p.close()
    // inner terraces
    p.addRect(Rect(w * 0.2f, base - unit * 1.25f, w * 0.8f, base - unit * 0.7f))
    p.addRect(Rect(w * 0.28f, base - unit * 1.7f, w * 0.72f, base - unit * 1.2f))
    // towers: two outer pairs, then the central sanctuary
    p.tower(w * 0.22f, base - unit * 0.74f, unit * 0.62f, unit * 2.2f)
    p.tower(w * 0.78f, base - unit * 0.74f, unit * 0.62f, unit * 2.2f)
    p.tower(w * 0.34f, base - unit * 1.2f, unit * 0.74f, unit * 2.6f)
    p.tower(w * 0.66f, base - unit * 1.2f, unit * 0.74f, unit * 2.6f)
    p.tower(w * 0.5f, base - unit * 1.66f, unit * 0.95f, unit * 3.6f)
    // sugar palms (ដើមត្នោត) — the iconic silhouette of the Cambodian countryside
    fun palm(x: Float, hgt: Float) {
        p.addRect(Rect(x - 1.5f, base - hgt, x + 1.5f, base - unit * 0.45f))
        for (a in listOf(-70f, -40f, -15f, 15f, 40f, 70f)) {
            val r = unit * 0.42f
            val rad = Math.toRadians(a.toDouble() - 90)
            val ex = x + (kotlin.math.cos(rad) * r).toFloat()
            val ey = base - hgt + (kotlin.math.sin(rad) * r).toFloat() + abs(a) / 70f * unit * 0.2f
            p.moveTo(x, base - hgt)
            p.quadraticTo((x + ex) / 2f, base - hgt - unit * 0.14f, ex, ey)
            p.quadraticTo((x + ex) / 2f, base - hgt - unit * 0.04f, x, base - hgt + 2f)
            p.close()
        }
    }
    palm(w * 0.05f, unit * 1.9f); palm(w * 0.095f, unit * 1.5f); palm(w * 0.93f, unit * 2.1f)
    return p
}

/**
 * Draws the skyline with a gilded rim and its reflection in the moat (barai).
 * [lift] (0‥1) is driven by scroll for parallax.
 */
fun DrawScope.drawAngkor(skyline: Path, silhouette: Color, rim: Color, water: Color, reflection: Boolean = true) {
    drawPath(skyline, silhouette)
    drawPath(skyline, Brush.verticalGradient(listOf(rim.copy(alpha = 0.55f), Color.Transparent), endY = size.height * 0.6f), style = Stroke(1.2f))
    if (reflection) {
        translate(0f, size.height) {
            scale(1f, -0.45f, pivot = Offset.Zero) {
                drawPath(skyline, Brush.verticalGradient(listOf(silhouette.copy(alpha = 0.5f), Color.Transparent), startY = size.height, endY = size.height * 0.4f))
            }
        }
        drawRect(water, topLeft = Offset(0f, size.height), size = Size(size.width, size.height * 0.45f))
    }
}

// ------------------------------------------------------------------ lotus

/** Lotus with [open] 0 (bud) … 1 (full bloom). */
fun DrawScope.drawLotus(center: Offset, radius: Float, open: Float, petal: Color, heart: Color) {
    val layers = arrayOf(floatArrayOf(7f, 1f, 0.55f), floatArrayOf(5f, 0.78f, 0.8f), floatArrayOf(3f, 0.56f, 1f))
    for ((li, layer) in layers.withIndex()) {
        val n = layer[0].toInt()
        val len = radius * layer[1]
        val spread = (12f + 62f * open) * (1f - li * 0.22f)
        for (i in 0 until n) {
            val a = if (n == 1) 0f else -spread + 2f * spread * i / (n - 1)
            rotate(a, pivot = center) {
                val pth = Path().apply {
                    moveTo(center.x, center.y)
                    cubicTo(center.x - len * 0.42f, center.y - len * 0.35f, center.x - len * 0.22f, center.y - len * 0.92f, center.x, center.y - len)
                    cubicTo(center.x + len * 0.22f, center.y - len * 0.92f, center.x + len * 0.42f, center.y - len * 0.35f, center.x, center.y)
                    close()
                }
                drawPath(pth, petal.copy(alpha = layer[2]))
                drawPath(pth, heart.copy(alpha = 0.35f * layer[2]), style = Stroke(1f))
            }
        }
    }
    drawCircle(heart, radius * 0.09f * (0.6f + 0.4f * open), center)
}

// ------------------------------------------------------------------ moon

/**
 * Moon phase glyph. Illumination 0 = new, 1 = full; waxing lights the right limb
 * (as seen from Cambodia, northern hemisphere).
 */
fun DrawScope.drawMoon(center: Offset, r: Float, illumination: Float, waxing: Boolean, lit: Color, dark: Color, glow: Boolean = true) {
    if (glow && illumination > 0.6f) {
        drawCircle(Brush.radialGradient(listOf(lit.copy(alpha = 0.35f * illumination), Color.Transparent), center, r * 2.6f), r * 2.6f, center)
    }
    drawCircle(dark, r, center)
    val f = illumination.coerceIn(0f, 1f)
    if (f < 0.01f) return
    val circle = Rect(center.x - r, center.y - r, center.x + r, center.y + r)
    val rx = r * abs(1f - 2f * f)
    val ell = Rect(center.x - rx, center.y - r, center.x + rx, center.y + r)
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        arcTo(circle, -90f, 180f, false)
        if (f < 0.5f) arcTo(ell, 90f, -180f, false) else arcTo(ell, 90f, 180f, false)
        close()
    }
    if (waxing) drawPath(path, lit) else scale(-1f, 1f, pivot = center) { drawPath(path, lit) }
}
