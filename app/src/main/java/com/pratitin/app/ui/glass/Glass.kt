package com.pratitin.app.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.pratitin.app.ui.theme.LocalPalette
import kotlin.math.max

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
internal val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S          // RenderEffect blur

@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
internal val CAN_REFRACT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU // AGSL

/**
 * A live, blurred snapshot of whatever is drawn inside [backdropSource].
 *
 * Performance notes
 *  - The source is recorded once per frame into a RenderNode-backed [GraphicsLayer].
 *  - The blur runs on a **quarter-resolution** copy (1/16 of the pixels) — glass is
 *    blurry anyway, so the downscale is invisible but the GPU cost drops ~16×.
 *  - Glass elements draw the blurred layer *by reference*: when the source re-records
 *    (scroll, animation) the RenderThread picks it up with no recomposition.
 */
@Stable
class Backdrop internal constructor(
    internal val source: GraphicsLayer,
    internal val blurred: GraphicsLayer,
    val blurRadius: Dp,
) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var ready by mutableStateOf(false)

    /**
     * Only the part of the source below this y (px) is blurred. The shell sets it to the
     * tab-bar strip while no glass sheet is open, so the full-screen re-blur that the
     * tab bar would otherwise trigger every frame shrinks to a thin band.
     */
    var captureTop by mutableFloatStateOf(0f)
    internal var appliedBlurPx = -1f
    internal val scale = 0.25f
}

/**
 * The backdrop whose source encloses the current composition. A glass element must
 * never sample a source that contains it (the RenderNode would draw itself), so
 * [glass] silently falls back to tint-only frost if that is attempted.
 */
val LocalEnclosingBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun rememberBackdrop(blurRadius: Dp = 28.dp): Backdrop {
    val a = rememberGraphicsLayer()
    val b = rememberGraphicsLayer()
    return remember(a, b, blurRadius) { Backdrop(a, b, blurRadius) }
}

/** Marks the content that glass elements will see through. */
fun Modifier.backdropSource(backdrop: Backdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        val src = backdrop.source
        src.record(this, layoutDirection, IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
            this@drawWithContent.drawContent()
        }
        drawLayer(src)
        if (CAN_BLUR && size.width >= 1f && size.height >= 1f) {
            val s = backdrop.scale
            val blurPx = backdrop.blurRadius.toPx() * s
            if (blurPx != backdrop.appliedBlurPx) {
                backdrop.blurred.renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                backdrop.appliedBlurPx = blurPx
            }
            val top = backdrop.captureTop.coerceIn(0f, (size.height - 1f).coerceAtLeast(0f))
            val small = IntSize(max(1, (size.width * s).toInt()), max(1, ((size.height - top) * s).toInt()))
            backdrop.blurred.record(this, layoutDirection, small) {
                scale(s, s, pivot = Offset.Zero) { translate(0f, -top) { drawLayer(src) } }
            }
            if (!backdrop.ready) backdrop.ready = true
        }
    }

/**
 * Frosted / liquid glass surface.
 *
 * @param liquid adds the AGSL lens (refraction, dispersion, specular rim). Use it on
 *               hero surfaces and floating bars; plain frost is cheaper for lists.
 * @param tint   colour wash over the blur; defaults to the theme's glass tint.
 */
fun Modifier.glass(
    backdrop: Backdrop?,
    shape: Shape,
    liquid: Boolean = false,
    tint: Color = Color.Unspecified,
    cornerRadius: Dp = 28.dp,
    strokeWidth: Dp = 1.dp,
): Modifier = composed {
    val palette = LocalPalette.current
    val safeBackdrop = backdrop.takeIf { it !== LocalEnclosingBackdrop.current }
    val wash = if (tint == Color.Unspecified) palette.glassTint else tint
    val stroke = palette.glassStroke
    var position by remember { mutableStateOf(Offset.Zero) }
    val lens = if (liquid && CAN_REFRACT) rememberGraphicsLayer() else null
    val shader = remember(lens) { if (lens != null && CAN_REFRACT) LensShader() else null }

    this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            if (lens != null && shader != null && CAN_REFRACT) {
                lens.renderEffect = shader.effect(size.width, size.height, cornerRadius.toPx(), palette.isDark)
            }
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.35f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (palette.isDark) 0.10f else 0.02f),
            )
            val rim = Brush.linearGradient(
                0f to stroke.copy(alpha = (stroke.alpha * 1.6f).coerceAtMost(1f)),
                0.5f to stroke.copy(alpha = stroke.alpha * 0.25f),
                1f to stroke.copy(alpha = stroke.alpha * 0.9f),
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
            val strokePx = strokeWidth.toPx()
            onDrawBehind {
                clipPath(path) {
                    val bd = safeBackdrop
                    if (bd != null && CAN_BLUR && bd.ready) {
                        val off = position - bd.origin - Offset(0f, bd.captureTop)
                        val inv = 1f / bd.scale
                        if (lens != null) {
                            lens.record(this, layoutDirection, IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))) {
                                translate(-off.x, -off.y) { scale(inv, inv, pivot = Offset.Zero) { drawLayer(bd.blurred) } }
                            }
                            drawLayer(lens)
                        } else {
                            translate(-off.x, -off.y) { scale(inv, inv, pivot = Offset.Zero) { drawLayer(bd.blurred) } }
                        }
                    }
                    drawRect(wash)
                    drawRect(sheen)
                }
                drawPath(path, rim, style = Stroke(strokePx))
            }
        }
}

/** Holds one RuntimeShader per glass element; uniforms update only on size change. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class LensShader {
    private val shader = RuntimeShader(Shaders.LIQUID_GLASS)

    fun effect(w: Float, h: Float, radius: Float, dark: Boolean): androidx.compose.ui.graphics.RenderEffect {
        val s = shader
        s.setFloatUniform("size", w, h)
        s.setFloatUniform("radius", radius)
        s.setFloatUniform("thickness", (minOf(w, h) * 0.22f).coerceIn(10f, 64f))
        s.setFloatUniform("refraction", (minOf(w, h) * 0.12f).coerceIn(6f, 36f))
        s.setFloatUniform("dispersion", if (dark) 0.08f else 0.05f)
        return RenderEffect.createRuntimeShaderEffect(s, "content").asComposeRenderEffect()
    }
}
