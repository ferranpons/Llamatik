package com.llamatik.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.llamatik.app.localization.getCurrentLocalization
import com.llamatik.app.resources.Res
import com.llamatik.app.resources.a_pair_of_llamas_in_a_field_with_clouds_and_mounta
import com.llamatik.app.resources.flying_around_the_world_cuate
import com.llamatik.app.resources.instruction_manual_pana
import com.llamatik.app.resources.pay_attention_pana
import com.llamatik.app.ui.components.CarouselItem
import com.llamatik.app.ui.components.OnboardingComponent
import com.llamatik.app.ui.theme.Fonts
import com.llamatik.app.ui.theme.LlamatikTheme
import com.llamatik.app.ui.theme.Typography
import org.jetbrains.compose.resources.painterResource

class OnboardingScreen : Screen {
    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        var showPrivacyStep by remember { mutableStateOf(false) }

        LlamatikTheme {
            if (showPrivacyStep) {
                PrivacyConsentStep(
                    onContinue = { navigator.push(ModelChoiceOnboardingScreen()) }
                )
            } else {
                val onboardingPromoTitle1 =
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(localization.onboardingPromoTitle1)
                        }
                    }
                val onboardingPromoLine1 =
                    buildAnnotatedString {
                        append(localization.welcomeToThe)
                        appendLine()
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("Llamatik App")
                        }
                        appendLine()
                        append(localization.onboardingPromoLine1)
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("llamatik.com")
                        }
                    }

                val onboardingPromoTitle2 =
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(localization.onboardingPromoTitle2)
                        }
                    }

                val onboardingPromoTitle3 =
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(localization.onboardingPromoTitle3)
                        }
                    }

                val onboardingPromoTitle4 =
                    buildAnnotatedString {
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(localization.onboardingPromoTitle4)
                        }
                    }

                val onboardingPromoLine3 =
                    buildAnnotatedString {
                        append(localization.onboardingPromoLine3)
                    }

                val onboardingPromoLine4 =
                    buildAnnotatedString {
                        append(localization.onboardingPromoLine4)
                        withStyle(
                            style = SpanStyle(
                                fontFamily = Fonts.poppinsFamily(),
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("hello@multiplatformkickstarter.com")
                        }
                    }

                val carouselItems: List<CarouselItem> =
                    listOf(
                        CarouselItem(
                            Res.drawable.pay_attention_pana,
                            onboardingPromoTitle1,
                            onboardingPromoLine1,
                            localization.next
                        ),
                        CarouselItem(
                            Res.drawable.instruction_manual_pana,
                            onboardingPromoTitle2,
                            localization.onboardingPromoLine2.toAnnotatedString(),
                            localization.next
                        ),
                        CarouselItem(
                            Res.drawable.flying_around_the_world_cuate,
                            onboardingPromoTitle3,
                            onboardingPromoLine3,
                            localization.next
                        ),
                        CarouselItem(
                            Res.drawable.flying_around_the_world_cuate,
                            onboardingPromoTitle4,
                            onboardingPromoLine4,
                            localization.next
                        ) { showPrivacyStep = true }
                    )
                val onboardingComponent = OnboardingComponent(carouselItems)
                onboardingComponent.DrawCarousel()
            }
        }
    }
}

@Composable
private fun PrivacyConsentStep(onContinue: () -> Unit) {
    val localization = getCurrentLocalization()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var sizeImage by remember { mutableStateOf(IntSize.Zero) }
        val gradient = Brush.verticalGradient(
            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
            startY = sizeImage.height.toFloat() / 3,
            endY = sizeImage.height.toFloat()
        )

        Box {
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .onGloballyPositioned { sizeImage = it.size },
                contentScale = ContentScale.FillWidth,
                painter = painterResource(Res.drawable.a_pair_of_llamas_in_a_field_with_clouds_and_mounta),
                contentDescription = null
            )
            Box(modifier = Modifier.matchParentSize().background(gradient))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "🦙\n${localization.welcome}",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = localization.onboardingMainText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.size(16.dp))

            Button(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier
                    .widthIn(max = 350.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(start = 16.dp, end = 16.dp),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(10)
            ) {
                Text(
                    text = localization.actionContinue,
                    style = Typography.get().titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(16.dp))
    }
}

fun String.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(this@toAnnotatedString)
    }
}
