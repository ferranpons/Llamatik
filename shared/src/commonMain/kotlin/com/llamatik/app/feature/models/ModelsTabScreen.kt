package com.llamatik.app.feature.models

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import com.llamatik.app.localization.getCurrentLocalization

// Models catalog tab — wraps the existing model download/selection UI.
// Will be expanded to include the full model catalog and import flow.
class ModelsTabScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(localization.generateModels) })
            }
        ) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = localization.generateModels,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
