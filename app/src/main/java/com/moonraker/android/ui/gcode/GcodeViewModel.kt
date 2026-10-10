package com.moonraker.android.ui.gcode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.GcodeFileEntry
import com.moonraker.android.data.moonraker.GcodeMetadata
import com.moonraker.android.data.moonraker.GcodePathPreview
import com.moonraker.android.data.moonraker.MoonrakerRepository
import com.moonraker.android.data.moonraker.PrintStatus
import com.moonraker.android.data.settings.PrinterConfig
import com.moonraker.android.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val currentJobFilename: String = "",
    val currentJobState: String = "standby",
    val currentJobProgress: Float = 0f,
    val liveX: Float? = null,
    val liveY: Float? = null,
    val liveLayer: Int? = null,
    val isLivePreview: Boolean = false,
    val loadCurrentGcodeOnStart: Boolean = false,
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
    val autoLoadedOnce: Boolean = false,
)

class GcodeViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val local = MutableStateFlow(GcodeLocalState())

    val uiState: StateFlow<GcodeUiState> = combine(
        settingsRepository.settings,
        local,
        moonrakerRepository.printStatus,
    ) { settings, state, print ->
        val selected = state.selectedPath
        val isLive = !selected.isNullOrBlank() &&
            selected == print.filename &&
            print.state in setOf("printing", "paused")
        GcodeUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            files = state.files,
            selectedPath = selected,
            metadata = state.metadata,
            thumbnailUrl = state.thumbnailUrl,
            pathPreview = state.pathPreview,
            loading = state.loading,
            previewLoading = state.previewLoading,
            error = state.error,
            message = state.message,
            currentJobFilename = print.filename,
            currentJobState = print.state,
            currentJobProgress = print.progress,
            liveX = print.liveX ?: print.gcodeX,
            liveY = print.liveY ?: print.gcodeY,
            liveLayer = print.currentLayer?.let { (it - 1).coerceAtLeast(0) },
            isLivePreview = isLive,
            loadCurrentGcodeOnStart = settings.loadCurrentGcodeOnStart,
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
                        autoLoadedOnce = false,
                        files = if (printer == null) emptyList() else it.files,
                        error = if (printer == null) null else it.error,
                    )
                }
                if (printer != null) refresh(printer)
            }
        }
        viewModelScope.launch {
            combine(
                settingsRepository.settings.map { it.loadCurrentGcodeOnStart }.distinctUntilChanged(),
                moonrakerRepository.printStatus,
                local.map { it.autoLoadedOnce to it.files },
            ) { enabled, print, pair ->
                Triple(enabled, print, pair)
            }.collect { (enabled, print, pair) ->
                val (autoLoaded, files) = pair
                if (!enabled || autoLoaded) return@collect
                if (print.filename.isBlank()) return@collect
                if (print.state !in setOf("printing", "paused")) return@collect
                if (files.isEmpty()) return@collect
                local.update { it.copy(autoLoadedOnce = true) }
                selectFile(print.filename)
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

    fun openCurrentJob() {
        val filename = uiState.value.currentJobFilename
        if (filename.isBlank()) return
        selectFile(filename)
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
                local.update {
                    if (it.selectedPath == path) {
                        it.copy(metadata = meta, thumbnailUrl = null)
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
