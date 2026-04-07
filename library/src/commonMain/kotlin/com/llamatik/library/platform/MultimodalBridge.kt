package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object MultimodalBridge {

    /**
     * Load the text/vision model and its multimodal projector (mmproj) side-by-side.
     * Both files must be available on disk before calling this.
     *
     * @param modelPath   Absolute path to the GGUF text/vision model.
     * @param mmprojPath  Absolute path to the GGUF mmproj file.
     * @return true on success.
     */
    fun initModel(modelPath: String, mmprojPath: String): Boolean

    /**
     * Analyze an image given as raw bytes (JPEG/PNG/BMP), streaming the response
     * token by token via [callback].
     *
     * Must be called from a background thread/coroutine; this call blocks until
     * generation completes.
     */
    fun analyzeImageBytesStream(imageBytes: ByteArray, prompt: String, callback: GenStream)

    /** Cancel an in-progress analyzeImageBytesStream call. */
    fun cancelAnalysis()

    /** Free all native resources (model, mmproj context, llama context). */
    fun release()
}
