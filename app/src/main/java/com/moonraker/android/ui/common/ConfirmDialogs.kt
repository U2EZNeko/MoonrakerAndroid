package com.moonraker.android.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun ConfirmCancelPrintDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel print?") },
        text = {
            Text(
                "This will abort the current job on the printer. " +
                    "Only continue if you really mean to stop it.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes, cancel print", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep printing")
            }
        },
    )
}

@Composable
fun ConfirmEmergencyStopDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Emergency stop?") },
        text = {
            Text(
                "This immediately stops the printer motors and heaters. " +
                    "Only use in an emergency.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("E-STOP", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Go back")
            }
        },
    )
}
