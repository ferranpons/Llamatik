package com.llamatik.app.platform

/**
 * Extracts *text* from a PDF file.
 *
 * Notes:
 * - This will NOT work for scanned/image-only PDFs (they need OCR).
 * - Implementation is platform-specific:
 *   - Android: pdfbox-android
 *   - JVM/Desktop: Apache PDFBox
 *   - iOS: PDFKit
 */
expect fun extractPdfText(pdfBytes: ByteArray): String
