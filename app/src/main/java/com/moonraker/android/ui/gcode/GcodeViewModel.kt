package com.moonraker.android.ui.gcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.GcodeFileEntry
import com.moonraker.android.data.moonraker.GcodeMetadata
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GcodeUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val files: List<GcodeFileEntry> = emptyList(),
    val selectedPath: String? = null,
    val metadata: GcodeMetadata? = null,
    val thumbnailUrl: String? = null,
    val pathPreview: GcodePathPreview? = null,
    val loading: Boolean = false,
    val previewLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

private data class GcodeLocalState(
    val files: List<GcodeFileEntry> = emptyList(),
    val selectedPath: String? = null,
    val metadata: GcodeMetadata? = null,
    val thumbnailUrl: String? = null,
    val pathPreview: GcodePathPreview? = null,
    val loading: Boolean = false,
    val previewLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class GcodeViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val local = MutableStateFlow(GcodeLocalState())

    val uiState: StateFlow<GcodeUiState> = combine(
        settingsRepository.settings,
        local,
    ) { settings, state ->
        GcodeUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            files = state.files,
            selectedPath = state.selectedPath,
            metadata = state.metadata,
            thumbnailUrl = state.thumbnailUrl,
            pathPreview = state.pathPreview,
            loading = state.loading,
            previewLoading = state.previewLoading,
            error = state.error,
            message = state.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GcodeUiState())

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect { printer ->
                local.update {
                    it.copy(
                        selectedPath = null,
                        metadata = null,
                        thumbnailUrl = null,
                        pathPreview = null,
                        files = if (printer == null) emptyList() else it.files,
                        error = if (printer == null) null else it.error,
                    )
                }
                if (printer != null) refresh(printer)
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

    fun selectFile(path: String) {
        val printer = uiState.value.activePrinter ?: return
        if (local.value.selectedPath == path) return
        local.update {
            it.copy(
                selectedPath = path,
                metadata = null,
                thumbnailUrl = null,
                pathPreview = null,
                message = null,
            )
        }
        viewModelScope.launch { loadPreview(printer, path) }
    }

    fun clearSelection() {
        local.update {
            it.copy(
                selectedPath = null,
                metadata = null,
                thumbnailUrl = null,
                pathPreview = null,
            )
        }
    }

    fun startPrint() {
        val printer = uiState.value.activePrinter ?: return
        val path = local.value.selectedPath ?: return
        viewModelScope.launch {
            local.update { it.copy(message = null) }
            moonrakerRepository.startPrint(printer, path)
                .onSuccess { local.update { s -> s.copy(message = "Started $path") } }
                .onFailure {
                    local.update { s -> s.copy(message = it.message ?: "Failed to start print") }
                }
        }
    }

    private suspend fun refresh(printer: PrinterConfig) {
        local.update { it.copy(loading = true, error = null) }
        moonrakerRepository.listGcodeFiles(printer)
            .onSuccess { list ->
                local.update { state ->
                    val selected = state.selectedPath
                    state.copy(
                        files = list,
                        message = if (list.isEmpty()) "No G-code files on printer" else null,
                        selectedPath = selected?.takeIf { path -> list.any { it.path == path } },
                        metadata = if (selected != null && list.any { it.path == selected }) {
                            state.metadata
                        } else {
                            null
                        },
                        thumbnailUrl = if (selected != null && list.any { it.path == selected }) {
                            state.thumbnailUrl
                        } else {
                            null
                        },
                        pathPreview = if (selected != null && list.any { it.path == selected }) {
                            state.pathPreview
                        } else {
                            null
                        },
                    )
                }
            }
            .onFailure { err ->
                local.update {
                    it.copy(
                        files = emptyList(),
                        error = err.message ?: "Failed to list G-code files",
                    )
                }
            }
        local.update { it.copy(loading = false) }
    }

    private suspend fun loadPreview(printer: PrinterConfig, path: String) {
        local.update { it.copy(previewLoading = true) }
        moonrakerRepository.gcodeMetadata(printer, path)
            .onSuccess { meta ->
                val best = meta.thumbnails.maxByOrNull { it.width * it.height }
                val thumb = best?.relativePath
                    ?.takeIf { it.isNotBlank() }
                    ?.let { moonrakerRepository.thumbnailUrl(printer, path, it) }
                local.update {
                    if (it.selectedPath == path) {
                        it.copy(metadata = meta, thumbnailUrl = thumb)
                    } else {
                        it
                    }
                }
            }
            .onFailure {
                local.update {
                    if (it.selectedPath == path) it.copy(metadata = null, thumbnailUrl = null) else it
                }
            }

        moonrakerRepository.downloadGcodePreview(printer, path)
            .onSuccess { preview ->
                local.update {
                    if (it.selectedPath == path) it.copy(pathPreview = preview) else it
                }
            }
            .onFailure {
                local.update {
                    if (it.selectedPath == path) it.copy(pathPreview = null) else it
                }
            }

        local.update { it.copy(previewLoading = false) }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return GcodeViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
