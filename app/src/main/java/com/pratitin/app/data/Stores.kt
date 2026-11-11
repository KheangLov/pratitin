package com.pratitin.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object K {
        val lang = stringPreferencesKey("lang")
        val theme = stringPreferencesKey("theme")
        val birth = intPreferencesKey("birth")
        val life = intPreferencesKey("life_years")
        val monday = booleanPreferencesKey("week_monday")
        val global = booleanPreferencesKey("show_global")
        val obs = booleanPreferencesKey("show_obs")
        val sila = booleanPreferencesKey("show_sila")
        val numerals = booleanPreferencesKey("khmer_numerals")
        val sky = booleanPreferencesKey("living_sky")
        val leave = intPreferencesKey("max_leave")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            lang = p[K.lang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: d.lang,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            birthDate = p[K.birth] ?: d.birthDate,
            lifeYears = p[K.life] ?: d.lifeYears,
            weekStartsMonday = p[K.monday] ?: d.weekStartsMonday,
            showGlobal = p[K.global] ?: d.showGlobal,
            showObservances = p[K.obs] ?: d.showObservances,
            showSila = p[K.sila] ?: d.showSila,
            khmerNumerals = p[K.numerals] ?: d.khmerNumerals,
            livingSky = p[K.sky] ?: d.livingSky,
            maxLeave = p[K.leave] ?: d.maxLeave,
        )
    }

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val cur = Settings(
                lang = p[K.lang]?.let { runCatching { Lang.valueOf(it) }.getOrNull() } ?: Lang.KM,
                theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
                birthDate = p[K.birth] ?: 0, lifeYears = p[K.life] ?: 80,
                weekStartsMonday = p[K.monday] ?: true, showGlobal = p[K.global] ?: true,
                showObservances = p[K.obs] ?: true, showSila = p[K.sila] ?: true,
                khmerNumerals = p[K.numerals] ?: true, livingSky = p[K.sky] ?: true, maxLeave = p[K.leave] ?: 3,
            )
            val n = transform(cur)
            p[K.lang] = n.lang.name; p[K.theme] = n.theme.name; p[K.birth] = n.birthDate
            p[K.life] = n.lifeYears; p[K.monday] = n.weekStartsMonday; p[K.global] = n.showGlobal
            p[K.obs] = n.showObservances; p[K.sila] = n.showSila; p[K.numerals] = n.khmerNumerals
            p[K.sky] = n.livingSky; p[K.leave] = n.maxLeave
        }
    }
}

/**
 * Mood journal. A few thousand entries at most, so the whole journal lives in an
 * in-memory map (O(1) lookups while drawing grids) and is persisted as a compact
 * tab-separated file with [AtomicFile] — crash-safe, no ORM, no annotation processing.
 * Writes are debounced so rapid edits cost a single disk write.
 */
@OptIn(FlowPreview::class)
class MoodStore(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "moods.tsv"))
    private val _moods = MutableStateFlow<Map<Int, MoodEntry>>(emptyMap())
    val moods: StateFlow<Map<Int, MoodEntry>> = _moods.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = load()
            _moods.value = loaded + _moods.value // edits made during load win
            _moods.drop(1).debounce(400).onEach { save(it) }.launchIn(scope)
        }
    }

    fun set(date: Int, mood: Mood?, note: String = "") {
        _moods.value = _moods.value.toMutableMap().apply {
            if (mood == null) remove(date) else put(date, MoodEntry(date, mood, note.take(500)))
        }
    }

    private fun load(): Map<Int, MoodEntry> = runCatching {
        val out = HashMap<Int, MoodEntry>()
        String(file.readFully(), Charsets.UTF_8).lineSequence().forEach { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size >= 2) {
                val d = parts[0].toIntOrNull() ?: return@forEach
                val m = Mood.entries.getOrNull(parts[1].toIntOrNull() ?: -1) ?: return@forEach
                out[d] = MoodEntry(d, m, parts.getOrNull(2)?.replace("\\n", "\n")?.replace("\\t", "\t").orEmpty())
            }
        }
        out
    }.getOrDefault(emptyMap())

    private suspend fun save(map: Map<Int, MoodEntry>) = withContext(Dispatchers.IO) {
        val text = buildString {
            map.values.sortedBy { it.date }.forEach {
                append(it.date).append('\t').append(it.mood.ordinal).append('\t')
                append(it.note.replace("\t", "\\t").replace("\n", "\\n")).append('\n')
            }
        }
        val out = file.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
