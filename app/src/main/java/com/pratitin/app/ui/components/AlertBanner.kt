package com.pratitin.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pratitin.app.audio.LoveSound
import com.pratitin.app.data.Lang
import com.pratitin.app.notify.InAppAlerts
import com.pratitin.app.notify.Reminders
import com.pratitin.app.ui.LocalStrings
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay

/**
 * Pops reminders up at the top of the screen while the app is open: slides down, chimes
 * (anniversaries) or ticks (everything else), stays 7 s, swipe up or ✕ to dismiss, tap to
 * open the day / tab it's about. Several in a row queue up and show one after another.
 */
@Composable
fun AlertBanner(onOpen: (InAppAlerts.Alert) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val km = LocalStrings.current.lang == Lang.KM
    val ctx = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val queue = remember { mutableStateListOf<InAppAlerts.Alert>() }
    var showing by remember { mutableStateOf<InAppAlerts.Alert?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { InAppAlerts.alerts.collect { queue += it } }
    LaunchedEffect(queue.size, showing) {
        if (showing == null && queue.isNotEmpty()) {
            val a = queue.removeAt(0)
            showing = a; visible = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (a.channel == Reminders.CH_LOVE) LoveSound.play(ctx, LoveSound.Sfx.CHIME)
        }
    }
    LaunchedEffect(showing) {
        if (showing != null) { delay(7000); visible = false }
    }
    LaunchedEffect(visible) {
        if (!visible && showing != null) { delay(350); showing = null }
    }

    Box(modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) { -it * 2 } + fadeIn(),
            exit = slideOutVertically(spring(stiffness = 600f)) { -it * 2 } + fadeOut(),
        ) {
            val a = showing ?: return@AnimatedVisibility
            val shape = RoundedCornerShape(26.dp)
            val accent = if (a.channel == Reminders.CH_LOVE) p.lotus else p.gold
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer { this.shape = shape; clip = true; shadowElevation = 14.dp.toPx() }
                    .background(if (p.isDark) p.nightDeep.copy(alpha = 0.97f) else p.night.copy(alpha = 0.98f))
                    .border(1.dp, accent.copy(alpha = 0.6f), shape)
                    .pointerInput(a) { detectVerticalDragGestures { _, dy -> if (dy < -8f) visible = false } }
                    .clickable { visible = false; onOpen(a) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp, 40.dp).clip(RoundedCornerShape(3.dp)).background(accent))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.title, style = HeritageType.heading.copy(fontSize = HeritageType.heading.fontSize * 0.9f), color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (a.text.isNotBlank()) Text(a.text, style = HeritageType.label, color = p.inkSoft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (km) "ចុចដើម្បីបើក" else "Tap to open", style = HeritageType.micro, color = accent, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("✕", style = HeritageType.heading, color = p.inkSoft, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { visible = false }.padding(8.dp))
            }
        }
    }
}
