package com.pratitin.app.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.pratitin.app.data.Lang
import com.pratitin.khmer.KhmerNames

/** In-app strings, switchable at runtime without recreating the Activity. */
@Immutable
data class Strings(
    val lang: Lang,
    val tabToday: String, val tabCalendar: String, val tabHolidays: String, val tabMood: String, val tabLife: String,
    val today: String, val lunarDate: String, val nextHoliday: String, val inDays: (Int) -> String, val todayIsHoliday: String,
    val silaDay: String, val fullMoon: String, val newMoon: String, val waxing: String, val waning: String,
    val yearOf: String, val beYear: String, val jsYear: String,
    val howFeel: String, val moodSaved: String, val addNote: String, val clear: String, val save: String,
    val holidaysTitle: String, val upcoming: String, val smartBreaks: String, val takeLeave: (Int, Int) -> String,
    val official: String, val calculated: String, val publicHoliday: String, val observance: String, val global: String,
    val filterAll: String, val filterKhmer: String, val filterGlobal: String,
    val moodTitle: String, val yearInPixels: String, val moodTrend: String, val streak: (Int) -> String, val noMoods: String,
    val lifeTitle: String, val lifeSubtitle: String, val weeksLived: String, val weeksLeft: String, val setBirthday: String,
    val lifeExpectancy: String, val ageNow: String, val percentLived: String, val nextKhmerNewYear: String,
    val settings: String, val language: String, val theme: String, val dark: String, val light: String, val system: String,
    val showGlobal: String, val showObservances: String, val showSila: String, val khmerNumerals: String,
    val livingSky: String, val weekStartsMonday: String, val maxLeave: String, val about: String, val aboutBody: String,
    val leapMonth: String, val leapDay: String, val songkranAt: String, val days: String, val weeks: String, val years: String,
    val weekdaysShort: List<String>, val monthNames: List<String>,
    /** Khmer digits (០១២…) vs Arabic digits — user setting, only meaningful in Khmer. */
    val khmerDigits: Boolean = lang == Lang.KM,
) {
    fun num(n: Int, khmer: Boolean = khmerDigits) = if (khmer) KhmerNames.digits(n) else n.toString()
}

private val EN_MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

val KhmerStrings = Strings(
    lang = Lang.KM,
    tabToday = "ថ្ងៃនេះ", tabCalendar = "ប្រតិទិន", tabHolidays = "បុណ្យ", tabMood = "អារម្មណ៍", tabLife = "ជីវិត",
    today = "ថ្ងៃនេះ", lunarDate = "ចន្ទគតិ", nextHoliday = "ថ្ងៃបុណ្យបន្ទាប់",
    inDays = { if (it == 0) "ថ្ងៃនេះ" else if (it == 1) "ថ្ងៃស្អែក" else "${KhmerNames.digits(it)} ថ្ងៃទៀត" },
    todayIsHoliday = "ថ្ងៃនេះជាថ្ងៃបុណ្យ",
    silaDay = "ថ្ងៃសីល", fullMoon = "ពេញបូណ៌មី", newMoon = "ថ្ងៃខែដាច់", waxing = "កើត", waning = "រោច",
    yearOf = "ឆ្នាំ", beYear = "ព.ស.", jsYear = "ច.ស.",
    howFeel = "ថ្ងៃនេះអ្នកមានអារម្មណ៍យ៉ាងណា?", moodSaved = "បានរក្សាទុក", addNote = "កំណត់ចំណាំ…", clear = "លុប", save = "រក្សាទុក",
    holidaysTitle = "ថ្ងៃបុណ្យ និងថ្ងៃឈប់សម្រាក", upcoming = "ខាងមុខ", smartBreaks = "ផែនការឈប់សម្រាកឆ្លាតវៃ",
    takeLeave = { leave, off -> "ឈប់ ${KhmerNames.digits(leave)} ថ្ងៃ → សម្រាក ${KhmerNames.digits(off)} ថ្ងៃ" },
    official = "ផ្លូវការ", calculated = "គណនា", publicHoliday = "ថ្ងៃឈប់សម្រាក", observance = "ពិធីបុណ្យ", global = "អន្តរជាតិ",
    filterAll = "ទាំងអស់", filterKhmer = "ខ្មែរ", filterGlobal = "សកល",
    moodTitle = "ប្រតិទិនអារម្មណ៍", yearInPixels = "មួយឆ្នាំជាពណ៌", moodTrend = "និន្នាការ ៣០ ថ្ងៃ",
    streak = { "${KhmerNames.digits(it)} ថ្ងៃជាប់គ្នា" }, noMoods = "ចាប់ផ្ដើមកត់ត្រាអារម្មណ៍ថ្ងៃនេះ",
    lifeTitle = "ជីវិតជាសប្ដាហ៍", lifeSubtitle = "ប្រអប់នីមួយៗគឺមួយសប្ដាហ៍", weeksLived = "សប្ដាហ៍បានរស់", weeksLeft = "សប្ដាហ៍នៅសល់",
    setBirthday = "កំណត់ថ្ងៃខែឆ្នាំកំណើត", lifeExpectancy = "អាយុរំពឹងទុក", ageNow = "អាយុ", percentLived = "បានរស់",
    nextKhmerNewYear = "ចូលឆ្នាំខ្មែរបន្ទាប់",
    settings = "ការកំណត់", language = "ភាសា", theme = "រចនាប័ទ្ម", dark = "យប់អង្គរ", light = "ថ្មភក់", system = "តាមប្រព័ន្ធ",
    showGlobal = "បង្ហាញថ្ងៃបុណ្យអន្តរជាតិ", showObservances = "បង្ហាញពិធីបុណ្យព្រះពុទ្ធសាសនា", showSila = "សម្គាល់ថ្ងៃសីល",
    khmerNumerals = "លេខខ្មែរ", livingSky = "ផ្ទៃមេឃមានចលនា", weekStartsMonday = "សប្ដាហ៍ចាប់ផ្ដើមថ្ងៃចន្ទ", maxLeave = "ថ្ងៃឈប់អតិបរមា",
    about = "អំពី", aboutBody = "គណនាតាមក្បួនចន្ទគតិ (សុរិយាត្រ) • ថ្ងៃឈប់សម្រាកតាមអនុក្រឹត្យរាជរដ្ឋាភិបាល",
    leapMonth = "អធិកមាស", leapDay = "ចន្ទ្រាធិមាស", songkranAt = "ទេវតាចុះ", days = "ថ្ងៃ", weeks = "សប្ដាហ៍", years = "ឆ្នាំ",
    weekdaysShort = KhmerNames.WEEKDAYS_SHORT.toList(), monthNames = KhmerNames.SOLAR_MONTHS.toList(),
)

val EnglishStrings = Strings(
    lang = Lang.EN,
    tabToday = "Today", tabCalendar = "Calendar", tabHolidays = "Holidays", tabMood = "Mood", tabLife = "Life",
    today = "Today", lunarDate = "Lunar date", nextHoliday = "Next holiday",
    inDays = { if (it == 0) "Today" else if (it == 1) "Tomorrow" else "in $it days" },
    todayIsHoliday = "Today is a holiday",
    silaDay = "Sila day", fullMoon = "Full moon", newMoon = "New moon", waxing = "Waxing", waning = "Waning",
    yearOf = "Year of the", beYear = "BE", jsYear = "JS",
    howFeel = "How do you feel today?", moodSaved = "Saved", addNote = "Add a note…", clear = "Clear", save = "Save",
    holidaysTitle = "Holidays & breaks", upcoming = "Upcoming", smartBreaks = "Smart breaks",
    takeLeave = { leave, off -> "Take $leave day${if (leave > 1) "s" else ""} → $off days off" },
    official = "Official", calculated = "Calculated", publicHoliday = "Public holiday", observance = "Observance", global = "Global",
    filterAll = "All", filterKhmer = "Khmer", filterGlobal = "Global",
    moodTitle = "Mood calendar", yearInPixels = "Year in pixels", moodTrend = "30-day trend",
    streak = { "$it-day streak" }, noMoods = "Log today's mood to begin",
    lifeTitle = "Life in weeks", lifeSubtitle = "Every square is one week", weeksLived = "weeks lived", weeksLeft = "weeks left",
    setBirthday = "Set your birthday", lifeExpectancy = "Life expectancy", ageNow = "Age", percentLived = "lived",
    nextKhmerNewYear = "Next Khmer New Year",
    settings = "Settings", language = "Language", theme = "Theme", dark = "Angkor Night", light = "Sandstone", system = "System",
    showGlobal = "Show global holidays", showObservances = "Show Buddhist observances", showSila = "Mark Sila days",
    khmerNumerals = "Khmer numerals", livingSky = "Living sky", weekStartsMonday = "Week starts Monday", maxLeave = "Max leave days",
    about = "About", aboutBody = "Lunar dates follow the Chhankitek (Soriyatra) reckoning • Public holidays follow the Royal Government sub-decrees",
    leapMonth = "Adhikameas · leap month", leapDay = "Chantrathimeas · leap day", songkranAt = "Tevoda descends", days = "days", weeks = "weeks", years = "years",
    weekdaysShort = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"), monthNames = EN_MONTHS,
)

val LocalStrings = staticCompositionLocalOf { KhmerStrings }
