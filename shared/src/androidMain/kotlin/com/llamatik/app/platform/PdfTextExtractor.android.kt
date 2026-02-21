package com.llamatik.app.platform

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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
