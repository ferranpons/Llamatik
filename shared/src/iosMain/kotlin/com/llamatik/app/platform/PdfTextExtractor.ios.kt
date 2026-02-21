package com.llamatik.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.PDFKit.PDFDocument

@OptIn(ExperimentalForeignApi::class)
actual fun extractPdfText(pdfBytes: ByteArray): String {
    if (pdfBytes.isEmpty()) return ""

    val data: NSData = NSData.dataWithBytes(pdfBytes, pdfBytes.size.toULong())
    val doc = PDFDocument(data) ?: return ""

    val sb = StringBuilder()
    val count = doc.pageCount
    for (i in 0 until count) {
        val page = doc.pageAtIndex(i)
        val text = page?.string
        if (!text.isNullOrBlank()) {
            sb.append(text)
            sb.append("\n")
        }
    }
    return sb.toString()
}
