package com.moonraker.android.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moonraker.android.data.settings.PrinterConfig

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
fun PrinterSwitcher(
    printers: List<PrinterConfig>,
    activeId: String?,
    onSelect: (String) -> Unit,
) {
    if (printers.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val active = printers.firstOrNull { it.id == activeId } ?: printers.first()
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(active.name)
            Icon(Icons.Default.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            printers.forEach { printer ->
                DropdownMenuItem(
                    text = { Text(printer.name) },
                    onClick = {
                        expanded = false
                        onSelect(printer.id)
                    },
                )
            }
        }
    }
}
