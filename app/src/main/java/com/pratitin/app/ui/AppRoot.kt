package com.pratitin.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratitin.app.AppViewModel
import com.pratitin.app.data.Lang
import com.pratitin.app.data.ThemeMode
import com.pratitin.app.ui.background.HeritageSky
import com.pratitin.app.ui.components.Icons
import com.pratitin.app.ui.glass.Backdrop
import com.pratitin.app.ui.glass.LocalEnclosingBackdrop
import com.pratitin.app.ui.glass.backdropSource
import com.pratitin.app.ui.glass.glass
import com.pratitin.app.ui.glass.rememberBackdrop
import com.pratitin.app.ui.motion.LocalReduceMotion
import com.pratitin.app.ui.motion.Motion
import com.pratitin.app.ui.motion.rememberSystemReduceMotion
import com.pratitin.app.ui.screens.calendar.CalendarScreen
import com.pratitin.app.ui.screens.detail.DayDetailSheet
import com.pratitin.app.ui.screens.holidays.HolidaysScreen
import com.pratitin.app.ui.screens.life.LifeScreen
import com.pratitin.app.ui.screens.mood.MoodScreen
import com.pratitin.app.ui.screens.settings.SettingsSheet
import com.pratitin.app.ui.screens.today.TodayScreen
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.ui.theme.PratitinTheme

enum class Tab { TODAY, CALENDAR, HOLIDAYS, MOOD, LIFE }

/** Everything a screen needs from the shell, bundled so signatures stay small. */
class ShellState(
    val vm: AppViewModel,
    val bg: Backdrop,
    val content: Backdrop,
    val openDay: (date: Int, origin: Rect?) -> Unit,
    val openSettings: () -> Unit,
    val goTo: (Tab) -> Unit,
    val reportScroll: (Float) -> Unit,
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val settings = vm.settings.collectAsStateWithLifecycle().value ?: return
    val dark = when (settings.theme) {
        ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        activity?.enableEdgeToEdge(style, style)
    }
    val reduce = rememberSystemReduceMotion()

    PratitinTheme(dark) {
        CompositionLocalProvider(
            LocalStrings provides if (settings.lang == Lang.KM) KhmerStrings.copy(khmerDigits = settings.khmerNumerals) else EnglishStrings,
            LocalReduceMotion provides reduce,
        ) { Shell(vm, animateSky = settings.livingSky && !reduce) }
    }
}

@Composable
private fun Shell(vm: AppViewModel, animateSky: Boolean) {
    val bg = rememberBackdrop(32.dp)
    val content = rememberBackdrop(24.dp)
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var detailDate by rememberSaveable { mutableIntStateOf(0) }
    var detailOrigin by remember { mutableStateOf<Rect?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = remember { mutableFloatStateOf(0f) }
    val holder = rememberSaveableStateHolder()

    val shell = remember(vm, bg, content) {
        ShellState(
            vm, bg, content,
            openDay = { d, o -> detailDate = d; detailOrigin = o; detailOpen = true },
            openSettings = { settingsOpen = true },
            goTo = { tab = it },
            reportScroll = { scroll.floatValue = it },
        )
    }

    BackHandler(enabled = detailOpen || settingsOpen || tab != Tab.TODAY) {
        when {
            detailOpen -> detailOpen = false
            settingsOpen -> settingsOpen = false
            else -> tab = Tab.TODAY
        }
    }

    Box(Modifier.fillMaxSize().background(LocalPalette.current.night)) {
        // Layer 1+2 — everything that glass bars and sheets look through
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            // Layer 1 — the sky that cards look through
            Box(Modifier.fillMaxSize().backdropSource(bg)) {
                // Own RenderNode: the 30 fps sky re-records only itself, never the screens on top.
                HeritageSky(animate = animateSky, parallax = { scroll.floatValue }, modifier = Modifier.graphicsLayer())
            }
            CompositionLocalProvider(LocalEnclosingBackdrop provides content) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(spring(stiffness = 300f)) + slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = 260f)) { it / 6 * dir } +
                        scaleIn(spring(stiffness = 300f), initialScale = 0.96f)) togetherWith
                        (fadeOut(spring(stiffness = 600f)) + slideOutHorizontally(spring(stiffness = 400f)) { -it / 8 * dir } +
                            scaleOut(spring(stiffness = 500f), targetScale = 0.98f))
                },
                label = "tabs",
                // Own RenderNode: screen content is not re-drawn when the sky animates.
                modifier = Modifier.fillMaxSize().graphicsLayer(),
            ) { t ->
                holder.SaveableStateProvider(t.name) {
                    when (t) {
                        Tab.TODAY -> TodayScreen(shell)
                        Tab.CALENDAR -> CalendarScreen(shell)
                        Tab.HOLIDAYS -> HolidaysScreen(shell)
                        Tab.MOOD -> MoodScreen(shell)
                        Tab.LIFE -> LifeScreen(shell)
                    }
                }
            }
            }
        }

        LiquidTabBar(
            selected = tab, backdrop = content,
            onSelect = { tab = it; scroll.floatValue = 0f },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp),
        )

        // Scrim + sheets
        val scrimAlpha by animateFloatAsState(if (detailOpen || settingsOpen) 1f else 0f, Motion.gentle, label = "scrim")
        if (scrimAlpha > 0.01f) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f * scrimAlpha))
                    .clickable(remember { MutableInteractionSource() }, null) { detailOpen = false; settingsOpen = false },
            )
        }
        AnimatedVisibility(
            visible = detailOpen,
            enter = fadeIn(spring(stiffness = 400f)), exit = fadeOut(spring(stiffness = 600f)),
        ) {
            DayDetailSheet(
                shell, date = detailDate, origin = detailOrigin,
                onDismiss = { detailOpen = false },
                onNavigate = { detailDate = it; detailOrigin = null },
            )
        }
        AnimatedVisibility(visible = settingsOpen, enter = fadeIn(), exit = fadeOut()) {
            SettingsSheet(shell, onDismiss = { settingsOpen = false })
        }
    }
}

// ------------------------------------------------------------------ liquid tab bar

@Composable
private fun LiquidTabBar(selected: Tab, backdrop: Backdrop, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    val items = remember(s) {
        listOf(
            Triple(Tab.TODAY, s.tabToday, Icons.today), Triple(Tab.CALENDAR, s.tabCalendar, Icons.calendar),
            Triple(Tab.HOLIDAYS, s.tabHolidays, Icons.lotus), Triple(Tab.MOOD, s.tabMood, Icons.mood),
            Triple(Tab.LIFE, s.tabLife, Icons.life),
        )
    }
    // Leading edge springs fast, trailing edge lags: the indicator stretches like a droplet in motion.
    val target = selected.ordinal.toFloat()
    val fast by animateFloatAsState(target, spring(dampingRatio = 0.62f, stiffness = 520f), label = "fast")
    val slow by animateFloatAsState(target, spring(dampingRatio = 0.78f, stiffness = 180f), label = "slow")

    Box(
        modifier.fillMaxWidth().height(70.dp)
            .glass(backdrop, Squircles.pill, liquid = true, cornerRadius = 35.dp, tint = p.glassTint.copy(alpha = p.glassTint.alpha * 1.4f))
            .clip(Squircles.pill),
    ) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val w = size.width / items.size
            val left = minOf(fast, slow) * w
            val right = (maxOf(fast, slow) + 1f) * w
            val stretch = (right - left - w) / w
            val squeeze = 1f - stretch.coerceIn(0f, 1f) * 0.18f
            val h = size.height * squeeze
            val top = (size.height - h) / 2
            drawRoundRect(
                Brush.verticalGradient(listOf(p.gold.copy(alpha = 0.42f), p.gold.copy(alpha = 0.18f)), startY = top, endY = top + h),
                topLeft = Offset(left, top), size = Size(right - left, h), cornerRadius = CornerRadius(h / 2),
            )
            drawRoundRect(
                p.goldSoft.copy(alpha = 0.55f), topLeft = Offset(left, top), size = Size(right - left, h),
                cornerRadius = CornerRadius(h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
        Row(Modifier.fillMaxSize().padding(6.dp)) {
            items.forEach { (t, label, icon) ->
                val sel = t == selected
                val lift by animateFloatAsState(if (sel) 1f else 0f, Motion.bouncy, label = "lift")
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(Squircles.pill)
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) {
                            if (t != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(t)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val tint = lerpColor(p.inkSoft, if (p.isDark) p.goldSoft else p.goldDeep, lift)
                    Canvas(Modifier.size(22.dp).graphicsLayer { val k = 1f + 0.12f * lift; scaleX = k; scaleY = k }) { icon(tint) }
                    Text(label, style = HeritageType.micro, color = tint, maxLines = 1)
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

