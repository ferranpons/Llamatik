/* ktlint-disable */

package com.dcshub.app


import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun homeScreenViewController(): UIViewController = ComposeUIViewController {
    MainApp()
}

/* ktlint-disable */
