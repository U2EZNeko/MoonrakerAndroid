package com.moonraker.android.ui.fans

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.FanKind
import com.moonraker.android.data.moonraker.FanReading
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FansScreen(viewModel: FansViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBarTitle(title = "Fans") },
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

            !ui.connected && ui.fans.isEmpty() -> EmptyState(
                message = ui.connectionMessage,
                modifier = Modifier.padding(padding),
            )

            ui.fans.isEmpty() -> EmptyState(
                message = "No fans reported by Klipper yet",
                modifier = Modifier.padding(padding),
            )

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ui.message?.let { msg ->
                        item {
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(ui.fans, key = { it.objectName }) { fan ->
                        FanCard(
                            fan = fan,
                            busy = ui.pendingObject == fan.objectName,
                            onSetSpeed = { speed ->
                                viewModel.setFanSpeed(fan.objectName, speed)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FanCard(
    fan: FanReading,
    busy: Boolean,
    onSetSpeed: (Float) -> Unit,
) {
    var draft by remember(fan.objectName) { mutableFloatStateOf(fan.speed) }
    LaunchedEffect(fan.speed, busy) {
        if (!busy) draft = fan.speed
    }

    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fan.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = kindLabel(fan.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${(draft * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            fan.rpm?.let { rpm ->
                Text(
                    text = "%.0f RPM".format(rpm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (fan.controllable) {
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    onValueChangeFinished = { onSetSpeed(draft) },
                    valueRange = 0f..1f,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { preset ->
                        FilterChip(
                            selected = (draft * 100).roundToInt() == (preset * 100).roundToInt(),
                            onClick = {
                                draft = preset
                                onSetSpeed(preset)
                            },
                            enabled = !busy,
                            label = { Text("${(preset * 100).roundToInt()}%") },
                        )
                    }
                }
            } else {
                Text(
                    text = "Automatic — speed follows heater/temp control",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Read-only visual bar
                Slider(
                    value = fan.speed,
                    onValueChange = {},
                    enabled = false,
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun kindLabel(kind: FanKind): String = when (kind) {
    FanKind.PART_COOLING -> "Part cooling fan"
    FanKind.GENERIC -> "Generic fan"
    FanKind.CONTROLLER -> "Controller fan"
    FanKind.HEATER -> "Heater fan"
    FanKind.TEMPERATURE -> "Temperature fan"
}
