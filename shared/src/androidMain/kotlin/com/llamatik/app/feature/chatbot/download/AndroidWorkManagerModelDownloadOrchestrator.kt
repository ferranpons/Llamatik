package com.llamatik.app.feature.chatbot.download

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.llamatik.app.feature.chatbot.model.LlamaModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidWorkManagerModelDownloadOrchestrator(
    private val context: Context
) : ModelDownloadOrchestrator {

    override fun download(model: LlamaModel): Flow<DownloadEvent> {
        val modelId = safeId(model)

        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .addTag(tagFor(modelId))
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_MODEL_ID to modelId,
                    ModelDownloadWorker.KEY_URL to model.url
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueNameFor(modelId),
            ExistingWorkPolicy.KEEP,
            req
        )

        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(uniqueNameFor(modelId))
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map DownloadEvent.Progress(0)
                val p = info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)

                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(ModelDownloadWorker.KEY_PATH)
                        if (path.isNullOrBlank()) DownloadEvent.Failed("Missing output path")
                        else DownloadEvent.Completed(path)
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        DownloadEvent.Failed("Download ${info.state.name.lowercase()}")
                    }
                    else -> DownloadEvent.Progress(p)
                }
            }
    }

    override fun cancel(model: LlamaModel) {
        val modelId = safeId(model)
        WorkManager.getInstance(context)
            .cancelUniqueWork(uniqueNameFor(modelId))
    }

    private fun uniqueNameFor(modelId: String) = "download_work_$modelId"
    private fun tagFor(modelId: String) = "download_$modelId"

    private fun safeId(model: LlamaModel): String =
        model.name.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
}
