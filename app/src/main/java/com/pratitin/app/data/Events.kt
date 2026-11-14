package com.pratitin.app.data

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.pratitin.khmer.Recurrence
import com.pratitin.khmer.Repeat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class EventKind(val emoji: String, val km: String, val en: String) {
    EVENT("📅", "ព្រឹត្តិការណ៍", "Event"),
    BIRTHDAY("🎂", "ខួបកំណើត", "Birthday"),
    NOTE("📝", "កំណត់ចំណាំ", "Note"),
    /** Mirrored read-only from the Love tab; never stored in events.json. */
    ANNIVERSARY("💞", "ខួបស្នេហា", "Anniversary"),
}

/** Reminder choice. All-day items remind at a clock time; timed items relative to start. */
enum class Remind(val km: String, val en: String) {
    NONE("គ្មាន", "None"),
    AT_TIME("ពេលចាប់ផ្ដើម", "At start"),
    MIN_10("១០ នាទីមុន", "10 min before"),
    MIN_30("៣០ នាទីមុន", "30 min before"),
    HOUR_1("១ ម៉ោងមុន", "1 hour before"),
    DAY_BEFORE("ថ្ងៃមុន", "Day before"),
}

val EVENT_COLORS = listOf(
    Color(0xFFEE8FB0), Color(0xFFF2C14E), Color(0xFF46B39A), Color(0xFF7FB7FF), Color(0xFF9C8CD9), Color(0xFFE0533F), Color(0xFFC9A27E),
)

@Immutable
data class UserEvent(
    val id: Long,
    val kind: EventKind = EventKind.EVENT,
    val title: String,
    val note: String = "",
    /** packed yyyymmdd of the first occurrence */
    val start: Int,
    val allDay: Boolean = true,
    /** minutes after midnight, when not all-day */
    val startMin: Int = 9 * 60,
    val durationMin: Int = 60,
    val repeat: Repeat = Repeat.NONE,
    val color: Int = 0,
    val remind: Remind = Remind.NONE,
) {
    fun occursOn(date: Int) = Recurrence.occursOn(start, repeat, date)
    val colorValue: Color get() = EVENT_COLORS[color.coerceIn(0, EVENT_COLORS.lastIndex)]

    fun timeLabel(): String = if (allDay) "" else "%02d:%02d".format(startMin / 60, startMin % 60)
}

/**
 * Events, birthdays and notes. Same storage model as moods: in-memory list, JSON file
 * written atomically and debounced. A few hundred items cost nothing to scan per day.
 */
@OptIn(FlowPreview::class)
class EventStore(context: Context, private val scope: CoroutineScope, private val onChange: () -> Unit) {
    private val file = AtomicFile(File(context.filesDir, "events.json"))
    private val _events = MutableStateFlow<List<UserEvent>>(emptyList())
    val events: StateFlow<List<UserEvent>> = _events.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = load()
            _events.value = (loaded + _events.value).distinctBy { it.id }
            _events.drop(1).debounce(300).onEach { save(it); onChange() }.launchIn(scope)
        }
    }

    fun upsert(e: UserEvent) {
        _events.value = _events.value.filter { it.id != e.id } + e
    }

    fun delete(id: Long) {
        _events.value = _events.value.filter { it.id != id }
    }

    /** Restore from a backup: replaces everything (and saves). */
    fun replaceAll(list: List<UserEvent>) { _events.value = list.distinctBy { it.id } }

    fun on(date: Int): List<UserEvent> =
        _events.value.filter { it.occursOn(date) }.sortedWith(compareBy({ !it.allDay }, { it.startMin }))

    private fun load(): List<UserEvent> = parse(file)

    private suspend fun save(list: List<UserEvent>) = withContext(Dispatchers.IO) {
        val arr = toJson(list)
        val out = file.startWrite()
        try { out.write(arr.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(out) } catch (t: Exception) { file.failWrite(out) }
    }

    companion object {
        /** Synchronous read, used by the reminder receiver when the UI isn't running. */
        fun readBlocking(context: Context): List<UserEvent> = parse(AtomicFile(File(context.filesDir, "events.json")))

        private fun parse(f: AtomicFile): List<UserEvent> = runCatching { fromJson(JSONArray(String(f.readFully(), Charsets.UTF_8))) }.getOrDefault(emptyList())

        fun toJson(list: List<UserEvent>): JSONArray {
            val arr = JSONArray()
            list.filter { it.kind != EventKind.ANNIVERSARY }.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id); put("kind", e.kind.name); put("title", e.title); put("note", e.note)
                    put("start", e.start); put("allDay", e.allDay); put("startMin", e.startMin); put("dur", e.durationMin)
                    put("repeat", e.repeat.name); put("color", e.color); put("remind", e.remind.name)
                })
            }
            return arr
        }

        fun fromJson(arr: JSONArray): List<UserEvent> = run {
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                UserEvent(
                    id = o.getLong("id"),
                    kind = runCatching { EventKind.valueOf(o.optString("kind")) }.getOrDefault(EventKind.EVENT),
                    title = o.optString("title"), note = o.optString("note"), start = o.getInt("start"),
                    allDay = o.optBoolean("allDay", true), startMin = o.optInt("startMin", 540), durationMin = o.optInt("dur", 60),
                    repeat = runCatching { Repeat.valueOf(o.optString("repeat")) }.getOrDefault(Repeat.NONE),
                    color = o.optInt("color", 0),
                    remind = runCatching { Remind.valueOf(o.optString("remind")) }.getOrDefault(Remind.NONE),
                )
            }
        }
    }
}
