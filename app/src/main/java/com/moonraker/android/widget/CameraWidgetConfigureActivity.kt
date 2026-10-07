package com.moonraker.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.moonraker.android.MoonrakerApp
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.WidgetBinding
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.theme.MoonrakerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

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
                ConfigureContent(
                    printers = settings.printers,
                    onPick = { printer, cam ->
                        val binding = WidgetBinding(
                            appWidgetId = appWidgetId,
                            printerId = printer.id,
                            webcamUid = cam.uid,
                            webcamName = cam.name,
                        )
                        lifecycleScope.launch {
                            app.settingsRepository.saveWidgetBinding(binding)
                            CameraSnapshotWidgetReceiver.refreshWidget(
                                this@CameraWidgetConfigureActivity,
                                AppWidgetManager.getInstance(this@CameraWidgetConfigureActivity),
                                binding.appWidgetId,
                                showLoading = true,
                            )
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                            )
                            finish()
                        }
                    },
                    loadWebcams = { printer ->
                        withContext(Dispatchers.IO) {
                            app.moonrakerRepository.listWebcams(printer)
                                .getOrElse { emptyList() }
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureContent(
    printers: List<PrinterConfig>,
    onPick: (PrinterConfig, WebcamEntry) -> Unit,
    loadWebcams: suspend (PrinterConfig) -> List<WebcamEntry>,
) {
    var selectedPrinter by remember { mutableStateOf<PrinterConfig?>(null) }
    var webcams by remember { mutableStateOf<List<WebcamEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { AppBarTitle(title = "Configure camera widget") })
        },
    ) { padding ->
        when {
            printers.isEmpty() -> {
                Text(
                    "Add a printer in the app first.",
                    modifier = Modifier.padding(padding).padding(24.dp),
                )
            }
            selectedPrinter == null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Text("Choose printer", style = MaterialTheme.typography.titleMedium) }
                    items(printers) { printer ->
                        Surface(
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPrinter = printer
                                    loading = true
                                },
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(printer.name, style = MaterialTheme.typography.titleMedium)
                                Text(printer.baseUrl, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                val printer = selectedPrinter!!
                LaunchedEffect(printer.id) {
                    loading = true
                    webcams = loadWebcams(printer)
                    loading = false
                }
                if (loading) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "Choose camera on ${printer.name}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (webcams.isEmpty()) {
                            item { Text("No cameras found on this printer.") }
                        }
                        items(webcams) { cam ->
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(printer, cam) },
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = cam.name.ifBlank { "Camera" },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(cam.service, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
