package com.pratitin.app

import android.app.Application
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
        setContent { AppRoot(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshToday()
    }
}
