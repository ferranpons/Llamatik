package com.llamatik.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import cafe.adriel.voyager.transitions.ScaleTransition
import com.llamatik.app.feature.chatbot.ChatBotTabScreen
import com.llamatik.app.ui.icon.LlamatikIcons

internal object ChatBotTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val icon = rememberVectorPainter(LlamatikIcons.ChatBot)

            return remember {
                TabOptions(
                    index = 2u,
                    title = "Llamatik AI",
                    icon = icon
                )
            }
        }

    @Composable
    override fun Content() {
        Navigator(ChatBotTabScreen()) {
            ScaleTransition(it)
        }
    }
}
