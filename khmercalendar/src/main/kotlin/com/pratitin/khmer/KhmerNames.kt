package com.pratitin.khmer

/** Khmer & romanised names used across the calendar. */
object KhmerNames {
    val LUNAR_MONTHS = arrayOf(
        "មិគសិរ", "បុស្ស", "មាឃ", "ផល្គុន", "ចេត្រ", "ពិសាខ",
        "ជេស្ឋ", "អាសាឍ", "ស្រាពណ៍", "ភទ្របទ", "អស្សុជ", "កត្ដិក",
        "បឋមាសាឍ", "ទុតិយាសាឍ",
    )
    val LUNAR_MONTHS_LATIN = arrayOf(
        "Migasir", "Boss", "Meak", "Phalkun", "Cheit", "Pisakh",
        "Jesth", "Asadh", "Srap", "Phatrabot", "Assoch", "Kadeuk",
        "Pathamasadh", "Tutiyasadh",
    )
    val SOLAR_MONTHS = arrayOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ",
    )
    val ANIMALS = arrayOf("ជូត", "ឆ្លូវ", "ខាល", "ថោះ", "រោង", "ម្សាញ់", "មមី", "មមែ", "វក", "រកា", "ច", "កុរ")
    val ANIMALS_LATIN = arrayOf("Rat", "Ox", "Tiger", "Rabbit", "Dragon", "Snake", "Horse", "Goat", "Monkey", "Rooster", "Dog", "Pig")
    val SAK = arrayOf("សំរឹទ្ធិស័ក", "ឯកស័ក", "ទោស័ក", "ត្រីស័ក", "ចត្វាស័ក", "បញ្ចស័ក", "ឆស័ក", "សប្តស័ក", "អដ្ឋស័ក", "នព្វស័ក")
    val WEEKDAYS = arrayOf("អាទិត្យ", "ចន្ទ", "អង្គារ", "ពុធ", "ព្រហស្បតិ៍", "សុក្រ", "សៅរ៍")
    val WEEKDAYS_SHORT = arrayOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
    val PHASE = arrayOf("កើត", "រោច")
    val PHASE_SHORT = arrayOf("ក", "រ")

    private const val ZERO = '០'
    fun digits(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') ZERO + (c - '0') else c)
        return sb.toString()
    }
    fun digits(n: Int): String = digits(n.toString())
}
