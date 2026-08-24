package com.llamatik.sdk.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed class ImportModelResult {
    data class Success(val model: LlamaModel) : ImportModelResult()
    data class Failure(val reason: String) : ImportModelResult()
}

class ImportModelUseCase(private val modelsRepository: ModelsRepository) {

    @OptIn(ExperimentalTime::class)
    fun importModel(
        filePath: String,
        displayName: String,
        sizeBytes: Long? = null,
    ): ImportModelResult {
        if (!isValidModelFile(filePath)) {
            return ImportModelResult.Failure("File must be a .gguf or .bin model file")
        }

        val name = displayName.ifBlank { fileNameFromPath(filePath) }
        val model = LlamaModel(
            name = name,
            url = "",
            sizeMb = sizeBytes?.let { (it / 1_048_576).toInt() } ?: 0,
            localPath = filePath,
            source = ModelSource.UserImported,
            displayName = displayName.ifBlank { null },
            sizeBytes = sizeBytes,
            quantization = detectQuantization(filePath),
            createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
        )

        modelsRepository.saveImportedModel(model)
        return ImportModelResult.Success(model)
    }

    private fun isValidModelFile(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".gguf") || lower.endsWith(".bin")
    }

    private fun fileNameFromPath(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\').ifBlank { path }

    private fun detectQuantization(path: String): String? {
        val name = path.substringAfterLast('/').uppercase()
        val regex = Regex("(Q[0-9]+_[0-9K]+(?:_[MS])?|F16|F32|BF16)")
        return regex.find(name)?.value
    }
}
