package com.moonraker.android.ui.camera

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.moonraker.android.data.moonraker.WebcamEntry

fun WebcamEntry.parsedAspectRatio(): Float {
    val parts = aspectRatio.split(":")
    if (parts.size == 2) {
        val w = parts[0].toFloatOrNull()
        val h = parts[1].toFloatOrNull()
        if (w != null && h != null && h != 0f) {
            val ratio = w / h
            return if (rotation == 90 || rotation == 270) 1f / ratio else ratio
        }
    }
    return if (rotation == 90 || rotation == 270) 3f / 4f else 4f / 3f
}

@Composable
fun TransformedCameraBox(
    webcam: WebcamEntry,
    modifier: Modifier = Modifier,
    applyGraphicsTransform: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .aspectRatio(webcam.parsedAspectRatio())

    Box(
        modifier = if (applyGraphicsTransform) {
            base.graphicsLayer {
                scaleX = if (webcam.flipHorizontal) -1f else 1f
                scaleY = if (webcam.flipVertical) -1f else 1f
                rotationZ = webcam.rotation.toFloat()
            }
        } else {
            base
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

enum class CameraServiceKind {
    MJPEG,
    MJPEG_ADAPTIVE,
    HLS,
    WEBRTC,
    IFRAME,
    JMUXER,
    UNKNOWN,
}

fun WebcamEntry.serviceKind(): CameraServiceKind = when (service.lowercase()) {
    "mjpegstreamer", "uv4l-mjpeg", "uv4l-mjpegstream", "ipstream" -> CameraServiceKind.MJPEG
    "mjpegstreamer-adaptive", "mjpegadaptive" -> CameraServiceKind.MJPEG_ADAPTIVE
    "hlsstream" -> CameraServiceKind.HLS
    "webrtc-camerastreamer", "webrtc-go2rtc", "webrtc-mediamtx", "webrtc-janus" -> CameraServiceKind.WEBRTC
    "iframe" -> CameraServiceKind.IFRAME
    "jmuxer-stream" -> CameraServiceKind.JMUXER
    else -> CameraServiceKind.UNKNOWN
}
