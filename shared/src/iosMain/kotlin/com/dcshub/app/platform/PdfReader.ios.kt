package com.dcshub.app.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import io.ktor.utils.io.ByteReadChannel
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKWebView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_write
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.open

@Composable
actual fun PdfReader(
    modifier: Modifier,
    pdfData: ByteArray,
    encodedBase64Data: String?,
    pdfUrl: String?
) {
    UIKitView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val wkWebView = WKWebView()
            wkWebView.scrollView.scrollEnabled = true
            wkWebView.scrollView.pinchGestureRecognizer?.enabled = true
            wkWebView.scrollView.panGestureRecognizer.enabled = true
            wkWebView.scrollView.directionalPressGestureRecognizer.enabled = true
            wkWebView
        },
        update = { webView ->
            webView.loadRequest(
                NSURLRequest.requestWithURL(
                    NSURL.URLWithString("$pdfUrl")!!
                )
            )
        }
    )
}

private const val BUFFER_SIZE = 4096

@OptIn(ExperimentalForeignApi::class)
actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
    val channel = this
    val queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0u)
    memScoped {
        val dst = allocArray<ByteVar>(BUFFER_SIZE)
        val fd = open(fileName, O_RDWR)

        try {
            while (!channel.isClosedForRead) {
                val rs = channel.readAvailable(dst, 0, BUFFER_SIZE)
                if (rs < 0) break

                val data = dispatch_data_create(dst, rs.convert(), queue) {}

                dispatch_write(fd, data, queue) { _, error ->
                    if (error != 0) {
                        channel.cancel(IllegalStateException("Unable to write data to the file $fileName"))
                    }
                }
            }
        } finally {
            close(fd)
        }
    }
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DCSHubTempFile actual constructor(fileName: String) {
    actual fun readBytes(): ByteArray {
        return byteArrayOf()
    }

    actual fun appendBytes(bytes: ByteArray) {
    }

    actual fun getBase64String(): String {
        return ""
    }

    actual fun appendBytesBase64(bytes: ByteArray) {
    }

    actual fun close() {
    }

    actual fun readBase64String(): String {
        return ""
    }

}