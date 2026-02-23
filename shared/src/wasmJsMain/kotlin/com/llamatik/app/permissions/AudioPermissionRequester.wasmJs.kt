package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberAudioPermissionRequester(): AudioPermissionRequester {
    return object : AudioPermissionRequester {
        override fun requestAndRun(onGranted: () -> Unit) {
            onGranted()
        }
    }
}
