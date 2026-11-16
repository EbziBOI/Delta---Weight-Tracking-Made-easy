package io.github.ebziboi.delta.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ebziboi.delta.data.AppSettings
import io.github.ebziboi.delta.data.HealthAvailability
import io.github.ebziboi.delta.data.HealthState

/** The whole app: theme, first-run set-up, then the three tabs. */
@Composable
fun DeltaRoot(vm: AppViewModel = viewModel()) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    // Keep the loading surface still until saved appearance preferences arrive.
    val settings = loaded ?: AppSettings(appearance = io.github.ebziboi.delta.data.AppAppearance(reduceMotion = true))

    val preview by vm.appearancePreview.collectAsStateWithLifecycle()
    DeltaTheme(if (preview == null) settings else settings.copy(appearance = preview)) {
        val colors = LocalDeltaColors.current
        SystemBars(light = if (settings.onboardingDone) false else colors.isLight)

        val health by vm.health.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }

        // Health Connect shows its own permission screen; when it closes, look again and sync.
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract(),
        ) { vm.refresh(announce = true) }

        val requestAccess: () -> Unit = {
            val missing = (health ?: HealthState(HealthAvailability.AVAILABLE)).missing
            if (missing.isNotEmpty()) {
                // If nothing on the phone can show the permission screen, nothing happens here
                // and the Health Connect card explains what is wrong.
                runCatching { permissionLauncher.launch(missing) }
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

        // The surface sets the text colour for everything inside it; without it text defaults to black.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(colors.bg),
            contentColor = Color(colors.text),
        ) {
            GlassBackground {
                when {
                    loaded == null -> androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(24.dp)) {
                        androidx.compose.foundation.layout.Spacer(Modifier.size(56.dp))
                        ShimmerPlaceholder()
                    }
                    settings.onboardingDone -> MainScreen(vm, settings, health, snackbar, requestAccess)
                    io.github.ebziboi.delta.BuildConfig.IS_BETA -> io.github.ebziboi.delta.transfer.BetaWelcome(vm,health,requestAccess)
                    else -> OnboardingScreen(vm, settings, health, requestAccess)
                }
            }
        }
    }
}

private data class Tab(val label: String, val id: Int)

private val TABS = listOf(Tab("Progress",0), Tab("Diary",3), Tab("History",1))

@Composable
private fun MainScreen(
    vm: AppViewModel,
    settings: AppSettings,
    health: HealthState?,
    snackbar: SnackbarHostState,
    requestAccess: () -> Unit,
) {
    val colors = LocalDeltaColors.current
    var tab by rememberSaveable { mutableStateOf(0) }
    val screens = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var metric by rememberSaveable { mutableStateOf<io.github.ebziboi.delta.data.DetailMetric?>(null) }
    var detailParent by rememberSaveable { mutableStateOf<io.github.ebziboi.delta.data.DetailMetric?>(null) }
    val closeDetail: () -> Unit = {metric=detailParent;detailParent=null}
    var logging by remember { mutableStateOf(false) }
    var foodAddSignal by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var nutritionDetail by remember { mutableStateOf(false) }
    val weights by vm.entries.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()

    var dockVisible by remember(tab) { mutableStateOf(true) }
    val threshold = with(LocalDensity.current) { 10.dp.toPx() }
    val dockScroll = remember(tab, threshold) { object : NestedScrollConnection {
        var travel = 0f
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && kotlin.math.abs(available.y) > kotlin.math.abs(available.x)) {
                if (travel * available.y < 0) travel = 0f
                travel += available.y
                if (kotlin.math.abs(travel) >= threshold) { dockVisible = travel > 0; travel = 0f }
            }
            return Offset.Zero
        }
    } }
    BackHandler(enabled = metric != null,onBack=closeDetail)
    BackHandler(enabled = tab == 2 || (tab == 3 && !nutritionDetail)) { tab = 0 }
    Scaffold(
        modifier = Modifier.nestedScroll(dockScroll),
        contentWindowInsets = WindowInsets(0),
        containerColor = Color.Transparent,
        contentColor = Color(colors.text),
        snackbarHost = { SnackbarHost(snackbar,Modifier.navigationBarsPadding().padding(bottom=if(dockVisible && metric==null) 72.dp else 8.dp)) },
    ) { scaffoldPadding ->
        val padding = PaddingValues(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        val hasActions = tab != 2 && metric == null && !(tab==3 && nutritionDetail)
        // Screens run the full height, so content scrolls on behind the floating glass bar
        // (their padding keeps the last item clear of it) instead of stopping at a hard edge.
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(scaffoldPadding)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalHeaderActionSpace provides if(hasActions) {if(tab==3) 52.dp else 104.dp} else 0.dp, LocalCardReordering provides {active -> if(active) dockVisible=false}) {
            if (metric != null) screens.SaveableStateProvider("detail-${metric!!.name}") { when(metric!!) {
                io.github.ebziboi.delta.data.DetailMetric.WEIGHT -> MeasurementDetailScreen(vm,settings,io.github.ebziboi.delta.data.MeasurementKind.WEIGHT,padding,onBack=closeDetail,onAccess=requestAccess)
                io.github.ebziboi.delta.data.DetailMetric.BODY_FAT -> MeasurementDetailScreen(vm,settings,io.github.ebziboi.delta.data.MeasurementKind.BODY_FAT,padding,onBack=closeDetail,onAccess=requestAccess)
                io.github.ebziboi.delta.data.DetailMetric.INSIGHTS -> InsightsDetailScreen(vm,settings,padding,closeDetail,{detailParent=io.github.ebziboi.delta.data.DetailMetric.INSIGHTS;metric=it})
                else -> MetricDetailScreen(vm,settings,metric!!,padding,closeDetail)
            } }
            else screens.SaveableStateProvider("tab-$tab") { when (tab) {
                0 -> ProgressScreen(vm, settings, health, padding, requestAccess, onOpenSettings = { tab = 2 }, onMetric = { detailParent=null;metric=it })
                1 -> HistoryScreen(vm, settings, padding)
                3 -> io.github.ebziboi.delta.nutrition.NutritionScreen(padding, foodAddSignal, {nutritionDetail=it;if(!it) dockVisible=true}, weights, weightUnit=settings.unit)
                else -> SettingsScreen(vm, settings, health, padding, requestAccess)
            }
            }
            }
            EdgeShading()
            if(hasActions) Row(Modifier.align(androidx.compose.ui.Alignment.TopEnd).statusBarsPadding().padding(top=8.dp,end=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(tab!=3) GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_sync,"Sync with Health Connect",{vm.refresh(announce=true)})
                GlassIconButton(io.github.ebziboi.delta.R.drawable.ic_settings,"Settings",{metric=null;tab=2})
            }
            AnimatedVisibility(dockVisible && metric == null && !(tab==3 && nutritionDetail), modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
                enter = if (LocalMotion.current) slideInVertically { it } else androidx.compose.animation.EnterTransition.None,
                exit = if (LocalMotion.current) slideOutVertically { it } else androidx.compose.animation.ExitTransition.None) {
            GlassBottomNavBar(itemCount=TABS.size, logLabel=if(tab==3) "Add food" else "Log weight", onLog = if (tab != 2) ({ if(tab==3) foodAddSignal++ else if (!io.github.ebziboi.delta.BuildConfig.IS_BETA && health?.canWrite == false) requestAccess() else logging = true }) else null) {
                TABS.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item.id,
                        onClick = { metric = null; tab = item.id },
                        icon = {
                            when (item.id) {
                                0 -> Icon(androidx.compose.ui.res.painterResource(io.github.ebziboi.delta.R.drawable.ic_delta_mark), contentDescription = null, modifier = Modifier.size(20.dp))
                                3 -> Icon(androidx.compose.ui.res.painterResource(io.github.ebziboi.delta.R.drawable.ic_food), contentDescription = null, modifier = Modifier.size(20.dp))
                                1 -> Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(20.dp))
                                else -> Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(item.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(colors.accent),
                            selectedTextColor = Color(colors.accent),
                            // No pill behind the selected tab: the accent colour alone shows which one is active.
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = Color(colors.textDim),
                            unselectedTextColor = Color(colors.textDim),
                        ),
                    )
                }
            }
            }

        }
    }

    if (logging) {
        LogWeightDialog(
            lastKg = snap?.currentKg,
            unit = settings.unit,
            onDismiss = { logging = false },
            onConfirm = { kg, at, fat ->
                logging = false
                vm.logWeight(kg, at, fat)
            },
            canWriteBodyFat = io.github.ebziboi.delta.BuildConfig.IS_BETA || health?.canWriteBodyFat == true,
            onBodyFatAccess = requestAccess,
        )
    }
}

/**
 * Soft shading at the top and bottom of the screen, as One UI does: content fades
 * into the background under the status bar and behind the tab bar, which shows
 * where the screen ends and that there is more to scroll.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.EdgeShading() {
    val edge = Color.Black
    // Identical short fades, retaining fully black ends and the dense midpoint.
    val length = 44.dp
    androidx.compose.foundation.layout.Box(
        Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().height(length)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.88f), Color.Transparent))),
    )
    androidx.compose.foundation.layout.Box(
        Modifier.align(androidx.compose.ui.Alignment.BottomCenter).fillMaxWidth().height(length)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, edge.copy(alpha = 0.88f), edge))),
    )
}

/** Dark themes want light status-bar icons and light themes dark ones. */
@Composable
internal fun SystemBars(light: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(light) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (light) SystemBarStyle.light(clear, clear) else SystemBarStyle.dark(clear)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
