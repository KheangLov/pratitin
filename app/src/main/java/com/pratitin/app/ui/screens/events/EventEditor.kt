package com.pratitin.app.ui.screens.events

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratitin.app.data.EVENT_COLORS
import com.pratitin.app.data.EventKind
import com.pratitin.app.data.Lang
import com.pratitin.app.data.Remind
import com.pratitin.app.data.UserEvent
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.LiquidToggle
import com.pratitin.app.ui.components.SettingRow
import com.pratitin.app.ui.screens.gregorianLong
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.Repeat
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs

/** A fresh event for [date], defaulting sensibly per kind. */
fun newEvent(date: Int, kind: EventKind = EventKind.EVENT) = UserEvent(
    id = System.currentTimeMillis(), kind = kind, title = "", start = date,
    allDay = kind != EventKind.EVENT, repeat = if (kind == EventKind.BIRTHDAY) Repeat.YEARLY else Repeat.NONE,
    remind = if (kind == EventKind.NOTE) Remind.NONE else Remind.AT_TIME,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(shell: ShellState, initial: UserEvent, isNew: Boolean, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    var e by remember(initial.id) { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 320f)) }

    if (pickDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = Day.unpack(e.start).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayMode = DisplayMode.Picker,
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { e = e.copy(start = Day.pack(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    pickDate = false
                }) { Text(s.save, color = p.gold) }
            },
            colors = DatePickerDefaults.colors(containerColor = p.nightDeep),
        ) { DatePicker(st, showModeToggle = false, colors = DatePickerDefaults.colors(containerColor = p.nightDeep, selectedDayContainerColor = p.gold, todayDateBorderColor = p.gold)) }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp), contentAlignment = Alignment.BottomCenter) {
        GlassCard(
            shell.content,
            Modifier.fillMaxWidth().graphicsLayer { translationY = rise.value * size.height }.navigationBarsPadding().imePadding(),
            radius = 36.dp, tint = p.night.copy(alpha = if (p.isDark) 0.62f else 0.78f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) s.addEvent else s.editEvent, style = HeritageType.title, color = p.ink, modifier = Modifier.weight(1f))
                GlassIconButton(null, onClick = onDismiss, size = 38.dp, icon = Icons.close)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // kind
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    EventKind.entries.filter { it != EventKind.ANNIVERSARY }.forEach { k ->
                        GlassChip(if (s.lang == Lang.KM) k.km else k.en, e.kind == k, null, leading = k.emoji) {
                            e = e.copy(
                                kind = k,
                                allDay = if (k == EventKind.EVENT) e.allDay else true,
                                repeat = if (k == EventKind.BIRTHDAY && e.repeat == Repeat.NONE) Repeat.YEARLY else e.repeat,
                            )
                        }
                    }
                }
                // title
                Field(e.title, s.titleHint, singleLine = true, big = true) { e = e.copy(title = it) }
                Spacer(Modifier.height(10.dp))
                // date (tap to pick, chevrons to nudge)
                val k = remember(e.start) { khmerOf(e.start) }
                Row(
                    Modifier.fillMaxWidth().clip(Squircles.m).background(p.inkFaint.copy(alpha = 0.08f)).clickable { pickDate = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(gregorianLong(e.start, s), style = HeritageType.body, color = p.ink)
                        Text(lunarLine(k, s), style = HeritageType.micro, color = p.gold)
                    }
                    GlassIconButton(null, onClick = { e = e.copy(start = Day.plusDays(e.start, -1)) }, size = 34.dp, icon = Icons.chevronLeft)
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(null, onClick = { e = e.copy(start = Day.plusDays(e.start, 1)) }, size = 34.dp, icon = Icons.chevronRight)
                }

                // time
                if (e.kind == EventKind.EVENT) {
                    SettingRow(s.allDay) { LiquidToggle(e.allDay, { v -> e = e.copy(allDay = v) }) }
                    AnimatedVisibility(!e.allDay, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                WheelPicker((0..23).map { "%02d".format(it).let { v -> if (s.lang == Lang.KM && s.khmerDigits) com.pratitin.khmer.KhmerNames.digits(v) else v } },
                                    e.startMin / 60, { h -> e = e.copy(startMin = h * 60 + e.startMin % 60) }, Modifier.width(72.dp))
                                Text(":", style = HeritageType.title, color = p.gold, modifier = Modifier.padding(horizontal = 6.dp))
                                WheelPicker((0 until 12).map { "%02d".format(it * 5).let { v -> if (s.lang == Lang.KM && s.khmerDigits) com.pratitin.khmer.KhmerNames.digits(v) else v } },
                                    (e.startMin % 60) / 5, { m -> e = e.copy(startMin = (e.startMin / 60) * 60 + m * 5) }, Modifier.width(72.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(s.duration, style = HeritageType.micro, color = p.inkSoft)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15, 30, 60, 90, 120, 180).forEach { m ->
                                    val label = if (m < 60) "${s.num(m)}′" else "${s.num(m / 60)}${if (m % 60 != 0) "½" else ""}h"
                                    GlassChip(label, e.durationMin == m, null) { e = e.copy(durationMin = m) }
                                }
                            }
                        }
                    }
                }

                // repeat
                Spacer(Modifier.height(12.dp))
                Text(s.repeat, style = HeritageType.micro, color = p.inkSoft)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeat.entries.forEachIndexed { i, r ->
                        GlassChip(s.repeatNames[i], e.repeat == r, null, accent = if (r == Repeat.LUNAR_YEARLY) p.lotus else p.gold,
                            leading = if (r == Repeat.LUNAR_YEARLY) "🌕" else null) { e = e.copy(repeat = r) }
                    }
                }
                if (e.repeat == Repeat.LUNAR_YEARLY) {
                    Text(
                        if (s.lang == Lang.KM) "ធ្វើម្ដងទៀតរៀងរាល់ឆ្នាំ នៅ ${lunarLine(k, s)}" else "Repeats every year on ${lunarLine(k, s)}",
                        style = HeritageType.micro, color = p.lotus, modifier = Modifier.padding(top = 6.dp),
                    )
                }

                // reminder
                Spacer(Modifier.height(12.dp))
                Text(s.remind, style = HeritageType.micro, color = p.inkSoft)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Remind.entries.filter { e.allDay.not() || it !in setOf(Remind.MIN_10, Remind.MIN_30, Remind.HOUR_1) }.forEach { r ->
                        GlassChip(if (s.lang == Lang.KM) r.km else r.en, e.remind == r, null, accent = p.jade) { e = e.copy(remind = r) }
                    }
                }

                // colour
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.color, style = HeritageType.micro, color = p.inkSoft)
                    EVENT_COLORS.forEachIndexed { i, c ->
                        val sel = e.color == i
                        Box(
                            Modifier.size(if (sel) 30.dp else 24.dp).clip(Squircles.pill).background(c)
                                .border(2.dp, if (sel) Color.White else Color.Transparent, Squircles.pill)
                                .clickable { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); e = e.copy(color = i) },
                        )
                    }
                }

                // note
                Spacer(Modifier.height(12.dp))
                Field(e.note, s.addNote, singleLine = false) { e = e.copy(note = it) }

                // actions
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isNew) {
                        Text(
                            s.delete, style = HeritageType.heading, color = p.holidayKhmer, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(Squircles.pill).background(p.holidayKhmer.copy(alpha = 0.14f))
                                .clickable { shell.vm.deleteEvent(e.id); onDismiss() }.padding(vertical = 14.dp),
                        )
                    }
                    Text(
                        s.save, style = HeritageType.heading, color = p.night, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(2f).clip(Squircles.pill).background(p.gold)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                shell.vm.saveEvent(e.copy(title = e.title.trim()))
                                onDismiss()
                            }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun Field(value: String, hint: String, singleLine: Boolean, big: Boolean = false, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().clip(Squircles.m).background(p.inkFaint.copy(alpha = 0.08f)).padding(14.dp)) {
        val style = (if (big) HeritageType.heading else HeritageType.body).copy(color = p.ink)
        if (value.isEmpty()) Text(hint, style = style.copy(color = p.inkFaint))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, textStyle = style,
            cursorBrush = SolidColor(p.gold),
            modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.height(90.dp)),
        )
    }
}

/**
 * iOS-style wheel: snapping LazyColumn, items scale & fade by distance from the centre
 * line (read in graphicsLayer only), haptic tick on every detent.
 */
@Composable
fun WheelPicker(values: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, itemHeight: Dp = 40.dp, visible: Int = 5) {
    val p = LocalPalette.current
    val haptics = LocalHapticFeedback.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, values.lastIndex))
    val fling = rememberSnapFlingBehavior(state)
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }
    val center by remember {
        derivedStateOf { state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0 }
    }
    LaunchedEffect(state) {
        snapshotFlow { center }.distinctUntilChanged().collect { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.distinctUntilChanged().collect { scrolling ->
            if (!scrolling) onSelect(center.coerceIn(0, values.lastIndex))
        }
    }
    Box(modifier.height(itemHeight * visible), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemHeight).clip(Squircles.s).background(p.gold.copy(alpha = 0.16f)))
        LazyColumn(
            state = state, flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight * (visible / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(values.size) { i ->
                Box(
                    Modifier.height(itemHeight).fillMaxWidth().graphicsLayer {
                        val info = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i }
                        val mid = state.layoutInfo.viewportEndOffset / 2f
                        val d = if (info != null) abs(info.offset + info.size / 2f - mid) / (itemPx * (visible / 2f)) else 1f
                        val k = (1f - d).coerceIn(0f, 1f)
                        alpha = 0.25f + 0.75f * k
                        scaleX = 0.8f + 0.2f * k; scaleY = scaleX
                        rotationX = (1f - k) * 35f * (if (info != null && info.offset + info.size / 2f < mid) 1f else -1f)
                    },
                    contentAlignment = Alignment.Center,
                ) { Text(values[i], style = HeritageType.title.copy(fontSize = 22.sp), color = p.ink) }
            }
        }
    }
}
