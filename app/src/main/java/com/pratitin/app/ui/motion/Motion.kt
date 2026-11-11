package com.pratitin.app.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/** Motion tokens — springs everywhere, no linear tweens. */
object Motion {
    val snappy = spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val bouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
    val gentle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow)
    val press = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
}

/** True when the user turned animations off in system settings — we honour it everywhere. */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun rememberSystemReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Squish-on-press: scales the element on the RenderThread (graphicsLayer), so a
 * press never triggers recomposition or relayout.
 */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.94f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val anim = remember { Animatable(1f) }
    LaunchedEffect(isPressed) { anim.animateTo(if (isPressed) pressed else 1f, Motion.press) }
    graphicsLayer { scaleX = anim.value; scaleY = anim.value }
}

/**
 * Staggered entrance: fades + rises + un-blurs into place `index * stepMs` after first
 * composition. Values are read only inside graphicsLayer → draw-phase only.
 */
fun Modifier.enterStagger(index: Int, stepMs: Long = 35, rise: Float = 36f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) { delay(index.coerceAtMost(18) * stepMs); p.animateTo(1f, Motion.snappy) }
    }
    graphicsLayer {
        val v = p.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * rise
        val s = 0.96f + 0.04f * v
        scaleX = s; scaleY = s
    }
}
