package com.pratitin.app.ui.background

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pratitin.app.ui.heritage.drawMoon
import com.pratitin.app.ui.motion.LocalAmbientClock
import com.pratitin.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The living backdrop. Three scenes in the logo's lacquer-and-gold language:
 *
 *  - [Scene.DIAL]     the lunar month as a gold dial; today's moon glows above its slot,
 *                     a spark travels along the arc, Sila days are marked
 *  - [Scene.MANDALA]  kbach flame petals and lotus leaves like a temple ceiling, two rings
 *                     turning slowly in opposite directions, glints flashing on the petals
 *  - [Scene.POND]     moon over a lotus pond with floating pratip candles that bob, drift
 *                     and flicker, a shimmering moon path, ripples and fireflies
 *
 * Every moon shows the real phase of the day.
 *
 * Performance
 *  - Each scene's static artwork (dozens of gold paths) is rendered once, off the main
 *    thread, into a bitmap sized to just the region it covers. Per frame the scene draws
 *    a gradient, that bitmap (translated or rotated: one textured quad on the GPU) and a
 *    handful of small animated primitives.
 *  - Time comes from the shared [LocalAmbientClock]: ~30 fps, asleep after 25 s idle,
 *    paused under sheets. With [animate] false nothing is redrawn at all.
 *  - No full-screen shader: cheaper than the aurora sky it replaces.
 */
enum class Scene { DIAL, MANDALA, POND }

@Immutable
data class LunarInfo(val illumination: Float, val waxing: Boolean, val day: Int, val monthDays: Int)

@Composable
fun HeritageSky(scene: Scene, lunar: LunarInfo, animate: Boolean, parallax: () -> Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val clock = LocalAmbientClock.current
    val sp = remember(p.isDark) { ScenePalette.of(p.isDark) }
    // Frozen at the last moment shown when not animating: the scene stops redrawing entirely.
    val frozen = remember { floatArrayOf(23f) }
    val time: () -> Float = { if (animate && clock != null) clock.time.also { frozen[0] = it } else frozen[0] }
    Crossfade(scene, animationSpec = tween(700), label = "scene", modifier = modifier.fillMaxSize()) { s ->
        when (s) {
            Scene.DIAL -> DialScene(sp, lunar, time, parallax)
            Scene.MANDALA -> MandalaScene(sp, lunar, time, parallax)
            Scene.POND -> PondScene(sp, lunar, time, parallax)
        }
    }
}

// ====================================================================== palette

@Immutable
private class ScenePalette(
    val top: Color, val mid: Color, val glow: Color, val gold: Color, val goldSoft: Color, val goldDeep: Color,
    val moonLit: Color, val moonDark: Color, val silhouette: Color, val water1: Color, val water2: Color,
    val pad: Color, val flame: Color, val flameGlow: Color, val star: Color, val starAlpha: Float, val dark: Boolean,
) {
    companion object {
        private val DARK = ScenePalette(
            top = Color(0xFF0B0818), mid = Color(0xFF2A0B1E), glow = Color(0xFF6E1A2A), gold = Color(0xFFE8B653),
            goldSoft = Color(0xFFF6D894), goldDeep = Color(0xFFB8782A), moonLit = Color(0xFFFFF0CD), moonDark = Color(0xFF3C1424),
            silhouette = Color(0xFF220818), water1 = Color(0xFF461022), water2 = Color(0xFF0E040E), pad = Color(0xFF1E0816),
            flame = Color(0xFFFFF6DC), flameGlow = Color(0xFFFFBE6E), star = Color(0xFFFFF0CD), starAlpha = 1f, dark = true,
        )
        private val LIGHT = ScenePalette(
            top = Color(0xFFF7EBD8), mid = Color(0xFFEBD6B8), glow = Color(0xFFE2B49C), gold = Color(0xFFA9742A),
            goldSoft = Color(0xFFC8923F), goldDeep = Color(0xFF7A5016), moonLit = Color(0xFFFFFBF0), moonDark = Color(0xFFE6CDB0),
            silhouette = Color(0xFFB08A66), water1 = Color(0xFFE4C6A8), water2 = Color(0xFFD2AE8C), pad = Color(0xFFA88A62),
            flame = Color(0xFFFFF4D0), flameGlow = Color(0xFFF2A65A), star = Color(0xFFA9742A), starAlpha = 0f, dark = false,
        )
        fun of(dark: Boolean) = if (dark) DARK else LIGHT
    }
}

// ====================================================================== shared helpers

/** Renders [draw] once into a w×h bitmap on a background thread; null until ready. */
@Composable
private fun rememberArt(key: Any, w: Int, h: Int, draw: DrawScope.() -> Unit): ImageBitmap? {
    val density = LocalDensity.current
    val art by produceState<ImageBitmap?>(null, key, w, h) {
        if (w <= 0 || h <= 0) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bmp = ImageBitmap(w, h)
                CanvasDrawScope().draw(Density(density.density), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bmp), Size(w.toFloat(), h.toFloat())) { draw() }
                bmp
            }.getOrNull()
        }
    }
    return art
}

private class Star(val x: Float, val y: Float, val r: Float, val ph: Float)

private fun stars(n: Int, seed: Long, yMax: Float): List<Star> {
    val r = java.util.Random(seed)
    return List(n) { Star(r.nextFloat(), r.nextFloat() * yMax, 0.6f + r.nextFloat() * 1.3f, r.nextFloat() * 6.28f) }
}

private fun DrawScope.drawStars(list: List<Star>, sp: ScenePalette, t: Float) {
    if (sp.starAlpha <= 0f) return
    for (s in list) {
        val a = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 1.3f + s.ph))
        drawCircle(sp.star.copy(alpha = a * 0.8f), s.r * density, Offset(s.x * size.width, s.y * size.height))
    }
}

/** The app logo's five-petal lotus, in its 108-unit viewport. */
private val LOTUS_PETALS: List<FloatArray> = listOf(
    floatArrayOf(54f, 30f, 60f, 40f, 62f, 52f, 54f, 66f, 46f, 52f, 48f, 40f, 54f, 30f),
    floatArrayOf(54f, 66f, 44f, 60f, 36f, 50f, 34f, 40f, 44f, 42f, 52f, 50f, 54f, 66f),
    floatArrayOf(54f, 66f, 64f, 60f, 72f, 50f, 74f, 40f, 64f, 42f, 56f, 50f, 54f, 66f),
    floatArrayOf(54f, 68f, 42f, 68f, 30f, 62f, 26f, 52f, 38f, 52f, 48f, 58f, 54f, 68f),
    floatArrayOf(54f, 68f, 66f, 68f, 78f, 62f, 82f, 52f, 70f, 52f, 60f, 58f, 54f, 68f),
)

private fun DrawScope.drawLogoLotus(center: Offset, size: Float, fills: List<Color>?, line: Color? = null, lineW: Float = 1.5f) {
    val k = size / 108f
    LOTUS_PETALS.forEachIndexed { i, c ->
        val path = Path().apply {
            fun X(v: Float) = center.x + (v - 54f) * k
            fun Y(v: Float) = center.y + (v - 54f) * k
            moveTo(X(c[0]), Y(c[1]))
            cubicTo(X(c[2]), Y(c[3]), X(c[4]), Y(c[5]), X(c[6]), Y(c[7]))
            cubicTo(X(c[8]), Y(c[9]), X(c[10]), Y(c[11]), X(c[12]), Y(c[13]))
            close()
        }
        if (fills != null) drawPath(path, fills[i.coerceAtMost(fills.lastIndex)])
        if (line != null) drawPath(path, line, style = Stroke(lineW * density))
    }
}

private fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

// ====================================================================== 1. Lunar dial

private class DialGeo(val w: Float, h: Float) {
    val k = w / 1080f
    val R = w * 1.167f
    val arcTop = h * 0.70f
    val cx = w / 2f
    val cy = arcTop + R
    val bandTop = arcTop - 110f * k
    val bandH = (h - bandTop).toInt().coerceAtLeast(1)
    fun angle(i: Int, n: Int): Float {
        val half = asin(((cx - 0.045f * w) / R).coerceIn(0f, 0.99f))
        val step = 2f * half / (n - 1)
        return (-PI.toFloat() / 2f) + (i - (n - 1) / 2f) * step
    }
    fun pos(a: Float, r: Float = R) = Offset(cx + cos(a) * r, cy + sin(a) * r)
}

@Composable
private fun DialScene(sp: ScenePalette, lunar: LunarInfo, time: () -> Float, parallax: () -> Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val geo = remember(w, h) { DialGeo(w.toFloat(), h.toFloat()) }
        val starList = remember { stars(55, 7, 0.62f) }
        val n = lunar.monthDays.coerceIn(29, 30)
        val art = rememberArt(Triple(sp, lunar, geo), w, geo.bandH) { translate(0f, -geo.bandTop) { drawDialArt(geo, sp, lunar, n) } }
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val bg = Brush.verticalGradient(0f to sp.top, 0.45f to sp.mid, 1f to sp.glow)
                val glowC = Offset(size.width / 2, geo.arcTop + 200f * geo.k)
                val glow = Brush.radialGradient(listOf(sp.glow.copy(alpha = 0.9f), Color.Transparent), glowC, size.width * 0.9f)
                val today = lunar.day.coerceIn(0, n - 1)
                val slot = geo.pos(geo.angle(today, n))
                val bigMoon = Offset(slot.x + 40f * geo.k, slot.y - 150f * geo.k)
                onDrawBehind {
                    val t = time()
                    drawRect(bg)
                    drawCircle(glow, size.width * 0.9f, glowC)
                    drawStars(starList, sp, t)
                    val py = parallax() * 30f * geo.k
                    translate(0f, py) {
                        art?.let { drawImage(it, Offset(0f, geo.bandTop)) }
                        // spark travelling along the dial, with a fading tail
                        val u = (t / 16f) % 1f
                        val a0 = geo.angle(0, n); val a1 = geo.angle(n - 1, n)
                        for (j in 0 until 7) {
                            val uu = u - j * 0.006f
                            if (uu < 0f) continue
                            val pos = geo.pos(a0 + (a1 - a0) * uu, geo.R + 52f * geo.k)
                            drawCircle(sp.goldSoft.copy(alpha = (1f - j / 7f) * 0.9f), (3.2f - j * 0.35f) * geo.k * 2f, pos)
                        }
                        // today's slot: a breathing ring
                        val br = 0.5f + 0.5f * sin(t * 1.6f)
                        drawCircle(sp.gold.copy(alpha = 0.35f + 0.4f * br), (26f + 6f * br) * geo.k, slot, style = Stroke(1.6f * density))
                        // today's moon above its slot, with a soft pulsing halo
                        drawLine(sp.gold.copy(alpha = 0.6f), Offset(slot.x, slot.y - 30f * geo.k), Offset(bigMoon.x - 20f * geo.k, bigMoon.y + 60f * geo.k), 1.4f * density, StrokeCap.Round)
                        val hr = 70f * geo.k * (2.6f + 0.25f * sin(t * 0.8f))
                        drawCircle(Brush.radialGradient(listOf(sp.goldSoft.copy(alpha = 0.32f), Color.Transparent), bigMoon, hr), hr, bigMoon)
                        drawMoon(bigMoon, 70f * geo.k, lunar.illumination, lunar.waxing, sp.moonLit, sp.moonDark.copy(alpha = 0.55f), glow = false)
                    }
                }
            },
        )
    }
}

private fun DrawScope.drawDialArt(g: DialGeo, sp: ScenePalette, lunar: LunarInfo, n: Int) {
    val k = g.k
    for ((dr, wdt) in listOf(46f to 1.6f, -46f to 1.6f, 58f to 0.8f, -58f to 0.8f)) {
        drawCircle(sp.gold.copy(alpha = 0.55f), g.R + dr * k, Offset(g.cx, g.cy), style = Stroke(wdt * density))
    }
    for (i in 0 until n - 1) {
        val a = (g.angle(i, n) + g.angle(i + 1, n)) / 2f
        drawLine(sp.gold.copy(alpha = 0.45f), g.pos(a, g.R - 46f * k), g.pos(a, g.R + 46f * k), 0.9f * density)
    }
    val half = n / 2
    for (i in 0 until n) {
        val a = g.angle(i, n)
        val c = g.pos(a)
        val waxing = i < half
        val illum = ((1 - cos(2 * PI * (i + 0.5) / n)) / 2).toFloat()
        val today = i == lunar.day
        drawMoon(c, (if (today) 21f else 15f) * k, illum, waxing, if (today) sp.moonLit else sp.goldSoft.copy(alpha = 0.92f), sp.moonDark, glow = false)
        // Sila days: 8 and 15 waxing, 8 and the last day waning
        if (i == 7 || i == 14 || i == half + 7 || i == n - 1) drawCircle(sp.gold, 3.4f * k * 1.4f, g.pos(a, g.R + 82f * k))
        // full and new moon: a tiny lotus mark inside the ring
        if (i == 14 || i == n - 1) drawLogoLotus(g.pos(a, g.R - 80f * k), 34f * k, null, sp.gold.copy(alpha = 0.8f), 0.8f)
    }
    // the logo lotus rising under the dial, in gold line
    drawLogoLotus(Offset(g.cx, size.height * 0.92f), 380f * k, null, sp.gold.copy(alpha = 0.55f), 1.3f)
}

// ====================================================================== 2. Kbach mandala

private fun kbachFlame(cx: Float, cy: Float, ang: Float, r0: Float, r1: Float, width: Float, curl: Float): Path {
    val path = Path()
    val n = 24
    fun pt(t: Float, side: Float): Offset {
        val rad = r0 + (r1 - r0) * t
        val wv = width * sin(PI.toFloat() * (t * 1.15f).coerceAtMost(1f)).pow(0.8f) * (1f - t * 0.35f)
        val off = curl * width * 0.9f * t * t * t
        val a = ang + (side * wv + off) / rad
        return Offset(cx + cos(a) * rad, cy + sin(a) * rad)
    }
    val first = pt(0f, 1f); path.moveTo(first.x, first.y)
    for (i in 1..n) { val p = pt(i / n.toFloat(), 1f); path.lineTo(p.x, p.y) }
    for (i in n downTo 0) { val p = pt(i / n.toFloat(), -1f); path.lineTo(p.x, p.y) }
    path.close()
    return path
}

@Composable
private fun MandalaScene(sp: ScenePalette, lunar: LunarInfo, time: () -> Float, parallax: () -> Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val k = w / 1080f
        val innerR = 420f * k; val outerR = 740f * k
        val starList = remember { stars(45, 11, 0.5f) }
        val inner = rememberArt(Pair(sp, w), (innerR * 2).toInt() + 4, (innerR * 2).toInt() + 4) { drawMandalaInner(sp, k, innerR + 2) }
        val outer = rememberArt(Triple(sp, w, "o"), (outerR * 2).toInt() + 4, (outerR * 2).toInt() + 4) { drawMandalaOuter(sp, k, outerR + 2) }
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val c = Offset(size.width / 2f, size.height * 0.80f)
                val bg = Brush.radialGradient(0f to sp.glow, 0.45f to sp.mid, 1f to sp.top, center = c, radius = size.height * 0.85f)
                onDrawBehind {
                    val t = time()
                    drawRect(bg)
                    drawStars(starList, sp, t)
                    translate(0f, parallax() * 30f * k) {
                        // two rings turning slowly in opposite directions
                        outer?.let { img -> rotate(-t * 1.2f, c) { drawImage(img, Offset(c.x - img.width / 2f, c.y - img.height / 2f), alpha = 0.55f) } }
                        val rotIn = t * 2.0f
                        inner?.let { img -> rotate(rotIn, c) { drawImage(img, Offset(c.x - img.width / 2f, c.y - img.height / 2f), alpha = 0.8f) } }
                        // glints flashing on the petal tips (they ride the inner ring)
                        for (i in 0 until 8) {
                            val ph = t * 0.9f + i * 1.93f
                            val f = (0.5f + 0.5f * sin(ph)).pow(10f)
                            if (f < 0.02f) continue
                            val a = (i * 4 + 1) * (2 * PI.toFloat() / 32f) + (rotIn * PI.toFloat() / 180f)
                            val pos = Offset(c.x + cos(a) * 380f * k, c.y + sin(a) * 380f * k)
                            drawSparkle(pos, 10f * k * (0.6f + 0.6f * f), sp.goldSoft.copy(alpha = f))
                        }
                        // the moon at the heart, breathing softly
                        val hr = 72f * k * (2.3f + 0.2f * sin(t * 0.7f))
                        drawCircle(Brush.radialGradient(listOf(sp.goldSoft.copy(alpha = 0.35f), Color.Transparent), c, hr), hr, c)
                        drawMoon(c, 72f * k, lunar.illumination, lunar.waxing, sp.moonLit, sp.moonDark.copy(alpha = 0.6f), glow = false)
                    }
                }
            },
        )
    }
}

private fun DrawScope.drawMandalaInner(sp: ScenePalette, k: Float, r: Float) {
    val c = Offset(r, r)
    val line = Stroke(1.5f * density)
    for (i in 0 until 16) drawPath(kbachFlame(c.x, c.y, i * 2 * PI.toFloat() / 16, 110f * k, 230f * k, 34f * k, 0f), sp.gold, style = line)
    for (i in 0 until 32) drawPath(kbachFlame(c.x, c.y, (i + 0.5f) * 2 * PI.toFloat() / 32, 250f * k, 380f * k, 26f * k, if (i % 2 == 0) 1f else -1f), sp.gold, style = line)
    for (rad in listOf(100f, 240f, 395f, 410f)) drawCircle(sp.gold, rad * k, c, style = Stroke(1.3f * density))
    for (i in 0 until 64) {
        val a = i * 2 * PI.toFloat() / 64
        drawCircle(sp.gold, 3.6f * k, Offset(c.x + cos(a) * 402f * k, c.y + sin(a) * 402f * k))
    }
}

private fun DrawScope.drawMandalaOuter(sp: ScenePalette, k: Float, r: Float) {
    val c = Offset(r, r)
    for (i in 0 until 24) drawPath(kbachFlame(c.x, c.y, (i + 0.5f) * 2 * PI.toFloat() / 24, 430f * k, 700f * k, 60f * k, if (i % 2 == 0) 1f else -1f), sp.gold, style = Stroke(1.4f * density))
    drawCircle(sp.gold, 730f * k, c, style = Stroke(1.4f * density))
    for (i in 0 until 24) {
        val a = i * 2 * PI.toFloat() / 24
        drawCircle(sp.gold, 4f * k, Offset(c.x + cos(a) * 730f * k, c.y + sin(a) * 730f * k))
    }
}

// ====================================================================== 3. Moon over the lotus pond

private class Pratip(val fx: Float, val fd: Float, val ph: Float)

private val FLOATS = listOf(
    Pratip(0.22f, 0.04f, 0.3f), Pratip(0.38f, 0.09f, 1.7f), Pratip(0.58f, 0.05f, 2.9f), Pratip(0.12f, 0.18f, 0.8f), Pratip(0.47f, 0.22f, 4.1f),
    Pratip(0.80f, 0.14f, 5.2f), Pratip(0.30f, 0.36f, 2.2f), Pratip(0.66f, 0.33f, 3.6f), Pratip(0.90f, 0.42f, 1.1f), Pratip(0.52f, 0.50f, 5.9f),
)

@Composable
private fun PondScene(sp: ScenePalette, lunar: LunarInfo, time: () -> Float, parallax: () -> Float) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth; val h = constraints.maxHeight
        val k = w / 1080f
        val hz = h * 0.70f
        val artTop = hz - 140f * k
        val starList = remember { stars(60, 3, 0.55f) }
        val art = rememberArt(Triple(sp, w, h), w, (h - artTop).toInt().coerceAtLeast(1)) { translate(0f, -artTop) { drawPondArt(sp, k, hz, h.toFloat()) } }
        val boat = remember { Path() }
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val sky = Brush.verticalGradient(0f to sp.top, 0.5f * hz / size.height to sp.mid, hz / size.height to sp.glow, 1f to sp.glow)
                val moonC = Offset(size.width * 0.72f, size.height * 0.585f)
                val mr = 64f * k
                val waterBottom = size.height - 420f * k
                onDrawBehind {
                    val t = time()
                    drawRect(sky)
                    drawStars(starList, sp, t)
                    val py = parallax() * 30f * k
                    // moon with a slow halo
                    val hr = mr * (3f + 0.25f * sin(t * 0.6f))
                    drawCircle(Brush.radialGradient(listOf(sp.goldSoft.copy(alpha = 0.28f), Color.Transparent), moonC, hr), hr, moonC)
                    drawMoon(moonC, mr, lunar.illumination, lunar.waxing, sp.moonLit, sp.moonDark.copy(alpha = 0.5f), glow = false)
                    translate(0f, py) {
                        art?.let { drawImage(it, Offset(0f, artTop)) }
                        // moon path: shimmering gold dashes, wider toward the viewer
                        for (j in 0 until 30) {
                            val f = j / 29f
                            val y = hz + 10f * k + (waterBottom - hz) * f.pow(1.4f)
                            val half = (10f + 110f * f) * k * (0.55f + 0.45f * sin(t * 2.1f + j * 1.7f))
                            val x = moonC.x + sin(t * 1.3f + j * 0.9f) * 8f * k * f
                            val a = (0.6f - 0.3f * f) * (0.45f + 0.55f * (0.5f + 0.5f * sin(t * 2.7f + j * 2.3f)))
                            drawLine(sp.goldSoft.copy(alpha = a), Offset(x - half, y), Offset(x + half, y), (2f + 2f * f) * k * 1.5f, StrokeCap.Round)
                        }
                        // floating pratip: drift, bob, flicker; ripples and reflections
                        FLOATS.forEachIndexed { i, fl ->
                            val sc = (0.35f + 1.4f * fl.fd) * k * 1.0f
                            val span = size.width + 160f * k
                            val x = ((fl.fx * size.width + t * 7f * k * (0.4f + fl.fd)) % span) - 80f * k
                            val baseY = hz + 20f * k + fl.fd * (waterBottom - hz)
                            val y = baseY + sin(t * 1.15f + fl.ph) * 3f * sc
                            val flick = 1f + 0.16f * sin(t * 9f + fl.ph * 3f) + 0.08f * sin(t * 13.7f + fl.ph)
                            val gr = 46f * sc * (0.92f + 0.08f * flick)
                            drawCircle(Brush.radialGradient(listOf(sp.flameGlow.copy(alpha = 0.42f), Color.Transparent), Offset(x, y - 14f * sc), gr), gr, Offset(x, y - 14f * sc))
                            for (d in 0 until 4) {
                                val ry = y + (10f + d * 11f) * sc
                                drawLine(sp.flameGlow.copy(alpha = (0.45f - d * 0.1f) * flick.coerceAtMost(1.1f)), Offset(x - 5f * sc, ry), Offset(x + 5f * sc, ry), 2.6f * sc, StrokeCap.Round)
                            }
                            if (i % 3 == 0) {
                                val u = ((t * 0.32f + fl.ph * 0.17f) % 1f)
                                val rr = (14f + 44f * u) * sc
                                drawOval(sp.goldSoft.copy(alpha = (1f - u) * 0.35f), Offset(x - rr, y - rr * 0.28f), Size(rr * 2, rr * 0.56f), style = Stroke(1.2f * density))
                            }
                            // banana-leaf boat with three petals
                            boat.reset()
                            val bw = 34f * sc
                            boat.addArc(Rect(x - bw, y - bw * 0.6f, x + bw, y + bw * 0.5f), 0f, 180f)
                            boat.close()
                            for (p in -1..1) {
                                val bx = x + p * bw * 0.45f
                                boat.moveTo(bx - bw * 0.28f, y); boat.lineTo(bx, y - bw * (if (p == 0) 0.75f else 0.55f)); boat.lineTo(bx + bw * 0.28f, y); boat.close()
                            }
                            drawPath(boat, sp.silhouette)
                            drawPath(boat, sp.gold.copy(alpha = 0.9f), style = Stroke(1.2f * density))
                            // candle flame
                            val fh = 9f * sc * flick
                            drawOval(sp.flame, Offset(x - 4f * sc, y - 30f * sc - fh), Size(8f * sc, fh * 2f))
                        }
                        // fireflies over the far shore
                        for (i in 0 until 8) {
                            val fx = size.width * (0.08f + 0.84f * ((i * 0.377f) % 1f)) + sin(t * 0.31f + i) * 40f * k
                            val fy = hz - 70f * k + cos(t * 0.43f + i * 1.3f) * 50f * k
                            val a = (0.5f + 0.5f * sin(t * 2.2f + i * 2.1f)).pow(3f)
                            drawCircle(sp.goldSoft.copy(alpha = a * 0.9f), 3.2f * k * 1.3f, Offset(fx, fy))
                        }
                    }
                }
            },
        )
    }
}

private fun DrawScope.drawPondArt(sp: ScenePalette, k: Float, hz: Float, h: Float) {
    val w = size.width
    // still water
    drawRect(Brush.verticalGradient(listOf(sp.water1, sp.water2), startY = hz, endY = h), Offset(0f, hz), Size(w, h - hz))
    // far shore: bumpy tree line with sugar palms, gold rim along the top
    val shore = Path()
    val r = java.util.Random(3)
    var x = -20f * k
    shore.moveTo(0f, hz + 6f * k)
    while (x < w + 20f * k) {
        val rr = (10f + r.nextFloat() * 18f) * k
        shore.addOval(Rect(x - rr, hz - rr * 1.5f, x + rr, hz + rr))
        x += rr * 1.3f
    }
    shore.addRect(Rect(0f, hz - 2f * k, w, hz + 6f * k))
    drawPath(shore, sp.silhouette)
    for (px in listOf(0.08f, 0.2f, 0.33f, 0.52f, 0.86f, 0.94f)) {
        val bx = px * w; val ph = (60f + r.nextFloat() * 40f) * k
        drawLine(sp.silhouette, Offset(bx, hz), Offset(bx, hz - ph), 3f * k * 1.5f)
        for (j in 0 until 9) {
            val a = (-180f + j * 22.5f) * PI.toFloat() / 180f
            drawLine(sp.silhouette, Offset(bx, hz - ph), Offset(bx + cos(a) * 22f * k, hz - ph + sin(a) * 18f * k), 4f * k * 1.4f, StrokeCap.Round)
        }
    }
    drawLine(sp.gold.copy(alpha = 0.45f), Offset(0f, hz + 5f * k), Offset(w, hz + 5f * k), 1.2f * density)
    // lotus pads with gold rims
    for ((cx, cy, rr) in listOf(Triple(0.12f, 0.905f, 120f), Triple(0.32f, 0.935f, 80f), Triple(0.88f, 0.91f, 110f))) {
        val rect = Rect(cx * w - rr * k, cy * h - rr * k * 0.32f, cx * w + rr * k, cy * h + rr * k * 0.32f)
        drawArc(sp.pad, 20f, 320f, true, rect.topLeft, rect.size)
        drawArc(sp.gold.copy(alpha = 0.8f), 20f, 320f, true, rect.topLeft, rect.size, style = Stroke(1.3f * density))
    }
    // the logo lotus in front
    val fills = listOf(Color(0xFFE8B653), Color(0xFFD99A3C), Color(0xFFD99A3C), Color(0xFFB8782A), Color(0xFFB8782A))
    drawLogoLotus(Offset(0.15f * w, 0.875f * h), 300f * k, fills)
    drawLogoLotus(Offset(0.86f * w, 0.885f * h), 210f * k, fills)
}
