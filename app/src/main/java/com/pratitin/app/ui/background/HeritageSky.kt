package com.pratitin.app.ui.background

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.pratitin.app.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.pratitin.app.ui.glass.CAN_REFRACT
import com.pratitin.app.ui.glass.Shaders
import com.pratitin.app.ui.theme.HeritagePalette
import com.pratitin.app.ui.theme.LocalPalette
import kotlin.math.sin
import kotlin.random.Random

/**
 * The living backdrop: an AGSL aurora sky (Android 13+) or layered radial glows
 * (older devices), twinkling stars, and the Angkor skyline reflected in the moat.
 *
 * Battery: the clock ticks at ≤30 fps, pauses entirely when [animate] is false
 * (user setting or system "remove animations"), and only the draw phase runs.
 * @param parallax 0‥1 scroll fraction from the active screen, nudges the skyline.
 */
@Composable
fun HeritageSky(animate: Boolean, parallax: () -> Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current
    // Frozen at the last moment shown when not animating: the sky stops redrawing entirely.
    val frozen = remember { floatArrayOf(17f) }
    fun time(): Float = if (animate && clock != null) clock.time.also { frozen[0] = it } else frozen[0]

    Box(modifier.fillMaxSize()) {
        // ---- sky
        if (CAN_REFRACT) AuroraShaderLayer(p) { time() } else GradientSky(p) { time() }

        // ---- stars (dark theme only), drawn once into cache, twinkle via alpha
        if (p.isDark) {
            val stars = remember { List(70) { floatArrayOf(Random.nextFloat(), Random.nextFloat() * 0.62f, 0.4f + Random.nextFloat() * 1.4f, Random.nextFloat() * 6.28f) } }
            Canvas(Modifier.fillMaxSize()) {
                val t = time()
                for (s in stars) {
                    val a = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.3f + s[3]))
                    drawCircle(p.moon.copy(alpha = a * 0.8f), s[2], Offset(s[0] * size.width, s[1] * size.height))
                }
            }
        }

        // ---- Khmer countryside: two painted layers (moon, rice paddies and stilt house, then
        // sugar palms and the lotus pond) that drift at different speeds on scroll for depth parallax.
        val backId = if (p.isDark) R.drawable.scene_back_dusk else R.drawable.scene_back_day
        val frontId = if (p.isDark) R.drawable.scene_front_dusk else R.drawable.scene_front_day
        val back = ImageBitmap.imageResource(backId)
        val front = ImageBitmap.imageResource(frontId)
        Image(
            back, contentDescription = null, contentScale = ContentScale.FillWidth, alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .graphicsLayer { translationY = parallax() * 40.dp.toPx(); alpha = 1f - parallax() * 0.35f },
        )
        Image(
            front, contentDescription = null, contentScale = ContentScale.FillWidth, alignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .graphicsLayer { translationY = parallax() * 90.dp.toPx(); alpha = 1f - parallax() * 0.5f },
        )
    }
}

@Composable
private fun AuroraShaderLayer(p: HeritagePalette, time: () -> Float) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val shader = remember { AuroraShader() }
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val brush = shader.brush(size.width, size.height, p)
            onDrawBehind {
                shader.setTime(time())
                drawRect(brush)
            }
        },
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AuroraShader {
    private val shader = RuntimeShader(Shaders.AURORA)
    private val brush = ShaderBrush(shader)

    fun brush(w: Float, h: Float, p: HeritagePalette): Brush {
        shader.setFloatUniform("size", w, h)
        fun set(name: String, c: Color) = shader.setFloatUniform(name, c.red, c.green, c.blue, 1f)
        val c = FlagSky.of(p)
        set("c0", c.deep); set("c1", c.royal); set("c2", c.crimson); set("c3", c.light)
        return brush
    }

    fun setTime(t: Float) = shader.setFloatUniform("time", t)
}

/**
 * Sky colours inspired by the Cambodian flag: royal blue overhead, a crimson glow on the
 * horizon behind the (white) temple.
 */
private class FlagSky(val deep: Color, val royal: Color, val crimson: Color, val light: Color) {
    companion object {
        private val DUSK = FlagSky(Color(0xFF050F3E), Color(0xFF0B2C96), Color(0xFFC8102E), Color(0xFF7FA6FF))
        private val DAY = FlagSky(Color(0xFF2F62D6), Color(0xFF5E8EF0), Color(0xFFE8394B), Color(0xFFFFFFFF))
        fun of(p: HeritagePalette) = if (p.isDark) DUSK else DAY
    }
}

/** Fallback sky for Android 8–12: drifting radial glows over a royal-blue gradient. */
@Composable
private fun GradientSky(p: HeritagePalette, time: () -> Float) {
    val c = FlagSky.of(p)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(c.deep, c.royal, c.deep)))
        val t = time()
        val glows = listOf(
            Triple(Offset(size.width * (0.2f + 0.08f * sin(t * 0.11f)), size.height * 0.25f), c.light, 0.22f),
            Triple(Offset(size.width * (0.85f + 0.06f * sin(t * 0.07f + 1f)), size.height * 0.45f), c.royal, 0.4f),
            Triple(Offset(size.width * 0.5f, size.height * 0.92f), c.crimson, 0.4f),
        )
        for ((c, col, a) in glows) {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = a), Color.Transparent), c, size.maxDimension * 0.55f), size.maxDimension * 0.55f, c)
        }
    }
}
