package com.pratitin.app.ui.screens.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.pratitin.app.data.UserEvent
import com.pratitin.app.ui.heritage.drawAllCorners
import com.pratitin.app.ui.screens.events.newEvent
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Lang
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.Badge
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.heritage.KbachDivider
import com.pratitin.app.ui.heritage.drawMoon
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.screens.ANIMAL_EMOJI
import com.pratitin.app.ui.screens.MoodPicker
import com.pratitin.app.ui.screens.gregorianLong
import com.pratitin.app.ui.screens.holidayColor
import com.pratitin.app.ui.screens.holidaysOn
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.screens.title
import com.pratitin.app.ui.screens.yearLine
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.HolidaySource
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames

/**
 * Day sheet: a stack of day cards. It grows out of the tapped cell ([origin]) — a hand-rolled
 * shared-element transition done purely in graphicsLayer — then you flick the top card left
 * or right and the next/previous day rises from the stack beneath it. Drag the handle down
 * (or tap outside) to dismiss.
 */
@Composable
fun DayDetailSheet(shell: ShellState, date: Int, origin: Rect?, onDismiss: () -> Unit, onNavigate: (Int) -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val reduce = LocalReduceMotion.current

    val grow = remember { Animatable(if (reduce || origin == null) 1f else 0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .onGloballyPositioned { bounds = it.boundsInRoot() }
                .graphicsLayer {
                    val b = bounds
                    val t = grow.value
                    if (origin != null && b != null && t < 1f) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val sx = origin.width / b.width
                        val sy = origin.height / b.height
                        scaleX = sx + (1f - sx) * t
                        scaleY = sy + (1f - sy) * t
                        translationX = (origin.left - b.left) * (1f - t)
                        translationY = (origin.top - b.top) * (1f - t) + drag
                        alpha = (t * 3f).coerceAtMost(1f)
                    } else {
                        translationY = drag
                    }
                }
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp),
        ) {
            // Grab handle + hint: drag down to dismiss (kept off the cards so gestures never fight)
            Column(
                Modifier.fillMaxWidth().draggable(
                    rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                    Orientation.Vertical,
                    onDragStopped = { v ->
                        if (drag > 140f || v > 1600f) onDismiss()
                        else animate(drag, 0f, animationSpec = spring(dampingRatio = 0.7f)) { value, _ -> drag = value }
                    },
                ).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp, 5.dp).clip(Squircles.pill).background(Color.White.copy(alpha = 0.55f)))
                Text(s.swipeHint, style = HeritageType.micro, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
            }
            CardStack(shell, date, onNavigate, Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}

/**
 * Three-deep card stack. The top card follows the finger with a slight tilt; the card
 * beneath rises and scales up as you drag; release past ~30 % (or flick) and the top
 * card flies off while the next one settles into place. All motion is graphicsLayer-only.
 */
@Composable
private fun CardStack(shell: ShellState, date: Int, onNavigate: (Int) -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    // Only flips when the drag direction changes, so dragging doesn't recompose every frame.
    val forward by remember { derivedStateOf { offset.value <= 0f } }
    val backDate = if (forward) Day.plusDays(date, 1) else Day.plusDays(date, -1)
    val deepDate = if (forward) Day.plusDays(date, 2) else Day.plusDays(date, -2)
    val density = LocalDensity.current

    Box(
        modifier
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(date) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { tracker.resetTracking() },
                    onHorizontalDrag = { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + dx) }
                    },
                    onDragEnd = {
                        val v = tracker.calculateVelocity().x
                        scope.launch {
                            val dir = when {
                                offset.value < -width * 0.3f || v < -1400f -> 1
                                offset.value > width * 0.3f || v > 1400f -> -1
                                else -> 0
                            }
                            if (dir == 0) {
                                offset.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 420f), initialVelocity = v)
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                offset.animateTo(-dir * width * 1.25f, spring(dampingRatio = 1f, stiffness = 420f), initialVelocity = v)
                                onNavigate(Day.plusDays(date, dir.toLong()))
                                offset.snapTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f)) } },
                )
            },
    ) {
        // deepest card: a peek of the day after next, for a sense of depth
        DayCard(shell, deepDate, preview = true, Modifier.fillMaxSize().graphicsLayer {
            val k = (abs(offset.value) / (width * 0.6f)).coerceIn(0f, 1f)
            val sc = 0.88f + 0.06f * k
            scaleX = sc; scaleY = sc
            translationY = with(density) { (34.dp - 14.dp * k).toPx() }
            alpha = 0.55f + 0.3f * k
        })
        // the card that will become the top card
        key(backDate) {
            DayCard(shell, backDate, preview = false, Modifier.fillMaxSize().graphicsLayer {
                val k = (abs(offset.value) / (width * 0.6f)).coerceIn(0f, 1f)
                val sc = 0.94f + 0.06f * k
                scaleX = sc; scaleY = sc
                translationY = with(density) { (20.dp * (1f - k)).toPx() }
                alpha = 0.85f + 0.15f * k
            })
        }
        // top card
        key(date) {
            DayCard(shell, date, preview = false, Modifier.fillMaxSize().graphicsLayer {
                translationX = offset.value
                translationY = abs(offset.value) * 0.06f
                rotationZ = offset.value / width * 9f
                transformOrigin = TransformOrigin(0.5f, 1.2f)
            })
        }
    }
}

/** A solid "physical" day card — lacquer surface, gilded rim and kbach corners. */
@Composable
private fun DayCard(shell: ShellState, d: Int, preview: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val events by shell.vm.events.collectAsStateWithLifecycle()
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val surface = if (p.isDark) Brush.verticalGradient(listOf(Color(0xFF231629), Color(0xFF140C1B)))
    else Brush.verticalGradient(listOf(Color(0xFFFFFAF1), Color(0xFFF3E4CC)))
    Box(
        modifier
            .shadow(18.dp, Squircles.xl, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(Squircles.xl)
            .background(surface)
            .border(1.dp, p.gold.copy(alpha = 0.45f), Squircles.xl)
            .drawBehind { drawAllCorners(p.gold.copy(alpha = 0.35f), 20.dp.toPx()) },
    ) {
        if (preview) {
            val k = remember(d) { khmerOf(d) }
            val s = LocalStrings.current
            Column(Modifier.padding(22.dp)) {
                Text(s.num(Day.dom(d)), style = HeritageType.number, color = p.ink.copy(alpha = 0.5f))
                Text(lunarLine(k, s), style = HeritageType.title, color = p.inkSoft)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                DayContent(shell, d, today, settings, moods[d]?.mood, moods[d]?.note.orEmpty(), events.filter { it.occursOn(d) }.sortedWith(compareBy({ !it.allDay }, { it.startMin })))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayContent(
    shell: ShellState, d: Int, today: Int,
    settings: com.pratitin.app.data.Settings, mood: com.pratitin.app.data.Mood?, note: String,
    dayEvents: List<UserEvent>,
) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val k = remember(d) { khmerOf(d) }
    val hol = remember(d, settings) { holidaysOn(d, settings) }
    val dim = KhmerCalendar.daysInMonth(k.monthIndex, k.beYear)
    val ny = remember(d) { KhmerCalendar.newYear(Day.year(d)) }
    val isSongkran = ny.year * 10000 + ny.month * 100 + ny.day == d
    val diff = Day.between(today, d).toInt()

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.num(Day.dom(d)), style = HeritageType.number, color = if (hol.isNotEmpty()) holidayColor(hol.first(), p) else p.ink)
            Text(gregorianLong(d, s), style = HeritageType.label, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when { diff == 0 -> s.today; diff > 0 -> s.inDays(diff); else -> if (s.lang == Lang.KM) "${s.num(-diff)} ថ្ងៃមុន" else "${-diff} days ago" },
                style = HeritageType.micro, color = p.gold,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    KbachDivider(height = 12.dp, alpha = 0.6f)
    Spacer(Modifier.height(16.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(78.dp)) {
            drawMoon(center, size.minDimension * 0.38f, k.illumination, k.dayNumber < 15, p.moon, p.nightDeep.copy(alpha = 0.6f))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(lunarLine(k, s), style = HeritageType.title, color = p.ink)
            Text(yearLine(k, s), style = HeritageType.label, color = p.gold)
            if (s.lang == Lang.KM) Text(k.format(numerals = true), style = HeritageType.micro, color = p.inkSoft)
            else Text("${KhmerNames.ANIMALS_LATIN[k.animalYear]} ${ANIMAL_EMOJI[k.animalYear]} · ${KhmerNames.SAK[k.sak]}", style = HeritageType.micro, color = p.inkSoft)
        }
    }
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (k.isSilaDay(dim)) Badge("☸ ${s.silaDay}", p.gold)
        if (k.isFullMoon) Badge("🌕 ${s.fullMoon}", p.moon)
        if (k.dayNumber == dim - 1) Badge("🌑 ${s.newMoon}", p.inkSoft)
        when (KhmerCalendar.leapKind(k.beYear)) { 1 -> Badge(s.leapMonth, p.lotus); 2 -> Badge(s.leapDay, p.jade) }
    }

    if (isSongkran) {
        Spacer(Modifier.height(14.dp))
        val time = "%02d:%02d".format(ny.hour, ny.minute)
        TintCard(p.lacquer.copy(alpha = 0.25f), Modifier.fillMaxWidth()) {
            Text("🎉 ${s.songkranAt} ${if (s.lang == Lang.KM) KhmerNames.digits(time) else time}", style = HeritageType.heading, color = p.goldSoft)
        }
    }

    if (hol.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        hol.forEach { h ->
            TintCard(holidayColor(h, p).copy(alpha = 0.14f), Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("${h.emoji} ${h.title(s)}", style = HeritageType.heading, color = p.ink)
                Text(if (s.lang == Lang.KM) h.nameEn else h.nameKm, style = HeritageType.micro, color = p.inkSoft)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (h.spanLength > 1) Badge("${s.num(h.dayOfSpan)}/${s.num(h.spanLength)}", holidayColor(h, p))
                    Badge(if (h.source == HolidaySource.OFFICIAL) s.official else s.calculated, if (h.source == HolidaySource.OFFICIAL) p.jade else p.gold)
                }
            }
        }
    }

    // ---- events & notes
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(s.events, style = HeritageType.heading, color = p.ink, modifier = Modifier.weight(1f))
        Row(
            Modifier.clip(Squircles.pill).background(p.gold).clickable { shell.editEvent(newEvent(d), true) }
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Text("+ ${s.addEvent}", style = HeritageType.label, color = p.night) }
    }
    Spacer(Modifier.height(8.dp))
    if (dayEvents.isEmpty()) Text(s.noEvents, style = HeritageType.micro, color = p.inkSoft)
    dayEvents.forEach { ev -> EventRow(ev, onClick = { shell.editEvent(ev, false) }) }

    Spacer(Modifier.height(18.dp))
    MoodPicker(mood, onPick = { shell.vm.setMood(d, it, note) })
    Spacer(Modifier.height(12.dp))
    NoteField(note, enabled = mood != null) { shell.vm.setMood(d, mood, it) }
    Spacer(Modifier.height(80.dp))
}

@Composable
private fun NoteField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    var text by remember(value) { mutableStateOf(value) }
    Box(
        Modifier.fillMaxWidth().clip(Squircles.m).background(p.inkFaint.copy(alpha = 0.08f)).padding(14.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f },
    ) {
        if (text.isEmpty()) Text(s.addNote, style = HeritageType.body, color = p.inkFaint)
        BasicTextField(
            value = text, enabled = enabled,
            onValueChange = { text = it; onChange(it) },
            textStyle = HeritageType.body.copy(color = p.ink, fontSize = 15.sp),
            cursorBrush = SolidColor(p.gold),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun EventRow(ev: UserEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(Squircles.m).background(ev.colorValue.copy(alpha = 0.14f))
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(4.dp, 34.dp).clip(Squircles.pill).background(ev.colorValue))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${ev.kind.emoji} ${ev.title.ifBlank { if (s.lang == Lang.KM) ev.kind.km else ev.kind.en }}", style = HeritageType.body, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (!ev.allDay) add(ev.timeLabel().let { if (s.khmerDigits) KhmerNames.digits(it) else it })
                if (ev.repeat != com.pratitin.khmer.Repeat.NONE) add("↻ " + s.repeatNames[ev.repeat.ordinal])
                if (ev.remind != com.pratitin.app.data.Remind.NONE) add("🔔")
                if (ev.note.isNotBlank()) add(ev.note.lineSequence().first().take(40))
            }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = HeritageType.micro, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Flat tinted panel for use on the solid day cards (glass would show the sky through). */
@Composable
private fun TintCard(tint: Color, modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.clip(Squircles.m).background(tint).padding(14.dp), content = content)
}
