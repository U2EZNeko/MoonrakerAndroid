package com.moonraker.android.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.data.moonraker.ConsoleLine
import com.moonraker.android.data.moonraker.ConsoleLineKind
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher

private val QuickCommands = listOf(
    "STATUS" to "STATUS",
    "M115" to "M115",
    "M18" to "M18",
    "G28" to "G28",
    "QUERY_ENDSTOPS" to "QUERY_ENDSTOPS",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(ui.lines.size) {
        if (ui.lines.isNotEmpty()) {
            listState.animateScrollToItem(ui.lines.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBarTitle(title = "Console") },
                actions = {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear")
                    }
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

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (!ui.connected) {
                        Text(
                            text = ui.connectionMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(ui.lines, key = { it.id }) { line ->
                            ConsoleLineRow(line)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickCommands.forEach { (label, script) ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.sendPreset(script) },
                                enabled = ui.connected && !ui.sending,
                                label = { Text(label) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = ui.draft,
                            onValueChange = viewModel::onDraftChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("G-code or macro") },
                            enabled = ui.connected && !ui.sending,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                        )
                        FilledIconButton(
                            onClick = viewModel::send,
                            enabled = ui.connected && !ui.sending && ui.draft.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsoleLineRow(line: ConsoleLine) {
    val color = when (line.kind) {
        ConsoleLineKind.COMMAND -> MaterialTheme.colorScheme.primary
        ConsoleLineKind.RESPONSE -> MaterialTheme.colorScheme.onSurface
        ConsoleLineKind.ERROR -> MaterialTheme.colorScheme.error
        ConsoleLineKind.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val prefix = when (line.kind) {
        ConsoleLineKind.COMMAND -> "> "
        ConsoleLineKind.ERROR -> "! "
        ConsoleLineKind.INFO -> "· "
        ConsoleLineKind.RESPONSE -> ""
    }
    Text(
        text = prefix + line.text,
        color = color,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = when (line.kind) {
                    ConsoleLineKind.COMMAND -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ConsoleLineKind.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
