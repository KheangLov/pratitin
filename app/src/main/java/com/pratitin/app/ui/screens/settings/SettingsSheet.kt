package com.pratitin.app.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.pratitin.app.notify.Reminders
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.data.Lang
import com.pratitin.app.data.ThemeMode
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.Tab
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.LiquidToggle
import com.pratitin.app.ui.components.SectionTitle
import com.pratitin.app.ui.components.SettingRow
import com.pratitin.app.ui.heritage.KbachDivider
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(shell: ShellState, onDismiss: () -> Unit) {
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val s = LocalStrings.current
    val p = LocalPalette.current
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 300f)) }
    fun upd(t: (com.pratitin.app.data.Settings) -> com.pratitin.app.data.Settings) = shell.vm.updateSettings(t)
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) upd { it.copy(notify = true) }
    }
    fun enableNotifications(on: Boolean) {
        if (!on) { upd { it.copy(notify = false) }; return }
        if (Build.VERSION.SDK_INT >= 33 && !Reminders.canPost(ctx)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else upd { it.copy(notify = true) }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp), contentAlignment = Alignment.BottomCenter) {
        GlassCard(
            shell.content,
            Modifier.fillMaxWidth().graphicsLayer { translationY = rise.value * size.height }.navigationBarsPadding(),
            radius = 36.dp, liquid = true, tint = p.night.copy(alpha = if (p.isDark) 0.6f else 0.75f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.settings, style = HeritageType.title, color = p.ink, modifier = Modifier.weight(1f))
                GlassIconButton(shell.content, onClick = onDismiss, size = 38.dp, icon = Icons.close)
            }
            KbachDivider(Modifier.padding(vertical = 8.dp), height = 10.dp, alpha = 0.5f)
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionTitle(s.language)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("ខ្មែរ", settings.lang == Lang.KM, shell.content, leading = "🇰🇭") { upd { it.copy(lang = Lang.KM) } }
                    GlassChip("English", settings.lang == Lang.EN, shell.content, leading = "🌐") { upd { it.copy(lang = Lang.EN) } }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(s.theme)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(s.dark, settings.theme == ThemeMode.DARK, shell.content, leading = "🌙") { upd { it.copy(theme = ThemeMode.DARK) } }
                    GlassChip(s.light, settings.theme == ThemeMode.LIGHT, shell.content, leading = "☀️") { upd { it.copy(theme = ThemeMode.LIGHT) } }
                    GlassChip(s.system, settings.theme == ThemeMode.SYSTEM, shell.content) { upd { it.copy(theme = ThemeMode.SYSTEM) } }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle(s.background)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("✨", "🌙", "🏵️", "🪷").forEachIndexed { i, e ->
                        GlassChip(s.sceneNames[i], settings.scene == i, shell.content, leading = e) { upd { it.copy(scene = i) } }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingRow(s.livingSky) { LiquidToggle(settings.livingSky, { v -> upd { it.copy(livingSky = v) } }) }
                SettingRow(s.khmerNumerals) { LiquidToggle(settings.khmerNumerals, { v -> upd { it.copy(khmerNumerals = v) } }) }
                SettingRow(s.weekStartsMonday) { LiquidToggle(settings.weekStartsMonday, { v -> upd { it.copy(weekStartsMonday = v) } }) }
                SettingRow(s.showGlobal) { LiquidToggle(settings.showGlobal, { v -> upd { it.copy(showGlobal = v) } }) }
                SettingRow(s.showObservances) { LiquidToggle(settings.showObservances, { v -> upd { it.copy(showObservances = v) } }) }
                SettingRow(s.showSila) { LiquidToggle(settings.showSila, { v -> upd { it.copy(showSila = v) } }) }
                SettingRow(s.maxLeave) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..3).forEach { n -> GlassChip(s.num(n), settings.maxLeave == n, shell.content) { upd { it.copy(maxLeave = n) } } }
                    }
                }
                SettingRow(s.setBirthday) {
                    GlassChip("→", false, shell.content) { onDismiss(); shell.goTo(Tab.LIFE) }
                }
                // ---- reminders
                Spacer(Modifier.height(16.dp))
                SectionTitle(s.reminders)
                SettingRow("🔔 ${s.enableReminders}") { LiquidToggle(settings.notify, { v -> enableNotifications(v) }) }
                if (denied) Text(s.permissionDenied, style = HeritageType.micro, color = p.holidayKhmer)
                AnimatedVisibility(settings.notify, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        SettingRow("🇰🇭 ${s.rHolidays}") { LiquidToggle(settings.notifyHolidays, { v -> upd { it.copy(notifyHolidays = v) } }) }
                        SettingRow("🎉 ${s.rNewYear}") { LiquidToggle(settings.notifyNewYear, { v -> upd { it.copy(notifyNewYear = v) } }) }
                        SettingRow("☸ ${s.rSila}") { LiquidToggle(settings.notifySila, { v -> upd { it.copy(notifySila = v) } }) }
                        SettingRow("🌍 ${s.rGlobal}") { LiquidToggle(settings.notifyGlobal, { v -> upd { it.copy(notifyGlobal = v) } }) }
                        SettingRow("🪷 ${s.rMood}") { LiquidToggle(settings.notifyMood, { v -> upd { it.copy(notifyMood = v) } }) }
                        Text(s.rWhen, style = HeritageType.micro, color = p.inkSoft, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassChip(s.rEve, settings.holidayEve, shell.content, leading = "🌙") { upd { it.copy(holidayEve = true) } }
                            GlassChip(s.rMorning, !settings.holidayEve, shell.content, leading = "☀️") { upd { it.copy(holidayEve = false) } }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SectionTitle(s.about)
                Text(s.aboutBody, style = HeritageType.label, color = p.inkSoft, modifier = Modifier.padding(top = 6.dp))
                Text(
                    "Fonts: Moul & Kantumruy Pro (SIL OFL) · Lunar algorithm after momentkh (MIT)",
                    style = HeritageType.micro, color = p.inkFaint, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
            }
        }
    }
}
