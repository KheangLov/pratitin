package com.pratitin.app

import android.app.Application
import android.content.Intent
import com.pratitin.app.notify.Reminders
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pratitin.app.ui.AppRoot
import com.pratitin.khmer.Holidays
import com.pratitin.khmer.KhmerCalendar
import com.pratitin.khmer.LeavePlanner
import java.time.LocalDate
import kotlin.concurrent.thread

class PratitinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannels(this)
        Reminders.rescheduleAsync(this)
        com.pratitin.app.widget.WidgetRefresher.scheduleMidnight(this)
        // Warm the lunar tables off the main thread so the first frame never computes them.
        thread(name = "chhankitek-prefetch", priority = Thread.MIN_PRIORITY) {
            val y = LocalDate.now().year
            KhmerCalendar.prefetch(y - 1..y + 1)
            for (yy in y..y + 1) { Holidays.forYear(yy); Holidays.byDate(yy); LeavePlanner.suggest(yy) }
            KhmerCalendar.prefetch(y - 3..y + 5)
        }
    }
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent { AppRoot(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A notification tap carries the day it's about — open that day's card. */
    private fun handleIntent(intent: Intent?) {
        val d = intent?.getIntExtra(Reminders.EXTRA_DATE, 0) ?: 0
        val tab = intent?.getStringExtra(Reminders.EXTRA_TAB)
        if (tab != null) { vm.pendingTab.value = tab; intent?.removeExtra(Reminders.EXTRA_TAB); intent?.removeExtra(Reminders.EXTRA_DATE); return }
        if (d != 0) { vm.pendingOpen.value = d; intent?.removeExtra(Reminders.EXTRA_DATE) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }

    override fun onStart() {
        super.onStart()
        com.pratitin.app.notify.InAppAlerts.visible = true
    }

    override fun onStop() {
        super.onStop()
        com.pratitin.app.notify.InAppAlerts.visible = false
        vm.backupNow()
        com.pratitin.app.widget.WidgetRefresher.refreshAll(this)
    }
}
