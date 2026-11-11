package com.pratitin.khmer

/**
 * How a user event repeats. [LUNAR_YEARLY] repeats on the same Khmer lunar date every
 * year — the way ancestor memorial days, lunar birthdays and many ceremonies are kept.
 */
enum class Repeat { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, LUNAR_YEARLY }

object Recurrence {

    /** Does an event that starts on [start] (packed yyyymmdd) occur on [date]? */
    fun occursOn(start: Int, repeat: Repeat, date: Int, until: Int = 0): Boolean {
        if (date < start) return false
        if (until != 0 && date > until) return false
        val sy = start / 10000; val sm = start / 100 % 100; val sd = start % 100
        val y = date / 10000; val m = date / 100 % 100; val d = date % 100
        return when (repeat) {
            Repeat.NONE -> date == start
            Repeat.DAILY -> true
            Repeat.WEEKLY -> (Chhankitek.toJdn(y, m, d) - Chhankitek.toJdn(sy, sm, sd)) % 7 == 0
            Repeat.MONTHLY -> {
                // the 31st falls back to the month's last day
                val dim = KhmerCalendar.gregorianDaysInMonth(y, m)
                d == minOf(sd, dim)
            }
            Repeat.YEARLY -> {
                if (sm == 2 && sd == 29) m == 2 && d == (if (KhmerCalendar.gregorianDaysInMonth(y, 2) == 29) 29 else 28)
                else m == sm && d == sd
            }
            Repeat.LUNAR_YEARLY -> {
                val a = KhmerCalendar.packedLunar(sy, sm, sd)
                val b = KhmerCalendar.packedLunar(y, m, d)
                lunarMatches(a, b, y, m, d)
            }
        }
    }

    /**
     * Same lunar month & day. Leap-month years have Pathamasadh/Tutiyasadh instead of
     * Asadh: an Asadh date is kept in Tutiyasadh (the observance month), and a date from
     * either leap Asadh maps back to Asadh in normal years. A 30th day (១៥រោច) in a
     * 29-day month falls on the month's last day.
     */
    private fun lunarMatches(a: Int, b: Int, y: Int, m: Int, d: Int): Boolean {
        val am = a / 100; val ad = a % 100
        val bm = b / 100; val bd = b % 100
        val mapped = when {
            am == 7 && bm == Chhankitek.M_TUTIYASADH -> true
            (am == Chhankitek.M_PATHAMASADH || am == Chhankitek.M_TUTIYASADH) && bm == 7 -> true
            else -> am == bm
        }
        if (!mapped) return false
        if (ad == bd) return true
        // day 30 requested but this month only has 29 days → its last day
        if (ad == 29 && bd == 28) {
            val k = KhmerCalendar.fromGregorian(y, m, d)
            return KhmerCalendar.daysInMonth(k.monthIndex, k.beYear) == 29
        }
        return false
    }

    /** Next occurrence on or after [from], searching up to ~2 years ahead; 0 if none. */
    fun nextOccurrence(start: Int, repeat: Repeat, from: Int, until: Int = 0): Int {
        var jdn = Chhankitek.toJdn(maxOf(from, start) / 10000, maxOf(from, start) / 100 % 100, maxOf(from, start) % 100)
        repeat(800) {
            val p = Chhankitek.fromJdnPacked(jdn)
            if (until != 0 && p > until) return 0
            if (occursOn(start, repeat, p, until)) return p
            if (repeat == Repeat.NONE) return 0
            jdn++
        }
        return 0
    }
}
