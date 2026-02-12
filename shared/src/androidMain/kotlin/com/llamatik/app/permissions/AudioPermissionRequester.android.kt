package com.llamatik.app.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberAudioPermissionRequester(): AudioPermissionRequester {
    val context = LocalContext.current

    var pending: (() -> Unit)? = null

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pending?.invoke()
        pending = null
    }

    return remember {
        object : AudioPermissionRequester {
            override fun requestAndRun(onGranted: () -> Unit) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (granted) {
                    onGranted()
                } else {
                    pending = onGranted
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}
