package com.moonraker.android.data.moonraker

import com.moonraker.android.data.settings.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    suspend fun listGcodeFiles(printer: PrinterConfig): Result<List<GcodeFileEntry>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${printer.baseUrl}/server/files/list?root=gcodes")
                    .withApiKey(printer)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    json.decodeFromString<GcodeFileListEnvelope>(body).result
                        .filter { entry ->
                            val name = entry.path.substringAfterLast('/')
                            name.contains('.') &&
                                !name.startsWith('.') &&
                                name.substringAfterLast('.').lowercase() in gcodeExtensions
                        }
                        .sortedByDescending { it.modified }
                }
            }
        }

    suspend fun gcodeMetadata(printer: PrinterConfig, filename: String): Result<GcodeMetadata> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(filename, Charsets.UTF_8.name())
                    .replace("+", "%20")
                val request = Request.Builder()
                    .url("${printer.baseUrl}/server/files/metadata?filename=$encoded")
                    .withApiKey(printer)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    json.decodeFromString<GcodeMetadataEnvelope>(body).result
                }
            }
        }

    suspend fun downloadGcodePreview(
        printer: PrinterConfig,
        filename: String,
        maxBytes: Long = 2_000_000L,
        maxSegments: Int = 25_000,
    ): Result<GcodePathPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(fileDownloadUrl(printer, "gcodes/$filename"))
                .withApiKey(printer)
                .header("Range", "bytes=0-${maxBytes - 1}")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    error("Download failed: HTTP ${response.code}")
                }
                val stream = response.body?.byteStream() ?: error("Empty body")
                parseGcodePath(stream, maxSegments)
            }
        }
    }

    fun fileDownloadUrl(printer: PrinterConfig, relativePath: String): String {
        val trimmed = relativePath.trimStart('/')
        return "${printer.baseUrl}/server/files/$trimmed"
    }

    fun thumbnailUrl(printer: PrinterConfig, gcodePath: String, relativeThumb: String): String {
        val parent = gcodePath.substringBeforeLast('/', missingDelimiterValue = "")
        val joined = if (parent.isBlank()) relativeThumb else "$parent/$relativeThumb"
        return fileDownloadUrl(printer, "gcodes/$joined")
    }

    fun firmwareConfigUrl(printer: PrinterConfig): String =
        "${printer.mediaBaseUrl.trimEnd('/')}/firmware-config/"

    suspend fun queryPrintJob(printer: PrinterConfig): Result<PrintJobSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(
                        "${printer.baseUrl}/printer/objects/query" +
                            "?print_stats&virtual_sdcard&display_status",
                    )
                    .withApiKey(printer)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val root = json.decodeFromString(JsonObject.serializer(), body)
                    val status = (root["result"] as? JsonObject)?.get("status") as? JsonObject
                        ?: error("Missing status")
                    val stats = status["print_stats"] as? JsonObject
                    val display = status["display_status"] as? JsonObject
                    val sd = status["virtual_sdcard"] as? JsonObject
                    fun JsonObject?.floatField(key: String): Float? =
                        this?.get(key)?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                    fun JsonObject?.stringField(key: String): String? =
                        this?.get(key)?.jsonPrimitive?.contentOrNull
                    val displayProgress = display.floatField("progress")
                    val sdProgress = sd.floatField("progress")
                    val progress = when {
                        displayProgress != null && displayProgress > 0f -> displayProgress
                        sdProgress != null -> sdProgress
                        else -> displayProgress ?: 0f
                    }
                    PrintJobSnapshot(
                        filename = stats.stringField("filename").orEmpty(),
                        state = stats.stringField("state") ?: "standby",
                        progress = progress.coerceIn(0f, 1f),
                    )
                }
            }
        }

    suspend fun downloadThumbnailBytes(
        printer: PrinterConfig,
        url: String,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .withApiKey(printer)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                response.body?.bytes() ?: error("Empty image")
            }
        }
    }
}

private val gcodeExtensions = setOf("gcode", "gco", "g", "nc")

private fun parseGcodePath(stream: java.io.InputStream, maxSegments: Int): GcodePathPreview {
    val segments = ArrayList<GcodePathSegment>(minOf(maxSegments, 4096))
    var x = 0f
    var y = 0f
    var z = 0f
    var e = 0f
    var feedMmPerMin = 3000f
    var absolute = true
    var absoluteE = true
    var layer = 0
    var lastExtrudeZ: Float? = null
    var timeSec = 0f
    var minX = Float.POSITIVE_INFINITY
    var maxX = Float.NEGATIVE_INFINITY
    var minY = Float.POSITIVE_INFINITY
    var maxY = Float.NEGATIVE_INFINITY
    var hasPoint = false
    var maxLayer = 0

    stream.bufferedReader().useLines { lines ->
        for (raw in lines) {
            if (segments.size >= maxSegments) break

            val comment = raw.substringAfter(';', missingDelimiterValue = "").trim()
            if (comment.isNotEmpty()) {
                parseLayerComment(comment)?.let { commentedLayer ->
                    layer = commentedLayer.coerceAtLeast(0)
                    maxLayer = maxOf(maxLayer, layer)
                }
            }

            val line = raw.substringBefore(';').trim()
            if (line.isEmpty()) continue
            val upper = line.uppercase()
            when {
                upper.startsWith("G90") -> absolute = true
                upper.startsWith("G91") -> absolute = false
                upper.startsWith("M82") -> absoluteE = true
                upper.startsWith("M83") -> absoluteE = false
                upper.startsWith("G92") -> {
                    parseAxis(upper, 'E')?.let { e = it }
                    parseAxis(upper, 'X')?.let { x = it }
                    parseAxis(upper, 'Y')?.let { y = it }
                    parseAxis(upper, 'Z')?.let { z = it }
                }
                upper.startsWith("G0") || upper.startsWith("G1") ||
                    upper.startsWith("G00") || upper.startsWith("G01") -> {
                    parseAxis(upper, 'F')?.let { feedMmPerMin = it.coerceAtLeast(1f) }
                    val nx = parseAxis(upper, 'X')
                    val ny = parseAxis(upper, 'Y')
                    val nz = parseAxis(upper, 'Z')
                    val ne = parseAxis(upper, 'E')
                    if (nx == null && ny == null && nz == null) {
                        if (ne != null) e = if (absoluteE) ne else e + ne
                        continue
                    }
                    val x1 = when {
                        nx == null -> x
                        absolute -> nx
                        else -> x + nx
                    }
                    val y1 = when {
                        ny == null -> y
                        absolute -> ny
                        else -> y + ny
                    }
                    val z1 = when {
                        nz == null -> z
                        absolute -> nz
                        else -> z + nz
                    }
                    val e1 = when {
                        ne == null -> e
                        absoluteE -> ne
                        else -> e + ne
                    }
                    val extruding = e1 > e + 1e-4f
                    if (extruding) {
                        val prevZ = lastExtrudeZ
                        if (prevZ != null && z1 > prevZ + 0.01f) {
                            layer += 1
                            maxLayer = maxOf(maxLayer, layer)
                        } else if (prevZ == null) {
                            maxLayer = maxOf(maxLayer, layer)
                        }
                        lastExtrudeZ = z1
                    }
                    val dist = kotlin.math.hypot((x1 - x).toDouble(), (y1 - y).toDouble()).toFloat()
                    val dz = kotlin.math.abs(z1 - z)
                    val moveLen = kotlin.math.hypot(dist.toDouble(), dz.toDouble()).toFloat()
                    if (moveLen > 1e-6f) {
                        timeSec += moveLen / (feedMmPerMin / 60f)
                    }
                    if (x1 != x || y1 != y) {
                        segments += GcodePathSegment(
                            x0 = x,
                            y0 = y,
                            x1 = x1,
                            y1 = y1,
                            extruding = extruding,
                            layer = layer,
                            timeSec = timeSec,
                        )
                        if (!hasPoint) {
                            minX = minOf(x, x1)
                            maxX = maxOf(x, x1)
                            minY = minOf(y, y1)
                            maxY = maxOf(y, y1)
                            hasPoint = true
                        } else {
                            minX = minOf(minX, x, x1)
                            maxX = maxOf(maxX, x, x1)
                            minY = minOf(minY, y, y1)
                            maxY = maxOf(maxY, y, y1)
                        }
                    }
                    x = x1
                    y = y1
                    z = z1
                    e = e1
                }
            }
        }
    }

    if (!hasPoint) {
        return GcodePathPreview()
    }
    return GcodePathPreview(
        segments = segments,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        maxLayer = maxLayer,
        totalTimeSec = timeSec,
    )
}

/** Parses `;LAYER:12` / `; layer 12`. `;LAYER_CHANGE` is handled via Z rises. */
private fun parseLayerComment(comment: String): Int? {
    val trimmed = comment.trim()
    val upper = trimmed.uppercase()
    return when {
        upper.startsWith("LAYER:") ->
            trimmed.substringAfter(':').trim().substringBefore(' ').toIntOrNull()
        upper.startsWith("LAYER ") ->
            trimmed.substringAfter(' ').trim().substringBefore(' ').toIntOrNull()
        else -> null
    }
}

private fun parseAxis(line: String, axis: Char): Float? {
    val idx = line.indexOf(axis)
    if (idx < 0) return null
    var end = idx + 1
    while (end < line.length) {
        val c = line[end]
        if (c == '-' || c == '+' || c == '.' || c.isDigit()) {
            end++
        } else {
            break
        }
    }
    if (end == idx + 1) return null
    return line.substring(idx + 1, end).toFloatOrNull()
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
