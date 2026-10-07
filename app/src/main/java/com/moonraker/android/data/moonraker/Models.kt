package com.moonraker.android.data.moonraker

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ServerInfoEnvelope(val result: ServerInfoResult)

@Serializable
data class WebcamListEnvelope(val result: WebcamListResult)

@Serializable
data class UploadEnvelope(val result: UploadResponse)

@Serializable
data class WebcamListResult(
    val webcams: List<WebcamEntry> = emptyList(),
)

@Serializable
data class WebcamEntry(
    val name: String = "",
    val location: String = "",
    val service: String = "mjpegstreamer",
    val enabled: Boolean = true,
    val icon: String = "",
    @SerialName("target_fps") val targetFps: Int = 15,
    @SerialName("target_fps_idle") val targetFpsIdle: Int = 5,
    @SerialName("stream_url") val streamUrl: String = "",
    @SerialName("snapshot_url") val snapshotUrl: String = "",
    @SerialName("flip_horizontal") val flipHorizontal: Boolean = false,
    @SerialName("flip_vertical") val flipVertical: Boolean = false,
    val rotation: Int = 0,
    @SerialName("aspect_ratio") val aspectRatio: String = "4:3",
    val source: String = "database",
    val uid: String = "",
)

@Serializable
data class ServerInfoResult(
    @SerialName("klippy_state") val klippyState: String = "",
    @SerialName("moonraker_version") val moonrakerVersion: String = "",
    @SerialName("api_version_string") val apiVersion: String = "",
)

@Serializable
data class JsonRpcResponse(
    val jsonrpc: String = "2.0",
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val id: Int? = null,
    val method: String? = null,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class UploadResponse(
    val item: UploadedFile? = null,
    @SerialName("print_started") val printStarted: Boolean = false,
)

@Serializable
data class UploadedFile(
    val path: String = "",
    val root: String = "",
)

data class HeaterReading(
    val name: String,
    val temperature: Float,
    val target: Float,
    val power: Float = 0f,
)

data class PrintStatus(
    val filename: String = "",
    val state: String = "standby",
    val progress: Float = 0f,
    val message: String = "",
    val printDurationSec: Float = 0f,
    val totalDurationSec: Float = 0f,
    val filamentUsedMm: Float = 0f,
    val currentLayer: Int? = null,
    val totalLayers: Int? = null,
    val gcodeX: Float? = null,
    val gcodeY: Float? = null,
    val gcodeZ: Float? = null,
    val liveX: Float? = null,
    val liveY: Float? = null,
    val liveZ: Float? = null,
    val liveVelocity: Float? = null,
    val speedFactor: Float = 1f,
    val extrudeFactor: Float = 1f,
    val homedAxes: String = "",
    val activeExtruder: String = "",
) {
    val etaRemainingSec: Float?
        get() {
            if (progress <= 0.001f || state != "printing") return null
            val remaining = printDurationSec * (1f - progress) / progress
            return remaining.coerceAtLeast(0f)
        }
}

data class ConnectionState(
    val connected: Boolean = false,
    val message: String = "Disconnected",
)

@Serializable
data class TemperatureStoreEnvelope(
    val result: Map<String, TemperatureStoreSensor> = emptyMap(),
)

@Serializable
data class TemperatureStoreSensor(
    val temperatures: List<Float?> = emptyList(),
    val targets: List<Float?> = emptyList(),
    val powers: List<Float?> = emptyList(),
    val speeds: List<Float?> = emptyList(),
)

data class TemperatureHistorySeries(
    val name: String,
    val temperatures: List<Float>,
    val targets: List<Float>,
    val powers: List<Float>,
    val current: Float,
    val target: Float,
    val power: Float,
)

@Serializable
data class FileDirectoryEnvelope(
    val result: FileDirectoryResult,
)

@Serializable
data class FileDirectoryResult(
    val files: List<RemoteFileEntry> = emptyList(),
)

@Serializable
data class RemoteFileEntry(
    val filename: String = "",
    val size: Long = 0,
    val modified: Double = 0.0,
    val permissions: String = "",
)

data class TimelapseItem(
    val filename: String,
    val sizeBytes: Long,
    val modifiedEpochSec: Double,
    val downloadPath: String,
    val coverPath: String?,
)
