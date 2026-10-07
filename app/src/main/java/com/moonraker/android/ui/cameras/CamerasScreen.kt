package com.moonraker.android.ui.cameras

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.ui.camera.CameraPlayer
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamerasScreen(viewModel: CamerasViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var fullscreen by remember { mutableStateOf<WebcamEntry?>(null) }
    KeepScreenOn(enabled = ui.keepScreenOn && ui.webcams.isNotEmpty())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AppBarTitle(
                        title = "Cameras",
                        subtitle = ui.connectionMessage,
                        subtitleColor = if (ui.connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                },
                actions = {
                    PrinterSwitcher(
                        printers = ui.printers,
                        activeId = ui.activePrinter?.id,
                        onSelect = viewModel::setActivePrinter,
                    )
                },
            )
        },
    ) { padding ->
        when {
            ui.activePrinter == null -> EmptyState(
                message = "Add a printer in Settings",
                modifier = Modifier.padding(padding),
            )
            ui.loading -> EmptyState(
                message = "Loading cameras…",
                modifier = Modifier.padding(padding),
            )
            ui.error != null && ui.webcams.isEmpty() -> EmptyState(
                message = ui.error ?: "Error",
                modifier = Modifier.padding(padding),
            )
            ui.webcams.isEmpty() -> EmptyState(
                message = "No cameras configured on this printer",
                modifier = Modifier.padding(padding),
            )
            else -> {
                val active = ui.activePrinter
                if (active == null) {
                    EmptyState(
                        message = "Add a printer in Settings",
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(ui.webcams, key = { it.uid.ifBlank { it.name } }) { cam ->
                            val index = ui.webcams.indexOf(cam)
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { fullscreen = cam },
                            ) {
                                Column {
                                    CameraPlayer(
                                        printer = active,
                                        webcam = cam,
                                        client = viewModel.httpClient,
                                        resolveUrl = { viewModel.resolveUrl(it) },
                                        isPrinterActive = ui.printActive,
                                        streamFps = ui.streamFps,
                                        preferSnapshot = ui.preferSnapshot,
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = cam.name.ifBlank { "Camera" },
                                            style = MaterialTheme.typography.titleSmall,
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(12.dp),
                                        )
                                        IconButton(
                                            onClick = { viewModel.moveCamera(index, -1) },
                                            enabled = index > 0,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                                        }
                                        IconButton(
                                            onClick = { viewModel.moveCamera(index, 1) },
                                            enabled = index < ui.webcams.lastIndex,
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val fullscreenCam = fullscreen
    val printer = ui.activePrinter
    if (fullscreenCam != null && printer != null) {
        Dialog(
            onDismissRequest = { fullscreen = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { fullscreen = null },
                contentAlignment = Alignment.Center,
            ) {
                CameraPlayer(
                    printer = printer,
                    webcam = fullscreenCam,
                    client = viewModel.httpClient,
                    resolveUrl = { viewModel.resolveUrl(it) },
                    isPrinterActive = ui.printActive,
                    streamFps = ui.streamFps,
                    preferSnapshot = ui.preferSnapshot,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
