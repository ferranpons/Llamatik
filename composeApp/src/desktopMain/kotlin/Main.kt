import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.llamatik.app.MainApp
import com.llamatik.app.di.commonModule
import com.llamatik.app.ui.theme.LlamatikTheme
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
