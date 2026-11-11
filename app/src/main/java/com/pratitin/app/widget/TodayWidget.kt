package com.pratitin.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pratitin.app.MainActivity
import com.pratitin.khmer.HolidayKind
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.KhmerNames
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Home-screen widget: today's lunar date, moon phase and the next public holiday. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val k = KhmerCalendar.fromGregorian(today.year, today.monthValue, today.dayOfMonth)
        val packed = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
        val next = (Holidays.forYear(today.year) + Holidays.forYear(today.year + 1))
            .firstOrNull { it.kind == HolidayKind.KHMER_PUBLIC && it.packed > packed }
        val moon = when {
            k.isFullMoon -> "🌕"
            k.dayNumber < 4 -> "🌒"; k.dayNumber < 11 -> "🌓"; k.dayNumber < 15 -> "🌔"
            k.dayNumber < 19 -> "🌖"; k.dayNumber < 26 -> "🌗"; else -> "🌘"
        }
        val gold = ColorProvider(Color(0xFFE8B653), Color(0xFFE8B653))
        val ink = ColorProvider(Color(0xFFFFF7E8), Color(0xFFFFF7E8))
        val soft = ColorProvider(Color(0xB3FFF7E8), Color(0xB3FFF7E8))

        provideContent {
            Column(
                GlanceModifier.fillMaxSize().background(Color(0xFF14091C)).cornerRadius(28.dp).padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(moon, style = TextStyle(fontSize = 30.sp))
                    Spacer(GlanceModifier.width(10.dp))
                    Column {
                        Text(
                            "${KhmerNames.digits(k.day)}${KhmerNames.PHASE[k.phase.ordinal]} ខែ${KhmerNames.LUNAR_MONTHS[k.monthIndex]}",
                            style = TextStyle(color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            "ឆ្នាំ${KhmerNames.ANIMALS[k.animalYear]} · ព.ស. ${KhmerNames.digits(k.beYear)}",
                            style = TextStyle(color = gold, fontSize = 12.sp),
                        )
                    }
                }
                if (next != null) {
                    Spacer(GlanceModifier.height(10.dp))
                    val days = ChronoUnit.DAYS.between(today, LocalDate.of(next.year, next.month, next.day))
                    Text("${next.emoji} ${next.nameKm}", style = TextStyle(color = ink, fontSize = 13.sp), maxLines = 1)
                    Text("${KhmerNames.digits(days.toInt())} ថ្ងៃទៀត", style = TextStyle(color = soft, fontSize = 11.sp))
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
