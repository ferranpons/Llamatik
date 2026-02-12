package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

interface AudioPermissionRequester {
    fun requestAndRun(onGranted: () -> Unit)
}

@Composable
expect fun rememberAudioPermissionRequester(): AudioPermissionRequester
