package com.pratitin.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Immutable
import com.pratitin.app.util.Day
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.Recurrence
import com.pratitin.khmer.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.ChronoUnit

// ------------------------------------------------------------------ model

enum class AnnivKind(val emoji: String, val km: String, val en: String, val annKm: String, val annEn: String) {
    WEDDING("💍", "អាពាហ៍ពិពាហ៍", "Wedding", "ខួបអាពាហ៍ពិពាហ៍", "Wedding Anniversary"),
    TOGETHER("💞", "ចាប់ផ្ដើមស្រឡាញ់គ្នា", "Together since", "ខួបស្នេហា", "Love Anniversary"),
    FIRST_MET("✨", "ជួបគ្នាដំបូង", "First met", "ខួបថ្ងៃជួបគ្នាដំបូង", "Anniversary of the day we met"),
    FIRST_DATE("🌹", "ណាត់ជួបដំបូង", "First date", "ខួបណាត់ជួបដំបូង", "First Date Anniversary"),
    PROPOSAL("🥂", "ថ្ងៃសុំរៀបការ", "Proposal", "ខួបថ្ងៃសុំរៀបការ", "Proposal Anniversary"),
    ENGAGEMENT("💎", "ភ្ជាប់ពាក្យ", "Engagement", "ខួបភ្ជាប់ពាក្យ", "Engagement Anniversary"),
    FIRST_KISS("💋", "ថើបដំបូង", "First kiss", "ខួបថើបដំបូង", "First Kiss Anniversary"),
    PARTNER_BIRTHDAY("🎂", "ថ្ងៃកំណើតនាង", "Her birthday", "ថ្ងៃកំណើតនាង", "Her Birthday"),
    CUSTOM("❤️", "ខួបផ្សេងៗ", "Other", "ខួប", "Anniversary"),
}

@Immutable
data class Anniversary(
    val id: Long,
    val kind: AnnivKind = AnnivKind.WEDDING,
    val title: String = "",
    /** packed yyyymmdd of the original day */
    val date: Int,
    /** celebrate on the same Khmer lunar date each year instead of the solar date */
    val lunar: Boolean = false,
    val remind: Boolean = true,
    /** 0 = only on the day; otherwise also N days before */
    val daysBefore: Int = 1,
    val milestones: Boolean = true,
    /** monthly "monthiversary" notes */
    val monthly: Boolean = false,
    val note: String = "",
) {
    fun name(km: Boolean) = title.ifBlank { if (km) kind.km else kind.en }
    fun annName(km: Boolean) = title.ifBlank { if (km) kind.annKm else kind.annEn }
    val repeat: Repeat get() = if (lunar) Repeat.LUNAR_YEARLY else Repeat.YEARLY
}

@Immutable
data class LoveProfile(
    val you: String = "",
    val partner: String = "",
    val featuredId: Long = 0,
    val sound: Boolean = true,
    val music: Boolean = true,
    /** key of [com.pratitin.app.audio.LoveSound.Song] */
    val song: String = "SERENADE",
)

@Immutable
data class LoveData(val profile: LoveProfile = LoveProfile(), val items: List<Anniversary> = emptyList()) {
    /** The anniversary the hero counts from: chosen one, else the wedding, else the oldest. */
    val featured: Anniversary?
        get() = items.firstOrNull { it.id == profile.featuredId }
            ?: items.firstOrNull { it.kind == AnnivKind.WEDDING }
            ?: items.filter { it.kind != AnnivKind.PARTNER_BIRTHDAY }.minByOrNull { it.date }
            ?: items.minByOrNull { it.date }

    fun couple(km: Boolean): String {
        val a = profile.you.ifBlank { if (km) "បង" else "Me" }
        val b = profile.partner.ifBlank { if (km) "អូន" else "My love" }
        return "$a ♥ $b"
    }

    /** Anniversaries as read-only entries for the main calendar. */
    fun asEvents(km: Boolean): List<UserEvent> = items.map { a ->
        UserEvent(
            id = -a.id, kind = EventKind.ANNIVERSARY, title = a.annName(km), note = a.note, start = a.date,
            allDay = true, repeat = a.repeat, color = 0, remind = Remind.NONE,
        )
    }
}

// ------------------------------------------------------------------ store

/** Same storage model as events: in-memory state, one JSON file written atomically and debounced. */
@OptIn(FlowPreview::class)
class LoveStore(context: Context, private val scope: CoroutineScope, private val onChange: () -> Unit) {
    private val file = AtomicFile(File(context.filesDir, "love.json"))
    private val _data = MutableStateFlow(LoveData())
    val data: StateFlow<LoveData> = _data.asStateFlow()
    @Volatile private var loaded = false

    init {
        scope.launch(Dispatchers.IO) {
            val disk = parse(file)
            val cur = _data.value
            _data.value = if (cur.items.isEmpty() && cur.profile == LoveProfile()) disk
            else cur.copy(items = (cur.items + disk.items).distinctBy { it.id })
            loaded = true
            _data.drop(1).debounce(300).onEach { save(it); onChange() }.launchIn(scope)
        }
    }

    fun upsert(a: Anniversary) { _data.value = _data.value.let { d -> d.copy(items = d.items.filter { it.id != a.id } + a) } }
    fun delete(id: Long) { _data.value = _data.value.let { d -> d.copy(items = d.items.filter { it.id != id }) } }
    fun updateProfile(t: (LoveProfile) -> LoveProfile) { _data.value = _data.value.let { it.copy(profile = t(it.profile)) } }

    private suspend fun save(d: LoveData) = withContext(Dispatchers.IO) {
        val o = JSONObject()
        o.put("you", d.profile.you); o.put("partner", d.profile.partner); o.put("featured", d.profile.featuredId)
        o.put("sound", d.profile.sound); o.put("music", d.profile.music); o.put("song", d.profile.song)
        val arr = JSONArray()
        d.items.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("kind", a.kind.name); put("title", a.title); put("date", a.date); put("lunar", a.lunar)
                put("remind", a.remind); put("before", a.daysBefore); put("milestones", a.milestones); put("monthly", a.monthly); put("note", a.note)
            })
        }
        o.put("items", arr)
        val out = file.startWrite()
        try { out.write(o.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) } catch (t: Exception) { file.failWrite(out) }
    }

    companion object {
        fun readBlocking(context: Context): LoveData = parse(AtomicFile(File(context.filesDir, "love.json")))

        private fun parse(f: AtomicFile): LoveData = runCatching {
            val o = JSONObject(String(f.readFully(), Charsets.UTF_8))
            val arr = o.optJSONArray("items") ?: JSONArray()
            LoveData(
                profile = LoveProfile(
                    you = o.optString("you"), partner = o.optString("partner"), featuredId = o.optLong("featured", 0),
                    sound = o.optBoolean("sound", true), music = o.optBoolean("music", true),
                    song = o.optString("song", "SERENADE").ifBlank { "SERENADE" },
                ),
                items = List(arr.length()) { i ->
                    val a = arr.getJSONObject(i)
                    Anniversary(
                        id = a.getLong("id"),
                        kind = runCatching { AnnivKind.valueOf(a.optString("kind")) }.getOrDefault(AnnivKind.CUSTOM),
                        title = a.optString("title"), date = a.getInt("date"), lunar = a.optBoolean("lunar", false),
                        remind = a.optBoolean("remind", true), daysBefore = a.optInt("before", 1),
                        milestones = a.optBoolean("milestones", true), monthly = a.optBoolean("monthly", false), note = a.optString("note"),
                    )
                },
            )
        }.getOrDefault(LoveData())
    }
}

// ------------------------------------------------------------------ the counting

/** Everything the Love tab counts. Pure functions on packed dates. */
object LoveMath {

    @Immutable
    data class Elapsed(
        val days: Long, val weeks: Long, val weekDays: Int,
        val years: Int, val months: Int, val monthDays: Int, val totalMonths: Long,
    )

    fun elapsed(from: Int, to: Int): Elapsed {
        val a = Day.unpack(minOf(from, to)); val b = Day.unpack(maxOf(from, to))
        val days = ChronoUnit.DAYS.between(a, b)
        val p = Period.between(a, b)
        return Elapsed(days, days / 7, (days % 7).toInt(), p.years, p.months, p.days, p.toTotalMonths())
    }

    fun occursOn(a: Anniversary, d: Int): Boolean = Recurrence.occursOn(a.date, a.repeat, d)

    /** The next celebration on or after [today]; the day itself if it's still in the future. */
    fun next(a: Anniversary, today: Int): Int {
        if (a.date >= today) return a.date
        if (!a.lunar) {
            val sm = Day.month(a.date); val sd = Day.dom(a.date)
            for (y in Day.year(today)..Day.year(today) + 1) {
                val dd = if (sm == 2 && sd == 29 && KhmerCalendar.gregorianDaysInMonth(y, 2) == 28) 28 else sd
                val c = y * 10000 + sm * 100 + dd
                if (c >= today) return c
            }
        }
        var d = today
        repeat(800) { if (occursOn(a, d)) return d; d = Day.plusDays(d, 1) }
        return Day.plusDays(today, 365)
    }

    /** The most recent celebration on or before [today] (or the original day). */
    fun previous(a: Anniversary, today: Int): Int {
        if (a.date >= today) return a.date
        val n = next(a, Day.plusDays(today, 1))
        if (!a.lunar) {
            val ny = Day.year(n) - 1
            val sm = Day.month(a.date); val sd = Day.dom(a.date)
            val dd = if (sm == 2 && sd == 29 && KhmerCalendar.gregorianDaysInMonth(ny, 2) == 28) 28 else sd
            return maxOf(a.date, ny * 10000 + sm * 100 + dd)
        }
        var d = today
        repeat(800) { if (occursOn(a, d)) return d; d = Day.plusDays(d, -1); if (d < a.date) return a.date }
        return a.date
    }

    /** Which anniversary an occurrence is (1st, 2nd…). 0 = the day itself. */
    fun number(a: Anniversary, occ: Int): Int {
        if (!a.lunar) return Day.year(occ) - Day.year(a.date)
        // lunar dates drift across Jan 1, so count by elapsed solar years, rounded
        return Math.round(Day.between(a.date, occ) / 365.2422).toInt()
    }

    /** 0‥1 progress from the last celebration to the next. */
    fun yearProgress(a: Anniversary, today: Int): Float {
        if (a.date >= today) return 0f
        val prev = previous(a, today); val nxt = next(a, today)
        if (nxt == today) return 1f
        val span = Day.between(prev, nxt).coerceAtLeast(1)
        return (Day.between(prev, today).toFloat() / span).coerceIn(0f, 1f)
    }

    /** Same day of month ("monthiversary"), excluding the yearly date itself. */
    fun isMonthiversary(a: Anniversary, d: Int): Boolean =
        d > a.date && Recurrence.occursOn(a.date, Repeat.MONTHLY, d) && !Recurrence.occursOn(a.date, Repeat.YEARLY, d)

    // ---- milestones

    enum class MUnit { DAYS, WEEKS, HOURS, MINUTES }

    @Immutable
    data class Milestone(val unit: MUnit, val value: Long, val date: Int)

    private val REPDIGITS = setOf(1111L, 2222L, 3333L, 4444L, 5555L, 6666L, 7777L, 8888L, 9999L, 11111L, 12345L)

    fun isDayMilestone(n: Long): Boolean =
        n > 0 && ((n <= 1000 && n % 100 == 0L) || n % 500 == 0L || n in REPDIGITS)

    /** All milestones of the relationship, in date order, up to ~60 years. */
    fun milestones(start: Int): List<Milestone> {
        val out = ArrayList<Milestone>()
        for (n in 100L..22_000L step 100) if (isDayMilestone(n)) out += Milestone(MUnit.DAYS, n, Day.plusDays(start, n))
        REPDIGITS.forEach { if (it % 100 != 0L) out += Milestone(MUnit.DAYS, it, Day.plusDays(start, it)) }
        listOf(100L, 500L, 1000L, 2000L).forEach { out += Milestone(MUnit.WEEKS, it, Day.plusDays(start, it * 7)) }
        listOf(10_000L, 50_000L, 100_000L, 250_000L).forEach { out += Milestone(MUnit.HOURS, it, Day.plusDays(start, ceilDiv(it, 24))) }
        listOf(1_000_000L, 5_000_000L, 10_000_000L).forEach { out += Milestone(MUnit.MINUTES, it, Day.plusDays(start, ceilDiv(it, 1440))) }
        return out.sortedWith(compareBy({ it.date }, { it.unit.ordinal }))
    }

    fun upcomingMilestones(start: Int, today: Int, count: Int) = milestones(start).filter { it.date >= today }.take(count)
    fun lastMilestone(start: Int, today: Int) = milestones(start).lastOrNull { it.date < today }
    fun milestonesOn(start: Int, d: Int) = milestones(start).filter { it.date == d }

    private fun ceilDiv(a: Long, b: Long) = (a + b - 1) / b

    // ---- fun facts

    fun fullMoons(days: Long): Long = (days / 29.530588).toLong()

    /** How many Khmer New Year moments (Tevoda descending) you have welcomed together. */
    fun khmerNewYears(start: Int, now: LocalDateTime): Int {
        val s = Day.unpack(start).atStartOfDay()
        if (!now.isAfter(s)) return 0
        var n = 0
        for (y in s.year..now.year) {
            val ny = KhmerCalendar.newYear(y)
            val t = LocalDateTime.of(ny.year, ny.month, ny.day, ny.hour, ny.minute)
            if (t.isAfter(s) && !t.isAfter(now)) n++
        }
        return n
    }

    // ---- words

    fun ordinalEn(n: Int): String {
        val suf = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suf"
    }

    /** Traditional wedding anniversary gifts (km, en, emoji). */
    fun gift(years: Int): Triple<String, String, String>? = when (years) {
        1 -> Triple("ក្រដាស", "Paper", "📜"); 2 -> Triple("កប្បាស", "Cotton", "🧺"); 3 -> Triple("ស្បែក", "Leather", "👜")
        4 -> Triple("ផ្លែឈើ និងផ្កា", "Fruit & flowers", "💐"); 5 -> Triple("ឈើ", "Wood", "🪵"); 6 -> Triple("ស្ករ", "Sugar", "🍬")
        7 -> Triple("រោមចៀម", "Wool", "🧶"); 8 -> Triple("សំរិទ្ធ", "Bronze", "🥉"); 9 -> Triple("ដីដុត", "Pottery", "🏺")
        10 -> Triple("សំណប៉ាហាំង", "Tin", "🥫"); 11 -> Triple("ដែកថែប", "Steel", "⚙️"); 12 -> Triple("សូត្រ", "Silk", "🎀")
        13 -> Triple("ក្រណាត់ចាក់", "Lace", "🪡"); 14 -> Triple("ភ្លុក", "Ivory", "🤍"); 15 -> Triple("គ្រីស្តាល់", "Crystal", "🔮")
        20 -> Triple("ប៉សឺឡែន", "China", "🍽️"); 25 -> Triple("ប្រាក់", "Silver", "🥈"); 30 -> Triple("គុជ", "Pearl", "🦪")
        35 -> Triple("ផ្កាថ្មសមុទ្រ", "Coral", "🪸"); 40 -> Triple("ត្បូងទទឹម", "Ruby", "❤️‍🔥"); 45 -> Triple("ត្បូងកណ្ដៀង", "Sapphire", "💙")
        50 -> Triple("មាស", "Gold", "🥇"); 55 -> Triple("ត្បូងមរកត", "Emerald", "💚"); 60 -> Triple("ពេជ្រ", "Diamond", "💎")
        else -> null
    }

    fun fmt(n: Long, khmer: Boolean): String = "%,d".format(n).let { if (khmer) com.pratitin.khmer.KhmerNames.digits(it) else it }
}

/** Start date as LocalDate (convenience for the UI). */
val Anniversary.localDate: LocalDate get() = Day.unpack(date)
