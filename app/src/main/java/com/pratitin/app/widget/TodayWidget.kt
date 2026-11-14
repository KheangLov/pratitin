package com.pratitin.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.TextAlign
import com.pratitin.app.R
import com.pratitin.app.data.Lang
import com.pratitin.app.data.LoveMath
import com.pratitin.app.data.Mood
import com.pratitin.app.ui.screens.love.anniversaryTitle
import com.pratitin.app.ui.screens.gregorianShort
import com.pratitin.app.ui.screens.holidaysOn
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.screens.title
import com.pratitin.app.ui.screens.upcomingHolidays
import com.pratitin.app.ui.screens.yearLine
import com.pratitin.app.util.Day
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 250.dp)

private fun isSila(date: Int): Boolean {
    val k = khmerOf(date)
    return k.isSilaDay(KhmerCalendar.daysInMonth(k.monthIndex, k.beYear))
}

// ====================================================================== Today

/** Today: Gregorian + lunar date, moon, holiday; wider adds the next holiday; larger adds the week and what's coming. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    private class Up(val date: Int, val emoji: String, val title: String, val holiday: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = loadData(context)
        val s = w.s
        val today = w.today
        val k = khmerOf(today)
        val todays = holidaysOn(today, w.settings)
        val next = upcomingHolidays(Day.plusDays(today, 1), w.settings, 1).firstOrNull()
        val (events, _) = readEvents(context, w.km)
        val start = weekStart(today, w.settings.weekStartsMonday)
        val week = (0 until 7).map { Day.plusDays(start, it.toLong()) }
        val weekHol = week.associateWith { holidaysOn(it, w.settings).firstOrNull() }
        val weekEvents = week.associateWith { d -> events.any { it.occursOn(d) } }
        val up = buildList {
            upcomingHolidays(Day.plusDays(today, 1), w.settings, 3).forEach { add(Up(it.packed, it.emoji, it.title(s), true)) }
            for (i in 0L..30L) {
                val d = Day.plusDays(today, i)
                events.filter { it.occursOn(d) }.forEach { add(Up(d, it.kind.emoji, it.title.ifBlank { if (w.km) it.kind.km else it.kind.en }, false)) }
            }
        }.sortedBy { it.date }.distinctBy { it.date to it.title }.take(3)
        val weekday = if (w.km) "ថ្ងៃ${KhmerNames.WEEKDAYS[Day.weekday(today)]}"
            else java.time.DayOfWeek.of(if (Day.weekday(today) == 0) 7 else Day.weekday(today)).name.lowercase().replaceFirstChar { it.uppercase() }
        val monthName = s.monthNames[Day.month(today) - 1]
        val moon = moonEmoji(k.dayNumber, k.isFullMoon)
        val sila = isSila(today)
        val openToday = open(context, "today-$today", date = today)

        provideContent {
            val size = LocalSize.current
            Frame(R.drawable.widget_bg_dusk, openToday, pad = if (size.width < 200.dp) 12.dp else 14.dp) {
                when {
                    size.width < 200.dp -> {
                        // ---------------- small: a centred lunar page
                        Spacer(GlanceModifier.defaultWeight())
                        T(moon, 26.sp, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        T(if (w.km) "${s.num(k.day)}${KhmerNames.PHASE[k.phase.ordinal]}" else "${k.day} ${if (k.dayNumber < 15) "Kaet" else "Roch"}",
                            18.sp, bold = true, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        T(if (w.km) "ខែ${KhmerNames.LUNAR_MONTHS[k.monthIndex]}" else KhmerNames.LUNAR_MONTHS_LATIN[k.monthIndex],
                            12.sp, WC.goldSoft, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        Spacer(GlanceModifier.height(4.dp))
                        T(gregorianShort(today, s) + " " + s.num(Day.year(today)), 11.sp, WC.soft, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        if (todays.isNotEmpty()) T("${todays[0].emoji} ${todays[0].title(s)}", 10.sp, WC.holiday, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        else if (sila) T("☸ ${s.silaDay}", 10.sp, WC.gold, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                        Spacer(GlanceModifier.defaultWeight())
                    }
                    else -> {
                        // ---------------- header (wide and large)
                        Row(GlanceModifier.fillMaxWidth().let { if (size.height < 200.dp) it.defaultWeight() else it }, verticalAlignment = Alignment.CenterVertically) {
                            Column(GlanceModifier.width(84.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                                T(weekday, 11.sp, WC.goldSoft, align = TextAlign.Center)
                                T(s.num(Day.dom(today)), 40.sp, WC.gold, bold = true, align = TextAlign.Center)
                                T((if (w.km) monthName else monthName.take(3)) + " " + s.num(Day.year(today)), 10.sp, WC.soft, align = TextAlign.Center)
                            }
                            Spacer(GlanceModifier.width(10.dp))
                            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    T(moon, 20.sp)
                                    Spacer(GlanceModifier.width(6.dp))
                                    T(lunarLine(k, s), 15.sp, bold = true, maxLines = 1)
                                }
                                T(yearLine(k, s), 11.sp, WC.goldSoft, maxLines = 1)
                                Spacer(GlanceModifier.height(6.dp))
                                when {
                                    todays.isNotEmpty() -> Pill("${todays[0].emoji} ${todays[0].title(s)}", R.drawable.widget_pill_holiday, WC.holiday)
                                    sila -> Pill("☸ ${s.silaDay}", R.drawable.widget_pill_soft, WC.gold)
                                    next != null -> {
                                        val dn = Day.between(today, next.packed).toInt()
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            T("${next.emoji} ${next.title(s)}", 12.sp, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                            Spacer(GlanceModifier.width(6.dp))
                                            Pill(s.inDays(dn), R.drawable.widget_pill_gold, WC.night)
                                        }
                                    }
                                }
                            }
                        }
                        if (size.height >= 200.dp) {
                            // ---------------- week strip
                            Spacer(GlanceModifier.height(10.dp))
                            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                                week.forEachIndexed { i, d ->
                                    val isToday = d == today
                                    val h = weekHol[d]
                                    val kd = khmerOf(d)
                                    val color = when {
                                        isToday -> WC.night
                                        h?.kind == HolidayKind.KHMER_PUBLIC -> WC.holiday
                                        isSila(d) -> WC.gold
                                        else -> WC.ink
                                    }
                                    Column(
                                        GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp)
                                            .background(ImageProvider(if (isToday) R.drawable.widget_pill_gold else R.drawable.widget_pill_soft)).cornerRadius(12.dp)
                                            .clickable(open(context, "wk-$d", date = d)),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        T(s.weekdaysShort[weekdayIndex(i, w.settings.weekStartsMonday)], 9.sp, if (isToday) WC.night else WC.soft, align = TextAlign.Center)
                                        T(s.num(Day.dom(d)), 15.sp, color, bold = true, align = TextAlign.Center)
                                        T(if (kd.isFullMoon) "🌕" else s.num(kd.day), 9.sp, if (isToday) WC.night else WC.goldSoft, align = TextAlign.Center)
                                        T(if (weekEvents[d] == true) "•" else " ", 9.sp, if (isToday) WC.night else WC.rose, align = TextAlign.Center)
                                    }
                                }
                            }
                            // ---------------- coming up
                            Spacer(GlanceModifier.height(8.dp))
                            up.forEach { u ->
                                Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    T("${u.emoji} ${u.title}", 12.sp, if (u.holiday) WC.ink else WC.blush, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                                    T(gregorianShort(u.date, s) + " · " + s.inDays(Day.between(today, u.date).toInt()), 10.sp, WC.soft)
                                }
                            }
                            if (up.isEmpty()) T(s.noEvents, 11.sp, WC.faint)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Pill(text: String, bg: Int, color: androidx.glance.unit.ColorProvider) {
    T(text, 11.sp, color, bold = true, maxLines = 1,
        modifier = GlanceModifier.background(ImageProvider(bg)).cornerRadius(12.dp).padding(horizontal = 9.dp, vertical = 3.dp))
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefresher.scheduleMidnight(context)
    }
}

// ====================================================================== Love

class LoveWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = loadData(context)
        val s = w.s
        val today = w.today
        val (_, love) = readEvents(context, w.km)
        val a = love.featured
        val e = a?.let { LoveMath.elapsed(it.date, today) }
        val future = a != null && a.date > today
        val nexts = love.items.map { it to LoveMath.next(it, today) }.sortedBy { it.second }.take(3)
        val couple = love.couple(w.km)
        fun big(n: Long) = LoveMath.fmt(n, s.khmerDigits)
        val openLove = open(context, "love", tab = "love")

        provideContent {
            val size = LocalSize.current
            Frame(R.drawable.widget_bg_love, openLove, pad = 12.dp) {
                if (a == null || e == null) {
                    Spacer(GlanceModifier.defaultWeight())
                    T("💞", 28.sp, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    T(w.t("បញ្ចូលថ្ងៃខួបរបស់យើង", "Add our anniversary"), 13.sp, bold = true, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 2)
                    T(w.t("ចុចដើម្បីចាប់ផ្ដើម", "Tap to begin"), 10.sp, WC.blush, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    Spacer(GlanceModifier.defaultWeight())
                    return@Frame
                }
                val label = if (future) w.t("ថ្ងៃទៀត", "days to go") else w.t("ថ្ងៃជាមួយគ្នា", "days together")
                if (size.width < 200.dp) {
                    Spacer(GlanceModifier.defaultWeight())
                    T("💞", 20.sp, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    T(big(e.days), 30.sp, WC.blush, bold = true, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    T(label, 11.sp, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    Spacer(GlanceModifier.height(2.dp))
                    T(couple, 10.sp, WC.roseGold, modifier = GlanceModifier.fillMaxWidth(), align = TextAlign.Center)
                    Spacer(GlanceModifier.defaultWeight())
                    return@Frame
                }
                Row(GlanceModifier.fillMaxWidth().let { if (size.height < 200.dp) it.defaultWeight() else it }, verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                        T("💞", 18.sp, align = TextAlign.Center)
                        T(big(e.days), 34.sp, WC.blush, bold = true, align = TextAlign.Center)
                        T(label, 11.sp, align = TextAlign.Center)
                    }
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        T(couple, 13.sp, bold = true, maxLines = 1)
                        T(if (w.km) "${s.num(e.years)} ឆ្នាំ ${s.num(e.months)} ខែ ${s.num(e.monthDays)} ថ្ងៃ" else "${e.years}y ${e.months}m ${e.monthDays}d", 12.sp, WC.roseGold)
                        T(if (w.km) "${big(e.weeks)} សប្ដាហ៍ · ${big(e.totalMonths)} ខែ" else "${big(e.weeks)} weeks · ${big(e.totalMonths)} months", 10.sp, WC.soft)
                        Spacer(GlanceModifier.height(6.dp))
                        nexts.firstOrNull()?.let { (na, occ) ->
                            val dn = Day.between(today, occ).toInt()
                            Pill(
                                "${na.kind.emoji} " + (if (dn == 0) w.t("ថ្ងៃនេះ!", "Today!") else s.inDays(dn)),
                                R.drawable.widget_pill_rose, WC.ink,
                            )
                        }
                    }
                }
                if (size.height >= 200.dp) {
                    Spacer(GlanceModifier.height(10.dp))
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        listOf(
                            s.num(e.years) to w.t("ឆ្នាំ", "years"), big(e.totalMonths) to w.t("ខែ", "months"),
                            big(e.weeks) to w.t("សប្ដាហ៍", "weeks"), big(e.days * 24) to w.t("ម៉ោង", "hours"),
                        ).forEach { (v, l) ->
                            Column(
                                GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)
                                    .background(ImageProvider(R.drawable.widget_pill_rose_soft)).cornerRadius(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(v, 15.sp, bold = true, align = TextAlign.Center)
                                T(l, 9.sp, WC.blush, align = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(GlanceModifier.height(8.dp))
                    nexts.forEach { (na, occ) ->
                        val num = LoveMath.number(na, occ)
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("${na.kind.emoji} ${anniversaryTitle(na, num, s)}", 12.sp, maxLines = 1, modifier = GlanceModifier.defaultWeight())
                            T(gregorianShort(occ, s) + " · " + s.inDays(Day.between(today, occ).toInt()), 10.sp, WC.blush)
                        }
                    }
                }
            }
        }
    }
}

class LoveWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LoveWidget()
}

// ====================================================================== Month

class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(LARGE, DpSize(300.dp, 360.dp)))

    private class Cell(val date: Int, val inMonth: Boolean, val lunar: String, val holiday: Boolean, val sila: Boolean, val event: Boolean)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = loadData(context)
        val s = w.s
        val today = w.today
        val y = Day.year(today); val mo = Day.month(today)
        val first = y * 10000 + mo * 100 + 1
        val start = weekStart(first, w.settings.weekStartsMonday)
        val dim = KhmerCalendar.gregorianDaysInMonth(y, mo)
        val rows = if (Day.between(start, first) + dim > 35) 6 else 5
        val (events, _) = readEvents(context, w.km)
        val hol = Holidays.byDate(y)
        val cells = (0 until rows * 7).map { i ->
            val d = Day.plusDays(start, i.toLong())
            val kd = khmerOf(d)
            Cell(
                d, Day.month(d) == mo,
                if (kd.isFullMoon) "🌕" else if (kd.day == 1 && kd.dayNumber == 0) "🌑" else s.num(kd.day),
                hol[d].orEmpty().any { it.kind == HolidayKind.KHMER_PUBLIC },
                kd.isSilaDay(KhmerCalendar.daysInMonth(kd.monthIndex, kd.beYear)) && w.settings.showSila,
                events.any { it.occursOn(d) },
            )
        }
        val lunarSpan = listOf(khmerOf(first), khmerOf(y * 10000 + mo * 100 + dim)).map {
            if (w.km) KhmerNames.LUNAR_MONTHS[it.monthIndex] else KhmerNames.LUNAR_MONTHS_LATIN[it.monthIndex]
        }.distinct().joinToString("–")

        provideContent {
            val size = LocalSize.current
            val big = size.height >= 340.dp
            Frame(R.drawable.widget_bg_dusk, open(context, "month", tab = "calendar"), pad = 10.dp) {
                Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(s.monthNames[mo - 1] + " " + s.num(y), 15.sp, bold = true, modifier = GlanceModifier.defaultWeight())
                    T((if (w.km) "ខែ" else "") + lunarSpan, 11.sp, WC.goldSoft)
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        T(s.weekdaysShort[weekdayIndex(c, w.settings.weekStartsMonday)], 9.sp, WC.soft, modifier = GlanceModifier.defaultWeight(), align = TextAlign.Center)
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                for (r in 0 until rows) {
                    Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                        for (c in 0 until 7) {
                            val cell = cells[r * 7 + c]
                            val isToday = cell.date == today
                            val color = when {
                                isToday -> WC.night
                                !cell.inMonth -> WC.faint
                                cell.holiday -> WC.holiday
                                cell.sila -> WC.gold
                                else -> WC.ink
                            }
                            Column(
                                GlanceModifier.defaultWeight().fillMaxHeight().padding(1.dp)
                                    .let { if (isToday) it.background(ImageProvider(R.drawable.widget_pill_gold)).cornerRadius(10.dp) else it }
                                    .clickable(open(context, "m-${cell.date}", date = cell.date)),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(s.num(Day.dom(cell.date)), if (big) 15.sp else 13.sp, color, bold = isToday || cell.holiday, align = TextAlign.Center)
                                T(cell.lunar + if (cell.event) "•" else "", if (big) 9.sp else 8.sp,
                                    if (isToday) WC.night else if (cell.inMonth) WC.goldSoft else WC.faint, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

// ====================================================================== Mood

class MoodWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(250.dp, 50.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 170.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = loadData(context)
        val s = w.s
        val today = w.today
        val moods = readMoods(context)
        val now = moods[today]
        val week = (6 downTo 0).map { Day.plusDays(today, -it.toLong()) }

        provideContent {
            val size = LocalSize.current
            Frame(R.drawable.widget_bg_jade, open(context, "mood", tab = "mood"), pad = if (size.height < 100.dp) 8.dp else 12.dp) {
                if (size.height >= 100.dp) {
                    T(
                        if (now != null) "${w.t("ថ្ងៃនេះ", "Today")}: ${now.emoji} ${if (w.km) now.km else now.en}" else s.howFeel,
                        13.sp, bold = true, modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                }
                Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    Mood.entries.forEach { m ->
                        val sel = m == now
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 2.dp)
                                .background(ImageProvider(if (sel) R.drawable.widget_pill_gold else R.drawable.widget_pill_soft)).cornerRadius(14.dp)
                                .clickable(open(context, "mood-${m.name}", tab = "mood:${m.name}")),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
                        ) { T(m.emoji, if (size.height < 100.dp) 20.sp else 24.sp, align = TextAlign.Center) }
                    }
                }
                if (size.height >= 160.dp) {
                    Spacer(GlanceModifier.height(8.dp))
                    Row(GlanceModifier.fillMaxWidth()) {
                        week.forEach { d ->
                            val m = moods[d]
                            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                T(m?.emoji ?: "·", 13.sp, if (m == null) WC.faint else WC.ink, align = TextAlign.Center)
                                T(s.weekdaysShort[Day.weekday(d)], 9.sp, if (d == today) WC.gold else WC.soft, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

class MoodWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoodWidget()
}

// ====================================================================== Holidays

class HolidaysWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp), LARGE, DpSize(250.dp, 330.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val w = loadData(context)
        val s = w.s
        val today = w.today
        val list = upcomingHolidays(today, w.settings, 7)

        provideContent {
            val size = LocalSize.current
            val n = when { size.height < 150.dp -> 2; size.height < 220.dp -> 3; size.height < 320.dp -> 5; else -> 7 }
            val wide = size.width >= 230.dp
            Frame(R.drawable.widget_bg_dusk, open(context, "holidays", tab = "holidays"), pad = 12.dp) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("🪷 " + s.upcoming, 13.sp, WC.goldSoft, bold = true, modifier = GlanceModifier.defaultWeight())
                    T(s.num(Day.year(today)), 10.sp, WC.soft)
                }
                Spacer(GlanceModifier.height(4.dp))
                list.take(n).forEach { h ->
                    val dn = Day.between(today, h.packed).toInt()
                    val col = when (h.kind) { HolidayKind.KHMER_PUBLIC -> WC.holiday; HolidayKind.GLOBAL -> WC.global; else -> WC.gold }
                    Row(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open(context, "h-${h.packed}", date = h.packed)), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.width(38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T(s.num(Day.dom(h.packed)), 15.sp, col, bold = true, align = TextAlign.Center)
                            T(s.monthNames[Day.month(h.packed) - 1].let { if (w.km) it else it.take(3) }, 8.sp, WC.soft, align = TextAlign.Center)
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            T("${h.emoji} ${h.title(s)}", 12.sp, maxLines = 1)
                            if (wide) T(
                                lunarLine(khmerOf(h.packed), s) + if (h.spanLength > 1) (if (w.km) " · ឈប់ ${s.num(h.spanLength)} ថ្ងៃ" else " · ${h.spanLength} days off") else "",
                                9.sp, WC.soft, maxLines = 1,
                            )
                        }
                        Spacer(GlanceModifier.width(6.dp))
                        Pill(if (dn == 0) w.t("ថ្ងៃនេះ", "Today") else s.inDays(dn), if (dn <= 7) R.drawable.widget_pill_gold else R.drawable.widget_pill_soft, if (dn <= 7) WC.night else WC.ink)
                    }
                }
            }
        }
    }
}

class HolidaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HolidaysWidget()
}
