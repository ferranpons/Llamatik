package com.llamatik.app.platform

actual fun extractPdfText(pdfBytes: ByteArray): String {
    // Browser/Wasm implementation would typically use PDF.js via a JS glue layer.
    // Returning empty string keeps the feature non-crashing until wired.
    return ""
}
