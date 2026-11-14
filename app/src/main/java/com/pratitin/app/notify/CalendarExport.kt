package com.pratitin.app.notify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.pratitin.app.data.Anniversary
import com.pratitin.app.data.LoveMath
import com.pratitin.app.util.Day
import java.time.ZoneOffset

/**
 * Hands an anniversary to the phone's calendar app (Google Calendar, Samsung Calendar…) as a
 * pre-filled, all-day, yearly event. No calendar permission is needed: the user confirms the
 * event in their own calendar app, which also syncs it to their account.
 */
object CalendarExport {
    fun add(ctx: Context, a: Anniversary, title: String, description: String, km: Boolean) {
        val today = Day.today()
        // Solar anniversaries repeat yearly from the original day; lunar ones move every
        // year, which RRULE can't express, so only the next celebration is added.
        val first = if (a.lunar) LoveMath.next(a, today) else a.date
        val begin = Day.unpack(first).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val rrule = when {
            a.lunar -> null
            Day.month(a.date) == 2 && Day.dom(a.date) == 29 -> "FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1"
            else -> "FREQ=YEARLY"
        }
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 86_400_000L)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (rrule != null) intent.putExtra(CalendarContract.Events.RRULE, rrule)
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, if (km) "រកមិនឃើញកម្មវិធីប្រតិទិនទេ" else "No calendar app found", Toast.LENGTH_LONG).show()
        }
    }
}
