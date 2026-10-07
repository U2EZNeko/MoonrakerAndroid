package com.moonraker.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.RemoteViews
import com.moonraker.android.MoonrakerApp
import com.moonraker.android.R
import com.moonraker.android.data.moonraker.MoonrakerApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CameraSnapshotWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            refreshWidget(context, appWidgetManager, id, showLoading = true)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val app = context.applicationContext as MoonrakerApp
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            appWidgetIds.forEach { app.settingsRepository.removeWidgetBinding(it) }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                refreshWidget(context, AppWidgetManager.getInstance(context), id, showLoading = true)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.moonraker.android.widget.ACTION_REFRESH"

        fun refreshWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            showLoading: Boolean,
        ) {
            val views = RemoteViews(context.packageName, R.layout.camera_widget)
            if (showLoading) {
                views.setTextViewText(R.id.widget_status, "Refreshing…")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            val refreshIntent = Intent(context, CameraSnapshotWidgetReceiver::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val result = loadSnapshot(context, appWidgetId)
                val updated = RemoteViews(context.packageName, R.layout.camera_widget)
                updated.setOnClickPendingIntent(R.id.widget_root, pending)
                if (result.bitmap != null) {
                    updated.setImageViewBitmap(R.id.widget_image, result.bitmap)
                    updated.setTextViewText(R.id.widget_status, result.label)
                } else {
                    updated.setTextViewText(R.id.widget_status, result.label)
                }
                appWidgetManager.updateAppWidget(appWidgetId, updated)
            }
        }

        private suspend fun loadSnapshot(context: Context, appWidgetId: Int): SnapshotResult {
            val app = context.applicationContext as MoonrakerApp
            val settings = app.settingsRepository.settings.first()
            val binding = settings.widgetBindings.firstOrNull { it.appWidgetId == appWidgetId }
                ?: return SnapshotResult(null, "Tap after configuring")
            val printer = settings.printers.firstOrNull { it.id == binding.printerId }
                ?: return SnapshotResult(null, "Printer missing")
            val api = MoonrakerApiClient()
            val webcams = api.listWebcams(printer).getOrElse {
                return SnapshotResult(null, it.message ?: "Webcam list failed")
            }
            val cam = webcams.firstOrNull { it.uid == binding.webcamUid || it.name == binding.webcamName }
                ?: return SnapshotResult(null, "Camera missing")
            val snapshotUrl = cam.snapshotUrl.ifBlank { cam.streamUrl }
            if (snapshotUrl.isBlank()) return SnapshotResult(null, "No snapshot URL")
            return try {
                val request = api.snapshotRequest(printer, snapshotUrl)
                api.httpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return SnapshotResult(null, "HTTP ${response.code}")
                    }
                    val bytes = response.body?.bytes() ?: return SnapshotResult(null, "Empty image")
                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return SnapshotResult(null, "Decode failed")
                    bitmap = transform(bitmap, cam.flipHorizontal, cam.flipVertical, cam.rotation)
                    SnapshotResult(bitmap, cam.name.ifBlank { "Camera" } + " · tap refresh")
                }
            } catch (t: Throwable) {
                SnapshotResult(null, t.message ?: "Refresh failed")
            }
        }

        private fun transform(
            source: Bitmap,
            flipH: Boolean,
            flipV: Boolean,
            rotation: Int,
        ): Bitmap {
            if (!flipH && !flipV && rotation == 0) return source
            val matrix = Matrix().apply {
                if (flipH) postScale(-1f, 1f, source.width / 2f, source.height / 2f)
                if (flipV) postScale(1f, -1f, source.width / 2f, source.height / 2f)
                if (rotation != 0) postRotate(rotation.toFloat(), source.width / 2f, source.height / 2f)
            }
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }
}

private data class SnapshotResult(val bitmap: Bitmap?, val label: String)
