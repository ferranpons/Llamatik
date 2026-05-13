package com.llamatik.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.SlideTransition
import com.llamatik.app.feature.models.ModelsTabScreen
import com.llamatik.app.ui.icon.LlamatikIcons

internal object ModelsTab : Tab {
    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(LlamatikIcons.Download)
            return remember {
                TabOptions(index = 1u, title = "Models", icon = icon)
            }
        }

    @Composable
    override fun Content() {
        Navigator(ModelsTabScreen()) {
            SlideTransition(it)
        }
    }
}
