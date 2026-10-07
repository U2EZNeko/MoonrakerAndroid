package com.moonraker.android.data.moonraker

import com.moonraker.android.data.settings.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class MoonrakerApiClient(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    },
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun httpClient(): OkHttpClient = client

    private fun Request.Builder.withApiKey(printer: PrinterConfig): Request.Builder {
        if (printer.apiKey.isNotBlank()) {
            header("X-Api-Key", printer.apiKey)
        }
        return this
    }

    /**
     * Absolute URLs stay as-is. Relative webcam paths resolve against [PrinterConfig.mediaBaseUrl]
     * (usually nginx on :80), not the Moonraker API port.
     */
    fun resolveUrl(printer: PrinterConfig, pathOrUrl: String): String {
        if (pathOrUrl.isBlank()) return ""
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("ws://") || trimmed.startsWith("wss://")
        ) {
            return trimmed
        }
        val base = printer.mediaBaseUrl.trimEnd('/')
        return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
    }

    suspend fun testConnection(printer: PrinterConfig): Result<ServerInfoResult> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/server/info")
                .withApiKey(printer)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<ServerInfoEnvelope>(body).result
            }
        }
    }

    suspend fun listWebcams(printer: PrinterConfig): Result<List<WebcamEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/server/webcams/list")
                .withApiKey(printer)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<WebcamListEnvelope>(body)
                    .result.webcams.filter { it.enabled }
            }
        }
    }

    suspend fun uploadGcode(
        printer: PrinterConfig,
        file: File,
        startPrint: Boolean,
    ): Result<UploadedFile> = withContext(Dispatchers.IO) {
        runCatching {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("root", "gcodes")
                .addFormDataPart(
                    "file",
                    file.name,
                    file.asRequestBody("application/octet-stream".toMediaType()),
                )
                .apply {
                    if (startPrint) addFormDataPart("print", "true")
                }
                .build()
            val request = Request.Builder()
                .url("${printer.baseUrl}/server/files/upload")
                .withApiKey(printer)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Upload failed: HTTP ${response.code}")
                val text = response.body?.string().orEmpty()
                // Upload may be bare or wrapped in result depending on version
                runCatching { json.decodeFromString<UploadResponse>(text) }.getOrNull()?.item
                    ?: runCatching {
                        json.decodeFromString<UploadEnvelope>(text).result.item
                    }.getOrNull()
                    ?: UploadedFile(path = file.name, root = "gcodes")
            }
        }
    }

    suspend fun startPrint(printer: PrinterConfig, filename: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("filename", filename) }
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/print/start")
                .withApiKey(printer)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Start failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun cancelPrint(printer: PrinterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/print/cancel")
                .withApiKey(printer)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Cancel failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun pausePrint(printer: PrinterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/print/pause")
                .withApiKey(printer)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Pause failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun resumePrint(printer: PrinterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/print/resume")
                .withApiKey(printer)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Resume failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun emergencyStop(printer: PrinterConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/emergency_stop")
                .withApiKey(printer)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("E-stop failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    suspend fun runGcode(printer: PrinterConfig, script: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildJsonObject { put("script", script) }
            val request = Request.Builder()
                .url("${printer.baseUrl}/printer/gcode/script")
                .withApiKey(printer)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("G-code failed: HTTP ${response.code}")
            }
            Unit
        }
    }

    fun snapshotRequest(printer: PrinterConfig, snapshotUrl: String): Request {
        val url = resolveUrl(printer, snapshotUrl)
        return Request.Builder()
            .url(url)
            .withApiKey(printer)
            .get()
            .build()
    }

    suspend fun temperatureStore(
        printer: PrinterConfig,
    ): Result<Map<String, TemperatureStoreSensor>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${printer.baseUrl}/server/temperature_store?include_monitors=true")
                .withApiKey(printer)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                json.decodeFromString<TemperatureStoreEnvelope>(body).result
            }
        }
    }

    suspend fun listTimelapses(printer: PrinterConfig): Result<List<TimelapseItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${printer.baseUrl}/server/files/directory?path=timelapse")
                    .withApiKey(printer)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val files = json.decodeFromString<FileDirectoryEnvelope>(body).result.files
                    groupTimelapseFiles(files)
                }
            }
        }

    fun fileDownloadUrl(printer: PrinterConfig, relativePath: String): String {
        val trimmed = relativePath.trimStart('/')
        return "${printer.baseUrl}/server/files/$trimmed"
    }
}

private fun groupTimelapseFiles(files: List<RemoteFileEntry>): List<TimelapseItem> {
    val byName = files.associateBy { it.filename }
    return files
        .filter { it.filename.endsWith(".mp4", ignoreCase = true) }
        .map { video ->
            val stem = video.filename.removeSuffix(".mp4").removeSuffix(".MP4")
            val cover = listOf("${stem}_cover.jpg", "$stem.jpg", "${stem}_cover.jpeg")
                .firstOrNull { byName.containsKey(it) }
            TimelapseItem(
                filename = video.filename,
                sizeBytes = video.size,
                modifiedEpochSec = video.modified,
                downloadPath = "timelapse/${video.filename}",
                coverPath = cover?.let { "timelapse/$it" },
            )
        }
        .sortedByDescending { it.modifiedEpochSec }
}
