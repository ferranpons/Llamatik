package com.dcshub.app.android

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.dcshub.app.MainApp
import com.dcshub.app.common.model.theme.DarkThemeConfig
import com.dcshub.app.ui.theme.LlamatikTheme
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FileKit.init(this)

        /*val modelPath = copyModelFromResources(
            context = this,
            resourcePath = "raw/nomic_embed_text_v1_5_q4_0.gguf",
            outputFileName = "nomic_embed_text_v1_5_q4_0.gguf"
        )*/
        //System.loadLibrary("llama-jni")

        //val modelPath = copyModelFromAssetsToCache(this)
        //println("Model Path: $modelPath")
        //LlamaBridge.initModel(modelPath)

        val uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)

        enableEdgeToEdge()

        setContent {
            val darkTheme = shouldUseDarkTheme(uiState)

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        lightScrim,
                        darkScrim,
                    ) { darkTheme },
                )
                onDispose {}
            }

            LlamatikTheme(darkTheme = darkTheme) {
                MainApp()
            }
        }
    }

    private fun copyModelFromResources(
        context: Context,
        resourcePath: String,
        outputFileName: String
    ): String {
        val outputFile = File(context.cacheDir, outputFileName)
        if (!outputFile.exists()) {
            context.classLoader.getResourceAsStream(resourcePath)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Resource $resourcePath not found.")
        }
        return outputFile.absolutePath
    }

    private fun copyModelFromAssetsToCache(context: Context): String {
        val assetName = "nomic_embed_text_v1_5_q4_0.gguf"
        val file = File(context.cacheDir, assetName)
        if (!file.exists()) {
            context.assets.open(assetName).use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }
}

@Composable
fun shouldUseDarkTheme(
    uiState: MainActivityUiState
): Boolean = when (uiState) {
    MainActivityUiState.Loading -> isSystemInDarkTheme()
    is MainActivityUiState.Success -> when (uiState.userData.darkThemeConfig) {
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
    }
}

/**
 * The default light scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=35-38;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val lightScrim = android.graphics.Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

/**
 * The default dark scrim, as defined by androidx and the platform:
 * https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:activity/activity/src/main/java/androidx/activity/EdgeToEdge.kt;l=40-44;drc=27e7d52e8604a080133e8b842db10c89b4482598
 */
private val darkScrim = android.graphics.Color.argb(0x80, 0x1b, 0x1b, 0x1b)
