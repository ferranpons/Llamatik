package com.llamatik.sdk.download

import com.llamatik.sdk.model.LlamaModel
import kotlinx.coroutines.flow.Flow

sealed interface DownloadEvent {
    data class Progress(val percent: Int) : DownloadEvent
    data class Completed(val localPath: String) : DownloadEvent
    data class Failed(val message: String?) : DownloadEvent
}

interface ModelDownloadOrchestrator {
    fun download(model: LlamaModel): Flow<DownloadEvent>
    fun cancel(model: LlamaModel)
}
