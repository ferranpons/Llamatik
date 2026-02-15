package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decode a PNG ByteArray into an ImageBitmap for Compose UI.
 * Returns null if decoding fails.
 */
expect fun decodePngToImageBitmap(pngBytes: ByteArray): ImageBitmap?
