package com.pratitin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pratitin.app.R

/**
 * "Angkor at Dusk" — a palette lifted from the temples at sunset:
 * night-indigo sky, lacquer red, gilded sandstone, lotus pink and moat jade.
 */
@Immutable
data class HeritagePalette(
    val night: Color,
    val nightDeep: Color,
    val lacquer: Color,
    val lacquerDeep: Color,
    val gold: Color,
    val goldSoft: Color,
    val goldDeep: Color,
    val sandstone: Color,
    val lotus: Color,
    val jade: Color,
    val moon: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val glassTint: Color,
    val glassStroke: Color,
    val holidayKhmer: Color,
    val holidayGlobal: Color,
    val sila: Color,
    val isDark: Boolean,
)

val DuskPalette = HeritagePalette(
    night = Color(0xFF0B0818),
    nightDeep = Color(0xFF05040C),
    lacquer = Color(0xFF8E1B2B),
    lacquerDeep = Color(0xFF3A0A18),
    gold = Color(0xFFE8B653),
    goldSoft = Color(0xFFF6D894),
    goldDeep = Color(0xFFA9742A),
    sandstone = Color(0xFFC9A27E),
    lotus = Color(0xFFEE8FB0),
    jade = Color(0xFF46B39A),
    moon = Color(0xFFFFF4D6),
    ink = Color(0xFFFFF7E8),
    inkSoft = Color(0xCCFFF7E8),
    inkFaint = Color(0x80FFF7E8),
    glassTint = Color(0x26FFFFFF),
    glassStroke = Color(0x40FFE7B0),
    holidayKhmer = Color(0xFFFF6B5A),
    holidayGlobal = Color(0xFF7FB7FF),
    sila = Color(0xFFF6D894),
    isDark = true,
)

/** "Sandstone Day" — warm parchment light theme. */
val SandstonePalette = HeritagePalette(
    night = Color(0xFFF7EBD8),
    nightDeep = Color(0xFFEBD6B8),
    lacquer = Color(0xFFA12235),
    lacquerDeep = Color(0xFFE9B9A6),
    gold = Color(0xFFA9742A),
    goldSoft = Color(0xFFC8923F),
    goldDeep = Color(0xFF7A5016),
    sandstone = Color(0xFF8C6A4A),
    lotus = Color(0xFFD25C86),
    jade = Color(0xFF1F8A72),
    moon = Color(0xFF6B4A1E),
    ink = Color(0xFF2A1A0E),
    inkSoft = Color(0xCC2A1A0E),
    inkFaint = Color(0x802A1A0E),
    glassTint = Color(0x8CFFFBF2),
    glassStroke = Color(0x66FFFFFF),
    holidayKhmer = Color(0xFFC8352A),
    holidayGlobal = Color(0xFF2F6FCB),
    sila = Color(0xFFA9742A),
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { DuskPalette }

// ---------------------------------------------------------------- type
private val Moul = FontFamily(Font(R.font.moul_regular))

@OptIn(ExperimentalTextApi::class)
private fun kantumruy(weight: Int) = Font(
    R.font.kantumruy_pro,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)
private val Kantumruy = FontFamily(
    kantumruy(300), kantumruy(400), kantumruy(500), kantumruy(600), kantumruy(700),
)

/** Khmer display type sits taller than Latin, so line heights are generous. */
@Immutable
object HeritageType {
    val display = TextStyle(fontFamily = Moul, fontSize = 34.sp, lineHeight = 1.5.em)
    val title = TextStyle(fontFamily = Moul, fontSize = 22.sp, lineHeight = 1.55.em)
    val heading = TextStyle(fontFamily = Kantumruy, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 1.45.em)
    val body = TextStyle(fontFamily = Kantumruy, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 1.5.em)
    val label = TextStyle(fontFamily = Kantumruy, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 1.4.em)
    val micro = TextStyle(fontFamily = Kantumruy, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 1.3.em, letterSpacing = 0.02.em)
    val number = TextStyle(fontFamily = Kantumruy, fontWeight = FontWeight.Light, fontSize = 64.sp, lineHeight = 1.1.em, letterSpacing = (-0.02).em)
}

@Composable
fun PratitinTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val p = if (dark) DuskPalette else SandstonePalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.gold, onPrimary = p.night, secondary = p.lotus, tertiary = p.jade,
            background = p.night, surface = p.night, onBackground = p.ink, onSurface = p.ink, error = p.holidayKhmer)
    } else {
        lightColorScheme(primary = p.gold, onPrimary = Color.White, secondary = p.lotus, tertiary = p.jade,
            background = p.night, surface = p.night, onBackground = p.ink, onSurface = p.ink, error = p.holidayKhmer)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = HeritageType.body, bodyMedium = HeritageType.body, labelLarge = HeritageType.label),
            content = content,
        )
    }
}
