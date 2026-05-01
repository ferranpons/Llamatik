@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

actual fun decodeImageBytesToImageBitmap(
    bytes: ByteArray,
    suggestedFileName: String?
): ImageBitmap? {
    if (bytes.isEmpty()) return null

    return try {
        // Most SD outputs / downloads are PNG/JPEG. Skia handles both.
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        // If decode fails, at least dump bytes to temp for debugging.
        saveTemp(bytes, suggestedFileName, ".bin")
        null
    }
}

actual fun normalizeToJpegBytes(bytes: ByteArray): ByteArray {
    if (bytes.isEmpty()) return bytes
    return try {
        val nsData = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        val uiImage = UIImage(data = nsData) ?: return bytes
        val jpegData = UIImageJPEGRepresentation(uiImage, 0.92) ?: return bytes
        val len = jpegData.length.toInt()
        ByteArray(len).also { out ->
            out.usePinned { pinned ->
                memcpy(pinned.addressOf(0), jpegData.bytes, jpegData.length.convert())
            }
        }
    } catch (_: Throwable) {
        bytes
    }
}

actual fun decodeImageBytesToRgba(bytes: ByteArray): Triple<ByteArray, Int, Int>? {
    if (bytes.isEmpty()) return null
    return try {
        val image = Image.makeFromEncoded(bytes)
        val w = image.width
        val h = image.height
        val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        val bitmap = Bitmap()
        bitmap.allocPixels(info)
        image.readPixels(bitmap, dstX = 0, dstY = 0)
        val rgba = bitmap.readPixels(dstInfo = info, dstRowBytes = w * 4, srcX = 0, srcY = 0)
            ?: return null
        Triple(rgba, w, h)
    } catch (_: Throwable) {
        null
    }
}

private fun saveTemp(bytes: ByteArray, suggestedName: String?, ext: String) {
    try {
        val name = ((suggestedName ?: "image") + ext)
            .replace("/", "_")
            .replace(":", "_")

        val path = NSTemporaryDirectory() + "/" + name

        bytes.usePinned { pinned ->
            val data = NSData.create(
                bytes = pinned.addressOf(0),
                length = bytes.size.toULong()
            )
            data.writeToFile(path, true)
        }
    } catch (_: Throwable) {
        // ignore
    }
}
