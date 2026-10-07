package com.moonraker.android.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

object MjpegDecoder {
    fun stream(
        client: OkHttpClient,
        request: Request,
    ): Flow<Bitmap> = flow {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("MJPEG HTTP ${response.code}")
            val input = BufferedInputStream(response.body?.byteStream() ?: error("Empty body"), 16 * 1024)
            val buffer = ByteArrayOutputStream()
            var prev = -1
            var inImage = false
            while (coroutineContext.isActive) {
                val current = input.read()
                if (current == -1) break
                if (!inImage) {
                    if (prev == 0xFF && current == 0xD8) {
                        inImage = true
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    }
                } else {
                    buffer.write(current)
                    if (prev == 0xFF && current == 0xD9) {
                        val bytes = buffer.toByteArray()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) emit(bitmap)
                        inImage = false
                    }
                }
                prev = current
            }
        }
    }.flowOn(Dispatchers.IO)
}
