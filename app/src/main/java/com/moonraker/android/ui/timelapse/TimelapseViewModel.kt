package com.moonraker.android.ui.timelapse

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.TimelapseItem
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimelapseUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val items: List<TimelapseItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class TimelapseViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val items = MutableStateFlow<List<TimelapseItem>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TimelapseUiState> = combine(
        settingsRepository.settings,
        items,
        loading,
        error,
        message,
    ) { settings, list, isLoading, err, msg ->
        TimelapseUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            items = list,
            loading = isLoading,
            error = err,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelapseUiState())

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                if (printer == null) {
                    items.value = emptyList()
                    error.value = null
                } else {
                    refresh(printer)
                }
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun refresh() {
        val printer = uiState.value.activePrinter ?: return
        viewModelScope.launch { refresh(printer) }
    }

    fun coverUrl(item: TimelapseItem): String? {
        val printer = uiState.value.activePrinter ?: return null
        val path = item.coverPath ?: return null
        return moonrakerRepository.fileDownloadUrl(printer, path)
    }

    fun download(context: Context, item: TimelapseItem) {
        val printer = uiState.value.activePrinter ?: return
        val url = moonrakerRepository.fileDownloadUrl(printer, item.downloadPath)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(item.filename)
            .setDescription("Moonraker timelapse")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, item.filename)
            .setMimeType("video/mp4")
        if (printer.apiKey.isNotBlank()) {
            request.addRequestHeader("X-Api-Key", printer.apiKey)
        }
        dm.enqueue(request)
        message.value = "Downloading ${item.filename}…"
    }

    private suspend fun refresh(printer: PrinterConfig) {
        loading.value = true
        error.value = null
        moonrakerRepository.listTimelapses(printer)
            .onSuccess {
                items.value = it
                if (it.isEmpty()) {
                    error.value = null
                    message.value = "No timelapse videos found"
                } else {
                    message.value = null
                }
            }
            .onFailure {
                items.value = emptyList()
                error.value = it.message ?: "Failed to list timelapses"
            }
        loading.value = false
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TimelapseViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
