package com.pratitin.app.ui.screens.mood

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Lang
import com.pratitin.app.data.Mood
import com.pratitin.app.data.MoodEntry
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.components.Stat
import com.pratitin.app.ui.motion.Motion
import com.pratitin.app.ui.motion.enterStagger
import com.pratitin.app.ui.screens.MoodPicker
import com.pratitin.app.ui.screens.ScreenList
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import com.pratitin.khmer.KhmerCalendar

@Composable
fun MoodScreen(shell: ShellState) {
    val today by shell.vm.today.collectAsStateWithLifecycle()
    val moods by shell.vm.moods.collectAsStateWithLifecycle()
    val s = LocalStrings.current
    val p = LocalPalette.current
    var year by rememberSaveable { mutableIntStateOf(Day.year(today)) }

    val stats = remember(moods, today) { MoodStats.of(moods, today) }

    ScreenList(shell) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).enterStagger(0), verticalAlignment = Alignment.CenterVertically) {
                Text(s.moodTitle, style = HeritageType.display.copy(fontSize = 26.sp), color = p.ink, modifier = Modifier.weight(1f))
            }
        }
        item(key = "today") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(1)) {
                SectionTitle(s.howFeel)
                Spacer(Modifier.height(14.dp))
                MoodPicker(moods[today]?.mood, onPick = { shell.vm.setMood(today, it, moods[today]?.note.orEmpty()) })
            }
        }
        item(key = "stats") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(2)) {
                if (stats.count == 0) {
                    Text(s.noMoods, style = HeritageType.body, color = p.inkSoft)
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        Stat(s.num(stats.streak), s.streak(stats.streak).substringAfter(" ").ifBlank { s.days })
                        Stat(s.num(stats.count), s.days)
                        Stat(stats.top?.emoji ?: "—", stats.top?.let { if (s.lang == Lang.KM) it.km else it.en } ?: "")
                    }
                    Spacer(Modifier.height(14.dp))
                    DistributionBar(stats.distribution)
                    Spacer(Modifier.height(16.dp))
                    Text(s.moodTrend, style = HeritageType.micro, color = p.gold)
                    TrendLine(stats.trend, Modifier.fillMaxWidth().height(70.dp))
                    stats.silaInsight?.let { (sila, other) ->
                        Spacer(Modifier.height(12.dp))
                        val diff = ((sila - other) * 100).toInt()
                        Text(
                            if (s.lang == Lang.KM) "☸ នៅថ្ងៃសីល អារម្មណ៍របស់អ្នក${if (diff >= 0) "ល្អជាង" else "ទាបជាង"} ${s.num(kotlin.math.abs(diff))}%"
                            else "☸ On Sila days your mood is ${kotlin.math.abs(diff)}% ${if (diff >= 0) "higher" else "lower"} than usual",
                            style = HeritageType.label, color = p.inkSoft,
                        )
                    }
                }
            }
        }
        item(key = "pixelsHeader") {
            Row(Modifier.fillMaxWidth().enterStagger(3), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(s.yearInPixels, Modifier.weight(1f))
                GlassIconButton(shell.bg, onClick = { year-- }, size = 34.dp, icon = Icons.chevronLeft)
                Text(s.num(year), style = HeritageType.heading, color = p.gold, modifier = Modifier.padding(horizontal = 10.dp))
                GlassIconButton(shell.bg, onClick = { year++ }, size = 34.dp, icon = Icons.chevronRight)
            }
        }
        item(key = "pixels") {
            GlassCard(shell.bg, Modifier.fillMaxWidth().enterStagger(4)) {
                YearInPixels(year, today, moods) { d, r -> shell.openDay(d, r) }
            }
        }
    }
}

private class MoodStats(
    val count: Int, val streak: Int, val top: Mood?, val distribution: List<Pair<Mood, Int>>,
    val trend: List<Float?>, val silaInsight: Pair<Float, Float>?,
) {
    companion object {
        fun of(moods: Map<Int, MoodEntry>, today: Int): MoodStats {
            var streak = 0
            var d = if (moods.containsKey(today)) today else Day.plusDays(today, -1)
            while (moods.containsKey(d)) { streak++; d = Day.plusDays(d, -1) }
            val dist = Mood.entries.map { m -> m to moods.values.count { it.mood == m } }.filter { it.second > 0 }.sortedByDescending { it.second }
            val trend = (29 downTo 0).map { moods[Day.plusDays(today, -it.toLong())]?.mood?.score }
            var silaSum = 0f; var silaN = 0; var otherSum = 0f; var otherN = 0
            for (e in moods.values) {
                val k = khmerOf(e.date)
                if (k.isSilaDay(KhmerCalendar.daysInMonth(k.monthIndex, k.beYear))) { silaSum += e.mood.score; silaN++ } else { otherSum += e.mood.score; otherN++ }
            }
            val insight = if (silaN >= 3 && otherN >= 3) (silaSum / silaN) to (otherSum / otherN) else null
            return MoodStats(moods.size, streak, dist.firstOrNull()?.first, dist, trend, insight)
        }
    }
}

@Composable
private fun DistributionBar(dist: List<Pair<Mood, Int>>) {
    val total = dist.sumOf { it.second }.coerceAtLeast(1)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(dist) { grow.snapTo(0f); grow.animateTo(1f, Motion.gentle) }
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        var x = 0f
        val gap = 2.dp.toPx()
        dist.forEach { (m, n) ->
            val w = size.width * n / total * grow.value
            if (w > gap) drawRoundRect(m.color, Offset(x, 0f), Size(w - gap, size.height), CornerRadius(size.height / 2))
            x += w
        }
    }
}

@Composable
private fun TrendLine(values: List<Float?>, modifier: Modifier) {
    val p = LocalPalette.current
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, Motion.gentle) }
    Canvas(modifier) {
        val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(size.width * i / (values.size - 1), size.height * (1f - it) * 0.85f + size.height * 0.07f) } }
        if (pts.size < 2) return@Canvas
        // smooth Catmull-Rom → cubic Bézier
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts.getOrElse(i - 1) { pts[i] }; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts.getOrElse(i + 2) { p2 }
                cubicTo(p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6, p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6, p2.x, p2.y)
            }
        }
        val fill = Path().apply { addPath(path); lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close() }
        clipRectReveal(reveal.value) {
            drawPath(fill, Brush.verticalGradient(listOf(p.gold.copy(alpha = 0.35f), p.gold.copy(alpha = 0f))))
            drawPath(path, Brush.horizontalGradient(listOf(p.lotus, p.gold, p.jade)), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            pts.lastOrNull()?.let { drawCircle(p.goldSoft, 4.dp.toPx(), it) }
        }
    }
}

private inline fun DrawScope.clipRectReveal(f: Float, block: DrawScope.() -> Unit) {
    clipRect(right = size.width * f, block = block)
}

/** 12 columns (months) × 31 rows (days). One Canvas, zero child composables. */
@Composable
private fun YearInPixels(year: Int, today: Int, moods: Map<Int, MoodEntry>, onTap: (Int, Rect?) -> Unit) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val measurer = rememberTextMeasurer(cacheSize = 32)
    var origin by remember { mutableStateOf(Offset.Zero) }
    val labelW = 22.dp
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(0.62f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(year) {
                detectTapGestures { pos ->
                    val lw = labelW.toPx()
                    val top = 18.dp.toPx()
                    val cw = (size.width - lw) / 12f
                    val ch = (size.height - top) / 31f
                    val m = ((pos.x - lw) / cw).toInt() + 1
                    val d = ((pos.y - top) / ch).toInt() + 1
                    if (m in 1..12 && d in 1..KhmerCalendar.gregorianDaysInMonth(year, m)) {
                        val x = origin.x + lw + (m - 1) * cw
                        val y = origin.y + top + (d - 1) * ch
                        onTap(year * 10000 + m * 100 + d, Rect(x, y, x + cw, y + ch))
                    }
                }
            },
    ) {
        val lw = labelW.toPx()
        val top = 18.dp.toPx()
        val cw = (size.width - lw) / 12f
        val ch = (size.height - top) / 31f
        val gap = 2.dp.toPx()
        val r = CornerRadius(minOf(cw, ch) * 0.3f)
        for (m in 1..12) {
            val label = s.monthNames[m - 1].take(if (s.lang == Lang.KM) 2 else 1)
            val l = measurer.measure(label, HeritageType.micro.copy(color = p.inkSoft, fontSize = 9.sp))
            drawText(l, topLeft = Offset(lw + (m - 1) * cw + (cw - l.size.width) / 2, 0f))
            val dim = KhmerCalendar.gregorianDaysInMonth(year, m)
            for (d in 1..dim) {
                val date = year * 10000 + m * 100 + d
                val c = moods[date]?.mood?.color
                val tl = Offset(lw + (m - 1) * cw + gap / 2, top + (d - 1) * ch + gap / 2)
                val sz = Size(cw - gap, ch - gap)
                if (c != null) drawRoundRect(c, tl, sz, r)
                else drawRoundRect(p.inkFaint.copy(alpha = if (date > today) 0.06f else 0.14f), tl, sz, r)
                if (date == today) drawRoundRect(p.goldSoft, tl, sz, r, style = Stroke(1.5.dp.toPx()))
            }
        }
        for (d in listOf(1, 10, 20, 31)) {
            val l = measurer.measure(s.num(d), HeritageType.micro.copy(color = p.inkFaint, fontSize = 8.sp))
            drawText(l, topLeft = Offset(0f, top + (d - 1) * ch + (ch - l.size.height) / 2))
        }
    }
}

