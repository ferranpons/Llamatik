package com.llamatik.app.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat


@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    val context = LocalContext.current

    // We store a pending callback because the result comes asynchronously.
    var pendingResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingResult?.invoke(granted)
        pendingResult = null
    }

    return remember {
        NotificationPermissionRequester { onResult ->
            // Android 12 and below: notification permission doesn't exist.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onResult(true)
                return@NotificationPermissionRequester
            }

            val permission = Manifest.permission.POST_NOTIFICATIONS
            val alreadyGranted = ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                onResult(true)
            } else {
                // Trigger system dialog
                pendingResult = onResult
                launcher.launch(permission)
            }
        }
    }
}

// Optional helper if you ever need an Activity (not required for the launcher approach).
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
