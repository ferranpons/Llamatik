package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import platform.Foundation.temporaryDirectory
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_write
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.open

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
actual class LlamatikTempFile actual constructor(fileName: String) {
    private val base64file = platform.Foundation.NSFileManager.defaultManager.temporaryDirectory
        .URLByAppendingPathComponent("${fileName}.tmp")

    actual fun appendBytes(bytes: ByteArray) {}
    actual fun readBytes(): ByteArray { return ByteArray(0) }
    actual fun getBase64String(): String { return "" }
    actual fun appendBytesBase64(bytes: ByteArray) {}
    actual fun close() {}
    actual fun readBase64String(): String { return "" }
    actual fun absolutePath(): String = base64file?.path ?: ""
}
