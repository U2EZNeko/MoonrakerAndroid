package com.moonraker.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Videocam
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
import com.moonraker.android.ui.dashboard.DashboardScreen
import com.moonraker.android.ui.dashboard.DashboardViewModel
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
    AppTabs.SETTINGS to Tab(AppTabs.SETTINGS, "Settings", Icons.Default.Settings),
)

@Composable
private fun MoonrakerRoot(
    app: MoonrakerApp,
    tabOrder: List<String>,
) {
    val navController = rememberNavController()
    val tabs = remember(tabOrder) {
        AppTabs.normalize(tabOrder).mapNotNull { allTabs[it] }
    }
    val startDestination = tabs.firstOrNull()?.route ?: AppTabs.DASHBOARD
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Child screens own TopAppBars (status-bar insets). Outer scaffold only
    // reserves space for the bottom nav — applying status-bar padding here too
    // left an empty gap above every title.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
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
            composable(AppTabs.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.factory(app.settingsRepository, app.moonrakerRepository),
                )
                SettingsScreen(vm)
            }
        }
    }
}
