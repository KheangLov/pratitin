package com.pratitin.app.ui.screens.settings

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

@Composable
fun SettingsSheet(shell: ShellState, onDismiss: () -> Unit) {
    val settings = shell.vm.settings.collectAsStateWithLifecycle().value ?: return
    val s = LocalStrings.current
    val p = LocalPalette.current
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 300f)) }
    fun upd(t: (com.pratitin.app.data.Settings) -> com.pratitin.app.data.Settings) = shell.vm.updateSettings(t)

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
