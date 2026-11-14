package com.pratitin.app.notify

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Reminders that fire while the app is on screen pop up *inside* the app as a banner
 * (the notification still goes to the tray, silently, so nothing is lost and nothing
 * pops up twice). When the app is in the background they pop up as phone heads-up alerts.
 */
object InAppAlerts {
    data class Alert(val id: Int, val title: String, val text: String, val channel: String, val date: Int, val tab: String?)

    /** Set by MainActivity onStart / onStop. */
    @Volatile var visible = false

    private val _alerts = MutableSharedFlow<Alert>(extraBufferCapacity = 8)
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    fun emit(a: Alert): Boolean = _alerts.tryEmit(a)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("in_app", true)
    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("in_app", on).apply()

    /** The one-time "allow notifications" prompt on first launch. */
    fun askedPermission(ctx: Context) = prefs(ctx).getBoolean("asked_permission", false)
    fun setAskedPermission(ctx: Context) = prefs(ctx).edit().putBoolean("asked_permission", true).apply()
}
