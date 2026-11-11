package com.pratitin.app.ui.screens.holidays

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Lang
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.Badge
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.heritage.KbachDivider
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.DateTile
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.screens.gregorianShort
import com.pratitin.app.ui.screens.holidayColor
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.screens.title
import com.pratitin.app.ui.screens.visible
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.BreakSuggestion
import com.pratitin.khmer.Holiday
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.HolidaySource
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.LeavePlanner

private enum class Filter { ALL, KHMER, GLOBAL }

@Composable
fun HolidaysScreen(shell: ShellState) {
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val s = LocalStrings.current
    val p = LocalPalette.current
    var year by rememberSaveable { mutableIntStateOf(Day.year(today)) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    // Group multi-day festivals into one row each.
    val events: List<List<Holiday>> = remember(year, filter, settings) {
        Holidays.forYear(year)
            .filter { visible(it, settings) }
            .filter {
                when (filter) {
                    Filter.ALL -> true
                    Filter.KHMER -> it.kind != HolidayKind.GLOBAL
                    Filter.GLOBAL -> it.kind == HolidayKind.GLOBAL
                }
            }
            .groupBy { "${it.id}-${it.year}" }.values.toList()
    }
    val breaks = remember(year, settings.maxLeave, settings.weekStartsMonday) { LeavePlanner.suggest(year, settings.maxLeave) }
    val publicDays = remember(year) { Holidays.forYear(year).filter { it.kind == HolidayKind.KHMER_PUBLIC }.map { it.packed }.distinct().size }

    ScreenList(shell) {
        item(key = "header") {
            Column(Modifier.padding(top = 8.dp).enterStagger(0)) {
                Text(s.holidaysTitle, style = HeritageType.display.copy(fontSize = 26.sp), color = p.ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(shell.bg, onClick = { year-- }, size = 36.dp, icon = Icons.chevronLeft)
                    Text(
                        s.num(year), style = HeritageType.title, color = p.gold,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                    GlassIconButton(shell.bg, onClick = { year++ }, size = 36.dp, icon = Icons.chevronRight)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(s.num(publicDays), style = HeritageType.heading, color = p.holidayKhmer)
                        Text(s.publicHoliday, style = HeritageType.micro, color = p.inkSoft)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Badge(if (year in Holidays.OFFICIAL_YEARS) "✓ ${s.official}" else "≈ ${s.calculated}", if (year in Holidays.OFFICIAL_YEARS) p.jade else p.gold)
            }
        }

        // ------------------------------------------------------------ smart breaks
        if (breaks.isNotEmpty()) {
            item(key = "breaksTitle") { SectionTitle(s.smartBreaks, Modifier.enterStagger(1)) }
            item(key = "breaks") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                    itemsIndexed(breaks, key = { _, b -> b.startPacked }) { i, b ->
                        BreakCard(shell, b, today, Modifier.width(236.dp).height(178.dp).enterStagger(2 + i))
                    }
                }
            }
        }

        // ------------------------------------------------------------ filters
        item(key = "filters") {
            Row(Modifier.enterStagger(3), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(s.filterAll, filter == Filter.ALL, shell.bg) { filter = Filter.ALL }
                GlassChip(s.filterKhmer, filter == Filter.KHMER, shell.bg, accent = p.holidayKhmer, leading = "🇰🇭") { filter = Filter.KHMER }
                GlassChip(s.filterGlobal, filter == Filter.GLOBAL, shell.bg, accent = p.holidayGlobal, leading = "🌍") { filter = Filter.GLOBAL }
            }
        }

        // ------------------------------------------------------------ list, grouped by month
        var lastMonth = -1
        events.forEachIndexed { i, group ->
            val h = group.first()
            if (h.month != lastMonth) {
                lastMonth = h.month
                item(key = "m-$year-${h.month}") {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(s.monthNames[h.month - 1], style = HeritageType.title.copy(fontSize = 18.sp), color = p.goldSoft)
                        KbachDivider(Modifier.padding(top = 2.dp), height = 8.dp, alpha = 0.35f)
                    }
                }
            }
            item(key = "h-${h.id}-${h.packed}") {
                HolidayRow(shell, group, today, Modifier.enterStagger(4 + i.coerceAtMost(10)))
            }
        }
    }
}

@Composable
private fun HolidayRow(shell: ShellState, group: List<Holiday>, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val h = group.first()
    val last = group.last()
    val past = last.packed < today
    val days = Day.between(today, h.packed).toInt()
    val color = holidayColor(h, p)
    GlassCard(
        shell.bg, modifier.fillMaxWidth().graphicsLayer { alpha = if (past) 0.5f else 1f },
        padding = PaddingValues(12.dp), onClick = { shell.openDay(h.packed, null) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTile(h.packed, color, Modifier.width(56.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${h.emoji} ${h.title(s).substringBefore(" · ")}", style = HeritageType.body, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val span = if (group.size > 1) "${gregorianShort(h.packed, s)} – ${gregorianShort(last.packed, s)}" else lunarLine(khmerOf(h.packed), s)
                Text(span, style = HeritageType.micro, color = p.inkSoft)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(
                        when (h.kind) { HolidayKind.KHMER_PUBLIC -> s.publicHoliday; HolidayKind.KHMER_OBSERVANCE -> s.observance; HolidayKind.GLOBAL -> s.global },
                        color,
                    )
                    if (h.kind == HolidayKind.KHMER_PUBLIC) Badge(if (h.source == HolidaySource.OFFICIAL) s.official else s.calculated, if (h.source == HolidaySource.OFFICIAL) p.jade else p.gold)
                }
            }
            if (!past) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (days <= 1) s.inDays(days) else s.num(days), style = HeritageType.heading, color = color, maxLines = 1)
                    if (days > 1) Text(s.days, style = HeritageType.micro, color = p.inkSoft)
                }
            }
        }
    }
}

/** "Take 1 day → 6 days off" with a mini timeline of the break. */
@Composable
private fun BreakCard(shell: ShellState, b: BreakSuggestion, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val publicHolidays = remember(b) {
        Holidays.forYear(Day.year(b.startPacked)).filter { it.kind == HolidayKind.KHMER_PUBLIC && it.packed in b.startPacked..b.endPacked }
    }
    val publicDates = remember(publicHolidays) { publicHolidays.map { it.packed }.toSet() }
    val names = remember(publicHolidays, s) { publicHolidays.distinctBy { it.id }.joinToString(" · ") { it.title(s).substringBefore(" · ") } }
    GlassCard(shell.bg, modifier, liquid = true, onClick = { shell.openDay(b.startPacked, null) }) {
        // Fixed-height card: headline, dates and timeline sit at the top, holiday names
        // are pinned to the bottom (max 2 lines), so every card in the row lines up.
        Text(s.takeLeave(b.leaveDays.size, b.totalDaysOff), style = HeritageType.heading, color = p.goldSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${gregorianShort(b.startPacked, s)} – ${gregorianShort(b.endPacked, s)}", style = HeritageType.label, color = p.inkSoft, maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val n = b.totalDaysOff
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val d = Day.plusDays(b.startPacked, i.toLong())
                val c = when {
                    d in b.leaveDays -> p.gold
                    d in publicDates -> p.holidayKhmer
                    else -> p.lotus.copy(alpha = 0.55f)
                }
                drawRoundRect(c, Offset(i * (w + gap), 0f), Size(w, size.height), CornerRadius(5.dp.toPx()))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (b.endPacked < today) "✓ $names" else names,
            style = HeritageType.micro, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            minLines = 2,
        )
    }
}

