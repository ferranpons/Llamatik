@file:Suppress("CAST_NEVER_SUCCEEDS")

package com.llamatik.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile

@OptIn(BetaInteropApi::class)
actual fun readResourceFile(fileName: String): String {
    val path = NSBundle.mainBundle.pathForResource(
        name = fileName.removeSuffix(".json"),
        ofType = "json"
    )!!
    val data = NSData.dataWithContentsOfFile(path)!!
    return NSString.create(data, NSUTF8StringEncoding) as String
}

private val cache: MutableMap<String, Font> = mutableMapOf()

@OptIn(InternalResourceApi::class)
@Composable
actual fun font(name: String, res: String, weight: FontWeight, style: FontStyle): Font {
    return cache.getOrPut(res) {
        val byteArray = runBlocking {
            readResourceBytes("font/$res.ttf")
        }
        androidx.compose.ui.text.platform.Font(res, byteArray, weight, style)
    }
}

/*
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ModelFileProvider {
    @OptIn(BetaInteropApi::class)
    actual fun getModelPath(modelFileName: String): String {
        val fileManager = NSFileManager.defaultManager
        val cacheDir = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).first() as String
        val outputPath = "$cacheDir/$modelFileName"

        val destination = NSString.create(string = outputPath)
        if (!fileManager.fileExistsAtPath(destination.toString())) {
            val bundle = NSBundle.mainBundle
            val sourcePath =
                bundle.pathForResource(name = modelFileName.removeSuffix(".gguf"), ofType = "gguf")
                    ?: error("Model file not found in bundle")

            fileManager.copyItemAtPath(sourcePath, destination.toString(), null)
        }

        return outputPath
    }
}
 */