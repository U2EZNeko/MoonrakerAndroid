package com.moonraker.android.ui.print

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PrintUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val printStatus: PrintStatus = PrintStatus(),
    val uploading: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val keepScreenOn: Boolean = true,
)

class PrintViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val uploading = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val isError = MutableStateFlow(false)

    val uiState: StateFlow<PrintUiState> = combine(
        settingsRepository.settings,
        moonrakerRepository.printStatus,
        uploading,
        message,
        isError,
    ) { settings, status, up, msg, err ->
        PrintUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            printStatus = status,
            uploading = up,
            message = msg,
            isError = err,
            keepScreenOn = settings.keepScreenOnPrint,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrintUiState())

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun pausePrint() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            moonrakerRepository.pausePrint(printer)
                .onSuccess {
                    message.value = "Pause requested"
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "Pause failed"
                    isError.value = true
                }
        }
    }

    fun resumePrint() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            moonrakerRepository.resumePrint(printer)
                .onSuccess {
                    message.value = "Resume requested"
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "Resume failed"
                    isError.value = true
                }
        }
    }

    fun cancelPrint() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            moonrakerRepository.cancelPrint(printer)
                .onSuccess {
                    message.value = "Cancel requested"
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "Cancel failed"
                    isError.value = true
                }
        }
    }

    fun emergencyStop() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            moonrakerRepository.emergencyStop(printer)
                .onSuccess {
                    message.value = "Emergency stop sent"
                    isError.value = false
                }
                .onFailure {
                    message.value = it.message ?: "E-stop failed"
                    isError.value = true
                }
        }
    }

    fun uploadAndStart(context: Context, uri: Uri) {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch {
            uploading.value = true
            message.value = null
            isError.value = false
            runCatching {
                val temp = withContext(Dispatchers.IO) {
                    copyUriToCache(context, uri)
                }
                moonrakerRepository.uploadAndStart(printer, temp, startImmediately = true)
                    .getOrThrow()
                    .also {
                        temp.delete()
                    }
            }.onSuccess {
                message.value = "Started ${it.path}"
                isError.value = false
            }.onFailure {
                message.value = it.message ?: "Upload failed"
                isError.value = true
            }
            uploading.value = false
        }
    }

    private fun copyUriToCache(context: Context, uri: Uri): File {
        val name = queryDisplayName(context, uri) ?: "upload.gcode"
        val safeName = name.substringAfterLast('/').ifBlank { "upload.gcode" }
        val out = File(context.cacheDir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to read file")
        return out
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
        cursor.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index)
        }
        return null
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PrintViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
