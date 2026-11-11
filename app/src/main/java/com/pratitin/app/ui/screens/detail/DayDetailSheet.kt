package com.pratitin.app.ui.screens.detail

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
 * Day sheet. On open it grows out of the tapped cell ([origin]) — a hand-rolled
 * shared-element transition done purely in graphicsLayer (no relayout per frame).
 * Drag down to dismiss; arrows (or swipe the header) step through days.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayDetailSheet(shell: ShellState, date: Int, origin: Rect?, onDismiss: () -> Unit, onNavigate: (Int) -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val reduce = LocalReduceMotion.current

    val grow = remember { Animatable(if (reduce || origin == null) 1f else 0f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) { grow.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f)) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
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
                .imePadding(),
        ) {
            GlassCard(shell.content, Modifier.fillMaxSize(), radius = 36.dp, liquid = true, tint = p.night.copy(alpha = if (p.isDark) 0.55f else 0.7f), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                // Grab handle: drag down to dismiss (kept off the scrolling body so gestures never fight)
                Box(
                    Modifier.fillMaxWidth().height(30.dp).draggable(
                        rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) },
                        Orientation.Vertical,
                        onDragStopped = { v ->
                            if (drag > 140f || v > 1600f) onDismiss()
                            else animate(drag, 0f, animationSpec = spring(dampingRatio = 0.7f)) { value, _ -> drag = value }
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(44.dp, 5.dp).clip(Squircles.pill).background(p.inkFaint.copy(alpha = 0.4f)))
                }
                AnimatedContent(
                    targetState = date,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (fadeIn() + slideInHorizontally { it / 4 * dir }) togetherWith (fadeOut() + slideOutHorizontally { -it / 4 * dir })
                    },
                    label = "day",
                ) { d ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
                        DayContent(shell, d, today, onDismiss, onNavigate, settings, moods[d]?.mood, moods[d]?.note.orEmpty())
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayContent(
    shell: ShellState, d: Int, today: Int, onDismiss: () -> Unit, onNavigate: (Int) -> Unit,
    settings: com.pratitin.app.data.Settings, mood: com.pratitin.app.data.Mood?, note: String,
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
        GlassIconButton(shell.content, onClick = { onNavigate(Day.plusDays(d, -1)) }, size = 38.dp, icon = Icons.chevronLeft)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.num(Day.dom(d)), style = HeritageType.number, color = if (hol.isNotEmpty()) holidayColor(hol.first(), p) else p.ink)
            Text(gregorianLong(d, s), style = HeritageType.label, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when { diff == 0 -> s.today; diff > 0 -> s.inDays(diff); else -> if (s.lang == Lang.KM) "${s.num(-diff)} ថ្ងៃមុន" else "${-diff} days ago" },
                style = HeritageType.micro, color = p.gold,
            )
        }
        GlassIconButton(shell.content, onClick = { onNavigate(Day.plusDays(d, 1)) }, size = 38.dp, icon = Icons.chevronRight)
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
        GlassCard(shell.bg, Modifier.fillMaxWidth(), tint = p.lacquer.copy(alpha = 0.25f)) {
            Text("🎉 ${s.songkranAt} ${if (s.lang == Lang.KM) KhmerNames.digits(time) else time}", style = HeritageType.heading, color = p.goldSoft)
        }
    }

    if (hol.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        hol.forEach { h ->
            GlassCard(shell.bg, Modifier.fillMaxWidth().padding(vertical = 4.dp), tint = holidayColor(h, p).copy(alpha = 0.14f)) {
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
