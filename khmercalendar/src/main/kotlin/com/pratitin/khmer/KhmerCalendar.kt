package com.pratitin.khmer

import com.pratitin.khmer.Chhankitek.M_CHEIT
import com.pratitin.khmer.Chhankitek.M_PISAKH
import java.util.concurrent.ConcurrentHashMap

/** Moon phase: កើត (waxing) or រោច (waning). */
enum class MoonPhase { WAXING, WANING }

/**
 * A fully-resolved Khmer lunar date.
 *
 * @property day lunar day inside the phase, 1‥15
 * @property monthIndex 0‥13 (12 = Pathamasadh, 13 = Tutiyasadh — only in leap-month years)
 * @property beYear ពុទ្ធសករាជ — changes on ១រោច ខែពិសាខ
 * @property jsYear ចុល្លសករាជ — changes on ថ្ងៃឡើងស័ក
 * @property animalYear 0 = Rat … 11 = Pig — changes at the exact Moha Songkran moment
 * @property sak 0‥9 (ស័ក)
 */
data class KhmerDate(
    val day: Int,
    val phase: MoonPhase,
    val monthIndex: Int,
    val beYear: Int,
    val jsYear: Int,
    val animalYear: Int,
    val sak: Int,
    val dayOfWeek: Int,
) {
    /** 0‥29 position in the lunar month. */
    val dayNumber: Int get() = if (phase == MoonPhase.WAXING) day - 1 else 14 + day

    val isFullMoon: Boolean get() = phase == MoonPhase.WAXING && day == 15
    val isNewMoonEve: Boolean get() = phase == MoonPhase.WANING && day >= 14

    /** Buddhist precept day ថ្ងៃសីល: 8 & 15 កើត, 8 រោច, and the last day of the month. */
    fun isSilaDay(daysInMonth: Int): Boolean =
        (phase == MoonPhase.WAXING && (day == 8 || day == 15)) ||
            (phase == MoonPhase.WANING && (day == 8 || dayNumber == daysInMonth - 1))

    /** Illuminated fraction 0‥1 — approximated from the lunar day for moon-glyph rendering. */
    val illumination: Float
        get() {
            val t = dayNumber / 29.5f
            return ((1 - kotlin.math.cos(2 * Math.PI * t)) / 2).toFloat()
        }

    fun format(numerals: Boolean = true): String {
        val s = "ថ្ងៃ${KhmerNames.WEEKDAYS[dayOfWeek]} $day${KhmerNames.PHASE[phase.ordinal]} " +
            "ខែ${KhmerNames.LUNAR_MONTHS[monthIndex]} ឆ្នាំ${KhmerNames.ANIMALS[animalYear]} " +
            "${KhmerNames.SAK[sak]} ពុទ្ធសករាជ $beYear"
        return if (numerals) KhmerNames.digits(s) else s
    }

    /** Compact form for calendar cells, e.g. "៩ក" / "១៤រ". */
    fun shortLabel(): String = KhmerNames.digits("$day") + KhmerNames.PHASE_SHORT[phase.ordinal]
}

/** Moha Songkran — the exact moment the Tevoda descends. */
data class KhmerNewYear(
    val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int,
    /** Number of celebration days: 3 or 4 (4 when there are two Vanabat days). */
    val celebrationDays: Int,
    /** Gregorian JDN of ថ្ងៃឡើងស័ក */
    val lerngSakJdn: Int,
)

/**
 * Public, thread-safe API. All heavy work is memoised per Gregorian year so
 * rendering a month grid is a handful of array lookups.
 */
object KhmerCalendar {

    private const val EPOCH_YEAR = 1900
    private val EPOCH_JDN = Chhankitek.toJdn(EPOCH_YEAR, 1, 1)

    private val leapCache = ConcurrentHashMap<Int, Int>()
    private val pisakhaCache = ConcurrentHashMap<Int, Int>()
    private val newYearCache = ConcurrentHashMap<Int, KhmerNewYear>()
    private val newYearMinuteCache = ConcurrentHashMap<Int, Long>()
    private val yearTable = ConcurrentHashMap<Int, IntArray>()

    /** Values confirmed by the Ministry of Cults & Religion that differ from pure calculation. */
    private val NEW_YEAR_OVERRIDES = mapOf(
        1879 to intArrayOf(1879, 4, 12, 11, 36),
        1897 to intArrayOf(1897, 4, 13, 2, 0),
        2011 to intArrayOf(2011, 4, 14, 13, 12),
        2012 to intArrayOf(2012, 4, 14, 19, 11),
        2013 to intArrayOf(2013, 4, 14, 2, 12),
        2014 to intArrayOf(2014, 4, 14, 8, 7),
        2015 to intArrayOf(2015, 4, 14, 14, 2),
        2024 to intArrayOf(2024, 4, 13, 22, 17),
    )

    private fun leapType(be: Int) = leapCache.getOrPut(be) { Chhankitek.leapType(be) }
    private fun daysInYear(be: Int) = when (leapType(be)) { 1 -> 384; 2 -> 355; else -> 354 }
    fun daysInMonth(month: Int, be: Int): Int {
        val lt = leapType(be)
        if (month == Chhankitek.M_JESTH && lt == 2) return 30
        if (month >= 12) return if (lt == 1) 30 else 0
        return if (month % 2 == 0) 29 else 30
    }
    private fun nextMonth(month: Int, be: Int): Int =
        if (month == Chhankitek.M_JESTH && leapType(be) == 1) Chhankitek.M_PATHAMASADH
        else Chhankitek.nextMonth(month, be)

    private fun maybeBe(year: Int, month: Int) = if (month <= 4) year + 543 else year + 544

    /** Lunar month index + day number (0‥29) for a JDN. Packed: month*100 + dayNumber. */
    private fun lunarPosition(year: Int, month: Int, jdn: Int): Int {
        var epoch = EPOCH_JDN
        var khMonth = 1 // Boss
        var diff = jdn - epoch
        if (diff > 0) {
            while (true) {
                val g = Chhankitek.fromJdnPacked(epoch)
                val dy = daysInYear(maybeBe(g / 10000 + 1, g / 100 % 100))
                if (diff > dy) { diff -= dy; epoch += dy } else break
            }
        } else {
            while (diff < 0) {
                val g = Chhankitek.fromJdnPacked(epoch)
                val dy = daysInYear(maybeBe(g / 10000, g / 100 % 100))
                diff += dy; epoch -= dy
            }
        }
        while (diff > 0) {
            val g = Chhankitek.fromJdnPacked(epoch)
            val be = maybeBe(g / 10000, g / 100 % 100)
            val dm = daysInMonth(khMonth, be)
            if (diff > dm) { diff -= dm; epoch += dm; khMonth = nextMonth(khMonth, be) } else break
        }
        val finalBe = maybeBe(year, month)
        val total = daysInMonth(khMonth, finalBe)
        if (diff >= total) {
            diff %= total
            khMonth = nextMonth(khMonth, finalBe)
        }
        return khMonth * 100 + diff
    }

    /**
     * Per-Gregorian-year table of packed lunar positions. One background pass per
     * year (≈365 cheap iterations) and every later lookup is O(1).
     */
    private fun table(year: Int): IntArray = yearTable.getOrPut(year) {
        val start = Chhankitek.toJdn(year, 1, 1)
        val end = Chhankitek.toJdn(year + 1, 1, 1)
        IntArray(end - start) { i ->
            val p = Chhankitek.fromJdnPacked(start + i)
            lunarPosition(year, p / 100 % 100, start + i)
        }
    }

    private fun position(year: Int, month: Int, day: Int): Int {
        val jdn = Chhankitek.toJdn(year, month, day)
        return table(year)[jdn - Chhankitek.toJdn(year, 1, 1)]
    }

    /** JDN of ១រោច ខែពិសាខ in [year] — the BE-year transition. */
    fun pisakhaBocheaTransitionJdn(year: Int): Int = pisakhaCache.getOrPut(year) {
        for (m in 4..6) {
            val dim = gregorianDaysInMonth(year, m)
            for (d in 1..dim) {
                val p = position(year, m, d)
                if (p / 100 == M_PISAKH && p % 100 == 15) return@getOrPut Chhankitek.toJdn(year, m, d)
            }
        }
        Chhankitek.toJdn(year, 4, 15)
    }

    /** Exact Moha Songkran for a Gregorian year. */
    fun newYear(year: Int): KhmerNewYear = newYearCache.getOrPut(year) {
        val js = Chhankitek.adToJs(year)
        val t = Chhankitek.newYearTime(js)
        var hour = t[0]
        var minute = t[1]
        val firstAngsar = t[2]

        var bodithey = Chhankitek.boditheyJs(js)
        if (Chhankitek.isAdhikameas(js - 1) && Chhankitek.isChantrathimeas(js - 1)) {
            bodithey = (bodithey + 1) % 30
        }
        val lsDay = if (bodithey >= 6) bodithey - 1 else bodithey
        val lsMonth = if (bodithey >= 6) M_CHEIT else M_PISAKH
        val nyDays = if (firstAngsar == 0) 4 else 3

        val epochJdn = Chhankitek.toJdn(year, 4, 17)
        val p = lunarPosition(year, 4, epochJdn)
        val diffFromEpoch = ((p / 100 - 4) * 29 + p % 100) - ((lsMonth - 4) * 29 + lsDay)
        var nyJdn = epochJdn - (diffFromEpoch + nyDays - 1)

        NEW_YEAR_OVERRIDES[year]?.let { o ->
            hour = o[3]; minute = o[4]
            nyJdn = Chhankitek.toJdn(o[0], o[1], o[2])
        }
        val g = Chhankitek.fromJdnPacked(nyJdn)
        KhmerNewYear(g / 10000, g / 100 % 100, g % 100, hour, minute, nyDays, nyJdn + nyDays - 1)
    }

    private fun newYearMinute(year: Int): Long = newYearMinuteCache.getOrPut(year) {
        val ny = newYear(year)
        Chhankitek.toJdn(ny.year, ny.month, ny.day) * 1440L + ny.hour * 60 + ny.minute
    }

    /**
     * Convert a Gregorian civil date/time (Cambodia local time) to Khmer.
     * Time of day only matters on Songkran / Pisakha boundary days.
     */
    fun fromGregorian(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): KhmerDate {
        require(month in 1..12 && day in 1..gregorianDaysInMonth(year, month)) { "Invalid date $year-$month-$day" }
        val jdn = Chhankitek.toJdn(year, month, day)
        val pos = position(year, month, day)
        val khMonth = pos / 100
        val dayNum = pos % 100
        val phase = if (dayNum < 15) MoonPhase.WAXING else MoonPhase.WANING
        val lDay = if (dayNum < 15) dayNum + 1 else dayNum - 14

        val transition = pisakhaBocheaTransitionJdn(year)
        val be = if (jdn >= transition) year + 544 else year + 543
        var js = Chhankitek.beToJs(be)
        var animal = Math.floorMod(be + 4, 12)

        val inputMinute = jdn * 1440L + hour * 60 + minute
        val transitionMinute = transition * 1440L
        if (inputMinute >= newYearMinute(year) && inputMinute <= transitionMinute) animal = (animal + 1) % 12
        if (inputMinute >= newYear(year).lerngSakJdn * 1440L && inputMinute <= transitionMinute) js++

        return KhmerDate(lDay, phase, khMonth, be, js, animal, Math.floorMod(js, 10), Chhankitek.dayOfWeek(jdn))
    }

    /** Lightweight lookup for grids: month index & day number only, no allocation. */
    fun packedLunar(year: Int, month: Int, day: Int): Int = position(year, month, day)

    /** Inverse conversion: first Gregorian date matching the Khmer date, as packed yyyymmdd, or -1. */
    fun toGregorianPacked(day: Int, phase: MoonPhase, monthIndex: Int, beYear: Int): Int {
        val target = monthIndex * 100 + (if (phase == MoonPhase.WAXING) day - 1 else day + 14)
        val approx = beYear - 544
        for (y in approx - 1..approx + 1) {
            val tbl = table(y)
            val start = Chhankitek.toJdn(y, 1, 1)
            for (i in tbl.indices) {
                if (tbl[i] == target) {
                    val g = Chhankitek.fromJdnPacked(start + i)
                    val k = fromGregorian(g / 10000, g / 100 % 100, g % 100)
                    if (k.beYear == beYear) return g
                }
            }
        }
        return -1
    }

    /** Leap kind of the lunar year that a BE year denotes. */
    fun leapKind(beYear: Int): Int = leapType(beYear)

    /** Warm caches for a range of years on a background thread. */
    fun prefetch(years: IntRange) { for (y in years) { table(y); newYear(y); pisakhaBocheaTransitionJdn(y) } }

    fun gregorianDaysInMonth(year: Int, month: Int): Int = when (month) {
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}
