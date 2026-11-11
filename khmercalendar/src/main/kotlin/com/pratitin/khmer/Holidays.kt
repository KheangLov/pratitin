package com.pratitin.khmer

enum class HolidayKind {
    /** Paid public holiday in Cambodia. */
    KHMER_PUBLIC,
    /** Khmer Buddhist / cultural observance, not a day off (Meak Bochea, Sila days…). */
    KHMER_OBSERVANCE,
    /** Worldwide observance (Christmas, Chinese New Year, Valentine's…). */
    GLOBAL,
}

enum class HolidaySource {
    /** Listed in the Royal Government sub-decree for that year. */
    OFFICIAL,
    /** Derived from Chhankitek or the standing rule — awaiting the year's sub-decree. */
    CALCULATED,
}

data class Holiday(
    val id: String,
    val year: Int, val month: Int, val day: Int,
    val nameKm: String,
    val nameEn: String,
    val kind: HolidayKind,
    val source: HolidaySource,
    /** e.g. "Day 2 of 3" for multi-day festivals. */
    val dayOfSpan: Int = 1,
    val spanLength: Int = 1,
    val emoji: String = "",
) {
    val packed: Int get() = year * 10000 + month * 100 + day
}

/**
 * Cambodian public holidays + Khmer observances + global observances.
 *
 * Rules follow the annual sub-decrees (2024–2027). Since 2020 Meak Bochea is no
 * longer a day off and since Sept 2021 holidays falling on Sunday are not moved.
 * Years with a published sub-decree are marked [HolidaySource.OFFICIAL].
 */
object Holidays {

    /** Years whose sub-decree has been checked against this data set. */
    val OFFICIAL_YEARS = setOf(2024, 2025, 2026, 2027)

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, List<Holiday>>()

    fun forYear(year: Int): List<Holiday> = cache.getOrPut(year) { build(year) }

    private val byDateCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, List<Holiday>>>()

    /** Holidays keyed by packed date — memoised, so per-cell lookups while drawing are O(1). */
    fun byDate(year: Int): Map<Int, List<Holiday>> = byDateCache.getOrPut(year) { forYear(year).groupBy { it.packed } }

    private fun build(year: Int): List<Holiday> {
        val src = if (year in OFFICIAL_YEARS) HolidaySource.OFFICIAL else HolidaySource.CALCULATED
        val out = ArrayList<Holiday>(48)
        fun pub(id: String, m: Int, d: Int, km: String, en: String, emoji: String, i: Int = 1, n: Int = 1) =
            out.add(Holiday(id, year, m, d, km, en, HolidayKind.KHMER_PUBLIC, src, i, n, emoji))

        // ---------- fixed-date national holidays
        pub("intl_new_year", 1, 1, "ទិវាចូលឆ្នាំសកល", "International New Year's Day", "🎆")
        pub("victory_day", 1, 7, "ទិវាជ័យជម្នះលើរបបប្រល័យពូជសាសន៍", "Victory over Genocide Day", "🕊️")
        pub("women_day", 3, 8, "ទិវាអន្តរជាតិនារី", "International Women's Day", "🌸")
        pub("labour_day", 5, 1, "ទិវាពលកម្មអន្តរជាតិ", "International Labour Day", "🛠️")
        pub("king_birthday", 5, 14, "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម ព្រះមហាក្សត្រ", "King Norodom Sihamoni's Birthday", "👑")
        pub("queen_mother_birthday", 6, 18, "ព្រះរាជពិធីបុណ្យចម្រើនព្រះជន្ម ព្រះវររាជមាតា", "Queen Mother's Birthday", "👑")
        pub("constitution_day", 9, 24, "ទិវាប្រកាសរដ្ឋធម្មនុញ្ញ", "Constitution Day", "📜")
        pub("king_father_day", 10, 15, "ទិវាប្រារព្ធពិធីគោរពព្រះវិញ្ញាណក្ខន្ធ ព្រះករុណា ព្រះបរមរតនកោដ្ឋ", "Commemoration of King Father Norodom Sihanouk", "🪷")
        pub("coronation_day", 10, 29, "ព្រះរាជពិធីគ្រងព្រះបរមរាជសម្បត្តិ", "King's Coronation Day", "👑")
        pub("independence_day", 11, 9, "ពិធីបុណ្យឯករាជ្យជាតិ", "Independence Day", "🇰🇭")
        if (year >= 2024) pub("peace_day", 12, 29, "ទិវាសន្តិភាពនៅកម្ពុជា", "Peace Day in Cambodia", "🕊️")

        // ---------- Khmer New Year (Moha Songkran)
        val ny = KhmerCalendar.newYear(year)
        val nyDays = OFFICIAL_NEW_YEAR_DAYS[year] ?: 3
        val nyStart = OFFICIAL_NEW_YEAR_START[year]?.let { Chhankitek.toJdn(year, 4, it) }
            ?: Chhankitek.toJdn(ny.year, ny.month, ny.day)
        val nyNames = arrayOf("មហាសង្រ្កាន្ត", "វារៈវ័នបត", "វារៈឡើងស័ក", "វារៈឡើងស័ក")
        val nyEn = arrayOf("Moha Songkran", "Veareak Vanabat", "Veareak Lerng Sak", "Veareak Lerng Sak")
        for (i in 0 until nyDays) {
            val g = Chhankitek.fromJdnPacked(nyStart + i)
            // 3 days: Songkran · Vanabat · Lerng Sak — 4 days: Songkran · Vanabat · Vanabat · Lerng Sak
            val idx = when { i == 0 -> 0; i == nyDays - 1 -> 2; else -> 1 }
            pub("khmer_new_year", g / 100 % 100, g % 100, "បុណ្យចូលឆ្នាំខ្មែរ · ${nyNames[idx]}", "Khmer New Year · ${nyEn[idx]}", "🎉", i + 1, nyDays)
        }

        // ---------- lunar public holidays
        lunar(year, Chhankitek.M_PISAKH, 14)?.let { pub("visak_bochea", it / 100 % 100, it % 100, "ពិធីបុណ្យវិសាខបូជា", "Visak Bochea Day", "🪔") }
        lunar(year, Chhankitek.M_PISAKH, 18)?.let { pub("royal_ploughing", it / 100 % 100, it % 100, "ព្រះរាជពិធីច្រត់ព្រះនង្គ័ល", "Royal Ploughing Ceremony", "🐂") }
        lunar(year, 9, 28)?.let { start ->
            val jdn = Chhankitek.toJdn(year, start / 100 % 100, start % 100)
            for (i in 0..2) {
                val g = Chhankitek.fromJdnPacked(jdn + i)
                pub("pchum_ben", g / 100 % 100, g % 100, "ពិធីបុណ្យភ្ជុំបិណ្ឌ", "Pchum Ben Festival", "🍚", i + 1, 3)
            }
        }
        lunar(year, Chhankitek.M_KADEUK, 13)?.let { start ->
            val jdn = Chhankitek.toJdn(year, start / 100 % 100, start % 100)
            for (i in 0..2) {
                val g = Chhankitek.fromJdnPacked(jdn + i)
                pub("water_festival", g / 100 % 100, g % 100, "ព្រះរាជពិធីបុណ្យអុំទូក បណ្ដែតប្រទីប និងសំពះព្រះខែ អកអំបុក", "Water & Moon Festival (Bon Om Touk)", "🚣", i + 1, 3)
            }
        }

        // ---------- Khmer observances (not days off)
        fun obs(id: String, packed: Int?, km: String, en: String, emoji: String) {
            if (packed != null) out.add(Holiday(id, year, packed / 100 % 100, packed % 100, km, en, HolidayKind.KHMER_OBSERVANCE, HolidaySource.CALCULATED, emoji = emoji))
        }
        obs("meak_bochea", lunar(year, 2, 14), "ពិធីបុណ្យមាឃបូជា", "Meak Bochea", "🕯️")
        obs("pchum_ben_start", lunar(year, 9, 15), "កាន់បិណ្ឌ ១", "Kan Ben begins", "🍙")
        // In Adhikameas years Asadh is replaced by Pathamasadh/Tutiyasadh — Lent follows the second one.
        obs("chol_vassa", lunar(year, 7, 15) ?: lunar(year, Chhankitek.M_TUTIYASADH, 15),"បុណ្យចូលព្រះវស្សា", "Buddhist Lent begins", "🪷")
        obs("chenh_vassa", lunar(year, 10, 14), "បុណ្យចេញព្រះវស្សា", "End of Buddhist Lent", "🪷")
        obs("ok_ambok", lunar(year, Chhankitek.M_KADEUK, 14), "សំពះព្រះខែ អកអំបុក", "Ok Ambok · Moon Salutation", "🌕")

        // ---------- global observances
        fun glob(id: String, m: Int, d: Int, km: String, en: String, emoji: String) =
            out.add(Holiday(id, year, m, d, km, en, HolidayKind.GLOBAL, HolidaySource.CALCULATED, emoji = emoji))
        CHINESE_NEW_YEAR[year]?.let { glob("chinese_new_year", it / 100, it % 100, "ចូលឆ្នាំចិន", "Chinese / Lunar New Year", "🧧") }
        glob("valentine", 2, 14, "ទិវានៃក្ដីស្រឡាញ់", "Valentine's Day", "💝")
        easter(year).let { glob("easter", it / 100, it % 100, "បុណ្យអ៊ីស្ទ័រ", "Easter Sunday", "🐣") }
        glob("earth_day", 4, 22, "ទិវាផែនដី", "Earth Day", "🌍")
        nthWeekday(year, 5, 0, 2).let { glob("mothers_day", 5, it, "ទិវាម្ដាយ", "Mother's Day", "💐") }
        nthWeekday(year, 6, 0, 3).let { glob("fathers_day", 6, it, "ទិវាឪពុក", "Father's Day", "👔") }
        glob("children_day", 6, 1, "ទិវាកុមារអន្តរជាតិ", "International Children's Day", "🎈")
        glob("halloween", 10, 31, "បុណ្យហាឡូវីន", "Halloween", "🎃")
        glob("christmas", 12, 25, "បុណ្យណូអែល", "Christmas Day", "🎄")
        glob("new_years_eve", 12, 31, "ថ្ងៃចុងឆ្នាំ", "New Year's Eve", "🥂")

        out.sortWith(compareBy({ it.packed }, { it.kind.ordinal }))
        return out
    }

    /** First Gregorian date in [year] whose lunar position is month/dayNumber, packed yyyymmdd. */
    private fun lunar(year: Int, monthIndex: Int, dayNumber: Int): Int? {
        val target = monthIndex * 100 + dayNumber
        val start = Chhankitek.toJdn(year, 1, 1)
        val end = Chhankitek.toJdn(year + 1, 1, 1)
        for (j in start until end) {
            val g = Chhankitek.fromJdnPacked(j)
            if (KhmerCalendar.packedLunar(g / 10000, g / 100 % 100, g % 100) == target) return g
        }
        return null
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). Returns month*100+day. */
    fun easter(y: Int): Int {
        val a = y % 19; val b = y / 100; val c = y % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return month * 100 + day
    }

    /** Day of month of the n-th [weekday] (0 = Sunday) of a month. */
    fun nthWeekday(year: Int, month: Int, weekday: Int, n: Int): Int {
        val first = Chhankitek.dayOfWeek(Chhankitek.toJdn(year, month, 1))
        return 1 + Math.floorMod(weekday - first, 7) + 7 * (n - 1)
    }

    /** Sub-decree overrides where the official days differ from the computed Songkran. */
    private val OFFICIAL_NEW_YEAR_START = mapOf(2024 to 13, 2025 to 14, 2026 to 14, 2027 to 14)
    private val OFFICIAL_NEW_YEAR_DAYS = mapOf(2024 to 4, 2025 to 3, 2026 to 3, 2027 to 3)

    /** Chinese New Year (1st day of the 1st Chinese lunar month), month*100+day. */
    private val CHINESE_NEW_YEAR = mapOf(
        2020 to 125, 2021 to 212, 2022 to 201, 2023 to 122, 2024 to 210, 2025 to 129,
        2026 to 217, 2027 to 206, 2028 to 126, 2029 to 213, 2030 to 203, 2031 to 123,
        2032 to 211, 2033 to 131, 2034 to 219, 2035 to 208, 2036 to 128, 2037 to 215,
        2038 to 204, 2039 to 124, 2040 to 212,
    )
}
