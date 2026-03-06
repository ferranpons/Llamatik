@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import com.llamatik.library.platform.llama.llama_embed
import com.llamatik.library.platform.llama.llama_embed_free
import com.llamatik.library.platform.llama.llama_embed_init
import com.llamatik.library.platform.llama.llama_embedding_size
import com.llamatik.library.platform.llama.llama_free_embedding
import com.llamatik.library.platform.llama.llama_generate
import com.llamatik.library.platform.llama.llama_generate_cancel
import com.llamatik.library.platform.llama.llama_generate_chat
import com.llamatik.library.platform.llama.llama_generate_chat_json_schema
import com.llamatik.library.platform.llama.llama_generate_chat_json_schema_stream
import com.llamatik.library.platform.llama.llama_generate_chat_stream
import com.llamatik.library.platform.llama.llama_generate_free
import com.llamatik.library.platform.llama.llama_generate_init
import com.llamatik.library.platform.llama.llama_generate_json_schema
import com.llamatik.library.platform.llama.llama_generate_json_schema_stream
import com.llamatik.library.platform.llama.llama_generate_set_params
import com.llamatik.library.platform.llama.llama_generate_stream
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.writeToURL

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual object LlamaBridge {

    @OptIn(ExperimentalResourceApi::class, BetaInteropApi::class)
    actual fun getModelPath(modelFileName: String): String {
        val fm = NSFileManager.defaultManager
        val cachesDir = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as NSURL
        val dstUrl = cachesDir.URLByAppendingPathComponent(modelFileName)!!

        println("📂 [getModelPath] Looking for: $modelFileName")
        println("📂 [getModelPath] Destination path: ${dstUrl.path}")
        println("📦 [getModelPath] Main bundle: ${NSBundle.mainBundle.bundlePath}")

        if (!fm.fileExistsAtPath(dstUrl.path!!)) {
            val (name, ext) = splitNameAndExt(modelFileName)

            fun urlFor(bundle: NSBundle, n: String, e: String?, subdir: String?): NSURL? {
                val url = bundle.URLForResource(n, e, subdir)
                if (url != null && fm.fileExistsAtPath(url.path!!)) return url
                return null
            }

            val candidates = mutableListOf<Pair<String, NSURL?>>()

            // 1) /models in main bundle
            candidates += "main bundle models/" to urlFor(NSBundle.mainBundle, name, ext, "models")
            // 2) root of main bundle
            candidates += "main bundle <root>" to urlFor(NSBundle.mainBundle, name, ext, null)
            // 3) Try every loaded bundle/framework (resources sometimes land there)
            NSBundle.allBundles().forEach { b ->
                (b as? NSBundle)?.let { bundle ->
                    candidates += "${bundle.bundlePath}: models/" to urlFor(bundle, name, ext, "models")
                    candidates += "${bundle.bundlePath}: <root>" to urlFor(bundle, name, ext, null)
                }
            }
            NSBundle.allFrameworks().forEach { b ->
                (b as? NSBundle)?.let { bundle ->
                    candidates += "framework ${bundle.bundlePath}: models/" to urlFor(bundle, name, ext, "models")
                    candidates += "framework ${bundle.bundlePath}: <root>" to urlFor(bundle, name, ext, null)
                }
            }

            // Log every probe and pick the first that exists
            var resUrl: NSURL? = null
            for ((label, url) in candidates) {
                println("🔎 [getModelPath] Probe -> $label => $url")
                if (url != null) {
                    resUrl = url
                    println("✅ [getModelPath] Found in $label")
                    break
                }
            }

            requireNotNull(resUrl) {
                """
            Resource "$modelFileName" not found in any bundle.
            Searched: models/ and root of main bundle and all frameworks.
            Main bundle path: ${NSBundle.mainBundle.bundlePath}
            """.trimIndent()
            }

            val data = NSData.create(contentsOfURL = resUrl)
            requireNotNull(data) { "Couldn't read model from bundle url=$resUrl" }
            val ok = data.writeToURL(dstUrl, true)
            require(ok) { "Failed to copy model to caches dir: ${dstUrl.path}" }
            println("📥 [getModelPath] Copied to caches: ${dstUrl.path}")
        } else {
            println("✅ [getModelPath] Already in caches: ${dstUrl.path}")
        }

        return dstUrl.path!!
    }

    private fun splitNameAndExt(fileName: String): Pair<String, String?> {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return fileName to null
        return fileName.substring(0, dot) to fileName.substring(dot + 1)
    }

    actual fun initEmbedModel(modelPath: String): Boolean = llama_embed_init(modelPath)
    actual fun embed(input: String): FloatArray {
        val ptr = llama_embed(input) ?: return FloatArray(0)
        val dim = llama_embedding_size()
        val out = FloatArray(dim)
        for (i in 0 until dim) out[i] = ptr[i]
        llama_free_embedding(ptr)
        return out
    }

    actual fun initGenerateModel(modelPath: String): Boolean = llama_generate_init(modelPath)

    actual fun generate(prompt: String): String {
        val c = llama_generate(prompt) ?: return ""
        val out = c.toKString()
        llama_generate_free()
        return out
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        val c = llama_generate_chat(systemPrompt, contextBlock, userPrompt) ?: return ""
        val out = c.toKString()
        llama_generate_free()
        return out
    }

    actual fun generateJson(prompt: String, jsonSchema: String?): String {
        val c = llama_generate_json_schema(prompt, jsonSchema) ?: return ""
        val out = c.toKString()
        llama_generate_free()
        return out
    }

    actual fun generateJsonWithContext(systemPrompt: String, contextBlock: String, userPrompt: String, jsonSchema: String?): String {
        val c = llama_generate_chat_json_schema(systemPrompt, contextBlock, userPrompt, jsonSchema) ?: return ""
        val out = c.toKString()
        llama_generate_free()
        return out
    }

    actual fun shutdown() {
        llama_embed_free()
        llama_generate_free()
    }

    // --------- Streaming (iOS via C callbacks) ---------

    actual fun generateStream(prompt: String, callback: GenStream) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                // NOTE: This call blocks until completion; call from a background dispatcher.
                llama_generate_stream(prompt, onDelta, onDone, onError, ref.asCPointer())
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        callback: GenStream
    ) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                // Same: synchronous; make sure to call off the main thread.
                llama_generate_chat_stream(
                    systemPrompt,
                    contextBlock,
                    userPrompt,
                    onDelta,
                    onDone,
                    onError,
                    ref.asCPointer()
                )
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateJsonStream(prompt: String, jsonSchema: String?, callback: GenStream) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_json_schema_stream(prompt, jsonSchema, onDelta, onDone, onError, ref.asCPointer())
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateJsonStreamWithContext(
        systemPrompt: String,
        contextBlock: String,
        userPrompt: String,
        jsonSchema: String?,
        callback: GenStream
    ) {
        memScoped {
            val ref = StableRef.create(callback)
            val onDelta = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val s = cstr?.toKString() ?: return@staticCFunction
                cb.onDelta(s)
            }
            val onDone = staticCFunction { ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                cb.onComplete()
            }
            val onError = staticCFunction { cstr: CPointer<ByteVar>?, ud: COpaquePointer? ->
                val cb = ud!!.asStableRef<GenStream>().get()
                val msg = cstr?.toKString() ?: "unknown error"
                cb.onError(msg)
            }
            try {
                llama_generate_chat_json_schema_stream(
                    systemPrompt,
                    contextBlock,
                    userPrompt,
                    jsonSchema,
                    onDelta,
                    onDone,
                    onError,
                    ref.asCPointer()
                )
            } finally {
                ref.dispose()
            }
        }
    }

    actual fun generateWithContextStream(
        system: String,
        context: String,
        user: String,
        onDelta: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val proxy = object : GenStream {
            override fun onDelta(text: String) = onDelta(text)
            override fun onComplete() = onDone()
            override fun onError(message: String) = onError(message)
        }
        generateStreamWithContext(system, context, user, proxy)
    }

    actual fun nativeCancelGenerate() {
        llama_generate_cancel()
    }

    actual fun updateGenerateParams(
        temperature: Float,
        maxTokens: Int,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
    ) {
        llama_generate_set_params(
            temperature,
            maxTokens,
            topP,
            topK,
            repeatPenalty
        )
    }
}