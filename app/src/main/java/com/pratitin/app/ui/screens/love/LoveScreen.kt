package com.pratitin.app.ui.screens.love

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.audio.LoveSound
import com.pratitin.app.data.AnnivKind
import com.pratitin.app.data.Anniversary
import com.pratitin.app.data.Lang
import com.pratitin.app.data.LoveData
import com.pratitin.app.data.LoveMath
import com.pratitin.app.data.LoveProfile
import com.pratitin.app.notify.CalendarExport
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.Strings
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.ProgressRing
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.heritage.KbachDivider
import com.pratitin.app.ui.heritage.drawAllCorners
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.screens.gregorianLong
import com.pratitin.app.ui.screens.gregorianShort
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

/** A fresh anniversary of [kind], defaulting to today. */
fun newAnniversary(kind: AnnivKind, today: Int) = Anniversary(
    id = System.currentTimeMillis(), kind = kind, date = today,
    daysBefore = if (kind == AnnivKind.WEDDING || kind == AnnivKind.PARTNER_BIRTHDAY) 3 else 1,
    monthly = kind == AnnivKind.TOGETHER,
)

/** "3rd Wedding Anniversary" / "ខួបអាពាហ៍ពិពាហ៍ លើកទី ៣" */
fun anniversaryTitle(a: Anniversary, num: Int, s: Strings): String {
    val km = s.lang == Lang.KM
    return when {
        num == 0 -> a.name(km)
        a.kind == AnnivKind.PARTNER_BIRTHDAY -> if (km) "${a.name(true)} · អាយុ ${s.num(num)}" else "${a.name(false)} · turning $num"
        km -> "${a.annName(true)} លើកទី ${s.num(num)}"
        else -> "${LoveMath.ordinalEn(num)} ${a.annName(false)}"
    }
}

private fun Strings.big(n: Long) = LoveMath.fmt(n, khmerDigits)

/** Keeps the lullaby in step with the tab and the app's lifecycle. */
@Composable
private fun LoveMusic(profile: LoveProfile) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val music by rememberUpdatedState(profile.music)
    LaunchedEffect(Unit) { LoveSound.preload(ctx) }
    DisposableEffect(owner, profile.music) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> if (music) LoveSound.startMusic(ctx)
                Lifecycle.Event.ON_PAUSE -> LoveSound.stopMusic()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        if (!profile.music) LoveSound.stopMusic()
        onDispose { owner.lifecycle.removeObserver(obs); LoveSound.stopMusic() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoveScreen(shell: ShellState) {
    val love by shell.vm.love.collectAsStateWithLifecycle()
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val p = LocalPalette.current
    val lp = lovePalette()
    val km = s.lang == Lang.KM
    val ctx = LocalContext.current
    val sound = love.profile.sound
    LoveMusic(love.profile)

    val featured = love.featured
    val timeline = remember(love.items) { love.items.sortedBy { it.date } }

    Box(Modifier.fillMaxSize()) {
        LoveAmbience(Modifier.fillMaxSize())
        ScreenList(shell) {
            // ------------------------------------------------------------ header
            item(key = "header") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (km) "ស្នេហារបស់យើង" else "Our Love", style = HeritageType.display.copy(fontSize = 30.sp), color = p.ink, maxLines = 1)
                        Text(love.couple(km), style = HeritageType.label, color = lp.blush, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    GlassIconButton(shell.bg, onClick = {
                        val on = !(love.profile.sound || love.profile.music)
                        shell.vm.updateLove { it.copy(sound = on, music = on) }
                        if (on) LoveSound.play(ctx, LoveSound.Sfx.CHIME)
                    }, icon = if (love.profile.sound || love.profile.music) Icons.speaker else Icons.mute)
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(shell.bg, onClick = shell.openLoveProfile, icon = Icons.settings)
                }
            }

            if (featured == null) {
                item(key = "welcome") { WelcomeCard(shell, today, Modifier.enterStagger(1)) }
            } else {
                // -------------------------------------------------------- hero
                item(key = "hero-${featured.id}") { HeroCard(shell, love, featured, today, Modifier.enterStagger(1)) }
                // -------------------------------------------------------- units
                item(key = "units-${featured.id}") { UnitsCard(shell, featured, today, Modifier.enterStagger(2)) }
                // -------------------------------------------------------- next anniversary
                item(key = "next-${featured.id}") { NextCard(shell, love, featured, today, Modifier.enterStagger(3)) }
                // -------------------------------------------------------- milestones
                if (featured.date < today || featured.kind != AnnivKind.PARTNER_BIRTHDAY) item(key = "milestones-${featured.id}") {
                    MilestonesCard(shell, featured, today, Modifier.enterStagger(4))
                }
                // -------------------------------------------------------- fun facts
                if (featured.date < today) item(key = "facts-${featured.id}") { FactsCard(shell, featured, today, Modifier.enterStagger(5)) }
            }

            // ------------------------------------------------------------ our story
            if (timeline.isNotEmpty()) {
                item(key = "storyTitle") {
                    SectionTitle(if (km) "រឿងរ៉ាវស្នេហារបស់យើង" else "Our story", Modifier.padding(top = 6.dp)) {
                        Text(
                            "＋ " + (if (km) "បន្ថែម" else "Add"), style = HeritageType.label, color = Color.White,
                            modifier = Modifier.clip(Squircles.pill).background(Brush.horizontalGradient(listOf(lp.rose, lp.wine)))
                                .clickable { shell.openLoveEditor(newAnniversary(AnnivKind.CUSTOM, today), true) }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
                item(key = "story") {
                    GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(6), tint = lp.cardTint, padding = PaddingValues(vertical = 10.dp, horizontal = 14.dp)) {
                        timeline.forEachIndexed { i, a ->
                            StoryRow(a, today, first = i == 0, last = i == timeline.lastIndex, featured = a.id == featured?.id) {
                                shell.openLoveEditor(a, false)
                            }
                        }
                    }
                }
                // quick adds for the kinds not yet used
                val missing = listOf(AnnivKind.WEDDING, AnnivKind.TOGETHER, AnnivKind.FIRST_MET, AnnivKind.FIRST_DATE, AnnivKind.ENGAGEMENT, AnnivKind.PARTNER_BIRTHDAY)
                    .filter { k -> love.items.none { it.kind == k } }
                if (missing.isNotEmpty()) item(key = "quick") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        missing.forEach { k ->
                            Text(
                                "${k.emoji} ${if (km) k.km else k.en}", style = HeritageType.label, color = p.ink,
                                modifier = Modifier.clip(Squircles.pill).background(lp.rose.copy(alpha = 0.16f))
                                    .border(1.dp, lp.rose.copy(alpha = 0.35f), Squircles.pill)
                                    .clickable { shell.openLoveEditor(newAnniversary(k, today), true) }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------------------ a line for her
            item(key = "quote") { LoveNote(today, Modifier.enterStagger(7)) }
        }
    }
}

// ================================================================== welcome

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WelcomeCard(shell: ShellState, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val km = LocalStrings.current.lang == Lang.KM
    val ctx = LocalContext.current
    GlassCard(shell.bg, modifier.fillMaxWidth(), liquid = true, radius = 34.dp, tint = lp.cardTint) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BeatingHeart(84.dp, onTap = { LoveSound.play(ctx, LoveSound.Sfx.TAP) })
            Text(if (km) "ចាប់ផ្ដើមរឿងស្នេហារបស់យើង" else "Begin our love story", style = HeritageType.title, color = p.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                if (km) "បញ្ចូលថ្ងៃរៀបការ ថ្ងៃស្រឡាញ់គ្នា ឬថ្ងៃពិសេសផ្សេងៗ ដើម្បីរាប់ថ្ងៃ សប្ដាហ៍ ខែ និងឆ្នាំ ដែលយើងនៅជាមួយគ្នា ព្រមទាំងទទួលការរំលឹកមុនថ្ងៃខួប។"
                else "Add your wedding day, the day you got together, or any special date to count the days, weeks, months and years together — and get reminded before every anniversary.",
                style = HeritageType.body, color = p.inkSoft, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AnnivKind.WEDDING, AnnivKind.TOGETHER, AnnivKind.FIRST_MET, AnnivKind.PARTNER_BIRTHDAY).forEach { k ->
                    Text(
                        "${k.emoji} ${if (km) k.km else k.en}", style = HeritageType.label, color = if (k == AnnivKind.WEDDING) Color.White else p.ink,
                        modifier = Modifier.clip(Squircles.pill)
                            .background(if (k == AnnivKind.WEDDING) Brush.horizontalGradient(listOf(lp.rose, lp.wine)) else Brush.horizontalGradient(listOf(lp.rose.copy(alpha = 0.18f), lp.rose.copy(alpha = 0.18f))))
                            .clickable { shell.openLoveEditor(newAnniversary(k, today), true) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

// ================================================================== hero

@Composable
private fun HeroCard(shell: ShellState, love: LoveData, a: Anniversary, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val ctx = LocalContext.current
    val future = a.date > today
    val days = kotlin.math.abs(Day.between(a.date, today))
    // count up when the tab opens
    val reduce = LocalReduceMotion.current
    val shown = remember(a.id) { Animatable(if (reduce) days.toFloat() else 0f) }
    LaunchedEffect(a.id, days) { shown.animateTo(days.toFloat(), tween(if (reduce) 0 else 1600, easing = FastOutSlowInEasing)) }

    GlassCard(shell.bg, modifier.fillMaxWidth(), liquid = true, radius = 36.dp, tint = lp.cardTint, padding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxWidth().drawBehind { drawAllCorners(lp.roseGold.copy(alpha = 0.6f), 22.dp.toPx()) }.padding(vertical = 18.dp, horizontal = 20.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${a.kind.emoji} " + (if (future) (if (km) "រាប់ថយក្រោយដល់ ${a.name(true)}" else "Counting down to ${a.name(false).lowercase()}")
                    else when (a.kind) {
                        AnnivKind.WEDDING -> if (km) "រៀបការតាំងពី" else "Married since"
                        AnnivKind.PARTNER_BIRTHDAY -> if (km) "កើតនៅ" else "Born on"
                        else -> if (km) "${a.name(true)} តាំងពី" else "${a.name(false)} ·"
                    }) + " " + gregorianShort(a.date, s) + " " + s.num(Day.year(a.date)),
                    style = HeritageType.label, color = lp.blush, textAlign = TextAlign.Center,
                )
                BeatingHeart(96.dp, onTap = { LoveSound.play(ctx, LoveSound.Sfx.TAP, love.profile.sound) })
                Text(love.couple(km), style = HeritageType.title, color = p.ink, textAlign = TextAlign.Center, maxLines = 2)
                Text(
                    s.big(shown.value.toLong()),
                    style = HeritageType.number.copy(
                        brush = Brush.verticalGradient(listOf(lp.blush, lp.rose)), fontSize = 66.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    if (future) (if (km) "ថ្ងៃទៀត" else if (days == 1L) "day to go" else "days to go")
                    else if (a.kind == AnnivKind.PARTNER_BIRTHDAY) (if (km) "ថ្ងៃនៃជីវិតនាង" else "days of her life")
                    else (if (km) "ថ្ងៃដែលយើងនៅជាមួយគ្នា" else "days together"),
                    style = HeritageType.heading, color = p.inkSoft,
                )
                Spacer(Modifier.height(12.dp))
                LiveTicker(a.date, future)
            }
        }
        KbachDivider(Modifier.padding(horizontal = 26.dp).padding(bottom = 14.dp), height = 12.dp, color = lp.roseGold, alpha = 0.5f)
    }
}

/** Years · months · days · h · m · s, ticking every second. */
@Composable
private fun LiveTicker(start: Int, future: Boolean) {
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(1000 - System.currentTimeMillis() % 1000); now = LocalDateTime.now() } }
    val startDt = Day.unpack(start).atStartOfDay()
    val (from, to) = if (future) now to startDt else startDt to now
    val p = java.time.Period.between(from.toLocalDate(), to.toLocalDate()).let {
        // the date part only counts whole days that have finished
        if (to.toLocalTime() < from.toLocalTime()) java.time.Period.between(from.toLocalDate(), to.toLocalDate().minusDays(1)) else it
    }
    val rest = Duration.between(from.plus(p), to).let { if (it.isNegative) Duration.ZERO else it }
    val cells = listOf(
        p.years.toLong() to (if (km) "ឆ្នាំ" else "years"),
        p.months.toLong() to (if (km) "ខែ" else "months"),
        p.days.toLong() to (if (km) "ថ្ងៃ" else "days"),
        rest.toHours() % 24 to (if (km) "ម៉ោង" else "hrs"),
        rest.toMinutes() % 60 to (if (km) "នាទី" else "min"),
        rest.seconds % 60 to (if (km) "វិនាទី" else "sec"),
    )
    val lp = lovePalette()
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEachIndexed { i, (v, label) ->
            Column(
                Modifier.weight(1f).clip(Squircles.s).background(lp.rose.copy(alpha = if (i < 3) 0.20f else 0.12f)).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (i >= 3) s.num(v.toInt()).padStart(2, if (s.khmerDigits) '០' else '0') else s.num(v.toInt()), style = HeritageType.heading, color = pal.ink, maxLines = 1)
                Text(label, style = HeritageType.micro, color = pal.inkSoft, maxLines = 1)
            }
        }
    }
}

// ================================================================== units

@Composable
private fun UnitsCard(shell: ShellState, a: Anniversary, today: Int, modifier: Modifier) {
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val lp = lovePalette()
    val e = remember(a.date, today) { LoveMath.elapsed(a.date, today) }
    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = lp.cardTint) {
        SectionTitle(if (a.date > today) (if (km) "នៅសល់" else "Still to go") else (if (km) "រាប់ជាមួយគ្នា" else "Counting together"))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UnitTile(s.big(e.days), if (km) "ថ្ងៃ" else "days", if (km) "សរុប" else "in total", "☀️", Modifier.weight(1f))
            UnitTile(s.big(e.weeks), if (km) "សប្ដាហ៍" else "weeks",
                if (e.weekDays > 0) (if (km) "និង ${s.num(e.weekDays)} ថ្ងៃ" else "and ${e.weekDays} day${if (e.weekDays > 1) "s" else ""}") else (if (km) "គត់" else "exactly"), "🗓", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UnitTile(s.big(e.totalMonths), if (km) "ខែ" else "months",
                if (e.monthDays > 0) (if (km) "និង ${s.num(e.monthDays)} ថ្ងៃ" else "and ${e.monthDays} day${if (e.monthDays > 1) "s" else ""}") else (if (km) "គត់" else "exactly"), "🌙", Modifier.weight(1f))
            UnitTile(s.num(e.years), if (km) "ឆ្នាំ" else if (e.years == 1) "year" else "years",
                if (km) "${s.num(e.months)} ខែ ${s.num(e.monthDays)} ថ្ងៃ" else "${e.months} mo ${e.monthDays} d", "💞", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UnitTile(s.big(e.days * 24), if (km) "ម៉ោង" else "hours", null, "⏳", Modifier.weight(1f), small = true)
            UnitTile(s.big(e.days * 1440), if (km) "នាទី" else "minutes", null, "💫", Modifier.weight(1f), small = true)
        }
    }
}

@Composable
private fun UnitTile(value: String, unit: String, sub: String?, emoji: String, modifier: Modifier, small: Boolean = false) {
    val p = LocalPalette.current
    val lp = lovePalette()
    Column(
        modifier.fillMaxHeight().clip(Squircles.m)
            .background(Brush.linearGradient(listOf(lp.rose.copy(alpha = 0.22f), lp.wine.copy(alpha = 0.10f))))
            .border(1.dp, lp.roseGold.copy(alpha = 0.25f), Squircles.m)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = if (small) 12.sp else 14.sp)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = HeritageType.micro, color = lp.blush, maxLines = 1)
        }
        Text(value, style = (if (small) HeritageType.heading else HeritageType.title.copy(fontSize = 24.sp)), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = HeritageType.micro, color = p.inkSoft, maxLines = 1)
    }
}

// ================================================================== next anniversary

@Composable
private fun NextCard(shell: ShellState, love: LoveData, featured: Anniversary, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val ctx = LocalContext.current
    // the soonest celebration across everything, featured wins ties
    val (a, occ) = remember(love.items, today) {
        love.items.map { it to LoveMath.next(it, today) }.minWith(compareBy<Pair<Anniversary, Int>>({ it.second }, { if (it.first.id == featured.id) 0 else 1 }))
    }
    val days = Day.between(today, occ).toInt()
    val num = LoveMath.number(a, occ)
    val gift = if (a.kind == AnnivKind.WEDDING && num > 0) LoveMath.gift(num) else null
    val title = anniversaryTitle(a, num, s)
    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = lp.cardTint, onClick = { shell.openLoveEditor(a, false) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(if (days == 0) 1f else LoveMath.yearProgress(a, today), Modifier.size(84.dp), color = lp.rose, stroke = 6.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (days == 0) Text("🎉", fontSize = 26.sp)
                    else {
                        Text(s.num(days), style = HeritageType.title.copy(fontSize = 22.sp), color = p.ink, maxLines = 1)
                        Text(s.days, style = HeritageType.micro, color = p.inkSoft)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (days == 0) (if (km) "ថ្ងៃនេះ!" else "Today!") else if (km) "ខួបបន្ទាប់" else "Next celebration",
                    style = HeritageType.micro, color = lp.blush,
                )
                Text("${a.kind.emoji} $title", style = HeritageType.heading, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(gregorianLong(occ, s), style = HeritageType.label, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("🌕 " + lunarLine(khmerOf(occ), s), style = HeritageType.micro, color = p.gold)
            }
        }
        if (gift != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "${gift.third} " + (if (km) "អំណោយប្រពៃណីខួបលើកទី ${s.num(num)}៖ ${gift.first}" else "Traditional ${LoveMath.ordinalEn(num)} anniversary gift: ${gift.second}"),
                style = HeritageType.label, color = p.ink,
                modifier = Modifier.fillMaxWidth().clip(Squircles.s).background(lp.roseGold.copy(alpha = 0.16f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill(
                if (km) "📅 ដាក់ក្នុងប្រតិទិនទូរស័ព្ទ" else "📅 Add to phone calendar", Modifier.weight(1.4f), filled = true,
            ) { exportToCalendar(ctx, love, a, s) }
            ActionPill(
                if (!a.remind) (if (km) "🔕 បិទ" else "🔕 Off")
                else if (a.daysBefore == 0) (if (km) "🔔 ថ្ងៃនោះ" else "🔔 On the day")
                else (if (km) "🔔 មុន ${s.num(a.daysBefore)} ថ្ងៃ" else "🔔 ${a.daysBefore}d before"),
                Modifier.weight(1f),
            ) { shell.openLoveEditor(a, false) }
        }
    }
}

fun exportToCalendar(ctx: android.content.Context, love: LoveData, a: Anniversary, s: Strings) {
    val km = s.lang == Lang.KM
    val title = "${a.kind.emoji} ${a.annName(km)} · ${love.couple(km)}"
    val desc = buildString {
        append(if (km) "ចាប់ផ្ដើម៖ " else "Since: ").append(gregorianLong(a.date, s))
        if (a.lunar) append(" (").append(lunarLine(khmerOf(a.date), s)).append(")")
        if (a.note.isNotBlank()) append("\n").append(a.note)
        append("\n\n").append(if (km) "បង្កើតដោយ ប្រតិទិន · Pratitin 💞" else "Added from Pratitin 💞")
    }
    CalendarExport.add(ctx, a, title, desc, km)
}

@Composable
private fun ActionPill(text: String, modifier: Modifier = Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    val lp = lovePalette()
    Text(
        text, style = HeritageType.label, color = if (filled) Color.White else p.ink, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(Squircles.pill)
            .background(if (filled) Brush.horizontalGradient(listOf(lp.rose, lp.wine)) else Brush.horizontalGradient(listOf(lp.rose.copy(alpha = 0.16f), lp.rose.copy(alpha = 0.16f))))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
    )
}

// ================================================================== milestones

@Composable
private fun MilestonesCard(shell: ShellState, a: Anniversary, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val list = remember(a.date, today) {
        listOfNotNull(LoveMath.lastMilestone(a.date, today)) + LoveMath.upcomingMilestones(a.date, today, 8)
    }
    if (list.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionTitle(if (km) "ចំណុចសំខាន់ៗ" else "Milestones")
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(list, key = { "${it.unit}-${it.value}" }) { m ->
                val d = Day.between(today, m.date).toInt()
                val done = d < 0
                val unit = when (m.unit) {
                    LoveMath.MUnit.DAYS -> if (km) "ថ្ងៃ" else "days"
                    LoveMath.MUnit.WEEKS -> if (km) "សប្ដាហ៍" else "weeks"
                    LoveMath.MUnit.HOURS -> if (km) "ម៉ោង" else "hours"
                    LoveMath.MUnit.MINUTES -> if (km) "នាទី" else "minutes"
                }
                GlassCard(shell.bg, Modifier.width(148.dp), radius = 24.dp, tint = if (d == 0) lp.rose.copy(alpha = 0.45f) else lp.cardTint, padding = PaddingValues(14.dp)) {
                    Text(if (done) "✓" else if (d == 0) "🎉" else "✨", fontSize = 16.sp)
                    Text(s.big(m.value), style = HeritageType.title.copy(fontSize = 22.sp), color = if (done) p.inkSoft else p.ink, maxLines = 1)
                    Text(unit, style = HeritageType.label, color = lp.blush)
                    Spacer(Modifier.height(4.dp))
                    Text(gregorianShort(m.date, s) + " " + s.num(Day.year(m.date)), style = HeritageType.micro, color = p.inkSoft, maxLines = 1)
                    Text(
                        if (done) (if (km) "បានឆ្លងកាត់" else "reached") else s.inDays(d),
                        style = HeritageType.micro, color = if (done) p.jade else p.gold, maxLines = 1,
                    )
                }
            }
        }
    }
}

// ================================================================== fun facts

@Composable
private fun FactsCard(shell: ShellState, a: Anniversary, today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val days = Day.between(a.date, today)
    val nyCount = remember(a.date, today) { LoveMath.khmerNewYears(a.date, LocalDateTime.now()) }
    val beats = days * 24 * 60 * 72 // ~72 bpm, two hearts in step
    val facts = listOf(
        Triple("🌕", s.big(LoveMath.fullMoons(days)), if (km) "ពេញបូណ៌មីជាមួយគ្នា" else "full moons together"),
        Triple("🌅", s.big(days + 1), if (km) "ថ្ងៃរះជាមួយគ្នា" else "sunrises side by side"),
        Triple("💓", compact(beats, s), if (km) "ចង្វាក់បេះដូងរួមគ្នា" else "heartbeats in step"),
        Triple("🪷", s.num(nyCount), if (km) "ចូលឆ្នាំខ្មែរជាមួយគ្នា" else "Khmer New Years together"),
    )
    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = lp.cardTint) {
        SectionTitle(if (km) "តាមរយៈពេលនេះ…" else "In that time…")
        Spacer(Modifier.height(8.dp))
        facts.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (e, v, l) ->
                    Row(Modifier.weight(1f).clip(Squircles.m).background(lp.rose.copy(alpha = 0.10f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(v, style = HeritageType.heading, color = p.ink, maxLines = 1)
                            Text(l, style = HeritageType.micro, color = p.inkSoft, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

private fun compact(n: Long, s: Strings): String {
    val km = s.lang == Lang.KM
    val (v, unit) = when {
        n >= 1_000_000_000L -> n / 1e9 to (if (km) " ពាន់លាន" else "B")
        n >= 1_000_000L -> n / 1e6 to (if (km) " លាន" else "M")
        else -> return s.big(n)
    }
    val txt = "%.1f".format(v)
    return (if (s.khmerDigits) com.pratitin.khmer.KhmerNames.digits(txt) else txt) + unit
}

// ================================================================== story timeline

@Composable
private fun StoryRow(a: Anniversary, today: Int, first: Boolean, last: Boolean, featured: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val e = remember(a.date, today) { LoveMath.elapsed(a.date, today) }
    val next = remember(a, today) { LoveMath.next(a, today) }
    val dNext = Day.between(today, next).toInt()
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(Squircles.m).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // the thread of the story
        Canvas(Modifier.width(26.dp).fillMaxHeight()) {
            val x = size.width / 2
            if (!first) drawLine(lp.roseGold.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height / 2), 2.dp.toPx())
            if (!last) drawLine(lp.roseGold.copy(alpha = 0.5f), Offset(x, size.height / 2), Offset(x, size.height), 2.dp.toPx())
            drawCircle(if (featured) lp.rose else lp.roseGold, if (featured) 7.dp.toPx() else 5.dp.toPx(), Offset(x, size.height / 2))
            if (featured) drawCircle(Color.White, 2.5.dp.toPx(), Offset(x, size.height / 2))
        }
        Spacer(Modifier.width(8.dp))
        Text(a.kind.emoji, fontSize = 24.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(a.name(km), style = HeritageType.heading.copy(fontSize = 16.sp), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                gregorianShort(a.date, s) + " " + s.num(Day.year(a.date)) + if (a.lunar) " · 🌕 " + lunarLine(khmerOf(a.date), s) else "",
                style = HeritageType.micro, color = p.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (a.date <= today) Text(
                if (km) "${s.num(e.years)} ឆ្នាំ ${s.num(e.months)} ខែ ${s.num(e.monthDays)} ថ្ងៃ · ${s.big(e.days)} ថ្ងៃ"
                else "${e.years}y ${e.months}m ${e.monthDays}d · ${s.big(e.days)} days",
                style = HeritageType.micro, color = lp.blush, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (dNext == 0) "🎉" else s.inDays(dNext), style = HeritageType.label, color = Color.White, maxLines = 1,
                modifier = Modifier.clip(Squircles.pill).background(if (dNext <= 7) lp.rose else lp.wine.copy(alpha = 0.7f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            if (a.remind) Text("🔔", fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ================================================================== a line for her

private val NOTES = listOf(
    "ថ្ងៃណាដែលមានអូន គឺជាថ្ងៃដែលបងចូលចិត្តបំផុត។" to "Every day with you is my favourite day.",
    "ស្នេហារបស់យើង រាប់មិនអស់ ដូចផ្កាយលើមេឃ។" to "Our love, as countless as the stars above.",
    "ពីព្រះអាទិត្យរះ ដល់ព្រះចន្ទពេញបូណ៌មី បងនៅក្បែរអូនជានិច្ច។" to "From sunrise to full moon, I'm always beside you.",
    "ដូចផ្កាឈូកក្នុងស្រះ ស្នេហាយើងរីកស្អាតរាល់ថ្ងៃ។" to "Like a lotus on the pond, our love blooms anew each day.",
    "មួយថ្ងៃទៀតជាមួយអូន គឺជាអំណោយដ៏មានតម្លៃបំផុត។" to "One more day with you is the most precious gift.",
    "ដៃក្នុងដៃ ដើរទៅមុខជាមួយគ្នា រហូតដល់ចាស់ជរា។" to "Hand in hand, all the way to growing old together.",
    "បេះដូងពីរ ចង្វាក់តែមួយ។" to "Two hearts, one rhythm.",
)

@Composable
private fun LoveNote(today: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val (k, e) = NOTES[(Day.between(20000101, today) % NOTES.size).toInt()]
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(18.dp)) { Icons.heartFilled(this, lp.rose) }
        Spacer(Modifier.height(6.dp))
        Text(if (s.lang == Lang.KM) k else e, style = HeritageType.body.copy(fontSize = 16.sp), color = p.inkSoft, textAlign = TextAlign.Center)
    }
}

// ================================================================== Today tab card

/** A small rose card on the Today tab: days together and the next celebration. */
@Composable
fun TodayLoveCard(shell: ShellState, love: LoveData, today: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val a = love.featured
    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = lp.cardTint, onClick = { shell.goTo(com.pratitin.app.ui.Tab.LOVE) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val beat = rememberHeartbeat()
            Canvas(Modifier.size(46.dp).graphicsLayerScale { beat?.value ?: 1f }) {
                drawCircle(Brush.radialGradient(listOf(lp.glow.copy(alpha = 0.4f), Color.Transparent)), radius = size.width * 0.7f)
                drawPath(
                    com.pratitin.app.ui.components.heartPath(Offset(size.width * 0.1f, size.height * 0.14f), size.width * 0.8f),
                    Brush.linearGradient(listOf(lp.blush, lp.rose, lp.wine)),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (a == null) {
                    Text(if (km) "ស្នេហារបស់យើង" else "Our love", style = HeritageType.micro, color = lp.blush)
                    Text(if (km) "បញ្ចូលថ្ងៃរៀបការ ដើម្បីរាប់ថ្ងៃជាមួយគ្នា 💞" else "Add your wedding day to count your days together 💞", style = HeritageType.body, color = p.ink, maxLines = 2)
                } else {
                    val days = Day.between(a.date, today)
                    val next = remember(love.items, today) { love.items.minOf { LoveMath.next(it, today) } }
                    val dn = Day.between(today, next).toInt()
                    Text(love.couple(km), style = HeritageType.micro, color = lp.blush, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (days >= 0) (if (km) "${s.big(days)} ថ្ងៃជាមួយគ្នា" else "${s.big(days)} days together")
                        else (if (km) "${s.big(-days)} ថ្ងៃទៀត ដល់${a.name(true)}" else "${s.big(-days)} days to ${a.name(false).lowercase()}"),
                        style = HeritageType.heading, color = p.ink, maxLines = 1,
                    )
                    Text(
                        if (dn == 0) (if (km) "🎉 ថ្ងៃនេះជាថ្ងៃខួប!" else "🎉 It's an anniversary today!")
                        else (if (km) "ខួបបន្ទាប់ ${s.inDays(dn)}" else "Next anniversary ${s.inDays(dn)}"),
                        style = HeritageType.label, color = p.inkSoft, maxLines = 1,
                    )
                }
            }
            Canvas(Modifier.size(16.dp)) { Icons.chevronRight(this, p.inkSoft) }
        }
    }
}

private fun Modifier.graphicsLayerScale(k: () -> Float) = this.graphicsLayer { val v = k(); scaleX = v; scaleY = v }
