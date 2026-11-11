package com.pratitin.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratitin.app.ui.glass.Backdrop
import com.pratitin.app.ui.glass.glass
import com.pratitin.app.ui.heritage.drawLotus
import com.pratitin.app.ui.heritage.drawMoon
import com.pratitin.app.ui.motion.Motion
import com.pratitin.app.ui.motion.pressScale
import com.pratitin.app.ui.shape.SquircleShape
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette

/** Frosted squircle card. */
@Composable
fun GlassCard(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    liquid: Boolean = false,
    tint: Color = Color.Unspecified,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = remember(radius) { SquircleShape(radius) }
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction, 0.97f) else Modifier)
            .glass(backdrop, shape, liquid = liquid, tint = tint, cornerRadius = radius)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(14.dp)) { drawLotus(Offset(size.width / 2, size.height * 0.9f), size.height * 0.9f, 0.8f, p.gold, p.goldSoft) }
        Spacer(Modifier.width(8.dp))
        Text(text, style = HeritageType.heading, color = p.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Selectable squircle chip with animated fill. */
@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    accent: Color = LocalPalette.current.gold,
    leading: String? = null,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.9f) else Color.Transparent, label = "chip")
    val textColor by animateColorAsState(if (selected) (if (p.isDark) p.night else Color.White) else p.ink, label = "chipText")
    Row(
        modifier
            .pressScale(interaction)
            .glass(backdrop, Squircles.pill, cornerRadius = 100.dp)
            .clip(Squircles.pill)
            .background(fill)
            .clickable(interaction, indication = null, role = Role.Tab) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { Text(leading, style = HeritageType.label); Spacer(Modifier.width(6.dp)) }
        Text(text, style = HeritageType.label, color = textColor, maxLines = 1)
    }
}

/** Liquid toggle — the knob stretches while it travels, like a droplet. */
@Composable
fun LiquidToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val lead by animateFloatAsState(if (checked) 1f else 0f, Motion.bouncy, label = "lead")
    val trail by animateFloatAsState(if (checked) 1f else 0f, Motion.gentle, label = "trail")
    val track by animateColorAsState(if (checked) p.gold else p.inkFaint.copy(alpha = 0.25f), label = "track")
    Canvas(
        modifier
            .size(52.dp, 32.dp)
            .clip(Squircles.pill)
            .clickable(role = Role.Switch) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onChange(!checked) },
    ) {
        drawRoundRect(track, cornerRadius = CornerRadius(size.height / 2))
        val pad = 3.dp.toPx()
        val d = size.height - pad * 2
        val travel = size.width - pad * 2 - d
        val a = pad + travel * minOf(lead, trail)
        val b = pad + travel * maxOf(lead, trail) + d
        drawRoundRect(Color.White, topLeft = Offset(a, pad), size = Size(b - a, d), cornerRadius = CornerRadius(d / 2))
    }
}

@Composable
fun SettingRow(label: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HeritageType.body, color = p.ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** Round glass icon button. */
@Composable
fun GlassIconButton(backdrop: Backdrop?, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, icon: DrawScope.(Color) -> Unit) {
    val p = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val shape = remember(size) { SquircleShape(size / 2.2f) }
    Box(
        modifier.size(size).pressScale(interaction, 0.88f).glass(backdrop, shape, liquid = true, cornerRadius = size / 2.2f).clip(shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(size * 0.45f)) { icon(p.ink) } }
}

@Composable
fun Badge(
    text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false,
    icon: (DrawScope.(Color) -> Unit)? = null,
) {
    // Fixed height + vector icons (no emoji): every badge in a row lines up exactly,
    // whatever script or symbol it contains.
    val fg = if (filled) Color.White else color
    Row(
        modifier.height(26.dp).clip(Squircles.pill).background(if (filled) color else color.copy(alpha = 0.16f))
            .padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Canvas(Modifier.size(12.dp)) { icon(fg) }
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = HeritageType.micro.copy(lineHeight = 13.sp), color = fg, maxLines = 1)
    }
}

@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, valueStyle: TextStyle = HeritageType.heading) {
    val p = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = valueStyle, color = p.gold, maxLines = 1)
        Text(label, style = HeritageType.micro, color = p.inkSoft, maxLines = 1)
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

// ---------------------------------------------------------------- icon set (heritage line icons)
object Icons {
    private fun DrawScope.sw() = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)

    val today: DrawScope.(Color) -> Unit = { c ->
        drawMoon(center, size.minDimension * 0.42f, 0.55f, true, c, c.copy(alpha = 0.2f), glow = false)
    }
    val calendar: DrawScope.(Color) -> Unit = { c ->
        drawRoundRect(c, Offset(1f, 3.dp.toPx()), Size(size.width - 2f, size.height - 4.dp.toPx()), CornerRadius(5.dp.toPx()), style = sw())
        val g = size.width / 4
        for (i in 1..3) for (j in 1..2) drawCircle(c, 1.4.dp.toPx(), Offset(g * i, size.height * (0.38f + j * 0.2f)))
        drawLine(c, Offset(g, 0f), Offset(g, 5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(c, Offset(g * 3, 0f), Offset(g * 3, 5.dp.toPx()), 1.8.dp.toPx(), StrokeCap.Round)
    }
    val lotus: DrawScope.(Color) -> Unit = { c -> drawLotus(Offset(center.x, size.height * 0.92f), size.height * 0.9f, 0.85f, c, c) }
    val mood: DrawScope.(Color) -> Unit = { c ->
        val r = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(c, r, style = sw())
        drawCircle(c, 1.6.dp.toPx(), Offset(center.x - r * 0.38f, center.y - r * 0.2f))
        drawCircle(c, 1.6.dp.toPx(), Offset(center.x + r * 0.38f, center.y - r * 0.2f))
        val smile = Path().apply { moveTo(center.x - r * 0.45f, center.y + r * 0.2f); quadraticTo(center.x, center.y + r * 0.75f, center.x + r * 0.45f, center.y + r * 0.2f) }
        drawPath(smile, c, style = sw())
    }
    val life: DrawScope.(Color) -> Unit = { c ->
        val g = size.width / 3
        for (i in 0..2) for (j in 0..2) {
            val filled = j * 3 + i < 5
            drawRoundRect(c.copy(alpha = if (filled) 1f else 0.35f), Offset(i * g + 1.5f, j * g + 1.5f), Size(g - 3f, g - 3f), CornerRadius(2.dp.toPx()))
        }
    }
    val settings: DrawScope.(Color) -> Unit = { c ->
        val r = size.minDimension / 2
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            drawLine(c, Offset(center.x + (r * 0.55f * kotlin.math.cos(a)).toFloat(), center.y + (r * 0.55f * kotlin.math.sin(a)).toFloat()),
                Offset(center.x + (r * 0.95f * kotlin.math.cos(a)).toFloat(), center.y + (r * 0.95f * kotlin.math.sin(a)).toFloat()), 2.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(c, r * 0.55f, style = sw()); drawCircle(c, r * 0.18f)
    }
    val chevronLeft: DrawScope.(Color) -> Unit = { c ->
        val p = Path().apply { moveTo(size.width * 0.65f, size.height * 0.15f); lineTo(size.width * 0.3f, size.height * 0.5f); lineTo(size.width * 0.65f, size.height * 0.85f) }
        drawPath(p, c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
    val chevronRight: DrawScope.(Color) -> Unit = { c ->
        val p = Path().apply { moveTo(size.width * 0.35f, size.height * 0.15f); lineTo(size.width * 0.7f, size.height * 0.5f); lineTo(size.width * 0.35f, size.height * 0.85f) }
        drawPath(p, c, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
    }
    val close: DrawScope.(Color) -> Unit = { c ->
        drawLine(c, Offset(size.width * 0.2f, size.height * 0.2f), Offset(size.width * 0.8f, size.height * 0.8f), 2.2.dp.toPx(), StrokeCap.Round)
        drawLine(c, Offset(size.width * 0.8f, size.height * 0.2f), Offset(size.width * 0.2f, size.height * 0.8f), 2.2.dp.toPx(), StrokeCap.Round)
    }
    /** Dharma wheel (ធម្មចក្រ) for Sila days. */
    val wheel: DrawScope.(Color) -> Unit = { c ->
        val r = size.minDimension / 2 - 0.5.dp.toPx()
        drawCircle(c, r, style = Stroke(1.3.dp.toPx()))
        for (k in 0 until 8) {
            val a = Math.toRadians(k * 45.0)
            drawLine(c, Offset(center.x + (r * 0.2f * kotlin.math.cos(a)).toFloat(), center.y + (r * 0.2f * kotlin.math.sin(a)).toFloat()),
                Offset(center.x + (r * kotlin.math.cos(a)).toFloat(), center.y + (r * kotlin.math.sin(a)).toFloat()), 1.dp.toPx())
        }
        drawCircle(c, r * 0.22f)
    }
    val fullMoon: DrawScope.(Color) -> Unit = { c -> drawCircle(c, size.minDimension / 2) }
    val newMoon: DrawScope.(Color) -> Unit = { c -> drawCircle(c, size.minDimension / 2 - 0.7.dp.toPx(), style = Stroke(1.3.dp.toPx())) }
    val leap: DrawScope.(Color) -> Unit = { c -> drawLotus(Offset(center.x, size.height * 0.95f), size.height * 0.95f, 0.9f, c, c) }
    val target: DrawScope.(Color) -> Unit = { c ->
        drawCircle(c, size.minDimension * 0.42f, style = sw()); drawCircle(c, size.minDimension * 0.16f)
    }
}

/** Small helper to render an icon lambda at a size. */
@Composable
fun IconCanvas(icon: DrawScope.(Color) -> Unit, color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) { icon(color) }
}

/** A thin animated progress arc. */
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, color: Color = LocalPalette.current.gold, stroke: Dp = 5.dp, content: @Composable BoxScope.() -> Unit = {}) {
    val p = LocalPalette.current
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.gentle, label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(size.width - s, size.height - s)
            drawArc(p.inkFaint.copy(alpha = 0.18f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun RowSpacer(w: Dp) = Spacer(Modifier.width(w))

