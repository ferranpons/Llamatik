package com.dcshub.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.ScaleTransition
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.ui.icon.LlamatikIcons
import com.dcshub.app.ui.screens.HomeTabScreen

internal object HomeTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(LlamatikIcons.Home)

            return remember {
                TabOptions(
                    index = 0u,
                    title = getCurrentLocalization().home,
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        Navigator(HomeTabScreen()) {
            ScaleTransition(it)
        }
    }
}
