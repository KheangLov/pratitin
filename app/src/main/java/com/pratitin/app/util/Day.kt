package com.pratitin.app.util

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Packed-date helpers. Dates are Ints (yyyymmdd) everywhere: zero allocation, trivially stable for Compose. */
object Day {
    /**
     * The Khmer calendar is defined for Cambodian civil time. We use the device zone so
     * "today" matches the user's wall clock, which is ICT for nearly all users.
     */
    val ZONE: ZoneId = ZoneId.systemDefault()

    fun today(): Int = pack(LocalDate.now(ZONE))
    fun pack(d: LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
    fun unpack(p: Int): LocalDate = LocalDate.of(p / 10000, p / 100 % 100, p % 100)
    fun year(p: Int) = p / 10000
    fun month(p: Int) = p / 100 % 100
    fun dom(p: Int) = p % 100
    fun plusDays(p: Int, n: Long) = pack(unpack(p).plusDays(n))
    fun between(a: Int, b: Int): Long = ChronoUnit.DAYS.between(unpack(a), unpack(b))
    /** 0 = Sunday */
    fun weekday(p: Int) = unpack(p).dayOfWeek.value % 7
}
