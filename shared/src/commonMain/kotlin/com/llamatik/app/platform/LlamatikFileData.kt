package com.llamatik.app.platform

import io.ktor.utils.io.ByteReadChannel

expect suspend fun ByteReadChannel.writeToFile(fileName: String)
expect suspend fun ByteArray.writeToFile(fileName: String)
expect suspend fun ByteArray.addBytesToFile(fileName: String)
expect fun migrateModelPathIfNeeded(modelNameOrFileName: String, savedPath: String): String

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class LlamatikTempFile(fileName: String) {
    fun readBytes(): ByteArray
    fun appendBytes(bytes: ByteArray)
    fun getBase64String(): String
    fun appendBytesBase64(bytes: ByteArray)
    fun close()
    fun readBase64String(): String
    fun absolutePath(): String
    fun delete(path: String): Boolean
}
