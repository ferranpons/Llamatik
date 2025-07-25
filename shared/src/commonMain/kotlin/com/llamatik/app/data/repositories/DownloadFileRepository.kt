package com.llamatik.app.data.repositories

import com.llamatik.app.platform.ServiceClient
import io.ktor.client.call.body
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining

private const val DEFAULT_BUFFER_SIZE: Int = 8 * 1024

class DownloadFileRepository(private val service: ServiceClient) {

    suspend fun download(url: String): ByteArray {
        return service.httpClient.get(url) { // make sure this is the correct link!
            onDownload { bytesSentTotal, contentLength ->
                println("Downloaded $bytesSentTotal of $contentLength")
            }
        }.body<ByteArray>()
        /*
        return service.httpClient.requestAndCatch(
            {
                get(url).bodyAsChannel()
            },
            {
                when (response.status) {
                    HttpStatusCode.BadRequest -> {
                        throw BadRequestException()
                    }
                    HttpStatusCode.Conflict -> {
                        throw ConflictException()
                    }
                    else -> throw this
                }
            }
        )

         */
    }

    suspend fun downloadFile(url: String, fileName: String): ByteReadChannel {
        /*return service.httpClient.get(url) {
            onDownload { bytesSentTotal, contentLength ->
                println("Downloaded $bytesSentTotal of $contentLength")
            }
        }.bodyAsChannel()*/

        var response: ByteReadChannel = ByteReadChannel.Empty
        service.httpClient.prepareGet(url).execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    //file.appendBytes(bytes)
                    println("Downloaded ${bytes.size} of ${httpResponse.contentLength()}")
                }
            }
            println("Download Finished")
        }
        return response
    }
/*
    suspend fun downloadFileAndSave(url: String, fileName: String): LlamatikTempFile {
        val file = LlamatikTempFile(fileName)
        service.httpClient.prepareGet(url).execute { httpResponse ->
            val channel: ByteReadChannel = httpResponse.body()
            println("Downloading ${httpResponse.contentLength()} bytes")
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                while (!packet.isEmpty) {
                    val bytes = packet.readBytes()
                    file.appendBytes(bytes)
                    file.appendBytesBase64(bytes)
                }
            }
            file.close()
            println("Download Finished")
        }
        return file
    }*/
}