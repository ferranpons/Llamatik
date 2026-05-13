package com.llamatik.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.llamatik.app.feature.companion.ui.CompanionTabScreen
import com.llamatik.app.ui.icon.LlamatikIcons

internal object CompanionTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(LlamatikIcons.Pets)
            return remember {
                TabOptions(index = 3u, title = "Companion", icon = icon)
            }
        }

    @Composable
    override fun Content() {
        Navigator(CompanionTabScreen()) {
            SlideTransition(it)
        }
    }
}
