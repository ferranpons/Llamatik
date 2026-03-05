@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

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
