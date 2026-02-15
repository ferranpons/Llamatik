package com.llamatik.app.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap? {
    return try {
        val bmp = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size) ?: return null
        bmp.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
