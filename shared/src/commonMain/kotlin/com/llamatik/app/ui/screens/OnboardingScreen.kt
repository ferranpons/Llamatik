package com.llamatik.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.platform.RootNavigatorRepository
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.resources.authentication_rafiki
import com.llamatik.app.resources.features_overview_cuate
import com.llamatik.app.resources.instruction_manual_pana
import com.llamatik.app.resources.llamatik_icon_logo
import com.llamatik.app.ui.theme.LlamatikTheme
import com.russhwolf.settings.Settings
import org.jetbrains.compose.resources.painterResource
import org.koin.mp.KoinPlatform

private const val TOTAL_PAGES = 7
private val CheckGreen = Color(0xFF4CAF50)

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        var currentPage by remember { mutableStateOf(0) }

        val rootNavigatorRepo: RootNavigatorRepository = remember {
            KoinPlatform.getKoin().get()
        }
        val settings: Settings = remember {
            KoinPlatform.getKoin().get()
        }

        fun skipToEnd() {
            settings.putBoolean("privacy_chatbot_viewed_key", true)
            settings.putBoolean("user_skipped_setup_key", true)
            rootNavigatorRepo.navigator.popUntilRoot()
        }

        fun finishSkipDownload() {
            settings.putBoolean("privacy_chatbot_viewed_key", true)
            settings.putBoolean("user_skipped_setup_key", true)
            rootNavigatorRepo.navigator.popUntilRoot()
        }

        fun finishGetStarted() {
            settings.putBoolean("privacy_chatbot_viewed_key", true)
            rootNavigatorRepo.navigator.popUntilRoot()
        }

        LlamatikTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top bar with Skip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (currentPage < TOTAL_PAGES - 1) {
                    TextButton(onClick = { skipToEnd() }) {
                        Text(
                            text = localization.onboardingSkip,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Content area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                when (currentPage) {
                    0 -> WelcomePage(localization)
                    1 -> RunLLMsPage(localization)
                    2 -> AIToolkitPage(localization)
                    3 -> BuiltForDevsPage(localization)
                    4 -> PrivacyControlPage(localization)
                    5 -> DownloadModelsPage(localization)
                    6 -> YoureReadyPage(localization)
                }
            }

            // Bottom navigation
            BottomNav(
                currentPage = currentPage,
                onBack = { if (currentPage > 0) currentPage-- },
                onNext = { if (currentPage < TOTAL_PAGES - 1) currentPage++ },
                onDownload = {
                    settings.putBoolean("privacy_chatbot_viewed_key", true)
                    settings.putBoolean("initial_download_requested_key", true)
                    if (currentPage < TOTAL_PAGES - 1) currentPage++
                },
                onDoItLater = { finishSkipDownload() },
                onGetStarted = { finishGetStarted() },
                localization = localization
            )

            // Dot indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(TOTAL_PAGES) { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (idx == currentPage)
                                    MaterialTheme.colorScheme.onBackground
                                else
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                            )
                    )
                    if (idx < TOTAL_PAGES - 1) Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }
        } // LlamatikTheme
    }
}

@Composable
private fun BottomNav(
    currentPage: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onDownload: () -> Unit,
    onDoItLater: () -> Unit,
    onGetStarted: () -> Unit,
    localization: com.llamatik.app.localization.Localization
) {
    when (currentPage) {
        0 -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                PrimaryPillButton(
                    text = localization.next,
                    onClick = onNext,
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            }
        }
        5 -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackTextButton(text = localization.backLabel, onClick = onBack)
                    Spacer(modifier = Modifier.weight(1f))
                    PrimaryPillButton(
                        text = localization.onboardingDownloadModelsButton,
                        onClick = onDownload,
                        modifier = Modifier.widthIn(min = 180.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedPillButton(
                        text = localization.onboardingDoItLaterButton,
                        onClick = onDoItLater,
                        modifier = Modifier.widthIn(min = 180.dp)
                    )
                }
            }
        }
        6 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackTextButton(text = localization.backLabel, onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                PrimaryPillButton(
                    text = localization.onboardingGetStartedButton,
                    onClick = onGetStarted,
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            }
        }
        else -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackTextButton(text = localization.backLabel, onClick = onBack)
                Spacer(modifier = Modifier.weight(1f))
                PrimaryPillButton(
                    text = localization.next,
                    onClick = onNext,
                    modifier = Modifier.widthIn(min = 120.dp)
                )
            }
        }
    }
}

@Composable
private fun PrimaryPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinedPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BackTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

// ─── Page 0: Welcome ─────────────────────────────────────────────────────────

@Composable
private fun WelcomePage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(16.dp))
    Image(
        painter = painterResource(Res.drawable.llamatik_icon_logo),
        contentDescription = null,
        modifier = Modifier
            .size(160.dp)
            .clip(CircleShape),
        contentScale = ContentScale.Crop
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = localization.appName,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = localization.onboardingWelcomeSubtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(32.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FeatureBadge(icon = Icons.Default.Lock, label = localization.onboardingPrivateLabel)
        FeatureBadge(icon = Icons.Default.WifiOff, label = localization.onboardingOfflineLabel)
        FeatureBadge(icon = Icons.Default.Code, label = localization.onboardingOpenSourceLabel)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun FeatureBadge(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Page 1: Run LLMs Offline ────────────────────────────────────────────────

@Composable
private fun RunLLMsPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    Image(
        painter = painterResource(Res.drawable.features_overview_cuate),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(modifier = Modifier.height(24.dp))
    OnboardingTitle(text = localization.onboardingPromoTitle1)
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingDescription(text = localization.onboardingRunLLMsDescription)
    Spacer(modifier = Modifier.height(16.dp))
}

// ─── Page 2: AI Toolkit ──────────────────────────────────────────────────────

@Composable
private fun AIToolkitPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    OnboardingTitle(text = localization.onboardingAIToolkitTitle)
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingDescription(text = localization.onboardingAIToolkitDescription)
    Spacer(modifier = Modifier.height(28.dp))
    // 2×2 feature grid
    val features = listOf(
        Icons.Default.Message to localization.onboardingFeatureChat,
        Icons.Default.Image to localization.onboardingFeatureImages,
        Icons.Default.Mic to localization.onboardingFeatureSpeech,
        Icons.Default.Description to localization.onboardingFeatureDocuments
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in features.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for ((icon, label) in row) {
                    FeatureCard(
                        icon = icon,
                        label = label,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun FeatureCard(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Page 3: Built for Developers ────────────────────────────────────────────

@Composable
private fun BuiltForDevsPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    Image(
        painter = painterResource(Res.drawable.instruction_manual_pana),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(modifier = Modifier.height(24.dp))
    OnboardingTitle(text = localization.onboardingBuiltForDevsTitle)
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingDescription(text = localization.onboardingBuiltForDevsDescription)
    Spacer(modifier = Modifier.height(16.dp))
}

// ─── Page 4: Private & In Your Control ───────────────────────────────────────

@Composable
private fun PrivacyControlPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    Image(
        painter = painterResource(Res.drawable.authentication_rafiki),
        contentDescription = null,
        modifier = Modifier
            .size(200.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(modifier = Modifier.height(20.dp))
    OnboardingTitle(text = localization.onboardingPrivacyControlTitle)
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingDescription(text = localization.onboardingPrivacyControlDescription)
    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CheckItem(text = localization.onboardingPrivacyBullet1)
        CheckItem(text = localization.onboardingPrivacyBullet2)
        CheckItem(text = localization.onboardingPrivacyBullet3)
        CheckItem(text = localization.onboardingPrivacyBullet4)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ─── Page 5: Download AI Models ──────────────────────────────────────────────

@Composable
private fun DownloadModelsPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    OnboardingTitle(text = localization.onboardingDownloadAIModelsTitle)
    Spacer(modifier = Modifier.height(12.dp))
    OnboardingDescription(text = localization.onboardingDownloadAIModelsDescription)
    Spacer(modifier = Modifier.height(28.dp))
    // Stacked model chips
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelChip(name = "Llama", color = Color(0xFF5B6BF8))
        ModelChip(name = "Gemma", color = Color(0xFF4CAF50))
        ModelChip(name = "Phi", color = Color(0xFF9E9E9E))
    }
    Spacer(modifier = Modifier.height(24.dp))
    // Download size info
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = localization.onboardingDownloadSizeInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = localization.onboardingDownloadSizeGuide,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ModelChip(name: String, color: Color) {
    Box(
        modifier = Modifier
            .widthIn(min = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 32.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

// ─── Page 6: You're Ready! ────────────────────────────────────────────────────

@Composable
private fun YoureReadyPage(localization: com.llamatik.app.localization.Localization) {
    Spacer(modifier = Modifier.height(8.dp))
    Image(
        painter = painterResource(Res.drawable.a_pair_of_llamas_in_a_field_with_clouds_and_mounta),
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentScale = ContentScale.Fit
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = localization.onboardingReadyTitle,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = localization.onboardingReadySubtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(24.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CheckItem(text = localization.onboardingReadyBullet1)
        CheckItem(text = localization.onboardingReadyBullet2)
        CheckItem(text = localization.onboardingReadyBullet3)
        CheckItem(text = localization.onboardingReadyBullet4)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
private fun OnboardingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun OnboardingDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CheckItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = CheckGreen
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

fun String.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(this@toAnnotatedString)
    }
}
