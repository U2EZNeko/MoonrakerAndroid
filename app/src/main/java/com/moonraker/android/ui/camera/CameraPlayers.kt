package com.moonraker.android.ui.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.moonraker.android.data.moonraker.WebcamEntry
import com.moonraker.android.data.settings.PrinterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * In-app camera playback prefers live streams.
 * Snapshot polling is used only as a temporary fallback if WebRTC fails,
 * and for Moonraker adaptive/snapshot service types.
 */
@Composable
fun CameraPlayer(
    printer: PrinterConfig,
    webcam: WebcamEntry,
    client: OkHttpClient,
    resolveUrl: (String) -> String,
    isPrinterActive: Boolean,
    streamFps: Int = 30,
    preferSnapshot: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fps = streamFps.coerceIn(1, 30)
    val snapshot = resolveUrl(webcam.snapshotUrl)
    // Avoid graphicsLayer around WebView/ExoPlayer — it often renders black on Android.
    val useSurfacePlayer = when (webcam.serviceKind()) {
        CameraServiceKind.WEBRTC, CameraServiceKind.IFRAME, CameraServiceKind.JMUXER, CameraServiceKind.HLS -> true
        else -> false
    }

    TransformedCameraBox(
        webcam = webcam,
        applyGraphicsTransform = !useSurfacePlayer || preferSnapshot,
        modifier = modifier,
    ) {
        val kind = webcam.serviceKind()
        val forceSnapshot = preferSnapshot && snapshot.isNotBlank() &&
            kind in setOf(
                CameraServiceKind.WEBRTC,
                CameraServiceKind.MJPEG,
                CameraServiceKind.UNKNOWN,
                CameraServiceKind.IFRAME,
                CameraServiceKind.JMUXER,
            )
        if (forceSnapshot) {
            AdaptiveSnapshotPlayer(
                printer = printer,
                snapshotUrl = snapshot,
                client = client,
                fps = fps,
            )
        } else when (kind) {
            CameraServiceKind.MJPEG,
            CameraServiceKind.UNKNOWN,
            -> {
                val stream = resolveUrl(webcam.streamUrl)
                when {
                    stream.contains("/webrtc", ignoreCase = true) -> {
                        WebrtcWithSnapshotFallback(
                            printer = printer,
                            webcam = webcam,
                            signalingUrl = stream,
                            snapshotUrl = snapshot,
                            client = client,
                            fps = fps,
                        )
                    }
                    stream.isNotBlank() -> MjpegPlayer(printer = printer, url = stream, client = client)
                    snapshot.isNotBlank() -> AdaptiveSnapshotPlayer(
                        printer = printer,
                        snapshotUrl = snapshot,
                        client = client,
                        fps = fps,
                    )
                    else -> CameraFrame(null, "Missing stream URL", false)
                }
            }

            CameraServiceKind.MJPEG_ADAPTIVE -> AdaptiveSnapshotPlayer(
                printer = printer,
                snapshotUrl = snapshot.ifBlank { resolveUrl(webcam.streamUrl) },
                client = client,
                fps = fps,
            )

            CameraServiceKind.WEBRTC -> WebrtcWithSnapshotFallback(
                printer = printer,
                webcam = webcam,
                signalingUrl = resolveUrl(webcam.streamUrl),
                snapshotUrl = snapshot,
                client = client,
                fps = fps,
            )

            CameraServiceKind.HLS -> HlsPlayer(url = resolveUrl(webcam.streamUrl))

            // iframe is often a remote screen UI (e.g. /screen/) that polls PNG snapshots.
            // Prefer snapshot polling — embedding the page in WebView breaks on auth.js redirects.
            CameraServiceKind.IFRAME -> {
                when {
                    snapshot.isNotBlank() -> AdaptiveSnapshotPlayer(
                        printer = printer,
                        snapshotUrl = snapshot,
                        client = client,
                        fps = fps,
                    )
                    else -> WebStreamPlayer(
                        url = resolveUrl(webcam.streamUrl),
                        apiKey = printer.apiKey,
                    )
                }
            }

            CameraServiceKind.JMUXER -> WebStreamPlayer(
                url = resolveUrl(webcam.streamUrl),
                apiKey = printer.apiKey,
            )
        }
    }
}

@Composable
private fun WebrtcWithSnapshotFallback(
    printer: PrinterConfig,
    webcam: WebcamEntry,
    signalingUrl: String,
    snapshotUrl: String,
    client: OkHttpClient,
    fps: Int,
) {
    var playing by remember(signalingUrl) { mutableStateOf(false) }
    var failed by remember(signalingUrl) { mutableStateOf(false) }

    LaunchedEffect(signalingUrl, playing, failed) {
        if (playing || failed) return@LaunchedEffect
        delay(8_000)
        if (!playing) failed = true
    }

    if (failed && snapshotUrl.isNotBlank()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AdaptiveSnapshotPlayer(
                printer = printer,
                snapshotUrl = snapshotUrl,
                client = client,
                fps = fps,
            )
            Text(
                text = "Live WebRTC unavailable — snapshot mode",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(6.dp),
            )
        }
    } else {
        WebRtcLivePlayer(
            signalingUrl = signalingUrl,
            service = webcam.service,
            apiKey = printer.apiKey,
            flipH = webcam.flipHorizontal,
            flipV = webcam.flipVertical,
            rotation = webcam.rotation,
            onError = { failed = true },
            onPlaying = { playing = true },
        )
    }
}

@Composable
private fun MjpegPlayer(
    printer: PrinterConfig,
    url: String,
    client: OkHttpClient,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url, printer.id) {
        error = null
        if (url.isBlank()) {
            error = "Missing stream URL"
            return@LaunchedEffect
        }
        val request = Request.Builder().url(url).apply {
            if (printer.apiKey.isNotBlank()) header("X-Api-Key", printer.apiKey)
        }.build()
        try {
            MjpegDecoder.stream(client, request).collect { frame ->
                bitmap = frame
            }
        } catch (t: Throwable) {
            error = t.message ?: "MJPEG failed"
        }
    }

    CameraFrame(bitmap = bitmap, error = error, loading = bitmap == null && error == null)
}

@Composable
private fun AdaptiveSnapshotPlayer(
    printer: PrinterConfig,
    snapshotUrl: String,
    client: OkHttpClient,
    fps: Int,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val delayMs = (1000L / fps.coerceIn(1, 30))

    LaunchedEffect(snapshotUrl, printer.id, fps) {
        error = null
        if (snapshotUrl.isBlank()) {
            error = "Missing snapshot URL"
            return@LaunchedEffect
        }
        while (isActive) {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    val bust = if (snapshotUrl.contains('?')) {
                        "$snapshotUrl&_ts=${System.currentTimeMillis()}"
                    } else {
                        "$snapshotUrl?_ts=${System.currentTimeMillis()}"
                    }
                    val request = Request.Builder().url(bust).apply {
                        header("Cache-Control", "no-cache")
                        header("Pragma", "no-cache")
                        if (printer.apiKey.isNotBlank()) header("X-Api-Key", printer.apiKey)
                    }.build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val bytes = response.body?.bytes() ?: error("Empty snapshot")
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            ?: error("Decode failed")
                    }
                }
                bitmap = decoded
                error = null
            } catch (t: Throwable) {
                error = t.message ?: "Snapshot failed"
            }
            delay(delayMs)
        }
    }

    CameraFrame(bitmap = bitmap, error = error, loading = bitmap == null && error == null)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebRtcLivePlayer(
    signalingUrl: String,
    service: String,
    apiKey: String,
    flipH: Boolean,
    flipV: Boolean,
    rotation: Int,
    onError: (String) -> Unit,
    onPlaying: () -> Unit,
) {
    if (signalingUrl.isBlank()) {
        CameraFrame(bitmap = null, error = "Missing WebRTC URL", loading = false)
        return
    }
    val context = LocalContext.current
    val template = remember {
        context.assets.open("webrtc_player.html").bufferedReader().use { it.readText() }
    }
    val baseUrl = remember(signalingUrl) {
        val http = signalingUrl.toHttpUrlOrNull()
        if (http != null) {
            val portPart = when {
                http.scheme == "http" && http.port == 80 -> ""
                http.scheme == "https" && http.port == 443 -> ""
                else -> ":${http.port}"
            }
            "${http.scheme}://${http.host}$portPart/"
        } else {
            "http://127.0.0.1/"
        }
    }
    val html = remember(signalingUrl, service, apiKey, flipH, flipV, rotation) {
        val cfg = JSONObject()
            .put("url", signalingUrl)
            .put("key", apiKey)
            .put("service", service)
            .put("flipH", flipH)
            .put("flipV", flipV)
            .put("rotation", rotation)
        val inject = "window.MOONRAKER_CAM = $cfg;"
        when {
            template.contains("// CONFIG_INJECT") ->
                template.replace("// CONFIG_INJECT", inject)
            template.contains("<!-- CONFIG_INJECT -->") ->
                template.replace("<!-- CONFIG_INJECT -->", "<script>$inject</script>")
            else ->
                template.replace("<head>", "<head><script>$inject</script>")
        }
    }
    val onErrorState by rememberUpdatedState(onError)
    val onPlayingState by rememberUpdatedState(onPlaying)

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.allowFileAccess = true
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onError(message: String) {
                            post { onErrorState(message) }
                        }

                        @JavascriptInterface
                        fun onPlaying() {
                            post { onPlayingState() }
                        }
                    },
                    "AndroidBridge",
                )
                tag = signalingUrl
                loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != signalingUrl) {
                webView.tag = signalingUrl
                webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun HlsPlayer(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            if (url.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    if (url.isBlank()) {
        CameraFrame(bitmap = null, error = "Missing HLS URL", loading = false)
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebStreamPlayer(
    url: String,
    apiKey: String,
) {
    if (url.isBlank()) {
        CameraFrame(bitmap = null, error = "Missing stream URL", loading = false)
        return
    }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = true
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }
                // Screen UI auth.js redirects to "/" (Fluidd) on auth failure — keep the stream page.
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                    ): Boolean {
                        val next = request?.url ?: return false
                        val streamHttp = url.toHttpUrlOrNull() ?: return false
                        if (next.host != streamHttp.host) return true
                        val path = next.encodedPath.orEmpty()
                        return path == "/" || path.isEmpty()
                    }
                }
                tag = url
                if (apiKey.isNotBlank()) {
                    loadUrl(url, mapOf("X-Api-Key" to apiKey))
                } else {
                    loadUrl(url)
                }
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                if (apiKey.isNotBlank()) {
                    webView.loadUrl(url, mapOf("X-Api-Key" to apiKey))
                } else {
                    webView.loadUrl(url)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CameraFrame(
    bitmap: Bitmap?,
    error: String?,
    loading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            error != null -> Text(
                text = error,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
            loading -> CircularProgressIndicator()
        }
    }
}
