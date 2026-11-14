package com.pratitin.app.ui.screens.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.pratitin.app.ui.Tab
import com.pratitin.app.ui.components.Badge
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.ProgressRing
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.heritage.KbachDivider
import com.pratitin.app.ui.heritage.drawAllCorners
import com.pratitin.app.ui.heritage.drawLotus
import com.pratitin.app.ui.heritage.drawMoon
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.motion.Motion
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.ANIMAL_EMOJI
import com.pratitin.app.ui.screens.detail.EventRow
import com.pratitin.app.ui.screens.events.newEvent
import com.pratitin.app.ui.screens.MoodPicker
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.screens.gregorianLong
import com.pratitin.app.ui.screens.gregorianShort
import com.pratitin.app.ui.screens.holidayColor
import com.pratitin.app.ui.screens.holidaysOn
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.screens.title
import com.pratitin.app.ui.screens.upcomingHolidays
import com.pratitin.app.ui.screens.yearLine
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.HolidaySource
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames

@Composable
fun TodayScreen(shell: ShellState) {
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val events by shell.vm.events.collectAsStateWithLifecycle()
    val love by shell.vm.love.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val p = LocalPalette.current

    val k = remember(today) { khmerOf(today) }
    // next occurrences over the coming two weeks
    val upcoming = remember(events, today) {
        (0L..13L).flatMap { i ->
            val d = Day.plusDays(today, i)
            events.filter { it.occursOn(d) }.sortedWith(compareBy({ !it.allDay }, { it.startMin })).map { d to it }
        }.take(5)
    }
    val todays = remember(today, settings) { holidaysOn(today, settings) }
    val next = remember(today, settings) { upcomingHolidays(Day.plusDays(today, 1), settings, 1).firstOrNull() }

    ScreenList(shell) {
        // ---------------------------------------------------------------- header
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.lang == Lang.KM) "ថ្ងៃ${KhmerNames.WEEKDAYS[k.dayOfWeek]}" else java.time.DayOfWeek.of(if (k.dayOfWeek == 0) 7 else k.dayOfWeek).name.lowercase().replaceFirstChar { it.uppercase() },
                        style = HeritageType.display, color = p.ink, maxLines = 1,
                    )
                    Text(gregorianLong(today, s), style = HeritageType.label, color = p.inkSoft)
                }
                GlassIconButton(shell.bg, onClick = shell.openSettings, icon = Icons.settings)
            }
        }

        // ---------------------------------------------------------------- hero
        item(key = "hero") {
            val originRect = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
            GlassCard(
                shell.bg, liquid = true, radius = 34.dp,
                modifier = Modifier.fillMaxWidth().enterStagger(1).onGloballyPositioned { originRect[0] = it.boundsInRoot() },
                onClick = { shell.openDay(today, originRect[0]) },
                padding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Box(Modifier.fillMaxWidth().drawBehind { drawAllCorners(p.gold.copy(alpha = 0.55f), 22.dp.toPx()) }.padding(22.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HeroMoon(k.illumination, k.dayNumber < 15, Modifier.size(92.dp))
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.lunarDate, style = HeritageType.micro, color = p.gold)
                                Text(
                                    if (s.lang == Lang.KM) "${s.num(k.day)} ${KhmerNames.PHASE[k.phase.ordinal]}" else "${k.day} ${if (k.dayNumber < 15) s.waxing else s.waning}",
                                    style = HeritageType.display.copy(fontSize = 30.sp), color = p.ink, maxLines = 1,
                                )
                                Text(
                                    if (s.lang == Lang.KM) "ខែ${KhmerNames.LUNAR_MONTHS[k.monthIndex]}" else KhmerNames.LUNAR_MONTHS_LATIN[k.monthIndex],
                                    style = HeritageType.title, color = p.goldSoft, maxLines = 1,
                                )
                            }
                            AnimalBadge(k.animalYear)
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(yearLine(k, s), style = HeritageType.body, color = p.inkSoft, maxLines = 2)
                        Spacer(Modifier.height(10.dp))
                        HeroBadges(k, todays.map { it.title(s) to holidayColor(it, p) })
                    }
                }
                KbachDivider(Modifier.padding(horizontal = 26.dp).padding(bottom = 14.dp), height = 12.dp, alpha = 0.55f)
            }
        }

        // ---------------------------------------------------------------- welcome back (fresh install)
        if (events.isEmpty() && moods.isEmpty() && love.items.isEmpty()) item(key = "restore") {
            com.pratitin.app.ui.screens.settings.RestorePromptCard(shell, Modifier.enterStagger(2))
        }

        // ---------------------------------------------------------------- love
        item(key = "love") { com.pratitin.app.ui.screens.love.TodayLoveCard(shell, love, today, Modifier.enterStagger(2)) }

        // ---------------------------------------------------------------- next holiday
        if (next != null) item(key = "next") {
            val days = Day.between(today, next.packed).toInt()
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(2), onClick = { shell.goTo(Tab.HOLIDAYS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(1f - (days / 60f).coerceIn(0f, 1f), Modifier.size(64.dp), color = holidayColor(next, p)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.num(days), style = HeritageType.heading, color = p.ink)
                            Text(s.days, style = HeritageType.micro, color = p.inkSoft)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.nextHoliday, style = HeritageType.micro, color = p.gold)
                        Text("${next.emoji} ${next.title(s)}", style = HeritageType.heading, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(gregorianShort(next.packed, s) + " · " + s.inDays(days), style = HeritageType.label, color = p.inkSoft)
                            Spacer(Modifier.width(8.dp))
                            if (next.source == HolidaySource.OFFICIAL) Badge(s.official, p.jade)
                        }
                    }
                }
            }
        }

        // ---------------------------------------------------------------- week strip
        item(key = "week") {
            WeekStrip(shell, today, settings.weekStartsMonday, Modifier.enterStagger(3))
        }

        // ---------------------------------------------------------------- schedule
        item(key = "schedule") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(s.schedule, Modifier.weight(1f))
                    Box(
                        Modifier.size(34.dp).clip(Squircles.pill).background(p.gold)
                            .clickable { shell.editEvent(newEvent(today), true) },
                        contentAlignment = Alignment.Center,
                    ) { Text("+", style = HeritageType.heading, color = p.night) }
                }
                Spacer(Modifier.height(8.dp))
                if (upcoming.isEmpty()) Text(s.noEvents, style = HeritageType.label, color = p.inkSoft)
                upcoming.forEach { (d, ev) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d == today) s.today else gregorianShort(d, s),
                            style = HeritageType.micro, color = p.gold, modifier = Modifier.width(58.dp),
                        )
                        EventRow(ev, onClick = { shell.editEvent(ev, false) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- mood
        item(key = "mood") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(4)) {
                SectionTitle(s.howFeel)
                Spacer(Modifier.height(14.dp))
                MoodPicker(moods[today]?.mood, onPick = { shell.vm.setMood(today, it, moods[today]?.note.orEmpty()) })
            }
        }

        // ---------------------------------------------------------------- Khmer New Year
        item(key = "songkran") { SongkranCard(shell, today, Modifier.enterStagger(5)) }

        // ---------------------------------------------------------------- life mini
        item(key = "life") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(6), onClick = { shell.goTo(Tab.LIFE) }) {
                if (settings.birthDate == 0) {
                    Text(s.lifeTitle, style = HeritageType.heading, color = p.ink)
                    Text(s.setBirthday, style = HeritageType.label, color = p.gold)
                } else {
                    val lived = (Day.between(settings.birthDate, today) / 7).toInt()
                    val total = settings.lifeYears * 52
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.num(lived), style = HeritageType.number.copy(fontSize = 40.sp), color = p.gold)
                        Spacer(Modifier.width(8.dp))
                        Text(s.weeksLived, style = HeritageType.label, color = p.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    LifeBar(lived / total.toFloat())
                }
            }
        }
    }
}

@Composable
private fun HeroMoon(illumination: Float, waxing: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) illumination else 0f) }
    LaunchedEffect(illumination) { anim.animateTo(illumination, Motion.gentle) }
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current
    Canvas(modifier) {
        val glow = 0.85f + 0.25f * (clock?.let { com.pratitin.app.ui.motion.pingPong(it.time, 6.4f) } ?: 0.6f)
        val r = size.minDimension * 0.36f
        drawCircle(Brush.radialGradient(listOf(p.goldSoft.copy(alpha = 0.28f * glow), Color.Transparent), center, size.minDimension * 0.5f * glow))
        drawMoon(center, r, anim.value, waxing, p.moon, p.nightDeep.copy(alpha = 0.55f), glow = false)
        drawCircle(p.gold.copy(alpha = 0.6f), r + 3.dp.toPx(), center, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
    }
}

@Composable
private fun AnimalBadge(animal: Int) {
    val p = LocalPalette.current
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current
    Box(
        Modifier.size(52.dp).graphicsLayer {
            val bob = clock?.let { -3f + 6f * com.pratitin.app.ui.motion.pingPong(it.time, 4.8f) } ?: 0f
            translationY = bob * density
        }.clip(Squircles.m).background(p.gold.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) { Text(ANIMAL_EMOJI[animal], fontSize = 28.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroBadges(k: com.pratitin.khmer.KhmerDate, holidays: List<Pair<String, Color>>) {
    val s = LocalStrings.current
    val p = LocalPalette.current
    val dim = KhmerCalendar.daysInMonth(k.monthIndex, k.beYear)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        holidays.forEach { (name, c) -> Badge(name, c, filled = true) }
        if (k.isSilaDay(dim)) Badge(s.silaDay, p.gold, icon = Icons.wheel)
        if (k.isFullMoon) Badge(s.fullMoon, p.moon, icon = Icons.fullMoon)
        if (k.dayNumber == dim - 1) Badge(s.newMoon, p.inkSoft, icon = Icons.newMoon)
        when (KhmerCalendar.leapKind(k.beYear)) {
            1 -> Badge(s.leapMonth, p.lotus, icon = Icons.leap)
            2 -> Badge(s.leapDay, p.jade)
        }
    }
}

@Composable
private fun WeekStrip(shell: ShellState, today: Int, monday: Boolean, modifier: Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val start = remember(today, monday) {
        val wd = Day.weekday(today)
        val back = if (monday) (wd + 6) % 7 else wd
        Day.plusDays(today, -back.toLong())
    }
    GlassCard(shell.bg, modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = Day.plusDays(start, i.toLong())
                val k = remember(d) { khmerOf(d) }
                val hol = remember(d, settings) { holidaysOn(d, settings) }
                val isToday = d == today
                val origin = remember { arrayOfNulls<androidx.compose.ui.geometry.Rect>(1) }
                Column(
                    Modifier.weight(1f).padding(2.dp).clip(Squircles.s)
                        .background(if (isToday) p.gold else Color.Transparent)
                        .onGloballyPositioned { origin[0] = it.boundsInRoot() }
                        .clickable(remember { MutableInteractionSource() }, null) { shell.openDay(d, origin[0]) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ink = if (isToday) p.night else p.ink
                    Text(s.weekdaysShort[Day.weekday(d)], style = HeritageType.micro, color = if (isToday) p.night.copy(alpha = 0.7f) else p.inkSoft)
                    Text(s.num(Day.dom(d)), style = HeritageType.heading, color = if (hol.any { it.kind == com.pratitin.khmer.HolidayKind.KHMER_PUBLIC } && !isToday) p.holidayKhmer else ink)
                    Text(k.shortLabel().let { if (s.lang == Lang.KM) it else "${k.day}${if (k.dayNumber < 15) "K" else "R"}" }, style = HeritageType.micro, color = if (isToday) p.night.copy(alpha = 0.75f) else p.gold)
                    Box(Modifier.padding(top = 3.dp).size(5.dp).clip(Squircles.xs).background(hol.firstOrNull()?.let { holidayColor(it, p) } ?: Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun SongkranCard(shell: ShellState, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val year = Day.year(today)
    val ny = remember(year, today) {
        val thisYear = KhmerCalendar.newYear(year)
        val packed = thisYear.year * 10000 + thisYear.month * 100 + thisYear.day
        if (packed + thisYear.celebrationDays - 1 >= today) thisYear else KhmerCalendar.newYear(year + 1)
    }
    val nyPacked = ny.year * 10000 + ny.month * 100 + ny.day
    val days = Day.between(today, nyPacked).toInt()
    val nextAnimal = remember(ny) { KhmerCalendar.fromGregorian(ny.year, ny.month, ny.day, 23, 59).animalYear }
    val clock = com.pratitin.app.ui.motion.LocalAmbientClock.current

    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = p.lacquer.copy(alpha = 0.18f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(70.dp)) {
                val bloom = 0.55f + 0.45f * (clock?.let { com.pratitin.app.ui.motion.pingPong(it.time, 5.6f, 0.3f) } ?: 1f)
                drawLotus(Offset(center.x, size.height * 0.92f), size.height * 0.85f, bloom, p.lotus, p.goldSoft)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.nextKhmerNewYear, style = HeritageType.micro, color = p.gold)
                Text(
                    if (s.lang == Lang.KM) "ឆ្នាំ${KhmerNames.ANIMALS[nextAnimal]} ${ANIMAL_EMOJI[nextAnimal]}" else "Year of the ${KhmerNames.ANIMALS_LATIN[nextAnimal]} ${ANIMAL_EMOJI[nextAnimal]}",
                    style = HeritageType.title, color = p.ink,
                )
                val time = "%02d:%02d".format(ny.hour, ny.minute).let { if (s.lang == Lang.KM) KhmerNames.digits(it) else it }
                Text("${gregorianShort(nyPacked, s)} · ${s.songkranAt} $time", style = HeritageType.label, color = p.inkSoft)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.num(maxOf(days, 0)), style = HeritageType.number.copy(fontSize = 34.sp), color = p.goldSoft)
                Text(s.days, style = HeritageType.micro, color = p.inkSoft)
            }
        }
    }
}

@Composable
fun LifeBar(fraction: Float, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), Motion.gentle) }
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(p.inkFaint.copy(alpha = 0.18f), cornerRadius = r)
        drawRoundRect(
            Brush.horizontalGradient(listOf(p.lacquer, p.gold, p.goldSoft)),
            size = Size(size.width * anim.value, size.height), cornerRadius = r,
        )
        drawCircle(p.goldSoft, size.height * 0.75f, Offset(size.width * anim.value, size.height / 2))
    }
}
