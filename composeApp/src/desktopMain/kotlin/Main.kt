import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.dcshub.app.MainApp
import com.dcshub.app.di.commonModule
import com.dcshub.app.ui.theme.LlamatikTheme
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(commonModule)
    }
    Window(onCloseRequest = ::exitApplication) {
        LlamatikTheme {
            MainApp()
        }
    }
}
