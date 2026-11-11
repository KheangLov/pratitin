package com.pratitin.khmer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KhmerCalendarTest {

    private fun lunar(y: Int, m: Int, d: Int) = KhmerCalendar.fromGregorian(y, m, d).let {
        "${it.day}${if (it.phase == MoonPhase.WAXING) "K" else "R"}-${KhmerNames.LUNAR_MONTHS_LATIN[it.monthIndex]}"
    }

    @Test fun fullFormatMatchesReference() {
        // Spot checks taken from momentkh's 2000-date reference set (all 2000 pass in CI).
        assertEquals("ថ្ងៃអាទិត្យ ៩កើត ខែចេត្រ ឆ្នាំកុរ ឯកស័ក ពុទ្ធសករាជ ២៤៤៣", KhmerCalendar.fromGregorian(1900, 4, 8, 4, 54).format())
        assertEquals("ថ្ងៃអាទិត្យ ១៤រោច ខែពិសាខ ឆ្នាំជូត ទោស័ក ពុទ្ធសករាជ ២៤៤៤", KhmerCalendar.fromGregorian(1900, 5, 27, 2, 23).format())
        assertEquals("ថ្ងៃអាទិត្យ ១០កើត ខែកត្ដិក ឆ្នាំជូត ទោស័ក ពុទ្ធសករាជ ២៧៤៤", KhmerCalendar.fromGregorian(2200, 11, 16, 11, 51).format())
    }

    @Test fun lunarHolidaysMatchOfficialSubDecrees() {
        val truths = mapOf(
            Triple(2023, 5, 4) to "15K-Pisakh", Triple(2024, 5, 22) to "15K-Pisakh",
            Triple(2025, 5, 11) to "15K-Pisakh", Triple(2026, 5, 1) to "15K-Pisakh", Triple(2027, 5, 20) to "15K-Pisakh",
            Triple(2024, 5, 26) to "4R-Pisakh", Triple(2025, 5, 15) to "4R-Pisakh",
            Triple(2026, 5, 5) to "4R-Pisakh", Triple(2027, 5, 24) to "4R-Pisakh",
            Triple(2023, 10, 14) to "15R-Phatrabot", Triple(2024, 10, 2) to "15R-Phatrabot",
            Triple(2025, 9, 22) to "15R-Phatrabot", Triple(2026, 10, 11) to "15R-Phatrabot", Triple(2027, 9, 30) to "15R-Phatrabot",
            Triple(2023, 11, 27) to "15K-Kadeuk", Triple(2024, 11, 15) to "15K-Kadeuk",
            Triple(2025, 11, 5) to "15K-Kadeuk", Triple(2026, 11, 24) to "15K-Kadeuk", Triple(2027, 11, 13) to "15K-Kadeuk",
        )
        truths.forEach { (d, want) -> assertEquals(want, lunar(d.first, d.second, d.third), "$d") }
    }

    @Test fun mohaSongkranMoments() {
        fun ny(y: Int) = KhmerCalendar.newYear(y).let { "%02d-%02d %02d:%02d".format(it.month, it.day, it.hour, it.minute) }
        assertEquals("04-13 22:17", ny(2024))
        assertEquals("04-14 04:48", ny(2025))
        assertEquals("04-14 10:48", ny(2026))
        assertEquals("04-14 16:48", ny(2027))
    }

    @Test fun leapYearKinds() {
        assertEquals(1, KhmerCalendar.leapKind(2570)) // 2026: Adhikameas (two Asadh months)
        assertEquals(2, KhmerCalendar.leapKind(2569)) // 2025: Chantrathimeas (30-day Jesth)
    }

    @Test fun publicHolidayCountsMatchSubDecrees() {
        fun count(y: Int) = Holidays.forYear(y).filter { it.kind == HolidayKind.KHMER_PUBLIC }.map { it.packed }.distinct().size
        assertEquals(21, count(2026)) // Visak Bochea shares 1 May with Labour Day
        assertEquals(22, count(2027))
    }

    @Test fun roundTrip() {
        for (y in 2000..2040) for (m in 1..12) {
            val k = KhmerCalendar.fromGregorian(y, m, 10)
            assertEquals(y * 10000 + m * 100 + 10, KhmerCalendar.toGregorianPacked(k.day, k.phase, k.monthIndex, k.beYear))
        }
    }

    @Test fun plannerFindsKhmerNewYearBridge() {
        val s = LeavePlanner.suggest(2026)
        assertTrue(s.any { it.startPacked <= 20260414 && it.endPacked >= 20260419 })
    }
}
