package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Tries to decode the bytes into an ImageBitmap, trying multiple formats. */
expect fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String? = null): ImageBitmap?

/**
 * Decodes encoded image bytes (JPEG/PNG/WebP/BMP) into raw RGBA8888 pixels.
 * Returns a Triple of (rgbaBytes, width, height), or null on failure.
 */
expect fun decodeImageBytesToRgba(bytes: ByteArray): Triple<ByteArray, Int, Int>?

/**
 * Ensures image bytes are in a format stb_image can decode (JPEG/PNG/BMP/GIF/WebP).
 * On iOS, HEIC images from the Photos library are re-encoded to JPEG.
 * On other platforms, bytes are returned unchanged.
 */
expect fun normalizeToJpegBytes(bytes: ByteArray): ByteArray
