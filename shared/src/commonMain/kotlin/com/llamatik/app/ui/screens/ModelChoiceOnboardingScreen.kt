package com.llamatik.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.koin.mp.KoinPlatform

// Final onboarding page: lets the user pick how to get a model, or skip.
class ModelChoiceOnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        val rootNavigatorRepo: RootNavigatorRepository =
            KoinPlatform.getKoin().get()

        fun finishOnboarding() {
            // Pop back to root — MainScreen / ChatBotTabScreen is the base.
            rootNavigatorRepo.navigator.popUntilRoot()
        }

        LlamatikTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "🦙",
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = localization.onboardingModelChoiceTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = localization.onboardingModelChoiceDescription,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.size(32.dp))

                Button(
                    onClick = {
                        finishOnboarding()
                        // The model download will be triggered from within the chat screen
                        // via the existing download flow. We signal intent via a nav extra.
                        // For now we navigate to root and the empty state will prompt download.
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(10),
                ) {
                    Text(
                        text = localization.onboardingDownloadDefaultModel,
                        style = Typography.get().titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.size(12.dp))

                OutlinedButton(
                    onClick = { finishOnboarding() },
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(10),
                ) {
                    Text(
                        text = localization.onboardingBrowseCatalog,
                        style = Typography.get().titleMedium,
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                TextButton(
                    onClick = { finishOnboarding() },
                    modifier = Modifier
                        .widthIn(max = 350.dp)
                        .fillMaxWidth(),
                ) {
                    Text(
                        text = localization.onboardingSkipForNow,
                        style = Typography.get().bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
