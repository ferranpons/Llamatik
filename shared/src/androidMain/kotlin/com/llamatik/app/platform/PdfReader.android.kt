package com.llamatik.app.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rizzi.bouquet.ResourceType
import com.rizzi.bouquet.VerticalPDFReader
import com.rizzi.bouquet.rememberVerticalPdfReaderState
import io.ktor.util.toByteArray
import io.ktor.utils.io.ByteReadChannel
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Base64
import kotlin.io.path.appendBytes
import kotlin.io.path.bufferedReader
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.writeBytes

@Composable
actual fun PdfReader(
    modifier: Modifier,
    pdfData: ByteArray,
    encodedBase64Data: String?,
    pdfUrl: String?
) {
    val pdfState = if (encodedBase64Data != null) {
        rememberVerticalPdfReaderState(
            resource = ResourceType.Base64(encodedBase64Data),
            isZoomEnable = true,
        )
    } else {
        rememberVerticalPdfReaderState(
            resource = ResourceType.Remote(pdfUrl ?: ""),
            isZoomEnable = true,
        )
    }
    VerticalPDFReader(
        state = pdfState,
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.primary),
    )
}

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val file = kotlin.io.path.createTempFile(
        prefix = fileName, suffix = ".pdf"
    )
    file.writeBytes(this.toByteArray())
    //this.copyTo(File(fileName).writeChannel())
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
    val file = kotlin.io.path.createTempFile(
        prefix = fileName, suffix = ".pdf"
    )
    file.writeBytes(this)
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {

    val file = kotlin.io.path.createTempFile(
        prefix = fileName, suffix = ".pdf"
    )
    file.writeBytes(this)
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class LlamatikTempFile actual constructor(fileName: String) {
    private val file = kotlin.io.path.createTempFile(
        prefix = fileName, suffix = ".pdf"
    )
    private val base64file = kotlin.io.path.createTempFile(
        prefix = fileName, suffix = ".txt"
    )
    private val base64fileStream = base64file.outputStream()
    private val base64Encoder = Base64.getEncoder().wrap(base64fileStream)

    actual fun readBytes(): ByteArray {
        RandomAccessFile(file.pathString, "r").use { raf ->
            val channel: FileChannel = raf.channel
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            val byteArray = ByteArray(buffer.remaining())
            buffer.get(byteArray)

            return byteArray
        }
    }

    actual fun appendBytes(bytes: ByteArray) {
        file.appendBytes(bytes)
    }

    actual fun getBase64String(): String {
        val outputStream = ByteArrayOutputStream()
        encodeFileToBase64(outputStream)
        return outputStream.toString("UTF-8")
    }

    private fun encodeFileToBase64(output: OutputStream) {
        val buffer = ByteArray(4096) // 4 KB buffer
        val base64Encoder = Base64.getEncoder().wrap(output)

        FileInputStream(base64file.pathString).use { inputStream ->
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                base64Encoder.write(buffer, 0, bytesRead)
            }
        }
        base64Encoder.close() // Ensure proper completion of Base64 encoding
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
        base64Encoder.write(bytes)
    }

    actual fun close() {
        base64Encoder.close()
    }

    actual fun readBase64String(): String {
        val stringBuilder = java.lang.StringBuilder()

        base64file.bufferedReader().use { reader ->
            reader.forEachLine { line ->
                stringBuilder.append(line) // Append each line with a newline
            }
        }

        return stringBuilder.toString()
    }
}