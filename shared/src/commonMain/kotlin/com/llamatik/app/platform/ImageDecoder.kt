package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Tries to decode the bytes into an ImageBitmap, trying multiple formats. */
expect fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String? = null): ImageBitmap?

/**
 * Ensures image bytes are in a format stb_image can decode (JPEG/PNG/BMP/GIF/WebP).
 * On iOS, HEIC images from the Photos library are re-encoded to JPEG.
 * On other platforms, bytes are returned unchanged.
 */
expect fun normalizeToJpegBytes(bytes: ByteArray): ByteArray
