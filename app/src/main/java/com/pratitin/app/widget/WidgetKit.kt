package com.pratitin.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import com.pratitin.app.MainActivity
import com.pratitin.app.data.EventStore
import com.pratitin.app.data.Lang
import com.pratitin.app.data.LoveData
import com.pratitin.app.data.LoveStore
import com.pratitin.app.data.Mood
import com.pratitin.app.data.Settings
import com.pratitin.app.data.SettingsStore
import com.pratitin.app.data.UserEvent
import com.pratitin.app.notify.Reminders
import com.pratitin.app.ui.EnglishStrings
import com.pratitin.app.ui.KhmerStrings
import com.pratitin.app.ui.Strings
import com.pratitin.app.util.Day
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

// ------------------------------------------------------------------ palette

internal fun cp(c: Color): GlanceColor = ColorProvider(c, c)

internal object WC {
    val ink = cp(Color(0xFFFFF7E8))
    val soft = cp(Color(0xBFFFF7E8))
    val faint = cp(Color(0x66FFF7E8))
    val gold = cp(Color(0xFFE8B653))
    val goldSoft = cp(Color(0xFFF6D894))
    val night = cp(Color(0xFF14091C))
    val holiday = cp(Color(0xFFFF7A6A))
    val global = cp(Color(0xFF7FB7FF))
    val jade = cp(Color(0xFF5CC9AE))
    val rose = cp(Color(0xFFFF8FB1))
    val blush = cp(Color(0xFFFFC2D2))
    val roseGold = cp(Color(0xFFF4C2A1))
}

// ------------------------------------------------------------------ data every widget needs

internal class WData(
    val settings: Settings,
    val s: Strings,
    val today: Int,
) {
    val km get() = s.lang == Lang.KM
    fun t(kmText: String, en: String) = if (km) kmText else en
}

internal suspend fun loadData(context: Context): WData {
    val st = runCatching { SettingsStore.current(context) }.getOrDefault(Settings())
    val s = if (st.lang == Lang.KM) KhmerStrings.copy(khmerDigits = st.khmerNumerals) else EnglishStrings
    return WData(st, s, Day.today())
}

internal fun readMoods(context: Context): Map<Int, Mood> = runCatching {
    val out = HashMap<Int, Mood>()
    String(AtomicFile(File(context.filesDir, "moods.tsv")).readFully(), Charsets.UTF_8).lineSequence().forEach { line ->
        val parts = line.split('\t', limit = 3)
        if (parts.size >= 2) {
            val d = parts[0].toIntOrNull() ?: return@forEach
            Mood.entries.getOrNull(parts[1].toIntOrNull() ?: -1)?.let { out[d] = it }
        }
    }
    out
}.getOrDefault(emptyMap())

/** Your events plus anniversaries, as the calendar shows them. */
internal fun readEvents(context: Context, km: Boolean): Pair<List<UserEvent>, LoveData> {
    val love = LoveStore.readBlocking(context)
    return (EventStore.readBlocking(context) + love.asEvents(km)) to love
}

// ------------------------------------------------------------------ actions

/** Opens the app; [key] keeps each widget button's PendingIntent distinct. */
internal fun open(context: Context, key: String, tab: String? = null, date: Int = 0): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = Uri.parse("pratitin://widget/$key")
            if (tab != null) putExtra(Reminders.EXTRA_TAB, tab)
            if (date != 0) putExtra(Reminders.EXTRA_DATE, date)
        },
    )

// ------------------------------------------------------------------ building blocks

@Composable
internal fun T(
    text: String, size: TextUnit, color: GlanceColor = WC.ink, bold: Boolean = false,
    modifier: GlanceModifier = GlanceModifier, maxLines: Int = 1, align: TextAlign = TextAlign.Start,
) = Text(
    text, modifier = modifier, maxLines = maxLines,
    style = TextStyle(color = color, fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, textAlign = align),
)

@Composable
internal fun Frame(bg: Int, click: Action?, pad: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().background(ImageProvider(bg)).cornerRadius(24.dp).padding(pad)
            .let { if (click != null) it.clickable(click) else it },
        content = content,
    )
}

internal fun moonEmoji(dayNumber: Int, full: Boolean) = when {
    full -> "🌕"
    dayNumber < 4 -> "🌒"; dayNumber < 11 -> "🌓"; dayNumber < 15 -> "🌔"
    dayNumber < 19 -> "🌖"; dayNumber < 26 -> "🌗"; else -> "🌘"
}

internal fun weekStart(today: Int, monday: Boolean): Int {
    val wd = Day.weekday(today) // 0 = Sunday
    val back = if (monday) (wd + 6) % 7 else wd
    return Day.plusDays(today, -back.toLong())
}

internal fun weekdayIndex(col: Int, monday: Boolean) = if (monday) (col + 1) % 7 else col

// ------------------------------------------------------------------ keeping widgets fresh

object WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun refreshAll(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { TodayWidget().updateAll(app) }
            runCatching { LoveWidget().updateAll(app) }
            runCatching { MonthWidget().updateAll(app) }
            runCatching { MoodWidget().updateAll(app) }
            runCatching { HolidaysWidget().updateAll(app) }
        }
        scheduleMidnight(app)
    }

    /** One inexact alarm just after midnight rolls every widget to the new day. */
    fun scheduleMidnight(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 7_701, Intent(context, WidgetTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = LocalDate.now().plusDays(1).atStartOfDay(Day.ZONE).toInstant().toEpochMilli() + 60_000
        am.setWindow(AlarmManager.RTC, at, 10 * 60_000L, pi)
    }
}

class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetRefresher.refreshAll(context)
    }
}
