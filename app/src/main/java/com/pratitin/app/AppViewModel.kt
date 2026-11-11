package com.pratitin.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratitin.app.data.EventStore
import com.pratitin.app.data.Mood
import com.pratitin.app.data.MoodEntry
import com.pratitin.app.data.MoodStore
import com.pratitin.app.data.Settings
import com.pratitin.app.data.SettingsStore
import com.pratitin.app.data.UserEvent
import com.pratitin.app.notify.Reminders
import com.pratitin.app.util.Day
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

@OptIn(FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val moodStore = MoodStore(app, viewModelScope)
    private val eventStore = EventStore(app, viewModelScope) { Reminders.rescheduleAsync(app) }

    val settings: StateFlow<Settings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val moods: StateFlow<Map<Int, MoodEntry>> = moodStore.moods
    val events: StateFlow<List<UserEvent>> = eventStore.events

    private val _today = MutableStateFlow(Day.today())
    /** Packed yyyymmdd; ticks over exactly at midnight. */
    val today: StateFlow<Int> = _today.asStateFlow()

    /** A day to open from a notification tap (consumed by the UI). */
    val pendingOpen = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now(Day.ZONE)
                val next = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, next).toMillis() + 500)
                _today.value = Day.today()
            }
        }
        // Re-plan reminders whenever a reminder-relevant setting changes.
        settings.filterNotNull()
            .map { listOf(it.notify, it.notifyHolidays, it.notifyGlobal, it.notifySila, it.notifyNewYear, it.notifyMood, it.holidayEve, it.lang, it.birthDate, it.showGlobal) }
            .distinctUntilChanged().drop(1).debounce(500)
            .onEach { Reminders.rescheduleAsync(app) }
            .launchIn(viewModelScope)
    }

    fun refreshToday() { _today.value = Day.today() }

    fun setMood(date: Int, mood: Mood?, note: String = "") = moodStore.set(date, mood, note)

    fun saveEvent(e: UserEvent) = eventStore.upsert(e)
    fun deleteEvent(id: Long) = eventStore.delete(id)
    fun eventsOn(date: Int): List<UserEvent> = events.value.filter { it.occursOn(date) }
        .sortedWith(compareBy({ !it.allDay }, { it.startMin }))

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch(Dispatchers.IO) { settingsStore.update(transform) }
    }
}
