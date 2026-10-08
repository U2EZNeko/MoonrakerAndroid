package com.moonraker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moonraker.android.data.settings.AppTabs
import com.moonraker.android.ui.cameras.CamerasScreen
import com.moonraker.android.ui.cameras.CamerasViewModel
import com.moonraker.android.ui.console.ConsoleScreen
import com.moonraker.android.ui.console.ConsoleViewModel
import com.moonraker.android.ui.dashboard.DashboardScreen
import com.moonraker.android.ui.dashboard.DashboardViewModel
import com.moonraker.android.ui.fans.FansScreen
import com.moonraker.android.ui.fans.FansViewModel
import com.moonraker.android.ui.firmware.FirmwareScreen
import com.moonraker.android.ui.firmware.FirmwareViewModel
import com.moonraker.android.ui.gcode.GcodeScreen
import com.moonraker.android.ui.gcode.GcodeViewModel
import com.moonraker.android.ui.more.MoreScreen
import com.moonraker.android.ui.more.MoreViewModel
import com.moonraker.android.ui.print.PrintScreen
import com.moonraker.android.ui.print.PrintViewModel
import com.moonraker.android.ui.settings.SettingsScreen
import com.moonraker.android.ui.settings.SettingsViewModel
import com.moonraker.android.ui.temps.TempsScreen
import com.moonraker.android.ui.temps.TempsViewModel
import com.moonraker.android.ui.theme.MoonrakerTheme
import com.moonraker.android.ui.timelapse.TimelapseScreen
import com.moonraker.android.ui.timelapse.TimelapseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MoonrakerApp
        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.moonraker.android.data.settings.AppSettings(),
            )
            MoonrakerTheme(
                themeMode = settings.themeMode,
                accentArgb = settings.accentColorArgb,
                useDynamicColor = settings.useDynamicColor,
            ) {
                MoonrakerRoot(
                    app = app,
                    tabOrder = settings.tabOrder,
                    bottomTabs = settings.bottomTabs,
                )
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val allTabs = mapOf(
    AppTabs.DASHBOARD to Tab(AppTabs.DASHBOARD, "Home", Icons.Default.Dashboard),
    AppTabs.CAMERAS to Tab(AppTabs.CAMERAS, "Cameras", Icons.Default.Videocam),
    AppTabs.TEMPS to Tab(AppTabs.TEMPS, "Temps", Icons.Default.Thermostat),
    AppTabs.PRINT to Tab(AppTabs.PRINT, "Print", Icons.Default.Print),
    AppTabs.TIMELAPSE to Tab(AppTabs.TIMELAPSE, "Lapse", Icons.Default.Movie),
    AppTabs.GCODE to Tab(AppTabs.GCODE, "G-code", Icons.Default.ViewInAr),
    AppTabs.FANS to Tab(AppTabs.FANS, "Fans", Icons.Default.Toys),
    AppTabs.CONSOLE to Tab(AppTabs.CONSOLE, "Console", Icons.Default.Terminal),
    AppTabs.FIRMWARE to Tab(AppTabs.FIRMWARE, "Firmware", Icons.Default.Memory),
    AppTabs.SETTINGS to Tab(AppTabs.SETTINGS, "Settings", Icons.Default.Settings),
    AppTabs.MORE to Tab(AppTabs.MORE, "More", Icons.Default.MoreHoriz),
)

@Composable
private fun MoonrakerRoot(
    app: MoonrakerApp,
    tabOrder: List<String>,
    bottomTabs: List<String>,
) {
    val navController = rememberNavController()
    val ordered = remember(tabOrder) { AppTabs.normalize(tabOrder) }
    val visibleSet = remember(bottomTabs) { AppTabs.normalizeBottomTabs(bottomTabs).toSet() }
    val barTabs = remember(ordered, visibleSet) {
        ordered.filter { it in visibleSet }.mapNotNull { allTabs[it] } +
            listOfNotNull(allTabs[AppTabs.MORE])
    }
    val startDestination = ordered.firstOrNull { it in visibleSet } ?: AppTabs.MORE
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val barRoutes = remember(barTabs) { barTabs.map { it.route }.toSet() }
    val selectedBarRoute = when {
        current == null -> barTabs.firstOrNull()?.route
        current in barRoutes -> current
        else -> AppTabs.MORE
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                barTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedBarRoute == tab.route,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppTabs.DASHBOARD) {
                val vm: DashboardViewModel = viewModel(
                    factory = DashboardViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                DashboardScreen(vm)
            }
            composable(AppTabs.CAMERAS) {
                val vm: CamerasViewModel = viewModel(
                    factory = CamerasViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                CamerasScreen(vm)
            }
            composable(AppTabs.TEMPS) {
                val vm: TempsViewModel = viewModel(
                    factory = TempsViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                TempsScreen(vm)
            }
            composable(AppTabs.PRINT) {
                val vm: PrintViewModel = viewModel(
                    factory = PrintViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                PrintScreen(vm)
            }
            composable(AppTabs.TIMELAPSE) {
                val vm: TimelapseViewModel = viewModel(
                    factory = TimelapseViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                TimelapseScreen(vm)
            }
            composable(AppTabs.GCODE) {
                val vm: GcodeViewModel = viewModel(
                    factory = GcodeViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                GcodeScreen(vm)
            }
            composable(AppTabs.FANS) {
                val vm: FansViewModel = viewModel(
                    factory = FansViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                FansScreen(vm)
            }
            composable(AppTabs.CONSOLE) {
                val vm: ConsoleViewModel = viewModel(
                    factory = ConsoleViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                ConsoleScreen(vm)
            }
            composable(AppTabs.FIRMWARE) {
                val vm: FirmwareViewModel = viewModel(
                    factory = FirmwareViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                FirmwareScreen(vm)
            }
            composable(AppTabs.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                SettingsScreen(vm)
            }
            composable(AppTabs.MORE) {
                val vm: MoreViewModel = viewModel(
                    factory = MoreViewModel.factory(app.settingsRepository),
                )
                MoreScreen(
                    viewModel = vm,
                    onOpenTab = { route ->
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}
