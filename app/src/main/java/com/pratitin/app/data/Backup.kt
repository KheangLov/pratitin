package com.pratitin.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Everything you made in Pratitin, as one human-readable JSON file:
 * settings, moods (with notes), events & notes, and the Love tab (names, anniversaries,
 * sound choices). Device-specific state (armed alarms, the backup folder itself) is
 * deliberately left out.
 *
 * Three ways it survives an uninstall:
 *  1. Android Auto Backup (Google account) — see res/xml/data_extraction_rules.xml.
 *  2. Auto-backup into a folder the user picks (Documents, Drive…), rewritten whenever
 *     data changes, plus one dated snapshot per day (last [KEEP_SNAPSHOTS] kept).
 *  3. Save / open a backup file by hand.
 */
object Backup {
    const val FORMAT = "pratitin-backup"
    const val VERSION = 1
    const val MAIN_NAME = "Pratitin-backup.json"
    private const val SNAP_PREFIX = "Pratitin-backup-"
    private const val KEEP_SNAPSHOTS = 7
    private const val PREFS = "backup"

    class Payload(val settings: Settings, val moods: Map<Int, MoodEntry>, val events: List<UserEvent>, val love: LoveData, val createdAt: String) {
        val summary get() = "${events.size} / ${love.items.size} / ${moods.size}"
    }

    // ------------------------------------------------------------------ codec

    fun encode(settings: Settings, moods: Map<Int, MoodEntry>, events: List<UserEvent>, love: LoveData): String {
        val root = JSONObject()
        root.put("format", FORMAT); root.put("version", VERSION)
        root.put("createdAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        root.put("settings", settingsToJson(settings))
        root.put("moods", JSONArray().apply {
            moods.values.sortedBy { it.date }.forEach { m -> put(JSONObject().put("d", m.date).put("m", m.mood.name).put("n", m.note)) }
        })
        root.put("events", EventStore.toJson(events))
        root.put("love", LoveStore.toJson(love))
        return root.toString(1)
    }

    /** Throws IllegalArgumentException if [json] isn't a Pratitin backup. */
    fun decode(json: String): Payload {
        val root = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("not JSON") }
        require(root.optString("format") == FORMAT) { "not a Pratitin backup" }
        val moods = HashMap<Int, MoodEntry>()
        root.optJSONArray("moods")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val m = runCatching { Mood.valueOf(o.getString("m")) }.getOrNull() ?: continue
                moods[o.getInt("d")] = MoodEntry(o.getInt("d"), m, o.optString("n"))
            }
        }
        return Payload(
            settings = settingsFromJson(root.optJSONObject("settings") ?: JSONObject()),
            moods = moods,
            events = EventStore.fromJson(root.optJSONArray("events") ?: JSONArray()),
            love = LoveStore.fromJson(root.optJSONObject("love") ?: JSONObject()),
            createdAt = root.optString("createdAt"),
        )
    }

    private fun settingsToJson(s: Settings) = JSONObject().apply {
        put("lang", s.lang.name); put("theme", s.theme.name); put("birthDate", s.birthDate); put("lifeYears", s.lifeYears)
        put("weekStartsMonday", s.weekStartsMonday); put("showGlobal", s.showGlobal); put("showObservances", s.showObservances)
        put("showSila", s.showSila); put("khmerNumerals", s.khmerNumerals); put("livingSky", s.livingSky); put("scene", s.scene)
        put("maxLeave", s.maxLeave); put("notify", s.notify); put("notifyHolidays", s.notifyHolidays); put("notifyGlobal", s.notifyGlobal)
        put("notifySila", s.notifySila); put("notifyNewYear", s.notifyNewYear); put("notifyMood", s.notifyMood); put("holidayEve", s.holidayEve)
    }

    private fun settingsFromJson(o: JSONObject): Settings {
        val d = Settings()
        return Settings(
            lang = runCatching { Lang.valueOf(o.getString("lang")) }.getOrDefault(d.lang),
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(d.theme),
            birthDate = o.optInt("birthDate", d.birthDate), lifeYears = o.optInt("lifeYears", d.lifeYears),
            weekStartsMonday = o.optBoolean("weekStartsMonday", d.weekStartsMonday), showGlobal = o.optBoolean("showGlobal", d.showGlobal),
            showObservances = o.optBoolean("showObservances", d.showObservances), showSila = o.optBoolean("showSila", d.showSila),
            khmerNumerals = o.optBoolean("khmerNumerals", d.khmerNumerals), livingSky = o.optBoolean("livingSky", d.livingSky),
            scene = o.optInt("scene", d.scene), maxLeave = o.optInt("maxLeave", d.maxLeave), notify = o.optBoolean("notify", d.notify),
            notifyHolidays = o.optBoolean("notifyHolidays", d.notifyHolidays), notifyGlobal = o.optBoolean("notifyGlobal", d.notifyGlobal),
            notifySila = o.optBoolean("notifySila", d.notifySila), notifyNewYear = o.optBoolean("notifyNewYear", d.notifyNewYear),
            notifyMood = o.optBoolean("notifyMood", d.notifyMood), holidayEve = o.optBoolean("holidayEve", d.holidayEve),
        )
    }

    // ------------------------------------------------------------------ device-local state

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(ctx: Context): Uri? = prefs(ctx).getString("folder", null)?.let(Uri::parse)
    fun lastBackup(ctx: Context): Long = prefs(ctx).getLong("last", 0L)
    fun lastError(ctx: Context): Boolean = prefs(ctx).getBoolean("error", false)
    fun restorePromptDone(ctx: Context): Boolean = prefs(ctx).getBoolean("prompted", false)
    fun setRestorePromptDone(ctx: Context) = prefs(ctx).edit().putBoolean("prompted", true).apply()

    fun setFolder(ctx: Context, tree: Uri?) {
        val cr = ctx.contentResolver
        folder(ctx)?.let { old -> runCatching { cr.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        if (tree != null) cr.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs(ctx).edit().putString("folder", tree?.toString()).putBoolean("error", false).apply()
    }

    /** A readable name for the chosen folder, e.g. "Documents/Pratitin". */
    fun folderLabel(tree: Uri): String =
        runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':').ifBlank { "/" } }.getOrDefault(tree.lastPathSegment ?: "…")

    // ------------------------------------------------------------------ writing to the folder

    /** Writes the main backup and today's snapshot into the chosen folder. Call off the main thread. */
    fun writeToFolder(ctx: Context, json: String): Boolean {
        val tree = folder(ctx) ?: return false
        val ok = runCatching {
            val cr = ctx.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val children = listChildren(ctx, tree)
            fun upsert(name: String) {
                val uri = children[name] ?: DocumentsContract.createDocument(cr, parent, "application/json", name) ?: error("create failed")
                cr.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            upsert(MAIN_NAME)
            val today = "$SNAP_PREFIX${LocalDate.now()}.json"
            upsert(today)
            // keep only the newest snapshots
            children.keys.filter { it.startsWith(SNAP_PREFIX) && it != today }.sortedDescending().drop(KEEP_SNAPSHOTS - 1).forEach { name ->
                children[name]?.let { runCatching { DocumentsContract.deleteDocument(cr, it) } }
            }
            true
        }.getOrDefault(false)
        prefs(ctx).edit().putBoolean("error", !ok).apply { if (ok) putLong("last", System.currentTimeMillis()) }.apply()
        return ok
    }

    private fun listChildren(ctx: Context, tree: Uri): Map<String, Uri> {
        val out = HashMap<String, Uri>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        ctx.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) out[c.getString(1)] = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
        }
        return out
    }

    fun writeTo(ctx: Context, uri: Uri, json: String): Boolean = runCatching {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }; true
    }.getOrDefault(false)

    fun readFrom(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes(), Charsets.UTF_8) }
    }.getOrNull()
}
