package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

actual fun normalizeToJpegBytes(bytes: ByteArray): ByteArray = bytes

actual fun decodeImageBytesToRgba(bytes: ByteArray): Triple<ByteArray, Int, Int>? = null

actual fun decodeImageBytesToImageBitmap(
    bytes: ByteArray,
    suggestedFileName: String?
): ImageBitmap? = null
