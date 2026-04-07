package com.llamatik.library.platform

actual object MultimodalBridge {
    init {
        // Reuse the same JNI shared library that contains all other bridge exports.
        loadNativeFromResources()
    }

    actual external fun initModel(modelPath: String, mmprojPath: String): Boolean

    private external fun nativeAnalyzeImageBytesStream(
        imageBytes: ByteArray,
        prompt: String,
        callback: GenStream
    )

    actual external fun cancelAnalysis()
    actual external fun release()

    actual fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream) {
        nativeAnalyzeImageBytesStream(imageBytes, prompt, callback)
    }

    private fun loadNativeFromResources() {
        val os = System.getProperty("os.name").lowercase()
        val platform = when {
            os.contains("mac") -> "macos"
            os.contains("linux") -> "linux"
            os.contains("win") -> "windows"
            else -> error("Unsupported OS: $os")
        }

        val libFileName = System.mapLibraryName("llama_jni")
        val resourcePath = "/native/$platform/$libFileName"

        val input = object {}.javaClass.getResourceAsStream(resourcePath)
            ?: error("Native library not found in resources: $resourcePath")

        val out = java.io.File.createTempFile("llama_jni_", libFileName)
        out.deleteOnExit()
        input.use { it.copyTo(out.outputStream()) }

        System.load(out.absolutePath)
    }
}
