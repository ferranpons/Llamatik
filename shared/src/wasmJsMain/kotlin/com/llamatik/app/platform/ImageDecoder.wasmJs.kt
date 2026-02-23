package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImageBytesToImageBitmap(
    bytes: ByteArray,
    suggestedFileName: String?
): ImageBitmap? {
    return try {
        if (bytes.isEmpty()) return null
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
