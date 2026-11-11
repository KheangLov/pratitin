package com.pratitin.khmer

/**
 * A long break you can build by taking a few days of leave next to public holidays.
 * @property leaveDays packed yyyymmdd of the work days to request off
 */
data class BreakSuggestion(
    val startPacked: Int,
    val endPacked: Int,
    val totalDaysOff: Int,
    val leaveDays: List<Int>,
    val holidayNames: List<String>,
) {
    /** Days off gained per day of leave spent. */
    val efficiency: Float get() = if (leaveDays.isEmpty()) totalDaysOff.toFloat() else totalDaysOff / leaveDays.size.toFloat()
}

/**
 * "Take 2 days, get 6 off" — finds the best bridge days around Cambodian holidays.
 * Runs in O(365 × maxWindow) and needs no allocation per day beyond two arrays.
 */
object LeavePlanner {

    private val cache = java.util.concurrent.ConcurrentHashMap<Long, List<BreakSuggestion>>()

    /**
     * @param weekend days of week that are not worked (0 = Sunday). Cambodia's
     *                standard work week is Mon–Fri since 2025.
     * @param maxLeave maximum leave days per suggestion
     */
    fun suggest(
        year: Int,
        maxLeave: Int = 3,
        weekend: Set<Int> = setOf(0, 6),
        extraHolidays: Set<Int> = emptySet(),
    ): List<BreakSuggestion> {
        if (extraHolidays.isEmpty()) {
            val key = (year.toLong() shl 20) or (maxLeave.toLong() shl 8) or weekend.fold(0L) { acc, d -> acc or (1L shl d) }
            return cache.getOrPut(key) { compute(year, maxLeave, weekend, extraHolidays) }
        }
        return compute(year, maxLeave, weekend, extraHolidays)
    }

    private fun compute(year: Int, maxLeave: Int, weekend: Set<Int>, extraHolidays: Set<Int>): List<BreakSuggestion> {
        val start = Chhankitek.toJdn(year, 1, 1)
        val n = Chhankitek.toJdn(year + 1, 1, 1) - start
        val holidayNames = HashMap<Int, MutableList<String>>()
        Holidays.forYear(year).filter { it.kind == HolidayKind.KHMER_PUBLIC }.forEach {
            val idx = Chhankitek.toJdn(it.year, it.month, it.day) - start
            holidayNames.getOrPut(idx) { mutableListOf() }.add(it.nameEn.substringBefore(" ·"))
        }
        extraHolidays.forEach { p ->
            val idx = Chhankitek.toJdn(p / 10000, p / 100 % 100, p % 100) - start
            if (idx in 0 until n) holidayNames.getOrPut(idx) { mutableListOf() }.add("Personal")
        }
        val off = BooleanArray(n) { i -> holidayNames.containsKey(i) || Chhankitek.dayOfWeek(start + i) in weekend }

        // prefix sums of work days → O(1) work count for any window
        val work = IntArray(n + 1)
        for (i in 0 until n) work[i + 1] = work[i] + if (off[i]) 0 else 1

        val windows = HashMap<Long, IntArray>() // key (a,b) → [a, b, leaveCount]
        for (h in holidayNames.keys) {
            for (a0 in maxOf(0, h - 10)..h) for (b0 in h..minOf(n - 1, h + 10)) {
                val w = work[b0 + 1] - work[a0]
                if (w !in 1..maxLeave) continue
                // only windows whose edges are leave days or holidays/weekends — then grow over free days
                var a = a0; var b = b0
                while (a > 0 && off[a - 1]) a--
                while (b + 1 < n && off[b + 1]) b++
                val key = a.toLong() shl 32 or b.toLong()
                if (!windows.containsKey(key)) windows[key] = intArrayOf(a, b, w)
            }
        }
        val candidates = windows.values.toMutableList()
        // keep the best-efficiency, then longest, non-overlapping suggestions
        candidates.sortWith(compareByDescending<IntArray> { (it[1] - it[0] + 1).toFloat() / it[2] }.thenByDescending { it[1] - it[0] })
        val taken = BooleanArray(n)
        val chosen = ArrayList<BreakSuggestion>()
        for (c in candidates) {
            val len = c[1] - c[0] + 1
            if (len < 4 || len.toFloat() / c[2] < 2f) continue
            // a holiday that already falls on a weekend gives no bridge value
            if ((c[0]..c[1]).none { holidayNames.containsKey(it) && Chhankitek.dayOfWeek(start + it) !in weekend }) continue
            if ((c[0]..c[1]).any { taken[it] }) continue
            // must beat the break you'd get for free
            var freeLen = 0
            for (i in c[0]..c[1]) if (off[i]) freeLen++
            if (len <= freeLen) continue
            for (i in c[0]..c[1]) taken[i] = true
            val leave = (c[0]..c[1]).filter { !off[it] }.map { packed(start + it) }
            val names = (c[0]..c[1]).flatMap { holidayNames[it].orEmpty() }.distinct()
            chosen.add(BreakSuggestion(packed(start + c[0]), packed(start + c[1]), len, leave, names))
        }
        return chosen.sortedBy { it.startPacked }
    }

    private fun packed(jdn: Int) = Chhankitek.fromJdnPacked(jdn)
}
