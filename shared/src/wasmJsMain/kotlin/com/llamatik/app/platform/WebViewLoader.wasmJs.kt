package com.llamatik.app.platform

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
actual fun WebViewLayout(
    htmlContent: String,
    isLoading: (isLoading: Boolean) -> Unit,
    onUrlClicked: (url: String) -> Unit
) {
    // Compose Multiplatform for Wasm renders to a Skia canvas, not a DOM WebView.
    // We provide a non-crashing placeholder.
    LaunchedEffect(Unit) {
        isLoading(false)
    }

    Text("Web preview is not available on the WASM target yet.")
}
