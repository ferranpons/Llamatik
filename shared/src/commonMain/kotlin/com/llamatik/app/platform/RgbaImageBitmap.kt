package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Builds an [ImageBitmap] from raw RGBA8888 pixels.
 *
 * Expected size: width * height * 4.
 */
expect fun rgbaToImageBitmap(width: Int, height: Int, rgba: ByteArray): ImageBitmap?
