package com.pratitin.app.ui.screens.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Backup
import com.pratitin.app.data.Lang
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.LiquidToggle
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.components.SettingRow
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/**
 * "Open a backup file → confirm → restore". Returns the action that opens the picker;
 * the confirmation dialog is composed here while a file is pending.
 */
@Composable
fun rememberRestoreAction(shell: ShellState, onDone: () -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val km = LocalStrings.current.lang == Lang.KM
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Backup.Payload?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val payload = withContext(Dispatchers.IO) { Backup.readFrom(ctx, uri)?.let { runCatching { Backup.decode(it) }.getOrNull() } }
            if (payload == null) Toast.makeText(ctx, if (km) "ឯកសារនេះមិនមែនជាការបម្រុងទុករបស់ប្រតិទិនទេ" else "That file isn't a Pratitin backup", Toast.LENGTH_LONG).show()
            else pending = payload
        }
    }
    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null },
            containerColor = p.nightDeep,
            title = { Text(if (km) "ស្ដារទិន្នន័យ?" else "Restore this backup?", style = HeritageType.heading, color = p.ink) },
            text = {
                Column {
                    Text(
                        if (km) "${b.events.size} ព្រឹត្តិការណ៍ · ${b.love.items.size} ខួប · ${b.moods.size} អារម្មណ៍ · ការកំណត់"
                        else "${b.events.size} events · ${b.love.items.size} anniversaries · ${b.moods.size} moods · settings",
                        style = HeritageType.body, color = p.ink,
                    )
                    if (b.createdAt.isNotBlank()) Text(
                        (if (km) "បម្រុងទុកនៅ " else "Backed up ") + b.createdAt.replace('T', ' ').take(16),
                        style = HeritageType.label, color = p.inkSoft, modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        if (km) "ទិន្នន័យបច្ចុប្បន្ននៅលើទូរស័ព្ទនេះនឹងត្រូវជំនួស។" else "This replaces what's on this phone now.",
                        style = HeritageType.label, color = p.holidayKhmer, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    shell.vm.restore(b); pending = null
                    Backup.setRestorePromptDone(ctx)
                    Toast.makeText(ctx, if (km) "បានស្ដារទិន្នន័យរួចរាល់ 💛" else "Restored 💛", Toast.LENGTH_SHORT).show()
                    onDone()
                }) { Text(if (km) "ស្ដារ" else "Restore", color = p.gold) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(if (km) "បោះបង់" else "Cancel", color = p.inkSoft) } },
        )
    }
    return { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }
}

/** Backup & restore section of the settings sheet. */
@Composable
fun BackupSection(shell: ShellState) {
    val ctx = LocalContext.current
    val km = LocalStrings.current.lang == Lang.KM
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val tick by shell.vm.backupTick.collectAsStateWithLifecycle()
    var folder by remember { mutableStateOf(Backup.folder(ctx)) }
    val last = remember(tick, folder) { Backup.lastBackup(ctx) }
    val error = remember(tick, folder) { Backup.lastError(ctx) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { Backup.setFolder(ctx, uri) }.onSuccess { folder = uri; shell.vm.backupNow() }
                .onFailure { Toast.makeText(ctx, if (km) "មិនអាចប្រើថតនេះបានទេ" else "Can't use that folder", Toast.LENGTH_LONG).show() }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = shell.vm.backupJson() ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.writeTo(ctx, uri, json) }
            Toast.makeText(ctx, if (ok) (if (km) "បានរក្សាទុកការបម្រុងទុក ✓" else "Backup saved ✓") else (if (km) "រក្សាទុកមិនបាន" else "Couldn't save"), Toast.LENGTH_SHORT).show()
        }
    }
    val restore = rememberRestoreAction(shell)

    SectionTitle(if (km) "បម្រុងទុក និងស្ដារ" else "Backup & restore")
    Text(
        if (km) "ទិន្នន័យរបស់អ្នក (ព្រឹត្តិការណ៍ ខួប អារម្មណ៍ និងការកំណត់) ត្រូវបានបម្រុងទុកដោយស្វ័យប្រវត្តិទៅគណនី Google របស់អ្នក ហើយត្រឡប់មកវិញពេលដំឡើងឡើងវិញ។ សម្រាប់សុវត្ថិភាពបន្ថែម សូមជ្រើសថតមួយ ដើម្បីរក្សាឯកសារបម្រុងទុកនៅខាងក្រៅកម្មវិធី។"
        else "Your events, anniversaries, moods and settings are backed up to your Google account automatically and come back when you reinstall. For extra safety, pick a folder to keep a backup file outside the app too.",
        style = HeritageType.label, color = p.inkSoft, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
    )
    val f = folder
    SettingRow(if (km) "📁 បម្រុងទុកស្វ័យប្រវត្តិទៅថត" else "📁 Auto-backup to a folder") {
        LiquidToggle(f != null, { on -> if (on) pickFolder.launch(null) else { Backup.setFolder(ctx, null); folder = null } })
    }
    if (f != null) {
        Text(
            buildString {
                append("→ ").append(Backup.folderLabel(f)).append("/").append(Backup.MAIN_NAME)
                append("\n")
                append(
                    when {
                        error -> if (km) "⚠️ សរសេរមិនបាន — សូមជ្រើសថតម្ដងទៀត" else "⚠️ Couldn't write — pick the folder again"
                        last > 0 -> (if (km) "បម្រុងទុកចុងក្រោយ៖ " else "Last backup: ") + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))
                        else -> if (km) "កំពុងបម្រុងទុក…" else "Backing up…"
                    },
                )
                append(if (km) "\nរក្សាច្បាប់ចម្លងប្រចាំថ្ងៃ ៧ ថ្ងៃចុងក្រោយ" else "\nKeeps a daily copy for the last 7 days")
            },
            style = HeritageType.micro, color = if (error) p.holidayKhmer else p.inkSoft,
            modifier = Modifier.clickable { pickFolder.launch(f) }.padding(bottom = 6.dp),
        )
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (km) "💾 រក្សាទុកឯកសារ…" else "💾 Save backup…", Modifier.weight(1f), filled = true) { saveFile.launch("Pratitin-backup-${LocalDate.now()}.json") }
        Pill(if (km) "♻️ ស្ដារពីឯកសារ…" else "♻️ Restore…", Modifier.weight(1f)) { restore() }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text, style = HeritageType.label, color = if (filled) p.night else p.ink, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier.clip(Squircles.pill).background(if (filled) p.gold else p.gold.copy(alpha = 0.12f))
            .border(1.dp, p.gold.copy(alpha = 0.5f), Squircles.pill).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
    )
}

/** On a fresh install with nothing in it yet: offer to bring a backup back. */
@Composable
fun RestorePromptCard(shell: ShellState, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val km = LocalStrings.current.lang == Lang.KM
    val p = LocalPalette.current
    var visible by remember { mutableStateOf(!Backup.restorePromptDone(ctx)) }
    // the stores load from disk just after the first frame: wait, so existing data never flashes this card
    var ready by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { kotlinx.coroutines.delay(900); ready = true }
    if (!visible || !ready) return
    val restore = rememberRestoreAction(shell) { visible = false }
    GlassCard(shell.bg, modifier.fillMaxWidth(), tint = p.gold.copy(alpha = 0.12f)) {
        Text(if (km) "👋 សូមស្វាគមន៍ត្រឡប់មកវិញ" else "👋 Welcome back", style = HeritageType.heading, color = p.ink)
        Text(
            if (km) "មានឯកសារបម្រុងទុកពីមុនទេ? ស្ដារព្រឹត្តិការណ៍ ខួប អារម្មណ៍ និងការកំណត់ទាំងអស់មកវិញ។"
            else "Have a backup from before? Bring back all your events, anniversaries, moods and settings.",
            style = HeritageType.label, color = p.inkSoft, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (km) "♻️ ស្ដារពីឯកសារ" else "♻️ Restore from file", Modifier.weight(1.4f), filled = true) { restore() }
            Pill(if (km) "ទេ អរគុណ" else "Not now", Modifier.weight(1f)) { Backup.setRestorePromptDone(ctx); visible = false }
        }
    }
}

/** Pop-up controls: in-app pop-ups, a test, and the phone's notification settings. */
@Composable
fun AlertControls() {
    val ctx = LocalContext.current
    val km = LocalStrings.current.lang == Lang.KM
    val p = LocalPalette.current
    var inApp by remember { mutableStateOf(com.pratitin.app.notify.InAppAlerts.enabled(ctx)) }
    SettingRow(if (km) "💬 លោតផ្ទាំងក្នុងកម្មវិធី ពេលកំពុងបើក" else "💬 Pop up inside the app while it's open") {
        LiquidToggle(inApp, { v -> inApp = v; com.pratitin.app.notify.InAppAlerts.setEnabled(ctx, v) })
    }
    Text(
        if (km) "ពេលកម្មវិធីបិទ ការរំលឹកលោតឡើងលើអេក្រង់ទូរស័ព្ទ (Pop-up) ម្ដង។ បើមិនលោត សូមបើក «Pop-up / Floating notifications» ក្នុងការកំណត់ទូរស័ព្ទ។"
        else "When the app is closed, reminders pop up once on your phone's screen. If they don't, turn on \"Pop-up / Floating notifications\" in your phone's settings.",
        style = HeritageType.micro, color = p.inkSoft, modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (km) "🔔 សាកល្បងឥឡូវ" else "🔔 Test now", Modifier.weight(1f)) {
            com.pratitin.app.notify.Reminders.show(
                ctx, 9_001, if (km) "🪷 ការរំលឹកសាកល្បង" else "🪷 Test reminder",
                if (km) "នេះជារបៀបដែលការរំលឹកលោតឡើង" else "This is how reminders pop up",
                com.pratitin.app.notify.Reminders.CH_EVENT, com.pratitin.app.util.Day.today(),
            )
        }
        Pill(if (km) "📱 សាកល្បងក្រោយ ៥ វិ" else "📱 Test in 5 s", Modifier.weight(1f)) {
            // leave the app (or lock the phone) to see the phone's own pop-up
            val am = ctx.getSystemService(android.app.AlarmManager::class.java)
            val i = android.content.Intent(ctx, com.pratitin.app.notify.ReminderReceiver::class.java).apply {
                putExtra("title", if (km) "🪷 ការរំលឹកសាកល្បង" else "🪷 Test reminder")
                putExtra("text", if (km) "ការជូនដំណឹងលោតលើអេក្រង់ទូរស័ព្ទ" else "Your phone's pop-up notification")
                putExtra("channel", com.pratitin.app.notify.Reminders.CH_EVENT); putExtra("id", 9_002)
                putExtra(com.pratitin.app.notify.Reminders.EXTRA_DATE, com.pratitin.app.util.Day.today())
            }
            val pi = android.app.PendingIntent.getBroadcast(ctx, 9_002, i, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            am?.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5_000, pi)
            Toast.makeText(ctx, if (km) "ចាកចេញពីកម្មវិធី ឬចាក់សោអេក្រង់ឥឡូវនេះ…" else "Leave the app or lock the screen now…", Toast.LENGTH_SHORT).show()
        }
    }
    Pill(if (km) "⚙️ ការកំណត់ការជូនដំណឹងរបស់ទូរស័ព្ទ" else "⚙️ Phone notification settings", Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        runCatching {
            ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
