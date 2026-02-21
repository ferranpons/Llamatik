package com.llamatik.app.platform

actual object AppStorage {
    actual suspend fun writeBytes(relativePath: String, bytes: ByteArray) {
    }

    actual suspend fun readBytes(relativePath: String): ByteArray? {
        TODO("Not yet implemented")
    }

    actual fun exists(relativePath: String): Boolean {
        TODO("Not yet implemented")
    }

    actual fun delete(relativePath: String): Boolean {
        TODO("Not yet implemented")
    }
}