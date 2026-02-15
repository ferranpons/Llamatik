package com.llamatik.app.platform

import androidx.compose.ui.graphics.ImageBitmap

/** Tries to decode the bytes into an ImageBitmap, trying multiple formats. */
expect fun decodeImageBytesToImageBitmap(bytes: ByteArray, suggestedFileName: String? = null): ImageBitmap?
