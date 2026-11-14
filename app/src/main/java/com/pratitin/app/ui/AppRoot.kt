package com.pratitin.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import com.pratitin.app.ui.motion.wakesAmbient
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
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
import com.pratitin.app.data.Anniversary
import com.pratitin.app.data.EventKind
import com.pratitin.app.data.Lang
import com.pratitin.app.data.UserEvent
import com.pratitin.app.ui.screens.events.EventEditorSheet
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
import com.pratitin.app.ui.screens.love.AnniversaryEditorSheet
import com.pratitin.app.ui.screens.love.LoveProfileSheet
import com.pratitin.app.ui.screens.love.LoveScreen
import com.pratitin.app.ui.screens.mood.MoodScreen
import com.pratitin.app.ui.screens.settings.SettingsSheet
import com.pratitin.app.ui.screens.today.TodayScreen
import com.pratitin.app.ui.shape.Squircles
import com.pratitin.app.ui.theme.HeritageType
import com.pratitin.app.ui.theme.LocalPalette
import com.pratitin.app.ui.theme.PratitinTheme

enum class Tab { TODAY, CALENDAR, HOLIDAYS, MOOD, LOVE, LIFE }

/** Everything a screen needs from the shell, bundled so signatures stay small. */
class ShellState(
    val vm: AppViewModel,
    val bg: Backdrop,
    val content: Backdrop,
    val openDay: (date: Int, origin: Rect?) -> Unit,
    val openSettings: () -> Unit,
    val goTo: (Tab) -> Unit,
    val reportScroll: (Float) -> Unit,
    val editEvent: (UserEvent, Boolean) -> Unit,
    val openLoveEditor: (Anniversary, Boolean) -> Unit,
    val openLoveProfile: () -> Unit,
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
    val reduceMotion = com.pratitin.app.ui.motion.LocalReduceMotion.current
    val bg = rememberBackdrop(32.dp)
    val content = rememberBackdrop(24.dp)
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    var detailDate by rememberSaveable { mutableIntStateOf(0) }
    var detailOrigin by remember { mutableStateOf<Rect?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Pair<UserEvent, Boolean>?>(null) }
    var loveEditing by remember { mutableStateOf<Pair<Anniversary, Boolean>?>(null) }
    var loveProfileOpen by rememberSaveable { mutableStateOf(false) }
    val scroll = remember { mutableFloatStateOf(0f) }
    val holder = rememberSaveableStateHolder()

    val shell = remember(vm, bg, content) {
        ShellState(
            vm, bg, content,
            openDay = { d, o -> detailDate = d; detailOrigin = o; detailOpen = true },
            openSettings = { settingsOpen = true },
            goTo = { tab = it },
            reportScroll = { scroll.floatValue = it },
            editEvent = { e, isNew ->
                if (e.kind == EventKind.ANNIVERSARY) {
                    // anniversaries live in the Love tab
                    val a = vm.love.value.items.firstOrNull { it.id == -e.id }
                    detailOpen = false; tab = Tab.LOVE
                    if (a != null) loveEditing = a to false
                } else editing = e to isNew
            },
            openLoveEditor = { a, isNew -> loveEditing = a to isNew },
            openLoveProfile = { loveProfileOpen = true },
        )
    }

    // Notification tap → open that day's card.
    val pendingOpen by vm.pendingOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pendingOpen) {
        if (pendingOpen != 0) {
            detailDate = pendingOpen; detailOrigin = null; detailOpen = true
            vm.pendingOpen.value = 0
        }
    }

    // First launch: ask once (Android's own popup) to allow notifications, and turn reminders on.
    val ctx = LocalContext.current
    val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.updateSettings { it.copy(notify = true) } }
    LaunchedEffect(Unit) {
        if (!com.pratitin.app.notify.InAppAlerts.askedPermission(ctx)) {
            com.pratitin.app.notify.InAppAlerts.setAskedPermission(ctx)
            kotlinx.coroutines.delay(1200) // after the splash and the first frame
            if (android.os.Build.VERSION.SDK_INT >= 33 && !com.pratitin.app.notify.Reminders.canPost(ctx)) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else vm.updateSettings { it.copy(notify = true) }
        }
    }

    // Notification tap → open a tab (anniversary reminders open Love).
    val pendingTab by vm.pendingTab.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTab) {
        val pt = pendingTab
        if (pt != null) {
            detailOpen = false; settingsOpen = false; loveEditing = null; loveProfileOpen = false; editing = null
            when {
                pt == "love" -> tab = Tab.LOVE
                pt == "calendar" -> tab = Tab.CALENDAR
                pt == "holidays" -> tab = Tab.HOLIDAYS
                pt == "mood" -> tab = Tab.MOOD
                pt == "life" -> tab = Tab.LIFE
                pt == "today" -> tab = Tab.TODAY
                pt == "add_event" -> { tab = Tab.TODAY; editing = com.pratitin.app.ui.screens.events.newEvent(vm.today.value) to true }
                pt.startsWith("mood:") -> {
                    // one-tap mood from the widget
                    com.pratitin.app.data.Mood.entries.firstOrNull { it.name == pt.removePrefix("mood:") }?.let { vm.setMood(vm.today.value, it) }
                    tab = Tab.MOOD
                }
            }
            vm.pendingTab.value = null
        }
    }

    BackHandler(enabled = loveEditing != null || loveProfileOpen || editing != null || detailOpen || settingsOpen || tab != Tab.TODAY) {
        when {
            loveEditing != null -> loveEditing = null
            loveProfileOpen -> loveProfileOpen = false
            editing != null -> editing = null
            detailOpen -> detailOpen = false
            settingsOpen -> settingsOpen = false
            else -> tab = Tab.TODAY
        }
    }

    // ---- performance: one shared ambient clock (≈30 fps, sleeps after idle), paused under sheets
    val anySheet = detailOpen || settingsOpen || editing != null || loveEditing != null || loveProfileOpen
    val ambient = com.pratitin.app.ui.motion.rememberAmbientClock(running = !reduceMotion && !anySheet)
    // ---- performance: blur only the tab-bar strip unless a glass sheet needs the whole screen
    val density = androidx.compose.ui.platform.LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    var rootH by remember { mutableIntStateOf(0) }
    val glassSheet = settingsOpen || editing != null || loveEditing != null || loveProfileOpen
    androidx.compose.runtime.SideEffect {
        content.captureTop = if (glassSheet || rootH == 0) 0f
            else (rootH - navBottom - with(density) { 112.dp.toPx() }).coerceAtLeast(0f)
    }

    CompositionLocalProvider(com.pratitin.app.ui.motion.LocalAmbientClock provides if (reduceMotion) null else ambient) {
    Box(
        Modifier.fillMaxSize().background(LocalPalette.current.night)
            .onSizeChanged { rootH = it.height }
            .wakesAmbient(ambient),
    ) {
        // Layer 1+2 — everything that glass bars and sheets look through
        Box(Modifier.fillMaxSize().backdropSource(content)) {
            // Layer 1 — the sky that cards look through
            Box(Modifier.fillMaxSize().backdropSource(bg)) {
                // Own RenderNode: the 30 fps sky re-records only itself, never the screens on top.
                val settingsNow = vm.settings.collectAsStateWithLifecycle().value
                val todayNow by vm.today.collectAsStateWithLifecycle()
                val lunar = remember(todayNow) {
                    val k = com.pratitin.app.ui.screens.khmerOf(todayNow)
                    com.pratitin.app.ui.background.LunarInfo(k.illumination, k.dayNumber < 15, k.dayNumber, com.pratitin.khmer.KhmerCalendar.daysInMonth(k.monthIndex, k.beYear))
                }
                val scene = when (settingsNow?.scene ?: 0) {
                    1 -> com.pratitin.app.ui.background.Scene.DIAL
                    2 -> com.pratitin.app.ui.background.Scene.MANDALA
                    3 -> com.pratitin.app.ui.background.Scene.POND
                    else -> when (tab) {
                        Tab.TODAY -> com.pratitin.app.ui.background.Scene.DIAL
                        Tab.LOVE -> com.pratitin.app.ui.background.Scene.POND
                        else -> com.pratitin.app.ui.background.Scene.MANDALA
                    }
                }
                HeritageSky(scene, lunar, animate = animateSky && !anySheet, parallax = { scroll.floatValue }, modifier = Modifier.graphicsLayer())
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
                        Tab.LOVE -> LoveScreen(shell)
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
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = (if (detailOpen) 0.62f else 0.45f) * scrimAlpha))
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
        // Event editor sits above everything (it can be opened from the day sheet).
        val ed = editing
        if (ed != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .clickable(remember { MutableInteractionSource() }, null) { editing = null })
        }
        AnimatedVisibility(visible = ed != null, enter = fadeIn(), exit = fadeOut()) {
            val keep = remember { arrayOfNulls<Pair<UserEvent, Boolean>>(1) }
            if (ed != null) keep[0] = ed
            keep[0]?.let { (e, isNew) -> EventEditorSheet(shell, e, isNew, onDismiss = { editing = null }) }
        }
        // Love tab sheets
        val le = loveEditing
        if (le != null || loveProfileOpen) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(remember { MutableInteractionSource() }, null) { loveEditing = null; loveProfileOpen = false })
        }
        AnimatedVisibility(visible = le != null, enter = fadeIn(), exit = fadeOut()) {
            val keep = remember { arrayOfNulls<Pair<Anniversary, Boolean>>(1) }
            if (le != null) keep[0] = le
            keep[0]?.let { (a, isNew) -> AnniversaryEditorSheet(shell, a, isNew, onDismiss = { loveEditing = null }) }
        }
        AnimatedVisibility(visible = loveProfileOpen, enter = fadeIn(), exit = fadeOut()) {
            LoveProfileSheet(shell, onDismiss = { loveProfileOpen = false })
        }
        // Reminders that fire while the app is open pop up here.
        com.pratitin.app.ui.components.AlertBanner(
            onOpen = { a ->
                when {
                    a.tab != null -> vm.pendingTab.value = a.tab
                    a.date != 0 -> vm.pendingOpen.value = a.date
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    }
}

// ------------------------------------------------------------------ liquid tab bar

/**
 * Floating liquid-glass tab bar. Tap a tab, or press and slide: the gold lens follows your
 * finger (swelling slightly while held), ticks a haptic as it crosses each tab, and on
 * release snaps to the nearest one. Its two edges ride different springs, so it stretches
 * like a droplet when it travels.
 */
@Composable
private fun LiquidTabBar(selected: Tab, backdrop: Backdrop, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val s = LocalStrings.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val items = remember(s) {
        listOf(
            Triple(Tab.TODAY, s.tabToday, Icons.today), Triple(Tab.CALENDAR, s.tabCalendar, Icons.calendar),
            Triple(Tab.HOLIDAYS, s.tabHolidays, Icons.lotus), Triple(Tab.MOOD, s.tabMood, Icons.mood),
            Triple(Tab.LOVE, s.tabLove, Icons.heart), Triple(Tab.LIFE, s.tabLife, Icons.life),
        )
    }
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val lead = remember { Animatable(selected.ordinal.toFloat()) }
    val trail = remember { Animatable(selected.ordinal.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val hold by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "hold")

    fun settle(index: Int) {
        scope.launch { lead.animateTo(index.toFloat(), spring(dampingRatio = 0.62f, stiffness = 520f)) }
        scope.launch { trail.animateTo(index.toFloat(), spring(dampingRatio = 0.78f, stiffness = 180f)) }
    }
    LaunchedEffect(selected) { if (!dragging) settle(selected.ordinal) }

    val pad = 6.dp
    Box(
        modifier.fillMaxWidth().height(70.dp)
            .glass(backdrop, Squircles.pill, liquid = true, cornerRadius = 35.dp, tint = p.glassTint.copy(alpha = p.glassTint.alpha * 1.4f))
            .clip(Squircles.pill)
            .pointerInput(items.size) {
                val n = items.size
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val padPx = pad.toPx()
                    val w = (size.width - padPx * 2) / n
                    fun pos(x: Float) = ((x - padPx) / w - 0.5f).coerceIn(0f, n - 1f)
                    var at = pos(down.position.x)
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        // a tap
                        val t = items[at.roundToInt()].first
                        if (t != currentSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(t)
                        return@awaitEachGesture
                    }
                    dragging = true
                    var lastTab = at.roundToInt()
                    horizontalDrag(slop.id) { change ->
                        at = pos(change.position.x)
                        change.consume()
                        scope.launch { lead.snapTo(at) }
                        scope.launch { trail.animateTo(at, spring(dampingRatio = 0.85f, stiffness = 380f)) }
                        val nearest = at.roundToInt()
                        if (nearest != lastTab) { lastTab = nearest; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    }
                    dragging = false
                    val target = at.roundToInt()
                    settle(target)
                    currentOnSelect(items[target].first)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize().padding(pad)) {
            val w = size.width / items.size
            val a = minOf(lead.value, trail.value)
            val b = maxOf(lead.value, trail.value)
            val stretch = (b - a).coerceIn(0f, 1f)
            val squeeze = 1f - stretch * 0.16f + hold * 0.10f
            val h = (size.height * squeeze).coerceAtMost(size.height + 8.dp.toPx())
            val grow = hold * 6.dp.toPx()
            val left = a * w - grow
            val right = (b + 1f) * w + grow
            val top = (size.height - h) / 2
            val r = CornerRadius(h / 2)
            drawRoundRect(
                Brush.verticalGradient(listOf(p.gold.copy(alpha = 0.42f + 0.1f * hold), p.gold.copy(alpha = 0.16f)), startY = top, endY = top + h),
                topLeft = Offset(left, top), size = Size(right - left, h), cornerRadius = r,
            )
            // glassy highlight on the lens' upper half, stronger while held
            drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f + 0.22f * hold), Color.Transparent), startY = top, endY = top + h * 0.55f),
                topLeft = Offset(left + 3f, top + 2f), size = Size(right - left - 6f, h * 0.5f), cornerRadius = r,
            )
            drawRoundRect(
                p.goldSoft.copy(alpha = 0.55f + 0.35f * hold), topLeft = Offset(left, top), size = Size(right - left, h),
                cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke((1f + hold).dp.toPx()),
            )
        }
        Row(Modifier.fillMaxSize().padding(pad)) {
            items.forEachIndexed { i, (t, label, icon) ->
                // how close the lens is to this tab (0‥1) drives colour and lift
                val near = (1f - abs(lead.value - i)).coerceIn(0f, 1f)
                Column(
                    Modifier.weight(1f).fillMaxSize().semantics {
                        role = Role.Tab
                        this.selected = t == selected
                        contentDescription = label
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    val tint = lerpColor(p.inkSoft, if (p.isDark) p.goldSoft else p.goldDeep, near)
                    Canvas(Modifier.size(22.dp).graphicsLayer { val k = 1f + 0.14f * near + 0.08f * hold * near; scaleX = k; scaleY = k; translationY = -2.dp.toPx() * near }) { icon(tint) }
                    Text(label, style = HeritageType.micro, color = tint, maxLines = 1)
                }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t.coerceIn(0f, 1f))

