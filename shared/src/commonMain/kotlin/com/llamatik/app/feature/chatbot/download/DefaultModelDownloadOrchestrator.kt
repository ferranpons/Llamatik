package com.llamatik.app.feature.chatbot.download

import com.llamatik.app.feature.chatbot.model.LlamaModel
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlin.math.roundToInt

class DefaultModelDownloadOrchestrator(
    private val modelsRepository: ModelsRepository
) : ModelDownloadOrchestrator {

    override fun download(model: LlamaModel): Flow<DownloadEvent> = channelFlow {
        trySend(DownloadEvent.Progress(0))

        val fileName = model.url.substringAfterLast("/").substringBeforeLast(".")
        val file = modelsRepository.downloadFileAndSave(
            url = model.url,
            fileName = fileName
        ) { downloaded, total ->
            val effectiveTotal = if (total > 0) total else model.sizeMb.toLong() * 1024L * 1024L
            if (effectiveTotal > 0) {
                val pct = ((downloaded.toDouble() / effectiveTotal.toDouble()) * 100.0)
                    .roundToInt()
                    .coerceIn(0, if (total > 0) 100 else 99)
                trySend(DownloadEvent.Progress(pct))
            }
        }

        trySend(DownloadEvent.Completed(file.absolutePath()))
    }.catch { e ->
        emit(DownloadEvent.Failed(e.message))
    }

    override fun cancel(model: LlamaModel) {
    }
}
