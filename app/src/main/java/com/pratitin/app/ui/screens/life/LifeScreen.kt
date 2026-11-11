package com.pratitin.app.ui.screens.life

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.MoodEntry
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.ProgressRing
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.components.Stat
import com.pratitin.app.ui.heritage.drawLotus
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.theme.HeritagePalette
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeScreen(shell: ShellState) {
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val p = LocalPalette.current
    var picking by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        val initial = if (settings.birthDate != 0) Day.unpack(settings.birthDate) else java.time.LocalDate.of(1998, 1, 1)
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        shell.vm.updateSettings { it.copy(birthDate = Day.pack(d)) }
                    }
                    picking = false
                }) { Text(s.save, color = p.gold) }
            },
            colors = DatePickerDefaults.colors(containerColor = p.nightDeep),
        ) { DatePicker(state, colors = DatePickerDefaults.colors(containerColor = p.nightDeep, selectedDayContainerColor = p.gold, todayDateBorderColor = p.gold)) }
    }

    ScreenList(shell) {
        item(key = "header") {
            Column(Modifier.padding(top = 8.dp).enterStagger(0)) {
                Text(s.lifeTitle, style = HeritageType.display.copy(fontSize = 26.sp), color = p.ink)
                Text(s.lifeSubtitle, style = HeritageType.label, color = p.inkSoft)
            }
        }

        if (settings.birthDate == 0) {
            item(key = "cta") {
                GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(1), liquid = true, onClick = { picking = true }) {
                    val inf = rememberInfiniteTransition(label = "cta")
                    val bloom by inf.animateFloat(0.3f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        drawLotus(Offset(center.x, size.height * 0.95f), size.height * 0.9f, bloom, p.lotus, p.goldSoft)
                    }
                    Text(s.setBirthday, style = HeritageType.title, color = p.ink, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            return@ScreenList
        }

        val lived = (Day.between(settings.birthDate, today) / 7).toInt().coerceAtLeast(0)
        val total = settings.lifeYears * 52
        val ageYears = Day.unpack(settings.birthDate).until(Day.unpack(today)).years

        item(key = "stats") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(1), liquid = true, onClick = { picking = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(lived / total.toFloat(), Modifier.size(86.dp), stroke = 7.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.num((lived * 100 / total).coerceAtMost(100)) + "%", style = HeritageType.heading, color = p.ink)
                            Text(s.percentLived, style = HeritageType.micro, color = p.inkSoft)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat(s.num(ageYears), s.ageNow)
                        Stat(s.num(lived), s.weeksLived)
                        Stat(s.num((total - lived).coerceAtLeast(0)), s.weeksLeft)
                    }
                }
            }
        }

        item(key = "expectancy") {
            Row(Modifier.fillMaxWidth().enterStagger(2), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.lifeExpectancy, style = HeritageType.label, color = p.inkSoft, modifier = Modifier.weight(1f))
                listOf(70, 80, 90, 100).forEach { y ->
                    GlassChip(s.num(y), settings.lifeYears == y, shell.bg) { shell.vm.updateSettings { it.copy(lifeYears = y) } }
                }
            }
        }

        item(key = "grid") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(3), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                SectionTitle("${s.num(settings.lifeYears)} ${s.years} · ${s.num(total)} ${s.weeks}")
                Spacer(Modifier.height(10.dp))
                WeeksGrid(settings.birthDate, settings.lifeYears, lived, moods) { d, r -> shell.openDay(d, r) }
            }
        }
    }
}

/** Decade hues: childhood lotus → youth lacquer → adulthood gold → elder jade. */
private fun decadeColor(p: HeritagePalette, year: Int): Color {
    val stops = listOf(p.lotus, p.lacquer, p.gold, p.goldSoft, p.jade, p.sandstone)
    val t = (year / 10f).coerceIn(0f, stops.size - 1f)
    val i = t.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], t - i)
}

@Composable
private fun WeeksGrid(birth: Int, years: Int, lived: Int, moods: Map<Int, MoodEntry>, onTap: (Int, Rect?) -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val reduce = LocalReduceMotion.current
    val reveal = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(lived) { reveal.animateTo(1f, tween(1400, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    val inf = rememberInfiniteTransition(label = "now")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "pulse")

    // Average mood colour per life-week (only weeks that have entries).
    val weekMood: Map<Int, Color> = remember(moods, birth) {
        moods.values.groupBy { (Day.between(birth, it.date) / 7).toInt() }
            .mapValues { (_, list) ->
                val r = list.map { it.mood.color.red }.average().toFloat()
                val g = list.map { it.mood.color.green }.average().toFloat()
                val b = list.map { it.mood.color.blue }.average().toFloat()
                Color(r, g, b)
            }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val labelW = 22.dp
        val cell = (maxWidth - labelW) / 52
        Canvas(
            Modifier.fillMaxWidth().height(cell * years)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(birth, years) {
                    detectTapGestures { pos ->
                        val lw = labelW.toPx()
                        val c = (size.width - lw) / 52f
                        val col = ((pos.x - lw) / c).toInt()
                        val row = (pos.y / c).toInt()
                        if (col in 0..51 && row in 0 until years) {
                            val date = Day.plusDays(birth, (row * 52L + col) * 7)
                            onTap(date, Rect(origin.x + lw + col * c, origin.y + row * c, origin.x + lw + (col + 1) * c, origin.y + (row + 1) * c))
                        }
                    }
                },
        ) {
            val lw = labelW.toPx()
            val c = (size.width - lw) / 52f
            val gap = (c * 0.18f).coerceAtLeast(1f)
            val r = CornerRadius(c * 0.28f)
            val shown = (lived * reveal.value).toInt()
            for (row in 0 until years) {
                if (row % 10 == 0) {
                    val l = measurer.measure(s.num(row), HeritageType.micro.copy(color = p.inkSoft, fontSize = 8.sp))
                    drawText(l, topLeft = Offset(0f, row * c + (c - l.size.height) / 2))
                }
                val base = decadeColor(p, row)
                for (col in 0 until 52) {
                    val w = row * 52 + col
                    val tl = Offset(lw + col * c + gap / 2, row * c + gap / 2)
                    val sz = Size(c - gap, c - gap)
                    when {
                        w < shown -> drawRoundRect(weekMood[w] ?: base.copy(alpha = 0.85f), tl, sz, r)
                        w == lived -> drawRoundRect(p.goldSoft, tl, sz, r)
                        else -> drawRoundRect(p.inkFaint.copy(alpha = 0.12f), tl, sz, r)
                    }
                }
            }
            // decade separators
            for (row in 10 until years step 10) {
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent, p.gold.copy(alpha = 0.4f), Color.Transparent)), Offset(lw, row * c - 0.5f), Offset(size.width, row * c - 0.5f), 1f)
            }
        }
        // The "you are here" ripple lives in its own tiny layer so the 4 000-cell grid
        // above is never redrawn by the infinite animation.
        if (lived < years * 52) {
            Canvas(Modifier.fillMaxWidth().height(cell * years)) {
                val lw = labelW.toPx()
                val c = (size.width - lw) / 52f
                val cx = lw + (lived % 52) * c + c / 2
                val cy = (lived / 52) * c + c / 2
                val ph = pulse
                drawCircle(p.goldSoft.copy(alpha = 0.6f * (1f - ph)), c * (0.6f + 1.8f * ph), Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}
