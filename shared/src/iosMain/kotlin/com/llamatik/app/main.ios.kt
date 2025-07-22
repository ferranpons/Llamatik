/* ktlint-disable */

package com.llamatik.app


import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun homeScreenViewController(): UIViewController = ComposeUIViewController {
    MainApp()
}

/* ktlint-disable */
