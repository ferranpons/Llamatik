package com.dcshub.app.platform

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
actual fun WebViewLayout(
    htmlContent: String,
    isLoading: (isLoading: Boolean) -> Unit,
    onUrlClicked: (url: String) -> Unit
) {
    val context = LocalContext.current
    var isLoadingFinished by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                WebView(context).apply {
                    scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
                    setBackgroundColor(Color.Transparent.toArgb())
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )

                    // Enable horizontal scrolling
                    settings.apply {
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?
                        ) {
                            isLoading(true)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            view?.scrollTo(view.contentHeight, 0)
                            isLoadingFinished = true
                            isLoading(false)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            return if (request?.url.toString().contains("jpg") ||
                                request?.url.toString().contains("png") ||
                                request?.url.toString().contains("attachment_id")
                            ) {
                                true
                            } else {
                                onUrlClicked(request?.url.toString())
                                true
                            }
                        }
                    }
                }
            },
            update = { webView ->
                webView.loadUrl(htmlContent)
            }
        )
    }
}
