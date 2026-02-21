package com.llamatik.app.platform

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

actual fun extractPdfText(pdfBytes: ByteArray): String {
    if (pdfBytes.isEmpty()) return ""
    ByteArrayInputStream(pdfBytes).use { input ->
        PDDocument.load(input).use { doc ->
            val stripper = PDFTextStripper()
            return stripper.getText(doc).orEmpty()
        }
    }
}
