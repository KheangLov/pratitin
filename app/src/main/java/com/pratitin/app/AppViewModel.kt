package com.pratitin.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pratitin.app.data.Anniversary
import com.pratitin.app.data.EventStore
import com.pratitin.app.data.Lang
import com.pratitin.app.data.LoveData
import com.pratitin.app.data.LoveProfile
import com.pratitin.app.data.LoveStore
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
import kotlinx.coroutines.flow.combine
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
    private val loveStore = LoveStore(app, viewModelScope) { Reminders.rescheduleAsync(app) }

    val settings: StateFlow<Settings?> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val moods: StateFlow<Map<Int, MoodEntry>> = moodStore.moods
    val love: StateFlow<LoveData> = loveStore.data

    /** Your events plus anniversaries mirrored from the Love tab (read-only, negative ids). */
    val events: StateFlow<List<UserEvent>> = combine(eventStore.events, love, settings) { e, l, st ->
        if (l.items.isEmpty()) e else e + l.asEvents(st?.lang != Lang.EN)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _today = MutableStateFlow(Day.today())
    /** Packed yyyymmdd; ticks over exactly at midnight. */
    val today: StateFlow<Int> = _today.asStateFlow()

    /** A day to open from a notification tap (consumed by the UI). */
    val pendingOpen = MutableStateFlow(0)
    /** A tab to open from a notification tap, e.g. "love" (consumed by the UI). */
    val pendingTab = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now(Day.ZONE)
                val next = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, next).toMillis() + 500)
                _today.value = Day.today()
            }
        }
        // Auto-backup into the chosen folder a few seconds after anything changes.
        combine(moods, eventStore.events, love, settings) { a, b, c, d -> listOf(a, b, c, d) }
            .drop(1).debounce(4000)
            .onEach { backupNow() }
            .launchIn(viewModelScope)
        // Widgets follow moods, events, anniversaries and settings (after the stores' own debounced writes).
        combine(moods, events, love, settings) { a, b, c, d -> listOf(a, b, c, d) }
            .drop(1).debounce(1500)
            .onEach { com.pratitin.app.widget.WidgetRefresher.refreshAll(app) }
            .launchIn(viewModelScope)
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

    // ------------------------------------------------------------------ backup & restore

    /** Bumped after every successful folder backup, so the UI can show "last backup …". */
    val backupTick = MutableStateFlow(0)

    /** Everything as one backup JSON (null until settings have loaded). */
    fun backupJson(): String? {
        val st = settings.value ?: return null
        return com.pratitin.app.data.Backup.encode(st, moods.value, eventStore.events.value, love.value)
    }

    /** Write to the auto-backup folder now (no-op if none chosen). */
    fun backupNow() {
        val app = getApplication<Application>()
        if (com.pratitin.app.data.Backup.folder(app) == null) return
        val json = backupJson() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.pratitin.app.data.Backup.writeToFolder(app, json)
            backupTick.value++
        }
    }

    /** Replace all data with a backup's contents. */
    fun restore(p: com.pratitin.app.data.Backup.Payload) {
        eventStore.replaceAll(p.events)
        moodStore.replaceAll(p.moods)
        loveStore.replaceAll(p.love)
        viewModelScope.launch(Dispatchers.IO) {
            settingsStore.update { p.settings }
            Reminders.rescheduleAsync(getApplication())
        }
    }

    fun saveAnniversary(a: Anniversary) = loveStore.upsert(a)
    fun deleteAnniversary(id: Long) = loveStore.delete(id)
    fun updateLove(t: (LoveProfile) -> LoveProfile) = loveStore.updateProfile(t)

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch(Dispatchers.IO) { settingsStore.update(transform) }
    }
}
