package com.pratitin.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pratitin.app.data.Lang
import com.pratitin.app.data.Mood
import com.pratitin.app.data.Settings
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.Strings
import com.pratitin.app.ui.motion.Motion
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritagePalette
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.Holiday
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerDate
import com.pratitin.khmer.KhmerNames
import kotlinx.coroutines.flow.distinctUntilChanged

// ------------------------------------------------------------------ data helpers

fun khmerOf(date: Int): KhmerDate = KhmerCalendar.fromGregorian(Day.year(date), Day.month(date), Day.dom(date))

fun visible(h: Holiday, s: Settings) = when (h.kind) {
    HolidayKind.KHMER_PUBLIC -> true
    HolidayKind.KHMER_OBSERVANCE -> s.showObservances
    HolidayKind.GLOBAL -> s.showGlobal
}

fun holidaysOn(date: Int, s: Settings): List<Holiday> =
    Holidays.byDate(Day.year(date))[date].orEmpty().filter { visible(it, s) }

/** Next distinct holiday events (multi-day festivals collapse to their first remaining day). */
fun upcomingHolidays(from: Int, s: Settings, count: Int, kinds: Set<HolidayKind> = HolidayKind.entries.toSet()): List<Holiday> {
    val out = ArrayList<Holiday>()
    val seen = HashSet<String>()
    var y = Day.year(from)
    while (out.size < count && y <= Day.year(from) + 2) {
        for (h in Holidays.forYear(y)) {
            if (h.packed < from || !visible(h, s) || h.kind !in kinds) continue
            val key = "${h.id}-${h.year}"
            if (seen.add(key)) out += h
            if (out.size >= count) break
        }
        y++
    }
    return out
}

fun gregorianLong(date: Int, s: Strings): String {
    val wd = Day.weekday(date)
    return if (s.lang == Lang.KM) "ថ្ងៃ${KhmerNames.WEEKDAYS[wd]} ទី${s.num(Day.dom(date))} ${s.monthNames[Day.month(date) - 1]} ${s.num(Day.year(date))}"
    else "${java.time.DayOfWeek.of(if (wd == 0) 7 else wd).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}, ${Day.dom(date)} ${s.monthNames[Day.month(date) - 1]} ${Day.year(date)}"
}

fun gregorianShort(date: Int, s: Strings): String =
    "${s.num(Day.dom(date))} ${s.monthNames[Day.month(date) - 1].let { if (s.lang == Lang.EN) it.take(3) else it }}"

/** "១៤ រោច ខែកត្ដិក" or "14 Waning · Kadeuk". */
fun lunarLine(k: KhmerDate, s: Strings): String =
    if (s.lang == Lang.KM) "${s.num(k.day)}${KhmerNames.PHASE[k.phase.ordinal]} ខែ${KhmerNames.LUNAR_MONTHS[k.monthIndex]}"
    else "${k.day} ${if (k.phase.ordinal == 0) "Kaet" else "Roch"} · ${KhmerNames.LUNAR_MONTHS_LATIN[k.monthIndex]}"

fun yearLine(k: KhmerDate, s: Strings): String =
    if (s.lang == Lang.KM) "ឆ្នាំ${KhmerNames.ANIMALS[k.animalYear]} ${KhmerNames.SAK[k.sak]} · ព.ស. ${s.num(k.beYear)}"
    else "Year of the ${KhmerNames.ANIMALS_LATIN[k.animalYear]} · BE ${k.beYear} · JS ${k.jsYear}"

val ANIMAL_EMOJI = arrayOf("🐀", "🐂", "🐅", "🐇", "🐉", "🐍", "🐎", "🐐", "🐒", "🐓", "🐕", "🐖")

fun holidayColor(h: Holiday, p: HeritagePalette) = when (h.kind) {
    HolidayKind.KHMER_PUBLIC -> p.holidayKhmer
    HolidayKind.KHMER_OBSERVANCE -> p.gold
    HolidayKind.GLOBAL -> p.holidayGlobal
}

fun Holiday.title(s: Strings) = if (s.lang == Lang.KM) nameKm else nameEn

// ------------------------------------------------------------------ screen scaffold

/**
 * A LazyColumn under the status bar with room for the floating tab bar, reporting a
 * 0‥1 scroll fraction (for the skyline parallax) via snapshotFlow — no recomposition.
 */
@Composable
fun ScreenList(
    shell: ShellState,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    LaunchedEffect(state) {
        snapshotFlow {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / 600f).coerceIn(0f, 1f)
        }.distinctUntilChanged().collect { shell.reportScroll(it) }
    }
    val birth = remember { android.os.SystemClock.uptimeMillis() }
    androidx.compose.runtime.CompositionLocalProvider(com.pratitin.app.ui.motion.LocalScreenBirth provides birth) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ mood picker

@Composable
fun MoodPicker(selected: Mood?, onPick: (Mood?) -> Unit, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Mood.entries.forEach { m ->
                val sel = m == selected
                val k by animateFloatAsState(if (sel) 1f else 0f, Motion.bouncy, label = "mood")
                val dim = selected != null && !sel
                Box(
                    Modifier.weight(1f).padding(horizontal = 3.dp).aspectRatio(1f)
                        .graphicsLayer {
                            val sc = 0.9f + 0.22f * k
                            scaleX = sc; scaleY = sc
                            alpha = if (dim) 0.55f else 1f
                            translationY = -6.dp.toPx() * k
                        }
                        .clip(Squircles.s)
                        .drawBehind {
                            drawRect(Brush.radialGradient(listOf(m.color.copy(alpha = 0.25f + 0.45f * k), m.color.copy(alpha = 0.06f))))
                        }
                        .border(1.dp, m.color.copy(alpha = 0.25f + 0.6f * k), Squircles.s)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(if (sel) null else m)
                        }
                        .semantics { contentDescription = if (s.lang == Lang.KM) m.km else m.en },
                    contentAlignment = Alignment.Center,
                ) { Text(m.emoji, fontSize = 22.sp) }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(10.dp))
            AnimatedContent(
                targetState = selected,
                transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
                label = "moodLabel",
            ) { m ->
                Text(
                    text = m?.let { if (s.lang == Lang.KM) it.km else it.en } ?: s.howFeel,
                    style = HeritageType.label, color = m?.color ?: p.inkSoft, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Tiny square tile with the Gregorian day and weekday, used in lists. */
@Composable
fun DateTile(date: Int, accent: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    Column(
        modifier.clip(Squircles.s).background(accent.copy(alpha = 0.16f)).padding(vertical = 6.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(s.num(Day.dom(date)), style = HeritageType.heading.copy(fontSize = 20.sp), color = accent)
        Text(s.weekdaysShort[Day.weekday(date)], style = HeritageType.micro, color = p.inkSoft)
    }
}
