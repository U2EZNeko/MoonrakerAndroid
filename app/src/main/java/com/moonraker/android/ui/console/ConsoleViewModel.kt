package com.moonraker.android.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moonraker.android.data.moonraker.ConsoleLine
import com.moonraker.android.data.moonraker.ConsoleLineKind
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

data class ConsoleUiState(
    val printers: List<PrinterConfig> = emptyList(),
    val activePrinter: PrinterConfig? = null,
    val lines: List<ConsoleLine> = emptyList(),
    val draft: String = "",
    val sending: Boolean = false,
    val connected: Boolean = false,
    val connectionMessage: String = "Disconnected",
)

class ConsoleViewModel(
    private val settingsRepository: SettingsRepository,
    private val moonrakerRepository: MoonrakerRepository,
) : ViewModel() {
    private val lineId = AtomicLong(1)
    private val lines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    private val draft = MutableStateFlow("")
    private val sending = MutableStateFlow(false)

    val uiState: StateFlow<ConsoleUiState> = combine(
        settingsRepository.settings,
        moonrakerRepository.connection,
        lines,
        draft,
        sending,
    ) { settings, connection, log, text, isSending ->
        ConsoleUiState(
            printers = settings.printers,
            activePrinter = settings.activePrinter,
            lines = log,
            draft = text,
            sending = isSending,
            connected = connection.connected,
            connectionMessage = connection.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConsoleUiState())

    init {
        viewModelScope.launch {
            moonrakerRepository.activePrinter.collect {
                lines.value = emptyList()
                draft.value = ""
                if (it != null) {
                    append(ConsoleLineKind.INFO, "Connected to ${it.name}")
                }
            }
        }
        viewModelScope.launch {
            moonrakerRepository.gcodeResponses.collect { event ->
                if (event.method != "notify_gcode_response") return@collect
                val text = extractResponse(event.params) ?: return@collect
                append(ConsoleLineKind.RESPONSE, text)
            }
        }
    }

    fun setActivePrinter(id: String) {
        viewModelScope.launch { settingsRepository.setActivePrinter(id) }
    }

    fun onDraftChange(value: String) {
        draft.value = value
    }

    fun clear() {
        lines.value = emptyList()
    }

    fun send() {
        val printer = uiState.value.activePrinter ?: return
        val script = draft.value.trim()
        if (script.isBlank() || sending.value) return
        viewModelScope.launch {
            sending.value = true
            append(ConsoleLineKind.COMMAND, script)
            draft.value = ""
            moonrakerRepository.runGcode(printer, script)
                .onFailure { err ->
                    append(ConsoleLineKind.ERROR, err.message ?: "Command failed")
                }
            sending.value = false
        }
    }

    fun sendPreset(script: String) {
        val printer = uiState.value.activePrinter ?: return
        if (script.isBlank() || sending.value) return
        viewModelScope.launch {
            sending.value = true
            append(ConsoleLineKind.COMMAND, script)
            moonrakerRepository.runGcode(printer, script)
                .onFailure { err ->
                    append(ConsoleLineKind.ERROR, err.message ?: "Command failed")
                }
            sending.value = false
        }
    }

    private fun append(kind: ConsoleLineKind, text: String) {
        val line = ConsoleLine(
            id = lineId.getAndIncrement(),
            text = text,
            kind = kind,
        )
        lines.update { current -> (current + line).takeLast(500) }
    }

    private fun extractResponse(params: kotlinx.serialization.json.JsonElement?): String? {
        return when (params) {
            is JsonArray -> params.firstOrNull()?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> params.contentOrNull
            else -> null
        }
    }

    companion object {
        fun factory(
            settingsRepository: SettingsRepository,
            moonrakerRepository: MoonrakerRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ConsoleViewModel(settingsRepository, moonrakerRepository) as T
            }
        }
    }
}
