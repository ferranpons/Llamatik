package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    // Desktop JVM targets don't have an Android-style runtime notification permission.
    return NotificationPermissionRequester { onResult -> onResult(true) }
}
