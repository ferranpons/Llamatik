package com.llamatik.app.permissions

import androidx.compose.runtime.Composable

/**
 * Platform abstraction for requesting notification permission (Android 13+).
 *
 * - Android: requests POST_NOTIFICATIONS at runtime when needed.
 * - iOS/Desktop: no-op (always granted).
 */
class NotificationPermissionRequester internal constructor(
    private val requestImpl: (onResult: (granted: Boolean) -> Unit) -> Unit
) {
    fun request(onResult: (granted: Boolean) -> Unit) = requestImpl(onResult)

    fun requestAndRun(
        onGranted: () -> Unit,
        onDenied: (() -> Unit)? = null,
    ) {
        request { granted ->
            if (granted) onGranted() else onDenied?.invoke()
        }
    }
}

@Composable
expect fun rememberNotificationPermissionRequester(): NotificationPermissionRequester