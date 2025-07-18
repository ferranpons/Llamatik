import androidx.compose.ui.window.ComposeUIViewController
import com.dcshub.app.MainApp
import platform.UIKit.UIViewController

fun homeScreenViewController(): UIViewController = ComposeUIViewController {
    MainApp()
}

/* ktlint-disable */
