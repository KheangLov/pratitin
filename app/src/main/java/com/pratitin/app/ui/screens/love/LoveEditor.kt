package com.pratitin.app.ui.screens.love

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.audio.LoveSound
import com.pratitin.app.data.AnnivKind
import com.pratitin.app.data.Anniversary
import com.pratitin.app.data.Lang
import com.pratitin.app.data.LoveMath
import com.pratitin.app.notify.Reminders
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.ShellState
import com.pratitin.app.ui.components.GlassCard
import com.pratitin.app.ui.components.GlassChip
import com.pratitin.app.ui.components.GlassIconButton
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.components.LiquidToggle
import com.pratitin.app.ui.components.SettingRow
import com.pratitin.app.ui.screens.gregorianLong
import com.pratitin.app.ui.screens.khmerOf
import com.pratitin.app.ui.screens.lunarLine
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.util.Day
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryEditorSheet(shell: ShellState, initial: Anniversary, isNew: Boolean, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val love by shell.vm.love.collectAsStateWithLifecycle()
    val today by shell.vm.today.collectAsStateWithLifecycle()
    var a by remember(initial.id) { mutableStateOf(initial) }
    var featured by remember(initial.id) { mutableStateOf(love.featured?.id == initial.id || love.items.isEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 320f)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    if (pickDate) {
        val st = rememberDatePickerState(
            initialSelectedDateMillis = Day.unpack(a.date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayMode = DisplayMode.Picker,
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { a = a.copy(date = Day.pack(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())) }
                    pickDate = false
                }) { Text(s.save, color = lp.rose) }
            },
            colors = DatePickerDefaults.colors(containerColor = p.nightDeep),
        ) {
            DatePicker(
                st, showModeToggle = false, // typed input crashes on some locales (e.g. Khmer); tap the month/year header to jump years
                colors = DatePickerDefaults.colors(containerColor = p.nightDeep, selectedDayContainerColor = lp.rose, todayDateBorderColor = lp.rose, selectedYearContainerColor = lp.rose),
            )
        }
    }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp), contentAlignment = Alignment.BottomCenter) {
        GlassCard(
            shell.content,
            Modifier.fillMaxWidth().graphicsLayer { translationY = rise.value * size.height }.navigationBarsPadding().imePadding(),
            radius = 36.dp, liquid = true, tint = p.night.copy(alpha = if (p.isDark) 0.66f else 0.8f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${a.kind.emoji} " + if (isNew) (if (km) "ខួបថ្មី" else "New anniversary") else (if (km) "កែប្រែខួប" else "Edit anniversary"),
                    style = HeritageType.title, color = p.ink, modifier = Modifier.weight(1f),
                )
                GlassIconButton(shell.content, onClick = onDismiss, size = 38.dp, icon = Icons.close)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // kind
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnnivKind.entries.forEach { k ->
                        GlassChip(if (km) k.km else k.en, a.kind == k, shell.content, accent = lp.rose, leading = k.emoji) { a = a.copy(kind = k) }
                    }
                }
                // title
                LoveField(a.title, if (km) "ចំណងជើង (ជម្រើស) — ${a.kind.km}" else "Title (optional) — ${a.kind.en}", singleLine = true, big = true) { a = a.copy(title = it) }
                Spacer(Modifier.height(10.dp))

                // date
                val k = remember(a.date) { khmerOf(a.date) }
                Row(
                    Modifier.fillMaxWidth().clip(Squircles.m).background(lp.rose.copy(alpha = 0.12f)).clickable { pickDate = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (km) "ថ្ងៃខែឆ្នាំ" else "Date", style = HeritageType.micro, color = lp.blush)
                        Text(gregorianLong(a.date, s), style = HeritageType.body, color = p.ink)
                        Text("🌕 " + lunarLine(k, s), style = HeritageType.micro, color = p.gold)
                    }
                    GlassIconButton(shell.content, onClick = { a = a.copy(date = Day.plusDays(a.date, -1)) }, size = 34.dp, icon = Icons.chevronLeft)
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(shell.content, onClick = { a = a.copy(date = Day.plusDays(a.date, 1)) }, size = 34.dp, icon = Icons.chevronRight)
                }
                // live preview of the count
                val e = remember(a.date, today) { LoveMath.elapsed(a.date, today) }
                Text(
                    if (a.date > today) (if (km) "នៅសល់ ${LoveMath.fmt(e.days, s.khmerDigits)} ថ្ងៃ" else "${LoveMath.fmt(e.days, false)} days to go")
                    else if (km) "💞 ${s.num(e.years)} ឆ្នាំ ${s.num(e.months)} ខែ ${s.num(e.monthDays)} ថ្ងៃ · ${LoveMath.fmt(e.days, s.khmerDigits)} ថ្ងៃ · ${LoveMath.fmt(e.weeks, s.khmerDigits)} សប្ដាហ៍"
                    else "💞 ${e.years}y ${e.months}m ${e.monthDays}d · ${LoveMath.fmt(e.days, false)} days · ${LoveMath.fmt(e.weeks, false)} weeks",
                    style = HeritageType.label, color = lp.blush, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )

                // solar or lunar
                Spacer(Modifier.height(12.dp))
                Text(if (km) "ប្រារព្ធតាម" else "Celebrate by", style = HeritageType.micro, color = p.inkSoft)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassChip(if (km) "សុរិយគតិ" else "Solar date", !a.lunar, shell.content, accent = lp.rose, leading = "☀️") { a = a.copy(lunar = false) }
                    GlassChip(if (km) "ចន្ទគតិខ្មែរ" else "Khmer lunar", a.lunar, shell.content, accent = p.gold, leading = "🌕") { a = a.copy(lunar = true) }
                }
                if (a.lunar) Text(
                    if (km) "រៀងរាល់ឆ្នាំ នៅ ${lunarLine(k, s)}" else "Every year on ${lunarLine(k, s)}",
                    style = HeritageType.micro, color = p.gold, modifier = Modifier.padding(top = 6.dp),
                )

                // show on the big heart
                SettingRow(if (km) "បង្ហាញលើបេះដូងធំ" else "Show on the big heart") { LiquidToggle(featured, { featured = it }) }

                // reminders
                SettingRow(if (km) "🔔 ជូនដំណឹង" else "🔔 Notify me") { LiquidToggle(a.remind, { a = a.copy(remind = it) }) }
                AnimatedVisibility(a.remind, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        Text(if (km) "រំលឹកជាមុន" else "Remind me ahead", style = HeritageType.micro, color = p.inkSoft)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0, 1, 3, 7, 14).forEach { d ->
                                val label = when (d) {
                                    0 -> if (km) "ថ្ងៃនោះតែម្ដង" else "Only on the day"
                                    7 -> if (km) "១ សប្ដាហ៍" else "1 week"
                                    14 -> if (km) "២ សប្ដាហ៍" else "2 weeks"
                                    else -> if (km) "${s.num(d)} ថ្ងៃ" else "$d day${if (d > 1) "s" else ""}"
                                }
                                GlassChip(label, a.daysBefore == d, shell.content, accent = p.jade) { a = a.copy(daysBefore = d) }
                            }
                        }
                        Text(
                            if (km) "ថ្ងៃខួប ម៉ោង ៨ ព្រឹក · ការរំលឹកជាមុន ម៉ោង ៧ ល្ងាច" else "On the day at 8 AM · heads-up at 7 PM",
                            style = HeritageType.micro, color = p.inkFaint, modifier = Modifier.padding(top = 6.dp),
                        )
                        if (a.kind != AnnivKind.PARTNER_BIRTHDAY) {
                            SettingRow(if (km) "🎉 ចំណុចសំខាន់ (១០០ ថ្ងៃ ១០០០ ថ្ងៃ…)" else "🎉 Milestones (100 days, 1,000 days…)") {
                                LiquidToggle(a.milestones, { a = a.copy(milestones = it) })
                            }
                            SettingRow(if (km) "💗 ខួបប្រចាំខែ" else "💗 Monthiversaries") { LiquidToggle(a.monthly, { a = a.copy(monthly = it) }) }
                        }
                    }
                }

                // note
                Spacer(Modifier.height(8.dp))
                LoveField(a.note, if (km) "អនុស្សាវរីយ៍ ឬកំណត់ចំណាំ…" else "A memory or a note…", singleLine = false) { a = a.copy(note = it) }

                // phone calendar
                Spacer(Modifier.height(12.dp))
                Text(
                    if (km) "📅 ដាក់ក្នុងប្រតិទិនទូរស័ព្ទ (Google Calendar…)" else "📅 Add to phone calendar (Google Calendar…)",
                    style = HeritageType.label, color = p.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(Squircles.pill).border(1.dp, lp.rose.copy(alpha = 0.5f), Squircles.pill)
                        .clickable { exportToCalendar(ctx, love, a, s) }.padding(vertical = 12.dp),
                )

                // actions
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isNew) {
                        Text(
                            if (confirmDelete) (if (km) "ប្រាកដទេ?" else "Sure?") else s.delete,
                            style = HeritageType.heading, color = p.holidayKhmer, textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).clip(Squircles.pill).background(p.holidayKhmer.copy(alpha = if (confirmDelete) 0.3f else 0.14f))
                                .clickable {
                                    if (!confirmDelete) confirmDelete = true
                                    else { shell.vm.deleteAnniversary(a.id); onDismiss() }
                                }.padding(vertical = 14.dp),
                        )
                    }
                    Text(
                        "💗 " + s.save, style = HeritageType.heading, color = Color.White, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(2f).clip(Squircles.pill).background(Brush.horizontalGradient(listOf(lp.rose, lp.wine)))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val saved = a.copy(title = a.title.trim(), note = a.note.trim())
                                shell.vm.saveAnniversary(saved)
                                val cur = love.featured?.id
                                if (featured && cur != saved.id) shell.vm.updateLove { it.copy(featuredId = saved.id) }
                                else if (!featured && cur == saved.id) shell.vm.updateLove { it.copy(featuredId = -1) }
                                LoveSound.play(ctx, LoveSound.Sfx.CELEBRATE, love.profile.sound)
                                if (saved.remind && Build.VERSION.SDK_INT >= 33 && !Reminders.canPost(ctx)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                onDismiss()
                            }.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Names, sound and a test of the anniversary notification. */
@Composable
fun LoveProfileSheet(shell: ShellState, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val lp = lovePalette()
    val s = LocalStrings.current
    val km = s.lang == Lang.KM
    val ctx = LocalContext.current
    val love by shell.vm.love.collectAsStateWithLifecycle()
    var you by remember { mutableStateOf(love.profile.you) }
    var partner by remember { mutableStateOf(love.profile.partner) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(Unit) { rise.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 320f)) }
    var denied by remember { mutableStateOf(false) }
    fun test() {
        val e = love.featured?.let { LoveMath.elapsed(it.date, Day.today()) }
        Reminders.show(
            ctx, 1_402_14, if (km) "💞 ${love.couple(true)}" else "💞 ${love.couple(false)}",
            if (e != null) (if (km) "${LoveMath.fmt(e.days, s.khmerDigits)} ថ្ងៃជាមួយគ្នា · នេះជាសំឡេងជូនដំណឹងខួបស្នេហា" else "${LoveMath.fmt(e.days, false)} days together · this is how anniversary reminders sound")
            else (if (km) "នេះជាសំឡេងជូនដំណឹងខួបស្នេហា" else "This is how anniversary reminders sound"),
            Reminders.CH_LOVE, Day.today(), "love",
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> denied = !ok; if (ok) test() }
    fun save() { shell.vm.updateLove { it.copy(you = you.trim(), partner = partner.trim()) } }

    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 40.dp), contentAlignment = Alignment.BottomCenter) {
        GlassCard(
            shell.content,
            Modifier.fillMaxWidth().graphicsLayer { translationY = rise.value * size.height }.navigationBarsPadding().imePadding(),
            radius = 36.dp, liquid = true, tint = p.night.copy(alpha = if (p.isDark) 0.66f else 0.8f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (km) "💞 យើងទាំងពីរ" else "💞 The two of us", style = HeritageType.title, color = p.ink, modifier = Modifier.weight(1f))
                GlassIconButton(shell.content, onClick = { save(); onDismiss() }, size = 38.dp, icon = Icons.close)
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                Text(if (km) "ឈ្មោះបង" else "Your name", style = HeritageType.micro, color = lp.blush)
                Spacer(Modifier.height(4.dp))
                LoveField(you, if (km) "ឧ. ឃាង" else "e.g. Kheang", singleLine = true, big = true) { you = it }
                Spacer(Modifier.height(10.dp))
                Text(if (km) "ឈ្មោះអូន" else "Her name", style = HeritageType.micro, color = lp.blush)
                Spacer(Modifier.height(4.dp))
                LoveField(partner, if (km) "ឈ្មោះភរិយា" else "Your wife's name", singleLine = true, big = true) { partner = it }

                Spacer(Modifier.height(8.dp))
                SettingRow(if (km) "🎵 តន្ត្រីស្នេហា (ប្រអប់តន្ត្រី)" else "🎵 Romantic music-box theme") {
                    LiquidToggle(love.profile.music, { v -> shell.vm.updateLove { it.copy(music = v) } })
                }
                SettingRow(if (km) "💓 សំឡេងចុចបេះដូង" else "💓 Heartbeat & sparkle sounds") {
                    LiquidToggle(love.profile.sound, { v -> shell.vm.updateLove { it.copy(sound = v) }; if (v) LoveSound.play(ctx, LoveSound.Sfx.TAP) })
                }
                Text(
                    if (km) "តន្ត្រីមិនលេងពេលអ្នកកំពុងស្ដាប់បទចម្រៀងផ្សេង ហើយឈប់ពេលអ្នកចាកចេញពីទំព័រនេះ។"
                    else "Music never plays over something you're already listening to, and stops when you leave this tab.",
                    style = HeritageType.micro, color = p.inkFaint,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (km) "🔔 សាកល្បងការជូនដំណឹង" else "🔔 Test the anniversary notification",
                    style = HeritageType.label, color = p.ink, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(Squircles.pill).border(1.dp, lp.rose.copy(alpha = 0.5f), Squircles.pill)
                        .clickable {
                            save()
                            if (Build.VERSION.SDK_INT >= 33 && !Reminders.canPost(ctx)) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else test()
                        }.padding(vertical = 12.dp),
                )
                if (denied) Text(s.permissionDenied, style = HeritageType.micro, color = p.holidayKhmer, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    "💗 " + s.save, style = HeritageType.heading, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clip(Squircles.pill).background(Brush.horizontalGradient(listOf(lp.rose, lp.wine)))
                        .clickable { save(); LoveSound.play(ctx, LoveSound.Sfx.CHIME, love.profile.sound); onDismiss() }.padding(vertical = 14.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LoveField(value: String, hint: String, singleLine: Boolean, big: Boolean = false, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    val lp = lovePalette()
    Box(Modifier.fillMaxWidth().clip(Squircles.m).background(lp.rose.copy(alpha = 0.10f)).padding(14.dp)) {
        val style = (if (big) HeritageType.heading else HeritageType.body).copy(color = p.ink)
        if (value.isEmpty()) Text(hint, style = style.copy(color = p.inkFaint, fontSize = if (big) 16.sp else style.fontSize))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine, textStyle = style,
            cursorBrush = SolidColor(lp.rose),
            modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.height(80.dp)),
        )
    }
}
