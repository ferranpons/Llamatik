package com.llamatik.app.feature.chatbot.download

import android.app.Notification
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
import com.llamatik.app.localization.getCurrentLocalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val localization = getCurrentLocalization()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .callTimeout(0, TimeUnit.MILLISECONDS) // no overall timeout for big files
        .build()

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()

        ensureChannel(applicationContext)

        val modelsDir = File(applicationContext.filesDir, "models").apply { mkdirs() }
        val partFile = File(modelsDir, "$modelId.gguf.part")
        val finalFile = File(modelsDir, "$modelId.gguf")

        return try {
            setForeground(foreground(modelId, 0))

            downloadResumable(url, partFile) { pct ->
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
        onProgress: suspend (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {

        val already = if (partFile.exists()) partFile.length() else 0L

        val request = Request.Builder()
            .url(url)
            // Prevent transparent gzip which can lead to buffering / weird memory patterns
            .header("Accept-Encoding", "identity")
            .apply {
                if (already > 0) header("Range", "bytes=$already-")
            }
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")

            // If we asked for Range but server ignored it (200 instead of 206), restart clean
            if (already > 0 && resp.code == 200) {
                partFile.delete()
                return@withContext downloadResumable(url, partFile, onProgress)
            }

            val body = resp.body
            val contentLength = body.contentLength() // remaining bytes
            val total = if (contentLength > 0) already + contentLength else -1L

            RandomAccessFile(partFile, "rw").use { raf ->
                raf.seek(already)

                val buffer = ByteArray(256 * 1024) // 256KB reusable buffer
                var written = already
                var lastPct = -1

                body.byteStream().use { input ->
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break

                        raf.write(buffer, 0, read)
                        written += read

                        if (total > 0) {
                            val pct = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        }

        partFile.absolutePath
    }

    private fun foreground(modelId: String, progress: Int): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(localization.downloading)
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

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = createNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotification(): Notification {
        createNotificationChannel()

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(localization.downloadingMainModels)
            .setContentText(localization.downloading)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
            )
            .build()
    }

    private fun createNotificationChannel() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            localization.downloadingMainModels,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Downloads AI models for offline use"
            setShowBadge(false)
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "model_downloads"
        const val KEY_MODEL_ID = "modelId"
        const val KEY_URL = "url"
        const val KEY_PROGRESS = "progress"
        const val KEY_PATH = "path"
        private const val NOTIFICATION_ID = 1001
    }
}
