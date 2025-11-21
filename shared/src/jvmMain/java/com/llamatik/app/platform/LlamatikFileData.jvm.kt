package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel

actual suspend fun ByteReadChannel.writeToFile(fileName: String) {
}

actual suspend fun ByteArray.writeToFile(fileName: String) {
}

actual suspend fun ByteArray.addBytesToFile(fileName: String) {
}

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class LlamatikTempFile actual constructor(fileName: String) {
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

    actual fun absolutePath(): String {
        TODO("Not yet implemented")
    }
}
