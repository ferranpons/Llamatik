package com.llamatik.app.feature.chatbot

import com.llamatik.app.feature.chatbot.model.ModelSource
import com.llamatik.app.feature.chatbot.usecases.ImportModelResult
import com.llamatik.app.feature.chatbot.usecases.ImportModelUseCase
import com.llamatik.app.feature.chatbot.repositories.ModelsRepository
import com.llamatik.app.platform.ServiceClient
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class ImportModelUseCaseTest {

    // ModelsRepository is only used for the save step; we test validation logic here.
    private val modelsRepository = ModelsRepository(ServiceClient)
    private val useCase = ImportModelUseCase(modelsRepository)

    @Test
    fun importModel_validGguf_succeeds() {
        val result = useCase.importModel(
            filePath = "/data/models/my-model-Q4_K_M.gguf",
            displayName = "My Custom Model",
            sizeBytes = 1_073_741_824L,
        )
        assertIs<ImportModelResult.Success>(result)
        val model = result.model
        assertEquals("My Custom Model", model.name)
        assertEquals(ModelSource.UserImported, model.source)
        assertEquals(1024, model.sizeMb)
        assertEquals("Q4_K_M", model.quantization)
    }

    @Test
    fun importModel_validBin_succeeds() {
        val result = useCase.importModel(
            filePath = "/data/models/whisper-tiny.bin",
            displayName = "Whisper Tiny",
        )
        assertIs<ImportModelResult.Success>(result)
    }

    @Test
    fun importModel_invalidExtension_fails() {
        val result = useCase.importModel(
            filePath = "/data/models/model.pth",
            displayName = "PyTorch Model",
        )
        assertIs<ImportModelResult.Failure>(result)
    }

    @Test
    fun importModel_zipExtension_fails() {
        val result = useCase.importModel(filePath = "not-a-model.zip", displayName = "Bad")
        assertIs<ImportModelResult.Failure>(result)
    }

    @Test
    fun importModel_blankDisplayName_usesFileName() {
        val result = useCase.importModel(
            filePath = "/storage/models/llama-3.2-1b.gguf",
            displayName = "",
        )
        assertIs<ImportModelResult.Success>(result)
        assertEquals("llama-3.2-1b.gguf", result.model.name)
    }

    @Test
    fun importModel_quantizationDetection_q8() {
        val result = useCase.importModel(
            filePath = "/models/gemma-Q8_0.gguf",
            displayName = "Gemma Q8",
        )
        assertIs<ImportModelResult.Success>(result)
        assertEquals("Q8_0", result.model.quantization)
    }
}
