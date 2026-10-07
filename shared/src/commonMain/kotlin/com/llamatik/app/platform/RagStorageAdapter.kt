package com.llamatik.app.platform

import com.llamatik.sdk.assistant.RagStorage

class RagStorageAdapter : RagStorage {
    override suspend fun write(relativePath: String, bytes: ByteArray) {
        AppStorage.writeBytes(relativePath, bytes)
    }

    override suspend fun read(relativePath: String): ByteArray? {
        return AppStorage.readBytes(relativePath)
    }

    override fun delete(relativePath: String): Boolean {
        return AppStorage.delete(relativePath)
    }
}
