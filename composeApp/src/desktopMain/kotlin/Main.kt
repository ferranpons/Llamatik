import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.llamatik.app.MainApp
import com.llamatik.app.di.commonModule
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.llamatik_icon_logo
import com.llamatik.app.ui.theme.LlamatikTheme
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.startKoin

@OptIn(ExperimentalMaterial3Api::class, InternalResourceApi::class)
fun main() =
    application {
        startKoin {
            modules(commonModule)
        }
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized)
        val localization = getCurrentLocalization()


        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = localization.appName,
            undecorated = windowState.placement == WindowPlacement.Fullscreen,
            icon = painterResource(Res.drawable.llamatik_icon_logo),
        ) {
            LlamatikTheme {
                MainApp()
            }
        }
    }
