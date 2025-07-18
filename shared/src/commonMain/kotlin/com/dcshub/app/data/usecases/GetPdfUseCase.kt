package com.dcshub.app.data.usecases

import com.dcshub.app.common.usecases.UseCase
import com.dcshub.app.data.repositories.DownloadFileRepository
import korlibs.encoding.Base64

class GetPdfUseCase(
    private val downloadFileRepository: DownloadFileRepository,
) : UseCase() {

    suspend fun invoke(userManualUrl: String): Result<Pair<ByteArray?, String>> = runCatching {
        val response = downloadFileRepository.download(userManualUrl)
        return@runCatching Pair<ByteArray, String>(response, Base64.encode(response))
    }

    suspend fun downloadFile(userManualUrl: String): Result<Pair<ByteArray?, String>> =
        runCatching {
            val fileName = extractFileName(userManualUrl)
            val tempFile =
                downloadFileRepository.downloadFileAndSave(url = userManualUrl, fileName = fileName)
            val bytes = tempFile.readBytes()
            val base64String = tempFile.readBase64String()
            if (bytes.isNotEmpty()) {
                return@runCatching Pair<ByteArray?, String>(
                    bytes,
                    base64String
                )
            } else {
                return@runCatching Pair<ByteArray?, String>(null, "")
            }
        }

    private fun extractFileName(url: String): String {
        val parts = url.split("/")
        return removeFileExtension(parts.last())
    }

    private fun removeFileExtension(filename: String): String {
        val lastIndex = filename.lastIndexOf(".")
        return if (lastIndex > 0) {
            filename.substring(0, lastIndex)
        } else {
            filename
        }
    }
}
