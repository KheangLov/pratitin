package com.pratitin.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pratitin.app.MainActivity
import com.pratitin.app.R
import com.pratitin.app.data.EventKind
import com.pratitin.app.data.EventStore
import com.pratitin.app.data.Lang
import com.pratitin.app.data.Remind
import com.pratitin.app.data.Settings
import com.pratitin.app.data.SettingsStore
import com.pratitin.app.data.UserEvent
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plans and schedules reminders with AlarmManager.
 *
 * Only the next [MAX_ALARMS] reminders inside a [WINDOW_DAYS]-day window are armed; every
 * time one fires (and on app start, boot, time-zone change, or when events/settings
 * change) the plan is rebuilt, so the chain keeps rolling forward without a background
 * service and with near-zero battery cost. Exact alarms are used where the system allows
 * (calendar apps may hold USE_EXACT_ALARM), otherwise inexact while-idle alarms.
 */
object Reminders {
    const val CH_HOLIDAY = "holidays"
    const val CH_EVENT = "events"
    const val CH_BUDDHIST = "buddhist"
    const val CH_MOOD = "mood"
    const val EXTRA_DATE = "open_date"
    private const val MAX_ALARMS = 48
    private const val WINDOW_DAYS = 60L
    private const val PREFS = "reminders"
    private const val KEY_IDS = "armed_ids"

    data class Planned(val id: Int, val at: Long, val title: String, val text: String, val channel: String, val date: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val km = ctx.resources.configuration.locales[0].language == "km"
        fun ch(id: String, en: String, kh: String, imp: Int) = NotificationChannel(id, if (km) kh else en, imp)
        nm.createNotificationChannels(listOf(
            ch(CH_HOLIDAY, "Holidays & New Year", "ថ្ងៃបុណ្យ និងចូលឆ្នាំ", NotificationManager.IMPORTANCE_DEFAULT),
            ch(CH_EVENT, "Your events & birthdays", "ព្រឹត្តិការណ៍ និងខួបកំណើត", NotificationManager.IMPORTANCE_HIGH),
            ch(CH_BUDDHIST, "Sila & Buddhist days", "ថ្ងៃសីល និងពិធីបុណ្យ", NotificationManager.IMPORTANCE_LOW),
            ch(CH_MOOD, "Daily mood check-in", "កត់ត្រាអារម្មណ៍ប្រចាំថ្ងៃ", NotificationManager.IMPORTANCE_LOW),
        ))
    }

    /** Fire-and-forget re-plan (safe from any thread). */
    fun rescheduleAsync(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { reschedule(app) }
    }

    suspend fun reschedule(ctx: Context) {
        val settings = SettingsStore.current(ctx)
        val events = EventStore.readBlocking(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // cancel what we armed last time
        prefs.getStringSet(KEY_IDS, emptySet())!!.forEach { idStr ->
            val id = idStr.toIntOrNull() ?: return@forEach
            PendingIntent.getBroadcast(ctx, id, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { am.cancel(it); it.cancel() }
        }
        if (!settings.notify) { prefs.edit().putStringSet(KEY_IDS, emptySet()).apply(); return }

        val now = System.currentTimeMillis()
        val plan = plan(settings, events, LocalDateTime.now()).filter { it.at > now + 5_000 }.sortedBy { it.at }.take(MAX_ALARMS)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        plan.forEach { r ->
            val intent = Intent(ctx, ReminderReceiver::class.java).apply {
                putExtra("title", r.title); putExtra("text", r.text); putExtra("channel", r.channel)
                putExtra("id", r.id); putExtra(EXTRA_DATE, r.date)
            }
            val pi = PendingIntent.getBroadcast(ctx, r.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.at, pi)
        }
        prefs.edit().putStringSet(KEY_IDS, plan.map { it.id.toString() }.toSet()).apply()
    }

    /** Pure planning: everything that should notify in the next window. */
    fun plan(s: Settings, events: List<UserEvent>, now: LocalDateTime): List<Planned> {
        val km = s.lang == Lang.KM
        val zone = ZoneId.systemDefault()
        val out = ArrayList<Planned>()
        val today = now.toLocalDate()
        fun millis(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
        fun pk(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
        fun lunar(d: LocalDate): String {
            val k = KhmerCalendar.fromGregorian(d.year, d.monthValue, d.dayOfMonth)
            return if (km) KhmerNames.digits("${k.day}") + "${KhmerNames.PHASE[k.phase.ordinal]} ខែ${KhmerNames.LUNAR_MONTHS[k.monthIndex]}"
            else "${k.day} ${if (k.dayNumber < 15) "Kaet" else "Roch"} · ${KhmerNames.LUNAR_MONTHS_LATIN[k.monthIndex]}"
        }

        // ---- holidays (first day of each festival only)
        val seen = HashSet<String>()
        for (y in today.year..today.plusDays(WINDOW_DAYS).year) for (h in Holidays.forYear(y)) {
            val d = LocalDate.of(h.year, h.month, h.day)
            if (d.isBefore(today) || d.isAfter(today.plusDays(WINDOW_DAYS))) continue
            if (h.id == "khmer_new_year") continue // handled with the exact Songkran moment below
            val want = when (h.kind) {
                HolidayKind.KHMER_PUBLIC -> s.notifyHolidays
                HolidayKind.GLOBAL -> s.notifyGlobal && s.showGlobal
                HolidayKind.KHMER_OBSERVANCE -> s.notifySila
            }
            if (!want || !seen.add("${h.id}-${h.year}")) continue
            val name = (if (km) h.nameKm else h.nameEn).substringBefore(" · ")
            val days = if (h.spanLength > 1) (if (km) " · ឈប់ ${KhmerNames.digits(h.spanLength)} ថ្ងៃ" else " · ${h.spanLength} days off") else ""
            val channel = if (h.kind == HolidayKind.KHMER_OBSERVANCE) CH_BUDDHIST else CH_HOLIDAY
            val (at, title) = if (s.holidayEve && h.kind != HolidayKind.KHMER_OBSERVANCE)
                millis(d.minusDays(1), 19, 0) to (if (km) "${h.emoji} ស្អែក៖ $name" else "${h.emoji} Tomorrow: $name")
            else millis(d, 7, 0) to (if (km) "${h.emoji} ថ្ងៃនេះ៖ $name" else "${h.emoji} Today: $name")
            out += Planned(id("h", h.id, pk(d)), at, title, lunar(d) + days, channel, pk(d))
        }

        // ---- Khmer New Year: evening before + the exact moment the Tevoda descends
        if (s.notifyNewYear) for (y in today.year..today.year + 1) {
            val ny = KhmerCalendar.newYear(y)
            val d = LocalDate.of(ny.year, ny.month, ny.day)
            val animal = KhmerCalendar.fromGregorian(ny.year, ny.month, ny.day, 23, 59).animalYear
            val an = if (km) "ឆ្នាំ${KhmerNames.ANIMALS[animal]}" else "Year of the ${KhmerNames.ANIMALS_LATIN[animal]}"
            val t = "%02d:%02d".format(ny.hour, ny.minute).let { if (km) KhmerNames.digits(it) else it }
            out += Planned(id("ny-eve", "", pk(d)), millis(d.minusDays(1), 19, 0),
                if (km) "🎉 ស្អែកចូលឆ្នាំខ្មែរ" else "🎉 Khmer New Year tomorrow",
                if (km) "ទេវតាចុះម៉ោង $t · $an" else "Tevoda descends at $t · $an", CH_HOLIDAY, pk(d))
            out += Planned(id("ny", "", pk(d)), millis(d, ny.hour, ny.minute),
                if (km) "🪷 សួស្ដីឆ្នាំថ្មី · $an" else "🪷 Happy Khmer New Year · $an",
                if (km) "ទេវតាឆ្នាំថ្មីបានចុះមកដល់ហើយ" else "The new year's Tevoda has descended", CH_HOLIDAY, pk(d))
        }

        // ---- Sila days (06:00)
        if (s.notifySila) for (i in 0..WINDOW_DAYS) {
            val d = today.plusDays(i)
            val k = KhmerCalendar.fromGregorian(d.year, d.monthValue, d.dayOfMonth)
            if (!k.isSilaDay(KhmerCalendar.daysInMonth(k.monthIndex, k.beYear))) continue
            out += Planned(id("sila", "", pk(d)), millis(d, 6, 0),
                if (km) "☸ ថ្ងៃនេះជាថ្ងៃសីល" else "☸ Today is a Sila day", lunar(d), CH_BUDDHIST, pk(d))
        }

        // ---- your birthday (from Life settings)
        if (s.birthDate != 0) for (y in today.year..today.year + 1) {
            val bm = s.birthDate / 100 % 100; val bd = s.birthDate % 100
            val d = runCatching { LocalDate.of(y, bm, bd) }.getOrElse { LocalDate.of(y, 2, 28) }
            if (d.isBefore(today)) continue
            val age = y - s.birthDate / 10000
            out += Planned(id("bday", "", pk(d)), millis(d, 8, 0),
                if (km) "🎂 រីករាយថ្ងៃកំណើត!" else "🎂 Happy birthday!",
                if (km) "អាយុ ${KhmerNames.digits(age)} ឆ្នាំ · សូមឲ្យឆ្នាំនេះពោរពេញដោយសេចក្ដីសុខ" else "You're $age today — wishing you a wonderful year", CH_EVENT, pk(d))
        }

        // ---- user events, birthdays, notes with a reminder
        for (e in events) {
            if (e.remind == Remind.NONE) continue
            for (i in -1..WINDOW_DAYS) {
                val d = today.plusDays(i)
                if (!e.occursOn(pk(d))) continue
                val at = when {
                    e.remind == Remind.DAY_BEFORE -> if (e.allDay) millis(d.minusDays(1), 19, 0) else millis(d, e.startMin / 60, e.startMin % 60) - 86_400_000L
                    e.allDay -> millis(d, 8, 0)
                    else -> millis(d, e.startMin / 60, e.startMin % 60) - when (e.remind) {
                        Remind.MIN_10 -> 10 * 60_000L; Remind.MIN_30 -> 30 * 60_000L; Remind.HOUR_1 -> 60 * 60_000L; else -> 0L
                    }
                }
                val title = "${e.kind.emoji} ${e.title.ifBlank { if (km) e.kind.km else e.kind.en }}"
                val whenTxt = buildString {
                    if (e.remind == Remind.DAY_BEFORE) append(if (km) "ស្អែក" else "Tomorrow") else append(if (km) "ថ្ងៃនេះ" else "Today")
                    if (!e.allDay) append(" · ").append(e.timeLabel().let { if (km) KhmerNames.digits(it) else it })
                    if (e.note.isNotBlank()) append(" · ").append(e.note.take(80))
                }
                out += Planned(id("ev", e.id.toString(), pk(d)), at, title, whenTxt, CH_EVENT, pk(d))
            }
        }

        // ---- daily mood check-in (21:00) — only the next two, the chain re-arms itself
        if (s.notifyMood) for (i in 0..1) {
            val d = today.plusDays(i.toLong())
            out += Planned(id("mood", "", pk(d)), millis(d, 21, 0),
                if (km) "🪷 ថ្ងៃនេះយ៉ាងម៉េចដែរ?" else "🪷 How was your day?",
                if (km) "ចុចដើម្បីកត់ត្រាអារម្មណ៍ថ្ងៃនេះ" else "Tap to log today's mood", CH_MOOD, pk(d))
        }
        return out
    }

    private fun id(kind: String, key: String, date: Int) = ("$kind|$key|$date").hashCode()

    fun canPost(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, title: String, text: String, channel: String, date: Int) {
        if (!canPost(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DATE, date)
        }
        val pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_lotus)
            .setColor(0xFFE8B653.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setCategory(if (channel == CH_EVENT) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_REMINDER)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(id, n) } catch (_: SecurityException) { }
    }
}

/** Fires a planned reminder, then rolls the schedule forward. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(
            context,
            intent.getIntExtra("id", 0),
            intent.getStringExtra("title").orEmpty(),
            intent.getStringExtra("text").orEmpty(),
            intent.getStringExtra("channel") ?: Reminders.CH_EVENT,
            intent.getIntExtra(Reminders.EXTRA_DATE, 0),
        )
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { Reminders.reschedule(context.applicationContext) } finally { pending.finish() }
        }
    }
}

/** Re-arms reminders after reboot, app update, or clock / time-zone changes. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Reminders.createChannels(context)
                Reminders.reschedule(context.applicationContext)
            } finally { pending.finish() }
        }
    }
}
