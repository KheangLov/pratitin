package com.pratitin.app.ui.screens.love

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pratitin.app.ui.components.heartPath
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The Love tab's palette, laid over the heritage one: rose, blush, wine and rose-gold. */
@Immutable
data class LovePalette(val rose: Color, val blush: Color, val wine: Color, val roseGold: Color, val glow: Color, val cardTint: Color)

@Composable
fun lovePalette(): LovePalette {
    val p = LocalPalette.current
    return remember(p.isDark) {
        if (p.isDark) LovePalette(
            rose = Color(0xFFFF5C8A), blush = Color(0xFFFFB8CB), wine = Color(0xFF6E0F33),
            roseGold = Color(0xFFF4C2A1), glow = Color(0xFFFF8FB1), cardTint = Color(0x33B0255A),
        ) else LovePalette(
            rose = Color(0xFFD6336C), blush = Color(0xFFF7A1BA), wine = Color(0xFF8E1B45),
            roseGold = Color(0xFFB9775A), glow = Color(0xFFE85C8A), cardTint = Color(0x40FFD6E2),
        )
    }
}

private class Floater(val x: Float, val speed: Float, val size: Float, val phase: Float, val sway: Float, val swaySpeed: Float, val color: Int, val sparkle: Boolean)

/**
 * A rose-dusk veil over the sky with hearts drifting upward and sparkles twinkling.
 * One Canvas; the clock is read in the draw phase only, so nothing recomposes.
 */
@Composable
fun LoveAmbience(modifier: Modifier = Modifier) {
    val lp = lovePalette()
    val p = LocalPalette.current
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current
    val floaters = remember {
        val r = java.util.Random(214)
        List(16) { i ->
            Floater(
                x = r.nextFloat(), speed = 0.6f + r.nextFloat() * 0.9f, size = 8f + r.nextFloat() * 16f, phase = r.nextFloat(),
                sway = 10f + r.nextFloat() * 26f, swaySpeed = 1f + r.nextFloat() * 2f, color = r.nextInt(4), sparkle = i % 3 == 0,
            )
        }
    }
    val path = remember { Path() }
    Canvas(modifier) {
        // one 48 s cycle of the shared ambient clock (30 fps, sleeps when idle)
        val t = clock?.let { (it.time / 48f) % 1f } ?: 0.3f
        // a faint wine veil at the very top only: the pond scene below stays clear
        drawRect(Brush.verticalGradient(0f to lp.wine.copy(alpha = if (p.isDark) 0.35f else 0.12f), 0.4f to Color.Transparent))
        val colors = arrayOf(lp.rose, lp.blush, lp.roseGold, lp.glow)
        val h = size.height + 80f
        floaters.forEach { f ->
            val prog = (t * 12f * f.speed + f.phase) % 1f
            val y = size.height + 40f - prog * h
            val x = f.x * size.width + sin((t * 12f * f.swaySpeed + f.phase) * 2 * PI).toFloat() * f.sway.dp.toPx() * 0.4f
            val fade = (if (prog < 0.1f) prog / 0.1f else 1f) * (1f - prog).coerceIn(0f, 1f)
            val c = colors[f.color]
            val s = f.size.dp.toPx()
            if (f.sparkle) {
                val tw = 0.5f + 0.5f * sin((t * 60f * f.swaySpeed + f.phase * 7) * 2 * PI).toFloat()
                drawSparkle(Offset(x, y), s * 0.45f * (0.6f + 0.4f * tw), c.copy(alpha = 0.55f * fade * tw))
            } else {
                rotate(sin((t * 8f * f.swaySpeed + f.phase) * 2 * PI).toFloat() * 18f, Offset(x, y)) {
                    drawPath(heartPath(Offset(x - s / 2, y - s / 2), s, path), c.copy(alpha = 0.38f * fade))
                }
            }
        }
    }
}

fun DrawScope.drawSparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

/** Lub-dub: two beats then a rest, from the shared ambient clock. Read only in draw/graphicsLayer. */
@Composable
fun rememberHeartbeat(): (() -> Float)? {
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current ?: return null
    return remember(clock) { { com.pratitin.app.ui.motion.heartbeatAt(clock.time) } }
}

/**
 * The beating heart: gradient body, glossy highlight, pulse rings and a burst of little
 * hearts on tap.
 */
@Composable
fun BeatingHeart(heartSize: Dp, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val lp = lovePalette()
    val beat = rememberHeartbeat()
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(1) }
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current
    val path = remember { Path() }
    val small = remember { Path() }
    Box(
        modifier.size(heartSize * 1.9f)
            .drawBehind {
                // pulse rings
                val base = heartSize.toPx()
                clock?.let { (it.time / 2.5f) % 1f }?.let { r ->
                    for (k in 0..2) {
                        val ph = (r + k / 3f) % 1f
                        val w = base * (1f + ph * 0.75f)
                        drawPath(heartPath(Offset(center.x - w / 2, center.y - w * 0.45f), w, path), lp.glow.copy(alpha = 0.35f * (1f - ph)), style = Stroke(1.5.dp.toPx()))
                    }
                }
                // burst of hearts
                val b = burst.value
                if (b < 1f) {
                    val rnd = java.util.Random(seed.toLong())
                    for (i in 0 until 18) {
                        val ang = i * (2 * PI / 18) + rnd.nextFloat() * 0.4
                        val dist = base * (0.55f + rnd.nextFloat() * 0.5f) * LinearOutSlowInEasing.transform(b)
                        val hs = base * (0.09f + rnd.nextFloat() * 0.08f) * (1f - b * 0.4f)
                        val x = center.x + (cos(ang) * dist).toFloat()
                        val y = center.y + (sin(ang) * dist).toFloat() + base * 0.35f * b * b
                        val col = if (i % 3 == 0) lp.roseGold else if (i % 3 == 1) lp.rose else lp.blush
                        drawPath(heartPath(Offset(x - hs / 2, y - hs / 2), hs, small), col.copy(alpha = (1f - b)))
                    }
                }
            }
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                seed++
                scope.launch { burst.snapTo(0f); burst.animateTo(1f, tween(1300)) }
                onTap()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(heartSize).graphicsLayer { val k = beat?.invoke() ?: 1f; scaleX = k; scaleY = k }) {
            val w = this.size.width
            val top = Offset(0f, this.size.height * 0.05f)
            // soft glow behind
            drawCircle(Brush.radialGradient(listOf(lp.glow.copy(alpha = 0.45f), Color.Transparent), center = center, radius = w * 0.75f), radius = w * 0.75f)
            val body = heartPath(top, w, path)
            drawPath(body, Brush.linearGradient(listOf(lp.blush, lp.rose, lp.wine), start = Offset(w * 0.2f, 0f), end = Offset(w * 0.8f, w)))
            // glossy highlight on the left lobe
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = Offset(w * 0.3f, w * 0.27f), radius = w * 0.2f),
                radius = w * 0.2f, center = Offset(w * 0.3f, w * 0.27f),
            )
            drawPath(body, lp.roseGold.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
        }
    }
}
