package com.pratitin.app.ui.screens.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.pratitin.app.ui.shape.Squircles
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Lang
import com.pratitin.app.data.MoodEntry
import com.pratitin.app.data.Settings
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.Strings
import com.pratitin.app.ui.components.Badge
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.heritage.drawMoon
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.DateTile
import com.pratitin.app.ui.screens.MoodPicker
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.screens.holidayColor
import com.pratitin.app.ui.screens.holidaysOn
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.screens.title
import com.pratitin.app.ui.screens.visible
import com.pratitin.app.ui.shape.squirclePath
import com.pratitin.app.ui.theme.HeritagePalette
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames
import kotlinx.coroutines.launch

private const val FIRST_YEAR = 1900
private const val LAST_YEAR = 2100

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(shell: ShellState) {
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val todayPage = (Day.year(today) - FIRST_YEAR) * 12 + Day.month(today) - 1
    val pager = rememberPagerState(initialPage = todayPage) { (LAST_YEAR - FIRST_YEAR + 1) * 12 }
    var selected by rememberSaveable { mutableIntStateOf(today) }
    var moodLayer by rememberSaveable { mutableStateOf(true) }

    val page = pager.currentPage
    val year = FIRST_YEAR + page / 12
    val month = page % 12 + 1

    ScreenList(shell) {
        // ------------------------------------------------------------ header
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn() + slideInVertically { it / 2 * dir }) togetherWith (fadeOut() + slideOutVertically { -it / 2 * dir })
                        },
                        label = "month",
                    ) { pg ->
                        val y = FIRST_YEAR + pg / 12
                        val m = pg % 12 + 1
                        Column {
                            Text(s.monthNames[m - 1], style = HeritageType.display, color = p.ink, maxLines = 1)
                            Text("${s.num(y)} · ${lunarSpan(y, m, s)}", style = HeritageType.label, color = p.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                GlassIconButton(shell.bg, onClick = { scope.launch { pager.animateScrollToPage(page - 1) } }, size = 40.dp, icon = Icons.chevronLeft)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(shell.bg, onClick = {
                    selected = today
                    scope.launch { pager.animateScrollToPage(todayPage) }
                }, size = 40.dp, icon = Icons.target)
                Spacer(Modifier.width(8.dp))
                GlassIconButton(shell.bg, onClick = { scope.launch { pager.animateScrollToPage(page + 1) } }, size = 40.dp, icon = Icons.chevronRight)
            }
        }

        // ------------------------------------------------------------ layer chips
        item(key = "layers") {
            LazyRow(Modifier.enterStagger(1), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
                item { GlassChip(s.global, settings.showGlobal, shell.bg, accent = p.holidayGlobal, leading = "🌍") { shell.vm.updateSettings { it.copy(showGlobal = !it.showGlobal) } } }
                item { GlassChip(s.observance, settings.showObservances, shell.bg, accent = p.gold, leading = "🪷") { shell.vm.updateSettings { it.copy(showObservances = !it.showObservances) } } }
                item { GlassChip(s.silaDay, settings.showSila, shell.bg, accent = p.goldSoft, leading = "☸") { shell.vm.updateSettings { it.copy(showSila = !it.showSila) } } }
                item { GlassChip(s.tabMood, moodLayer, shell.bg, accent = p.lotus, leading = "🎨") { moodLayer = !moodLayer } }
            }
        }

        // ------------------------------------------------------------ grid
        item(key = "grid") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(2), liquid = true, radius = 30.dp, padding = PaddingValues(10.dp)) {
                WeekdayHeader(settings.weekStartsMonday, s)
                Spacer(Modifier.height(4.dp))
                HorizontalPager(state = pager, beyondViewportPageCount = 1, pageSpacing = 12.dp) { pg ->
                    val y = FIRST_YEAR + pg / 12
                    val m = pg % 12 + 1
                    MonthGrid(
                        year = y, month = m, today = today, selected = { selected },
                        settings = settings, moods = if (moodLayer) moods else emptyMap(),
                        onTap = { d, rect ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (d == selected) shell.openDay(d, rect) else selected = d
                            val target = (Day.year(d) - FIRST_YEAR) * 12 + Day.month(d) - 1
                            if (target != pager.currentPage) scope.launch { pager.animateScrollToPage(target) }
                        },
                    )
                }
            }
        }

        // ------------------------------------------------------------ selected day
        item(key = "selected") {
            SelectedDayCard(shell, selected, settings, moods[selected], Modifier.enterStagger(3))
        }

        // ------------------------------------------------------------ month events
        val events = Holidays.forYear(year).filter { it.month == month && visible(it, settings) }
            .distinctBy { it.id + it.dayOfSpan.coerceAtMost(1) }
        if (events.isNotEmpty()) {
            item(key = "eventsTitle") { SectionTitle(s.upcoming, Modifier.padding(top = 6.dp)) }
            events.forEachIndexed { i, h ->
                item(key = "ev-${h.id}-${h.packed}") {
                    GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(4 + i), padding = PaddingValues(12.dp), onClick = { shell.openDay(h.packed, null) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTile(h.packed, holidayColor(h, p), Modifier.width(54.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${h.emoji} ${h.title(s)}", style = HeritageType.body, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val k = khmerOf(h.packed)
                                Text(lunarLine(k, s), style = HeritageType.micro, color = p.inkSoft)
                            }
                            if (h.spanLength > 1) Badge("${s.num(h.spanLength)} ${s.days}", holidayColor(h, p))
                        }
                    }
                }
            }
        }
    }
}

/** "កត្ដិក – មិគសិរ" — the lunar months a Gregorian month overlaps. */
private fun lunarSpan(year: Int, month: Int, s: Strings): String {
    val a = KhmerCalendar.fromGregorian(year, month, 1)
    val b = KhmerCalendar.fromGregorian(year, month, KhmerCalendar.gregorianDaysInMonth(year, month))
    val names = if (s.lang == Lang.KM) KhmerNames.LUNAR_MONTHS else KhmerNames.LUNAR_MONTHS_LATIN
    val be = if (s.lang == Lang.KM) "ព.ស. ${s.num(b.beYear)}" else "BE ${b.beYear}"
    return if (a.monthIndex == b.monthIndex) "${names[a.monthIndex]} · $be" else "${names[a.monthIndex]} – ${names[b.monthIndex]} · $be"
}

@Composable
private fun WeekdayHeader(monday: Boolean, s: Strings) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val wd = if (monday) (i + 1) % 7 else i
            Text(
                s.weekdaysShort[wd], style = HeritageType.micro,
                color = if (wd == 0 || wd == 6) p.lotus.copy(alpha = 0.85f) else p.inkSoft,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Pre-computed, immutable model of one month page — built once per (month, inputs). */
private class Cell(
    val date: Int, val inMonth: Boolean, val dom: String, val lunar: String,
    val publicHoliday: Boolean, val dot: Color?, val sila: Boolean, val moon: Int, // 0 none, 1 full, 2 new
    val mood: Color?, val weekend: Boolean,
)

private fun buildCells(year: Int, month: Int, s: Settings, strings: Strings, p: HeritagePalette, moods: Map<Int, MoodEntry>): List<Cell> {
    val first = year * 10000 + month * 100 + 1
    val wd = Day.weekday(first)
    val lead = if (s.weekStartsMonday) (wd + 6) % 7 else wd
    val start = Day.plusDays(first, -lead.toLong())
    return List(42) { i ->
        val d = Day.plusDays(start, i.toLong())
        val k = khmerOf(d)
        val dim = KhmerCalendar.daysInMonth(k.monthIndex, k.beYear)
        val hol = holidaysOn(d, s)
        val w = Day.weekday(d)
        Cell(
            date = d, inMonth = Day.month(d) == month,
            dom = strings.num(Day.dom(d)),
            lunar = if (strings.lang == Lang.KM) k.shortLabel() else "${k.day}${if (k.dayNumber < 15) "K" else "R"}",
            publicHoliday = hol.any { it.kind == HolidayKind.KHMER_PUBLIC },
            dot = hol.firstOrNull()?.let { holidayColor(it, p) },
            sila = s.showSila && k.isSilaDay(dim),
            moon = when { k.isFullMoon -> 1; k.dayNumber == dim - 1 -> 2; else -> 0 },
            mood = moods[d]?.mood?.color,
            weekend = w == 0 || w == 6,
        )
    }
}

@Composable
private fun MonthGrid(
    year: Int, month: Int, today: Int, selected: () -> Int,
    settings: Settings, moods: Map<Int, MoodEntry>,
    onTap: (Int, Rect?) -> Unit,
) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val cells = remember(year, month, settings, s, p, moods) { buildCells(year, month, settings, s, p, moods) }
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }

    // Selection highlight glides between cells with a spring (draw-phase only).
    val selIndex = cells.indexOfFirst { it.date == selected() }
    val target = if (selIndex >= 0) Offset((selIndex % 7).toFloat(), (selIndex / 7).toFloat()) else Offset(-10f, -10f)
    val selPos by animateOffsetAsState(target, spring(dampingRatio = 0.7f, stiffness = 420f), label = "sel")
    val selAlpha by animateFloatAsState(if (selIndex >= 0) 1f else 0f, label = "selA")

    Box(
        Modifier.fillMaxWidth().aspectRatio(7f / 6f / 1.08f)
            .onGloballyPositioned { gridOrigin = it.positionInRoot() }
            .pointerInput(cells) {
                detectTapGestures { pos ->
                    val cw = size.width / 7f
                    val ch = size.height / 6f
                    val c = (pos.x / cw).toInt().coerceIn(0, 6)
                    val r = (pos.y / ch).toInt().coerceIn(0, 5)
                    val cell = cells[r * 7 + c]
                    val rect = Rect(gridOrigin.x + c * cw, gridOrigin.y + r * ch, gridOrigin.x + (c + 1) * cw, gridOrigin.y + (r + 1) * ch)
                    onTap(cell.date, rect)
                }
            }
            .drawWithCache {
                val cw = size.width / 7f
                val ch = size.height / 6f
                val inset = 2.5.dp.toPx()
                val cellSize = Size(cw - inset * 2, ch - inset * 2)
                val cellPath = squirclePath(cellSize, 14.dp.toPx(), 0.6f, Path())
                val domStyle = HeritageType.heading.copy(fontSize = 17.sp)
                val lunarStyle = HeritageType.micro.copy(fontSize = 9.sp)
                val layouts = cells.map { cell ->
                    val isToday = cell.date == today
                    val domColor = when {
                        isToday -> p.night
                        cell.publicHoliday -> p.holidayKhmer
                        cell.weekend -> p.lotus.copy(alpha = 0.9f)
                        else -> p.ink
                    }.let { if (cell.inMonth || isToday) it else it.copy(alpha = 0.32f) }
                    val lunarColor = (if (isToday) p.night.copy(alpha = 0.75f) else p.gold).let { if (cell.inMonth) it else it.copy(alpha = 0.3f) }
                    measurer.measure(cell.dom, domStyle.copy(color = domColor)) to measurer.measure(cell.lunar, lunarStyle.copy(color = lunarColor))
                }
                onDrawBehind {
                    // selection glow (under cells)
                    if (selAlpha > 0.01f) {
                        translate(selPos.x * cw + inset, selPos.y * ch + inset) {
                            drawPath(cellPath, p.gold.copy(alpha = 0.18f * selAlpha))
                            drawPath(cellPath, p.goldSoft.copy(alpha = 0.9f * selAlpha), style = Stroke(1.5.dp.toPx()))
                        }
                    }
                    cells.forEachIndexed { i, cell ->
                        val x = (i % 7) * cw
                        val y = (i / 7) * ch
                        val isToday = cell.date == today
                        translate(x + inset, y + inset) {
                            when {
                                isToday -> drawPath(cellPath, p.gold)
                                cell.publicHoliday && cell.inMonth -> drawPath(cellPath, p.holidayKhmer.copy(alpha = 0.13f))
                            }
                            cell.mood?.let { mc ->
                                val bw = cellSize.width * 0.42f
                                drawRoundRect(mc.copy(alpha = if (cell.inMonth) 0.95f else 0.35f), Offset((cellSize.width - bw) / 2, cellSize.height - 5.dp.toPx()), Size(bw, 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                            }
                        }
                        val (domL, lunL) = layouts[i]
                        drawCentered(domL, x + cw / 2, y + ch * 0.40f)
                        drawCentered(lunL, x + cw / 2, y + ch * 0.72f)
                        // moon & markers in the top-right corner
                        val mx = x + cw - inset - 8.dp.toPx()
                        val my = y + inset + 8.dp.toPx()
                        when (cell.moon) {
                            1 -> drawMoon(Offset(mx, my), 3.5.dp.toPx(), 1f, true, if (isToday) p.night else p.moon, Color.Transparent, glow = false)
                            2 -> drawCircle(if (isToday) p.night else p.inkSoft, 3.5.dp.toPx(), Offset(mx, my), style = Stroke(1.dp.toPx()))
                        }
                        if (cell.sila && cell.moon == 0) drawCircle(if (isToday) p.night else p.goldSoft, 1.8.dp.toPx(), Offset(mx, my))
                        cell.dot?.let { if (!cell.publicHoliday || !cell.inMonth) drawCircle(it.copy(alpha = if (cell.inMonth) 1f else 0.4f), 2.2.dp.toPx(), Offset(x + inset + 8.dp.toPx(), my)) }
                    }
                }
            },
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCentered(layout: TextLayoutResult, cx: Float, cy: Float) {
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, cy - layout.size.height / 2f))
}

@Composable
private fun SelectedDayCard(shell: ShellState, date: Int, settings: Settings, mood: MoodEntry?, modifier: Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val k = remember(date) { khmerOf(date) }
    val hol = remember(date, settings) { holidaysOn(date, settings) }
    GlassCard(shell.bg, modifier.fillMaxWidth(), onClick = { shell.openDay(date, null) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(com.pratitin.app.ui.screens.gregorianLong(date, s), style = HeritageType.label, color = p.inkSoft)
                Text(lunarLine(k, s), style = HeritageType.title.copy(fontSize = 19.sp), color = p.ink)
                Text(com.pratitin.app.ui.screens.yearLine(k, s), style = HeritageType.micro, color = p.gold)
            }
            androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
                drawMoon(center, size.minDimension * 0.4f, k.illumination, k.dayNumber < 15, p.moon, p.nightDeep.copy(alpha = 0.5f))
            }
        }
        if (hol.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            hol.forEach { h ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(Squircles.xs).background(holidayColor(h, p)))
                    Spacer(Modifier.width(8.dp))
                    Text("${h.emoji} ${h.title(s)}", style = HeritageType.body, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        MoodPicker(mood?.mood, onPick = { shell.vm.setMood(date, it, mood?.note.orEmpty()) }, showLabel = false)
    }
}

