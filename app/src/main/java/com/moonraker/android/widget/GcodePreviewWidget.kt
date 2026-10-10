package com.moonraker.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.moonraker.android.MainActivity
import com.moonraker.android.MoonrakerApp
import com.moonraker.android.R
import com.moonraker.android.data.moonraker.MoonrakerApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class GcodePreviewWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            refreshWidget(context, appWidgetManager, id, showLoading = true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                refreshWidget(context, AppWidgetManager.getInstance(context), id, showLoading = true)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.moonraker.android.widget.ACTION_GCODE_REFRESH"

        fun refreshWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            showLoading: Boolean,
        ) {
            val openApp = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_GCODE, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val refreshIntent = Intent(context, GcodePreviewWidgetReceiver::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                appWidgetId + 10_000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            if (showLoading) {
                val loading = RemoteViews(context.packageName, R.layout.gcode_widget)
                loading.setTextViewText(R.id.gcode_widget_title, "G-code")
                loading.setTextViewText(R.id.gcode_widget_status, "Refreshing…")
                loading.setOnClickPendingIntent(R.id.gcode_widget_root, refreshPending)
                appWidgetManager.updateAppWidget(appWidgetId, loading)
            }

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val result = loadPreview(context)
                val views = RemoteViews(context.packageName, R.layout.gcode_widget)
                views.setTextViewText(R.id.gcode_widget_title, result.title)
                views.setTextViewText(R.id.gcode_widget_status, result.status)
                if (result.bitmap != null) {
                    views.setImageViewBitmap(R.id.gcode_widget_image, result.bitmap)
                }
                // Tap opens app; long-press style refresh via secondary pending on root after load
                views.setOnClickPendingIntent(R.id.gcode_widget_root, openApp)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private suspend fun loadPreview(context: Context): GcodeWidgetResult {
            val app = context.applicationContext as MoonrakerApp
            val settings = app.settingsRepository.settings.first()
            val printer = settings.activePrinter
                ?: return GcodeWidgetResult(null, "G-code", "Add a printer")
            val api = MoonrakerApiClient()
            val job = api.queryPrintJob(printer).getOrElse {
                return GcodeWidgetResult(null, printer.name, it.message ?: "Job query failed")
            }
            if (job.filename.isBlank() || job.state !in setOf("printing", "paused", "complete")) {
                return GcodeWidgetResult(
                    null,
                    printer.name,
                    if (job.state == "standby") "No active job · tap refresh" else job.state,
                )
            }
            val meta = api.gcodeMetadata(printer, job.filename).getOrNull()
            val thumb = meta?.thumbnails?.maxByOrNull { it.width * it.height }
            val bitmap = if (thumb != null && thumb.relativePath.isNotBlank()) {
                val url = api.thumbnailUrl(printer, job.filename, thumb.relativePath)
                api.downloadThumbnailBytes(printer, url).getOrNull()?.let { bytes ->
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } else {
                null
            }
            val pct = (job.progress * 100f).roundToInt()
            val shortName = job.filename.substringAfterLast('/')
            return GcodeWidgetResult(
                bitmap = bitmap,
                title = shortName,
                status = "${job.state.replaceFirstChar { it.titlecase() }} · $pct% · tap open",
            )
        }
    }
}

const val EXTRA_OPEN_GCODE = "com.moonraker.android.OPEN_GCODE"

private data class GcodeWidgetResult(
    val bitmap: android.graphics.Bitmap?,
    val title: String,
    val status: String,
)
