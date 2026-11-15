package io.github.ebziboi.delta.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
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

    DeltaTheme(settings) {
        val colors = LocalDeltaColors.current
        SystemBars(light = colors.isLight)

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
                    else -> OnboardingScreen(vm, settings, health, requestAccess)
                }
            }
        }
    }
}

private data class Tab(val label: String)

private val TABS = listOf(Tab("Progress"), Tab("History"), Tab("Settings"))

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
    var logging by remember { mutableStateOf(false) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = Color(colors.text),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            GlassBottomNavBar {
                TABS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            when (index) {
                                0 -> DeltaIcon()
                                1 -> Icon(Icons.Filled.DateRange, contentDescription = null)
                                else -> Icon(Icons.Filled.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(colors.accent),
                            selectedTextColor = Color(colors.accent),
                            indicatorColor = Color(colors.surfaceHigh),
                            unselectedIconColor = Color(colors.textDim),
                            unselectedTextColor = Color(colors.textDim),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                AccentButton(
                    text = "Log weight", icon = io.github.ebziboi.delta.R.drawable.ic_add,
                    modifier = Modifier.semantics { contentDescription = "Log weight" },
                    onClick = { if (health?.canWrite == false) requestAccess() else logging = true },
                )
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 12.dp).clipToBounds().drawWithContent {
            drawContent()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f)),
                startY = (size.height - 180.dp.toPx()).coerceAtLeast(0f), endY = size.height))
        }) {
            when (tab) {
                0 -> ProgressScreen(vm, settings, health, padding, requestAccess, onOpenSettings = { tab = 2 })
                1 -> HistoryScreen(vm, settings, padding)
                else -> SettingsScreen(vm, settings, health, padding, requestAccess)
            }
        }
    }

    if (logging) {
        LogWeightDialog(
            lastKg = snap?.currentKg,
            unit = settings.unit,
            onDismiss = { logging = false },
            onConfirm = { kg, at ->
                logging = false
                vm.logWeight(kg, at)
            },
        )
    }
}

/** The Progress tab's icon: a small delta, tinted like any other icon. */
@Composable
private fun DeltaIcon() {
    val tint = LocalContentColor.current
    Canvas(modifier = Modifier.size(24.dp)) {
        val inset = 3.5.dp.toPx()
        val outline = Path().apply {
            moveTo(size.width / 2f, inset)
            lineTo(size.width - inset + 1f, size.height - inset)
            lineTo(inset - 1f, size.height - inset)
            close()
        }
        drawPath(
            path = outline,
            color = tint,
            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
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
