package com.pratitin.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratitin.app.data.Mood
import com.pratitin.app.data.MoodEntry
import com.pratitin.app.data.MoodStore
import com.pratitin.app.data.Settings
import com.pratitin.app.data.SettingsStore
import com.pratitin.app.util.Day
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsStore = SettingsStore(app)
    private val moodStore = MoodStore(app, viewModelScope)

    val settings: StateFlow<Settings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val moods: StateFlow<Map<Int, MoodEntry>> = moodStore.moods

    private val _today = MutableStateFlow(Day.today())
    /** Packed yyyymmdd in Cambodia time; ticks over exactly at midnight. */
    val today: StateFlow<Int> = _today.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now(Day.ZONE)
                val next = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, next).toMillis() + 500)
                _today.value = Day.today()
            }
        }
    }

    fun refreshToday() { _today.value = Day.today() }

    fun setMood(date: Int, mood: Mood?, note: String = "") = moodStore.set(date, mood, note)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch(Dispatchers.IO) { settingsStore.update(transform) }
    }

    @Suppress("unused")
    private val zone: ZoneId = Day.ZONE
}
