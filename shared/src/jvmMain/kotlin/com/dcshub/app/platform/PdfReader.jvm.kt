package com.dcshub.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.utils.io.ByteReadChannel

@Composable
actual fun PdfReader(
    modifier: Modifier,
    pdfData: ByteArray,
    encodedBase64Data: String?,
    pdfUrl: String?
) {
}

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DCSHubTempFile actual constructor(fileName: String) {
    actual fun readBytes(): ByteArray {
        TODO("Not yet implemented")
    }

    actual fun appendBytes(bytes: ByteArray) {
    }

    actual fun getBase64String(): String {
        TODO("Not yet implemented")
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
    }

    actual fun close() {
    }

    actual fun readBase64String(): String {
        TODO("Not yet implemented")
    }

}