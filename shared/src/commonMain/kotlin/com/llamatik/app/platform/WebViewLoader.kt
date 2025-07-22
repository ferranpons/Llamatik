package com.llamatik.app.platform

import androidx.compose.runtime.Composable

@Composable
expect fun WebViewLayout(
    htmlContent: String,
    isLoading: (isLoading: Boolean) -> Unit,
    onUrlClicked: (url: String) -> Unit
)