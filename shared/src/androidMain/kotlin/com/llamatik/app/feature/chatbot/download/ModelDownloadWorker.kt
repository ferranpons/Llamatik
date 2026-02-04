package com.llamatik.app.feature.chatbot.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.llamatik.app.platform.ServiceClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()

        ensureChannel(applicationContext)

        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val partFile = File(modelsDir, "$modelId.gguf.part")
        val finalFile = File(modelsDir, "$modelId.gguf")

        return try {
            setForeground(foreground(modelId, 0))

            downloadResumable(url, partFile) { pct ->
                // ✅ Ahora sí: estamos en suspend lambda
                setForeground(foreground(modelId, pct))
                setProgress(workDataOf(KEY_PROGRESS to pct))
            }

            if (finalFile.exists()) finalFile.delete()
            partFile.renameTo(finalFile)

            Result.success(workDataOf(KEY_PATH to finalFile.absolutePath))
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private suspend fun downloadResumable(
        url: String,
        partFile: File,
        onProgress: suspend (Int) -> Unit // ✅ IMPORTANT: suspend
    ): String = withContext(Dispatchers.IO) {

        val already = if (partFile.exists()) partFile.length() else 0L

        val resp: HttpResponse = ServiceClient.httpClient.get(url) {
            if (already > 0) header(HttpHeaders.Range, "bytes=$already-")
        }

        if (!resp.status.isSuccess()) error("HTTP ${resp.status.value}")

        val channel = resp.bodyAsChannel()
        val total = parseTotalFromHeaders(resp, already) // ✅ no resp.contentLength()

        RandomAccessFile(partFile, "rw").use { raf ->
            raf.seek(already)

            var written = already
            var lastPct = -1

            while (!channel.isClosedForRead) {
                ensureActive()

                // ✅ Compatible con más versiones de Ktor
                val packet = channel.readRemaining(64 * 1024)
                if (packet.isEmpty) break

                val bytes = packet.readBytes()
                raf.write(bytes)
                written += bytes.size

                if (total > 0) {
                    val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                    if (pct != lastPct) {
                        lastPct = pct
                        onProgress(pct)
                    }
                }
            }
        }

        partFile.absolutePath
    }

    private fun parseTotalFromHeaders(resp: HttpResponse, already: Long): Long {
        // Content-Range example: "bytes 100-999/12345"
        val contentRange = resp.headers[HttpHeaders.ContentRange]
        if (!contentRange.isNullOrBlank()) {
            val slash = contentRange.lastIndexOf('/')
            if (slash != -1 && slash + 1 < contentRange.length) {
                contentRange.substring(slash + 1).toLongOrNull()?.let { return it }
            }
        }

        // Content-Length here is "remaining"; total = already + remaining
        val remaining = resp.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
        return if (remaining > 0) already + remaining else -1L
    }

    private fun foreground(modelId: String, progress: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading model")
            .setContentText("$modelId • $progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .build()

        val id = modelId.hashCode()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, n)
        }
    }

    private fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val KEY_MODEL_ID = "modelId"
        const val KEY_URL = "url"
        const val KEY_PROGRESS = "progress"
        const val KEY_PATH = "path"
    }
}
