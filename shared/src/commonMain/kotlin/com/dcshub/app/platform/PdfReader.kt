package com.dcshub.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.ktor.utils.io.ByteReadChannel

@Composable
expect fun PdfReader(
    modifier: Modifier = Modifier,
    pdfData: ByteArray,
    encodedBase64Data: String?,
    pdfUrl: String?
)

expect suspend fun ByteReadChannel.writeToFile(fileName: String)
expect suspend fun ByteArray.writeToFile(fileName: String)
expect suspend fun ByteArray.addBytesToFile(fileName: String)

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DCSHubTempFile(fileName: String) {

    fun readBytes(): ByteArray
    fun appendBytes(bytes: ByteArray)
    fun getBase64String(): String
    fun appendBytesBase64(bytes: ByteArray)
    fun close()
    fun readBase64String(): String
}