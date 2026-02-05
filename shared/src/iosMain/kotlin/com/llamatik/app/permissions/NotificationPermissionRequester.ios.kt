package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    // iOS doesn't require runtime permission for app notifications in the same way as Android 13+.
    // (User can manage notification authorization in iOS Settings; requesting it here is not required
    // to show in-app download UI.)
    return NotificationPermissionRequester { onResult -> onResult(true) }
}
