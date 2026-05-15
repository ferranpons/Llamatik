package com.llamatik.app.feature.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.llamatik.app.feature.companion.CompanionMode
import com.llamatik.app.feature.companion.CompanionRepository
import com.llamatik.app.feature.entitlement.EntitlementRepository
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.ui.theme.Typography
import org.koin.mp.KoinPlatform

class CompanionTabScreen : Screen {
    @Composable
    override fun Content() {
        val scope = rememberCoroutineScope()
        val localization = getCurrentLocalization()
        val companionRepo: CompanionRepository = KoinPlatform.getKoin().get()
        val entitlementRepo: EntitlementRepository = KoinPlatform.getKoin().get()
        val isPremium by entitlementRepo.isPremium.collectAsState(initial = false)

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Companion",
                            style = Typography.get().titleLarge
                        )
                    }
                )
            }
        ) { padding ->
            CompanionScreenContent(
                modifier = Modifier.fillMaxSize().padding(padding),
                currentMode = companionRepo.getCompanionMode(),
                isCompanionEnabled = companionRepo.isCompanionEnabled(),
                isPremium = isPremium,
                onModeSelected = { mode ->
                    if (isPremium) {
                        companionRepo.setCompanionMode(mode)
                        companionRepo.setCompanionEnabled(true)
                    }
                },
                onToggleEnabled = { enabled ->
                    if (isPremium) companionRepo.setCompanionEnabled(enabled)
                },
            )
        }
    }
}

@Composable
private fun CompanionScreenContent(
    modifier: Modifier = Modifier,
    currentMode: CompanionMode,
    isCompanionEnabled: Boolean,
    isPremium: Boolean,
    onModeSelected: (CompanionMode) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🦙 Companion Mode",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.size(8.dp))
        if (!isPremium) {
            Text(
                text = "Unlock Companion Mode with Premium",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        } else {
            Text(
                text = "Current: ${currentMode.name}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.size(16.dp))
            CompanionMode.entries.forEach { mode ->
                androidx.compose.material3.Button(
                    onClick = { onModeSelected(mode) },
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(mode.name)
                }
            }
        }
    }
}
