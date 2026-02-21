package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap? {
    return try {
        if (pngBytes.isEmpty()) return null
        Image.makeFromEncoded(pngBytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
