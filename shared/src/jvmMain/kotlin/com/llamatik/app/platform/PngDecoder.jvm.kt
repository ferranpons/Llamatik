package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image

actual fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(pngBytes).asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
