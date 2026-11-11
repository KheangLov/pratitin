package com.pratitin.khmer

/**
 * Chhankitek (ចន្ទគតិ) — the traditional Khmer luni-solar reckoning.
 *
 * Pure-Kotlin, dependency-free port of the algorithm used by the MIT-licensed
 * `momentkh` library (© 2018 ThyrithSor), itself based on the traditional
 * Soriyatra Lerng Sak rules. Every intermediate value keeps its Khmer name
 * (Aharkun, Avoman, Bodithey, Kromthupul…) so it can be checked against the
 * printed calculation tables.
 *
 * All functions here are pure and allocation-free; caching lives in [KhmerCalendar].
 */
internal object Chhankitek {

    // ---------------------------------------------------------------- eras
    fun adToJs(ad: Int) = ad - 638
    fun beToJs(be: Int) = be - 1182

    // ------------------------------------------------- Julian Day Numbers
    fun toJdn(year: Int, month: Int, day: Int): Int {
        val a = (14 - month) / 12
        val y = year + 4800 - a
        val m = month + 12 * a - 3
        return day + (153 * m + 2) / 5 + 365 * y + Math.floorDiv(y, 4) -
            Math.floorDiv(y, 100) + Math.floorDiv(y, 400) - 32045
    }

    /** Returns packed year*10000 + month*100 + day to stay allocation-free. */
    fun fromJdnPacked(jdn: Int): Int {
        val a = jdn + 32044
        val b = (4 * a + 3) / 146097
        val c = a - (146097 * b) / 4
        val d = (4 * c + 3) / 1461
        val e = c - (1461 * d) / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = 100 * b + d - 4800 + m / 10
        return year * 10000 + month * 100 + day
    }

    /** 0 = Sunday … 6 = Saturday */
    fun dayOfWeek(jdn: Int) = (jdn + 1) % 7

    // ------------------------------------------------ BE-year reckoning
    private fun aharkun(be: Int) = ((be.toLong() * 292207 + 499) / 800).toInt() + 4
    private fun aharkunMod(be: Int) = ((be.toLong() * 292207 + 499) % 800).toInt()
    private fun kromthupul(be: Int) = 800 - aharkunMod(be)
    private fun avoman(be: Int) = (aharkun(be) * 11 + 25) % 692
    private fun bodithey(be: Int): Int {
        val a = aharkun(be)
        return ((a * 11 + 25) / 692 + a + 29) % 30
    }

    private fun isSolarLeap(be: Int) = kromthupul(be) <= 207

    private fun isLeapDayByCalc(be: Int): Boolean {
        val av = avoman(be)
        return when {
            av == 0 && avoman(be - 1) == 137 -> true
            isSolarLeap(be) -> av < 127
            av == 137 && avoman(be + 1) == 0 -> false
            av < 138 -> true
            else -> false
        }
    }

    private fun isLeapMonth(be: Int): Boolean {
        val b = bodithey(be)
        val bn = bodithey(be + 1)
        if (b == 25 && bn == 5) return false
        return (b == 24 && bn == 6) || b >= 25 || b < 6
    }

    /** 0 = normal (354 d), 1 = Adhikameas leap month (384 d), 2 = Chantrathimeas leap day (355 d). */
    fun leapType(be: Int): Int {
        if (isLeapMonth(be)) return LEAP_MONTH
        if (isLeapDayByCalc(be)) return LEAP_DAY
        if (isLeapMonth(be - 1)) {
            var prev = be - 1
            while (true) {
                if (isLeapDayByCalc(prev)) return LEAP_DAY
                prev -= 1
                if (!isLeapMonth(prev)) return NORMAL
            }
        }
        return NORMAL
    }

    fun daysInMonth(month: Int, be: Int): Int {
        val lt = leapType(be)
        if (month == M_JESTH && lt == LEAP_DAY) return 30
        if (month == M_PATHAMASADH || month == M_TUTIYASADH) return if (lt == LEAP_MONTH) 30 else 0
        return if (month % 2 == 0) 29 else 30
    }

    fun daysInYear(be: Int) = when (leapType(be)) {
        LEAP_MONTH -> 384
        LEAP_DAY -> 355
        else -> 354
    }

    fun nextMonth(month: Int, be: Int): Int = when {
        month == M_JESTH && leapType(be) == LEAP_MONTH -> M_PATHAMASADH
        month == M_KADEUK -> M_MIGASIR
        month == M_PATHAMASADH -> M_TUTIYASADH
        month == M_TUTIYASADH -> M_SRAP
        else -> month + 1
    }

    // ------------------------------------------- JS-year (New Year) reckoning
    private fun aharkunJs(js: Int) = ((js.toLong() * 292207 + 373) / 800).toInt() + 1
    private fun avomanJs(js: Int) = (aharkunJs(js) * 11 + 650) % 692
    private fun kromthupulJs(js: Int) = 800 - ((292207L * js + 373) % 800).toInt()
    fun boditheyJs(js: Int): Int {
        val a = aharkunJs(js)
        return (a + (11 * a + 650) / 692) % 30
    }

    fun isAdhikameas(js: Int): Boolean {
        val b = boditheyJs(js)
        val bn = boditheyJs(js + 1)
        if (b == 24 && bn == 6) return true
        if (b == 25 && bn == 5) return false
        return b > 24 || b < 6
    }

    fun isChantrathimeas(js: Int): Boolean {
        val av = avomanJs(js)
        val avN = avomanJs(js + 1)
        val avP = avomanJs(js - 1)
        val solarLeap = kromthupulJs(js) <= 207
        if (av == 0 && avP == 137) return true
        if (solarLeap) return av < 127
        if (av == 137 && avN == 0) return false
        if (av < 138) return true
        return false
    }

    private fun has366Days(js: Int) = kromthupulJs(js) <= 207

    /** Sun inauguration (Sotin) — returns packed angsar*100 + libda (both < 60). */
    fun sunAngsarLibda(js: Int, sotin: Int): Int {
        val r2 = 800 * sotin + kromthupulJs(js - 1)
        val reasey = r2 / 24350
        val r3 = r2 % 24350
        val angsar = r3 / 811
        val r4 = r3 % 811
        val libda = r4 / 14 - 3
        val sunAvg = 1800 * reasey + 60 * angsar + libda

        val s1 = 1800 * 2 + 60 * 20
        var leftOver = sunAvg - s1
        if (sunAvg < s1) leftOver += 1800 * 12
        val kaen = leftOver / 1800

        val rs = when (kaen) {
            0, 1, 2 -> kaen
            3, 4, 5 -> 1800 * 6 - leftOver
            6, 7, 8 -> leftOver - 1800 * 6
            else -> (1800 * 11 + 60 * 29 + 60) - leftOver
        }
        val lReasey = Math.floorDiv(rs, 1800)
        val lAngsar = Math.floorDiv(Math.floorMod(rs, 1800), 60)
        val lLibda = Math.floorMod(rs, 60)

        val khan: Int
        val pouichalip: Int
        if (lAngsar >= 15) {
            khan = 2 * lReasey + 1
            pouichalip = 60 * (lAngsar - 15) + lLibda
        } else {
            khan = 2 * lReasey
            pouichalip = 60 * lAngsar + lLibda
        }
        val (mult, chhaya) = if (khan in 0..5) CHHAYA[khan] else (0 to 134)
        val phol = Math.floorDiv(pouichalip * mult, 900) + chhaya
        val inaug = if (kaen <= 5) sunAvg - phol else sunAvg + phol
        val ang = Math.floorDiv(Math.floorMod(inaug, 1800), 60)
        val lib = Math.floorMod(inaug, 60)
        return ang * 100 + lib
    }

    private val CHHAYA = arrayOf(35 to 0, 32 to 35, 27 to 67, 22 to 94, 13 to 116, 5 to 129)

    /** Returns [hour, minute, angsarOfFirstSotin]. */
    fun newYearTime(js: Int): IntArray {
        val sotins = if (has366Days(js - 1)) intArrayOf(363, 364, 365, 366) else intArrayOf(362, 363, 364, 365)
        val first = sunAngsarLibda(js, sotins[0])
        var h = 0
        var m = 0
        for (s in sotins) {
            val p = sunAngsarLibda(js, s)
            if (p / 100 == 0) {
                val minutes = 24 * 60 - (p % 100) * 24
                h = Math.floorMod(Math.floorDiv(minutes, 60), 24)
                m = Math.floorMod(minutes, 60)
                break
            }
        }
        return intArrayOf(h, m, first / 100)
    }

    const val NORMAL = 0
    const val LEAP_MONTH = 1
    const val LEAP_DAY = 2

    const val M_MIGASIR = 0
    const val M_CHEIT = 4
    const val M_PISAKH = 5
    const val M_JESTH = 6
    const val M_SRAP = 8
    const val M_KADEUK = 11
    const val M_PATHAMASADH = 12
    const val M_TUTIYASADH = 13
}
