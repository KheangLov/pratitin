package com.pratitin.app.data

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Seven moods, each tied to a colour from the heritage palette. */
enum class Mood(val emoji: String, val km: String, val en: String, val color: Color) {
    RADIANT("😄", "រីករាយ", "Radiant", Color(0xFFF2C14E)),
    LOVED("🥰", "ពោរពេញក្ដីស្រឡាញ់", "Loved", Color(0xFFEE8FB0)),
    CALM("😌", "ស្ងប់ស្ងាត់", "Calm", Color(0xFF46B39A)),
    OKAY("🙂", "ធម្មតា", "Okay", Color(0xFFC9A27E)),
    TIRED("😴", "អស់កម្លាំង", "Tired", Color(0xFF9C8CD9)),
    LOW("😔", "ក្រៀមក្រំ", "Low", Color(0xFF5C7FD6)),
    STRESSED("😤", "តានតឹង", "Stressed", Color(0xFFE0533F));

    /** 0‥1 valence used for the trend line. */
    val score: Float get() = when (this) {
        RADIANT -> 1f; LOVED -> 0.92f; CALM -> 0.78f; OKAY -> 0.55f; TIRED -> 0.38f; LOW -> 0.2f; STRESSED -> 0.12f
    }
}

@Immutable
data class MoodEntry(val date: Int, val mood: Mood, val note: String = "")

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class Lang { KM, EN }

@Immutable
data class Settings(
    val lang: Lang = Lang.KM,
    val theme: ThemeMode = ThemeMode.DARK,
    /** packed yyyymmdd, 0 = not set */
    val birthDate: Int = 0,
    val lifeYears: Int = 80,
    val weekStartsMonday: Boolean = true,
    val showGlobal: Boolean = true,
    val showObservances: Boolean = true,
    val showSila: Boolean = true,
    val khmerNumerals: Boolean = true,
    val livingSky: Boolean = true,
    val maxLeave: Int = 3,
    // ---- reminders
    val notify: Boolean = false,
    val notifyHolidays: Boolean = true,
    val notifyGlobal: Boolean = false,
    val notifySila: Boolean = false,
    val notifyNewYear: Boolean = true,
    val notifyMood: Boolean = false,
    /** true = evening before (19:00), false = morning of (07:00) */
    val holidayEve: Boolean = true,
)
