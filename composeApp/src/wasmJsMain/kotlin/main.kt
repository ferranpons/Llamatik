@file:OptIn(ExperimentalComposeUiApi::class)

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.llamatik.app.MainApp
import com.llamatik.app.platform.initKoinWasm

fun main() {
    initKoinWasm()
    ComposeViewport(
        content = {
            MainApp()
        }
    )
}