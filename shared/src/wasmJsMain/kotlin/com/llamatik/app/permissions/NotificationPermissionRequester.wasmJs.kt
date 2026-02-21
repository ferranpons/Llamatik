package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    return NotificationPermissionRequester { onResult -> onResult(true) }
}