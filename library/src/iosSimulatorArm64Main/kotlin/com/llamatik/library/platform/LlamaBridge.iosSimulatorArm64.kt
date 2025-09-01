@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.llamatik.library.platform

import androidx.compose.runtime.Composable
import com.llamatik.library.platform.llama.llama_embed
import com.llamatik.library.platform.llama.llama_embed_free
import com.llamatik.library.platform.llama.llama_embed_init
import com.llamatik.library.platform.llama.llama_embedding_size
import com.llamatik.library.platform.llama.llama_free_embedding
import com.llamatik.library.platform.llama.llama_generate
import com.llamatik.library.platform.llama.llama_generate_free
import com.llamatik.library.platform.llama.llama_generate_init
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.get
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
    @Composable
    actual fun getModelPath(modelFileName: String): String {
        val fm = NSFileManager.defaultManager
        val cachesDir = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask).first() as NSURL
        val dstUrl = cachesDir.URLByAppendingPathComponent(modelFileName)!!

        println("📂 [getModelPath] Looking for: $modelFileName")
        println("📂 [getModelPath] Destination path: ${dstUrl.path}")
        println("📦 [getModelPath] Main bundle: ${NSBundle.mainBundle.bundlePath}")

        if (!fm.fileExistsAtPath(dstUrl.path!!)) {
            val (name, ext) = splitNameAndExt(modelFileName)

            // 1) Try main bundle under "models/" (your intended location)
            val candidates = mutableListOf<Pair<String, NSURL?>>()
            candidates += "main: models/" to urlFor(NSBundle.mainBundle, name, ext, "models")

            // 2) Try main bundle root (in case the folder got flattened)
            candidates += "main: <root>" to urlFor(NSBundle.mainBundle, name, ext, null)

            // 3) Try every loaded bundle/framework (resources sometimes land there)
            (NSBundle.allBundles() as? List<*>)?.forEach { b ->
                (b as? NSBundle)?.let { bundle ->
                    candidates += "${bundle.bundlePath}: models/" to urlFor(bundle, name, ext, "models")
                    candidates += "${bundle.bundlePath}: <root>" to urlFor(bundle, name, ext, null)
                }
            }
            (NSBundle.allFrameworks() as? List<*>)?.forEach { b ->
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

            val data = requireNotNull(NSData.create(contentsOfURL = resUrl)) {
                "Failed to read bundled resource: $modelFileName (url=$resUrl)"
            }

            val ok = data.writeToURL(dstUrl, true)
            require(ok) { "Failed to copy $modelFileName to ${dstUrl.path}" }
            println("📂 [getModelPath] Copied $modelFileName to cache path: ${dstUrl.path}")
        } else {
            println("📂 [getModelPath] Using cached file: ${dstUrl.path}")
        }

        return dstUrl.path!!
    }

    private fun urlFor(bundle: NSBundle, name: String, ext: String, subdirectory: String?): NSURL? {
        return if (ext.isNotEmpty())
            bundle.URLForResource(name, ext, subdirectory = subdirectory)
        else
            bundle.URLForResource(name, withExtension = null, subdirectory = subdirectory)
    }

    private fun splitNameAndExt(fileName: String): Pair<String, String> {
        val i = fileName.lastIndexOf('.')
        return if (i > 0 && i < fileName.length - 1)
            fileName.substring(0, i) to fileName.substring(i + 1)
        else
            fileName to ""
    }

    actual fun initModel(modelPath: String): Boolean = llama_embed_init(modelPath)

    actual fun embed(input: String): FloatArray {
        val raw = llama_embed(input) ?: return FloatArray(0)
        val size = llama_embedding_size()
        return try {
            FloatArray(size) { i -> raw[i] }
        } finally {
            llama_free_embedding(raw)
        }
    }

    actual fun initGenerateModel(modelPath: String) = llama_generate_init(modelPath)

    actual fun generate(prompt: String): String {
        val c = llama_generate(prompt) ?: return ""
        val out = c.toKString()
        llama_generate_free()
        return out
    }

    actual fun generateWithContext(systemPrompt: String, contextBlock: String, userPrompt: String): String {
        return generate(userPrompt)
    }

    actual fun shutdown() {
        llama_embed_free()
        llama_generate_free()
    }
}