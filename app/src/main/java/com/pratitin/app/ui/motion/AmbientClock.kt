package com.pratitin.app.ui.motion

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

/**
 * One shared, throttled clock for every decorative ("ambient") animation: the sky, the
 * moon glow, bobbing badges, the lotus, hearts.
 *
 * Why: each `rememberInfiniteTransition` asks for a frame on *every* vsync (60–120 Hz)
 * forever, and because the whole screen is a blurred glass backdrop, every one of those
 * frames re-renders and re-blurs the scene. Ambient motion doesn't need that:
 *  - all ambient readers share one time value that advances at ~30 fps, so their redraws
 *    land in the same frames and the frames in between are free;
 *  - the clock falls asleep after [IDLE_MS] without a touch (the screen becomes fully
 *    static, zero GPU work) and wakes on the next touch.
 *
 * Readers must read [time] in the draw phase (Canvas / drawBehind / graphicsLayer).
 */
@Stable
class AmbientClock {
    private val _time = mutableFloatStateOf(17f)
    val time: Float get() = _time.floatValue
    internal var awake by mutableStateOf(true)
    internal var lastTouch = SystemClock.uptimeMillis()

    fun poke() {
        lastTouch = SystemClock.uptimeMillis()
        if (!awake) awake = true
    }

    internal fun set(t: Float) { _time.floatValue = t }

    companion object {
        const val FRAME_MS = 33L
        const val IDLE_MS = 25_000L
    }
}

/** null = ambient motion disabled (reduce-motion), readers draw their resting pose. */
val LocalAmbientClock = staticCompositionLocalOf<AmbientClock?> { null }

@Composable
fun rememberAmbientClock(running: Boolean): AmbientClock {
    val clock = remember { AmbientClock() }
    val awake = clock.awake
    LaunchedEffect(running, awake) {
        if (!running || !awake) return@LaunchedEffect
        clock.lastTouch = SystemClock.uptimeMillis()
        val start = withFrameMillis { it } - (clock.time * 1000).toLong()
        while (true) {
            withFrameMillis { now -> clock.set((now - start) / 1000f) }
            delay(AmbientClock.FRAME_MS - 8) // skip vsyncs in between: ~30 fps on any refresh rate
            if (SystemClock.uptimeMillis() - clock.lastTouch > AmbientClock.IDLE_MS) { clock.awake = false; break }
        }
    }
    return clock
}

/** Wakes the clock on any touch, without consuming the event. */
fun Modifier.wakesAmbient(clock: AmbientClock): Modifier = pointerInput(clock) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            clock.poke()
        }
    }
}

/** Lub-dub heartbeat scale for time [t] seconds (two beats, then a rest; 1.25 s period). */
fun heartbeatAt(t: Float): Float {
    val ph = ((t % 1.25f) + 1.25f) % 1.25f * 1000f
    fun lerp(a: Float, b: Float, x: Float) = a + (b - a) * x
    return when {
        ph < 110f -> lerp(1f, 1.12f, ph / 110f)
        ph < 230f -> lerp(1.12f, 0.98f, (ph - 110f) / 120f)
        ph < 340f -> lerp(0.98f, 1.07f, (ph - 230f) / 110f)
        ph < 560f -> lerp(1.07f, 1f, (ph - 340f) / 220f)
        else -> 1f
    }
}

/** Smooth 0‥1 ping-pong with the given period, for glows and bobbing. */
fun pingPong(t: Float, periodS: Float, phase: Float = 0f): Float =
    (0.5f - 0.5f * kotlin.math.cos(((t / periodS) + phase) * 2f * Math.PI.toFloat()))
