package com.moonraker.android.data.moonraker

import com.moonraker.android.data.settings.PrinterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

class MoonrakerWebSocket(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val requestId = AtomicInteger(1)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var printer: PrinterConfig? = null
    private var intentionalClose = false
    private var listObjectsId: Int? = null

    private val _connection = MutableStateFlow(ConnectionState())
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _status = MutableStateFlow(JsonObject(emptyMap()))
    val status: StateFlow<JsonObject> = _status.asStateFlow()

    private val _events = MutableSharedFlow<JsonRpcResponse>(extraBufferCapacity = 32)
    val events: SharedFlow<JsonRpcResponse> = _events.asSharedFlow()

    fun connect(printerConfig: PrinterConfig) {
        if (printer?.id == printerConfig.id && socket != null) return
        disconnect()
        intentionalClose = false
        printer = printerConfig
        openSocket(printerConfig)
    }

    fun disconnect() {
        intentionalClose = true
        reconnectJob?.cancel()
        reconnectJob = null
        socket?.close(1000, "bye")
        socket = null
        _connection.value = ConnectionState(false, "Disconnected")
        _status.value = JsonObject(emptyMap())
    }

    private fun openSocket(printerConfig: PrinterConfig) {
        val builder = Request.Builder().url(printerConfig.websocketUrl)
        if (printerConfig.apiKey.isNotBlank()) {
            builder.header("X-Api-Key", printerConfig.apiKey)
        }
        _connection.value = ConnectionState(false, "Connecting…")
        socket = client.newWebSocket(builder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connection.value = ConnectionState(true, "Connected")
                // Subscribe to defaults immediately, then expand via objects.list
                val defaults = defaultObjects()
                subscribe(webSocket, defaults)
                queryStatus(webSocket, defaults)
                requestObjectList(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val parsed = runCatching { json.decodeFromString<JsonRpcResponse>(text) }.getOrNull()
                    ?: return
                scope.launch { _events.emit(parsed) }

                when {
                    parsed.method == "notify_status_update" -> {
                        val statusObj = when (val params = parsed.params) {
                            is JsonArray -> params.firstOrNull()?.jsonObject
                            is JsonObject -> params["0"]?.jsonObject
                                ?: params.values.firstOrNull()?.jsonObject
                            else -> null
                        } ?: return
                        mergeStatus(statusObj)
                    }

                    parsed.id != null && parsed.id == listObjectsId && parsed.result != null -> {
                        val objects = parsed.result.jsonObject["objects"]?.jsonArray
                            ?: return
                        val names = objects.mapNotNull { it.jsonPrimitive.contentOrNull }
                        val interesting = names.filter { name ->
                            name == "print_stats" ||
                                name == "display_status" ||
                                name == "virtual_sdcard" ||
                                name == "gcode_move" ||
                                name == "toolhead" ||
                                name == "motion_report" ||
                                name.startsWith("extruder") ||
                                name == "heater_bed" ||
                                name.startsWith("heater_generic") ||
                                name.startsWith("temperature_sensor") ||
                                name.startsWith("temperature_fan") ||
                                name == "fan" ||
                                name.startsWith("fan_generic") ||
                                name.startsWith("heater_fan") ||
                                name.startsWith("controller_fan")
                        }.ifEmpty { defaultObjects().keys.toList() }
                        val payload = buildJsonObject {
                            interesting.forEach { put(it, JsonNull) }
                        }
                        subscribe(webSocket, payload)
                        queryStatus(webSocket, payload)
                    }

                    parsed.result != null -> {
                        val resultObj = runCatching { parsed.result.jsonObject }.getOrNull() ?: return
                        val statusObj = resultObj["status"]?.jsonObject
                        if (statusObj != null) mergeStatus(statusObj)
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connection.value = ConnectionState(false, t.message ?: "Connection failed")
                socket = null
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connection.value = ConnectionState(false, reason.ifBlank { "Closed" })
                socket = null
                if (!intentionalClose) scheduleReconnect()
            }
        })
    }

    private fun defaultObjects(): JsonObject = buildJsonObject {
        put("print_stats", JsonNull)
        put("display_status", JsonNull)
        put("virtual_sdcard", JsonNull)
        put("gcode_move", JsonNull)
        put("toolhead", JsonNull)
        put("motion_report", JsonNull)
        put("heater_bed", JsonNull)
        put("extruder", JsonNull)
        put("extruder1", JsonNull)
        put("fan", JsonNull)
    }

    private fun requestObjectList(webSocket: WebSocket) {
        val id = requestId.getAndIncrement()
        listObjectsId = id
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "printer.objects.list")
            put("id", id)
        }
        webSocket.send(payload.toString())
    }

    private fun subscribe(webSocket: WebSocket, objects: JsonObject) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "printer.objects.subscribe")
            put("params", buildJsonObject { put("objects", objects) })
            put("id", requestId.getAndIncrement())
        }
        webSocket.send(payload.toString())
    }

    private fun queryStatus(webSocket: WebSocket, objects: JsonObject) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "printer.objects.query")
            put("params", buildJsonObject { put("objects", objects) })
            put("id", requestId.getAndIncrement())
        }
        webSocket.send(payload.toString())
    }

    private fun mergeStatus(update: JsonObject) {
        val current = _status.value.toMutableMap()
        update.forEach { (key, value) ->
            val existing = current[key] as? JsonObject
            current[key] = if (existing != null && value is JsonObject) {
                JsonObject(
                    existing.toMutableMap().apply {
                        value.forEach { (k, v) -> put(k, v) }
                    },
                )
            } else {
                value
            }
        }
        _status.value = JsonObject(current)
    }

    private fun scheduleReconnect() {
        if (intentionalClose) return
        val target = printer ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(2_000)
            if (isActive && !intentionalClose) {
                openSocket(target)
            }
        }
    }
}

fun JsonObject.floatOrNull(key: String): Float? =
    this[key]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()

fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull
