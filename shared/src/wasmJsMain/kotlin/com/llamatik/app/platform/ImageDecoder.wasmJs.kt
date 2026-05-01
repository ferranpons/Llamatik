package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

actual fun normalizeToJpegBytes(bytes: ByteArray): ByteArray = bytes

actual fun decodeImageBytesToRgba(bytes: ByteArray): Triple<ByteArray, Int, Int>? {
    if (bytes.isEmpty()) return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        image.readPixels(bitmap, srcX = 0, srcY = 0)
        val rgba = bitmap.readPixels(dstInfo = info, dstRowBytes = w * 4, srcX = 0, srcY = 0)
            ?: return null
        Triple(rgba, w, h)
    } catch (_: Throwable) {
        null
    }
}

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
