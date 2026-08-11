@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.llamatik.core.platform

import java.io.File
import java.util.Locale

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object LlamaBridge {

    init {
        loadNativeFromResources()
        println("🖥️ [JVM LlamaBridge] Loaded native library 'llama_jni'")
    }

    actual external fun initEmbedModel(modelPath: String): Boolean
    actual external fun embed(input: String): FloatArray

    actual fun getModelPath(modelFileName: String): String = modelFileName

    actual external fun initGenerateModel(modelPath: String): Boolean
    actual external fun generate(prompt: String): String
    actual external fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String
    actual external fun generateJson(prompt: String, jsonSchema: String?): String
    actual external fun generateJsonWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?
    ): String

    private external fun nativeGenerateStream(prompt: String, callback: GenStream)
    private external fun nativeGenerateWithContextStream(system: String, context: String, user: String, callback: GenStream)
    private external fun nativeGenerateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream)
    private external fun nativeGenerateJsonWithContextStream(
        system: String,
        context: String,
        user: String,
        jsonSchema: String?,
        callback: GenStream
    )

    private external fun nativeUpdateGenerationParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        contextLength: Int,
        numThreads: Int,
        useMmap: Boolean,
        flashAttention: Boolean,
        batchSize: Int,
        gpuLayers: Int,
    )

    // ===================== KV session (JNI) =====================
    private external fun nativeSessionReset(): Boolean
    private external fun nativeSessionSave(path: String): Boolean
    private external fun nativeSessionLoad(path: String): Boolean
    private external fun nativeGenerateContinue(prompt: String): String
    private external fun nativeGenerateContinueStream(prompt: String, callback: GenStream)

    actual fun sessionReset(): Boolean = nativeSessionReset()
    actual fun sessionSave(path: String): Boolean = nativeSessionSave(path)
    actual fun sessionLoad(path: String): Boolean = nativeSessionLoad(path)
    actual fun generateContinue(prompt: String): String = nativeGenerateContinue(prompt)
    actual fun generateContinueStream(prompt: String, callback: GenStream) = nativeGenerateContinueStream(prompt, callback)

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        contextLength: Int,
        numThreads: Int,
        useMmap: Boolean,
        flashAttention: Boolean,
        batchSize: Int,
        gpuLayers: Int,
    ) {
        nativeUpdateGenerationParams(temperature, maxTokens, topP, topK, repeatPenalty, contextLength, numThreads, useMmap, flashAttention, batchSize, gpuLayers)
    }

    actual fun generateStream(prompt: String, callback: GenStream) {
        nativeGenerateStream(prompt, callback)
    }

    private fun buildChatPrompt(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        val userTurn = buildString {
            if (contextBlock.isNotBlank()) {
                append("CONTEXT:\n")
                append(contextBlock.trim())
                append("\n\nQUESTION:\n")
            }
            append(userPrompt.trim())
        }
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add("system" to systemPrompt.trim())
            add("user" to userTurn)
        }
        return applyChatTemplate(messages, addAssistantPrefix = true)
            ?: buildString {
                if (systemPrompt.isNotBlank()) {
                    append("<start_of_turn>system\n")
                    append(systemPrompt.trim())
                    append("\n<end_of_turn>\n")
                }
                append("<start_of_turn>user\n")
                append(userTurn)
                append("\n<end_of_turn>\n")
                append("<start_of_turn>assistant\n")
            }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        val prompt = buildChatPrompt(systemPrompt, contextBlock, userPrompt)
        generateStream(prompt, callback)
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) {
        nativeGenerateJsonStream(prompt, jsonSchema, callback)
    }

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) {
        nativeGenerateJsonWithContextStream(systemPrompt, contextBlock, userPrompt, jsonSchema, callback)
    }

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val cb = object : GenStream {
            override fun onDelta(text: String) = onDelta(text)
            override fun onComplete() = onDone()
            override fun onError(message: String) = onError(message)
        }
        nativeGenerateWithContextStream(system, context, user, cb)
    }

    actual external fun getModelFinetuneType(): String?
    actual external fun getModelChatTemplate(): String?

    private external fun nativeApplyChatTemplate(
        template: String?,
        roles: Array<String>,
        contents: Array<String>,
        addAssistantPrefix: Boolean,
    ): String?

    actual fun applyChatTemplate(messages: List<Pair<String, String>>, addAssistantPrefix: Boolean): String? {
        val roles = messages.map { it.first }.toTypedArray()
        val contents = messages.map { it.second }.toTypedArray()
        return nativeApplyChatTemplate(getModelChatTemplate(), roles, contents, addAssistantPrefix)
    }

    // ===================== Concurrent sessions (JNI) =====================
    private external fun nativeCreateSession(): Long
    private external fun nativeCloseSession(handle: Long)
    private external fun nativeSessionStream(handle: Long, prompt: String, callback: GenStream)
    private external fun nativeSessionClearKv(handle: Long)
    private external fun nativeSessionCancel(handle: Long)

    internal fun sessionStreamBridge(handle: Long, prompt: String, callback: GenStream) =
        nativeSessionStream(handle, prompt, callback)
    internal fun sessionResetBridge(handle: Long) = nativeSessionClearKv(handle)
    internal fun sessionCancelBridge(handle: Long) = nativeSessionCancel(handle)
    internal fun sessionCloseBridge(handle: Long) = nativeCloseSession(handle)

    actual fun createSession(name: String): LlamaSession? {
        val handle = nativeCreateSession()
        if (handle < 0L) return null
        return LlamaSession(handle, name)
    }

    actual external fun shutdown()
    actual external fun nativeCancelGenerate()

    actual fun initMtp(modelPath: String, draftLen: Int): Boolean =
        nativeInitMtp(modelPath, draftLen)

    private external fun nativeInitMtp(modelPath: String, draftLen: Int): Boolean

    actual fun shutdownMtp() = nativeShutdownMtp()
    private external fun nativeShutdownMtp()

    private fun loadNativeFromResources() {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        val platform = when {
            os.contains("mac") -> "macos"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> error("Unsupported OS: $os")
        }

        val tempDir = createTempNativeDir(platform)
        val resourceRoot = "/native/$platform"
        val resourceNames = nativeResourceNames(platform)

        val extractedFiles = resourceNames.map { resourceName ->
            extractNativeResource(resourceRoot, resourceName, tempDir)
        }

        extractedFiles
            .filter { it.name != System.mapLibraryName("llama_jni") }
            .forEach { System.load(it.absolutePath) }

        val jniLib = extractedFiles.firstOrNull { it.name == System.mapLibraryName("llama_jni") }
            ?: error("JNI library not found among extracted resources for platform: $platform")

        System.load(jniLib.absolutePath)
    }

    private fun createTempNativeDir(platform: String): File {
        val dir = createTempDir(prefix = "llamatik_${platform}_")
        dir.deleteOnExit()
        return dir
    }

    private fun nativeResourceNames(platform: String): List<String> {
        val jniLib = System.mapLibraryName("llama_jni")
        val allResources = object {}.javaClass.getResourceAsStream("/native/$platform/native-libs.txt")
            ?.bufferedReader()
            ?.useLines { lines ->
                lines
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .toList()
            }
            .orEmpty()

        return when {
            allResources.isNotEmpty() -> {
                val ordered = allResources.filter { it != jniLib }
                ordered + jniLib
            }
            else -> listOf(jniLib)
        }
    }

    private fun extractNativeResource(resourceRoot: String, resourceName: String, tempDir: File): File {
        val resourcePath = "$resourceRoot/$resourceName"
        val input = object {}.javaClass.getResourceAsStream(resourcePath)
            ?: error("Native library not found in resources: $resourcePath")

        val out = File(tempDir, resourceName)
        input.use { inputStream ->
            out.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        out.deleteOnExit()
        return out
    }
}
