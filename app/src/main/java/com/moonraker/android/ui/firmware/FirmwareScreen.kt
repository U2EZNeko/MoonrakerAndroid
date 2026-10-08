package com.moonraker.android.ui.firmware

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moonraker.android.ui.common.AppBarTitle
import com.moonraker.android.ui.common.EmptyState
import com.moonraker.android.ui.common.PrinterSwitcher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareScreen(viewModel: FirmwareViewModel) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var reloadToken by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(reloadToken) {
        if (reloadToken > 0) webView?.reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AppBarTitle(title = "Firmware") },
                actions = {
                    IconButton(onClick = { reloadToken++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    PrinterSwitcher(
                        printers = ui.printers,
                        activeId = ui.activePrinter?.id,
                        onSelect = viewModel::setActivePrinter,
                    )
                },
            )
        },
    ) { padding ->
        val printer = ui.activePrinter
        val url = ui.configUrl
        when {
            printer == null -> EmptyState(
                message = "Add a printer in Settings",
                modifier = Modifier.padding(padding),
            )

            url.isNullOrBlank() -> EmptyState(
                message = "Firmware config URL unavailable",
                modifier = Modifier.padding(padding),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Text(
                    text = "Paxx12 firmware-config · enable Advanced Mode on the printer if this page is blank",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                FirmwareConfigWebView(
                    url = url,
                    apiKey = printer.apiKey,
                    onWebViewReady = { webView = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FirmwareConfigWebView(
    url: String,
    apiKey: String,
    onWebViewReady: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                onWebViewReady(this)
                if (apiKey.isNotBlank()) {
                    loadUrl(url, mapOf("X-Api-Key" to apiKey))
                } else {
                    loadUrl(url)
                }
            }
        },
        update = { view ->
            onWebViewReady(view)
            val current = view.url.orEmpty()
            if (current.isBlank() || (!current.startsWith(url.trimEnd('/')) && current != url)) {
                if (apiKey.isNotBlank()) {
                    view.loadUrl(url, mapOf("X-Api-Key" to apiKey))
                } else {
                    view.loadUrl(url)
                }
            }
        },
    )
}
