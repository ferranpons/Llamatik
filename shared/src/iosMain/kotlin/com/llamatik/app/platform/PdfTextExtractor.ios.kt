@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.PDFKit.PDFDocument

actual fun extractPdfText(pdfBytes: ByteArray): String {
    if (pdfBytes.isEmpty()) return ""

    val data: NSData = pdfBytes.usePinned { pinned ->
        NSData.dataWithBytes(
            bytes = pinned.addressOf(0),
            length = pdfBytes.size.toULong()
        )
    }

    val doc = PDFDocument(data) ?: return ""

    val sb = StringBuilder()

    // pageCount is not reliably an Int in K/N bindings → convert explicitly
    val count: Int = doc.pageCount.toInt()
    for (i in 0 until count) {
        // Some bindings want ULong/NSUInteger for the index
        val page = doc.pageAtIndex(i.toULong())
        val text = page?.string
        if (!text.isNullOrBlank()) {
            sb.append(text)
            sb.append('\n')
        }
    }
    return sb.toString()
}
