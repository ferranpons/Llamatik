package com.dcshub.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.dcshub.app.localization.getCurrentLocalization
import com.dcshub.app.resources.Res
import com.dcshub.app.resources.flying_around_the_world_cuate
import com.dcshub.app.resources.instruction_manual_pana
import com.dcshub.app.resources.logo_sky_vector
import com.dcshub.app.resources.pay_attention_pana
import com.dcshub.app.ui.components.CarouselItem
import com.dcshub.app.ui.components.OnboardingComponent
import com.dcshub.app.ui.theme.LlamatikTheme
import com.dcshub.app.ui.theme.Fonts

class OnboardingScreen : Screen {

    @Composable
    override fun Content() {
        val localization = getCurrentLocalization()
        val navigator = LocalNavigator.currentOrThrow
        LlamatikTheme {
            val onboardingPromoTitle1 = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontFamily = Fonts.poppinsFamily(),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(localization.onboardingPromoTitle1)
                }
            }
            val onboardingPromoLine1 = buildAnnotatedString {
                append("Welcome to the ")
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
                    append("digitalcombatsimulator.com")
                }
            }

            val onboardingPromoTitle2 = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontFamily = Fonts.poppinsFamily(),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(localization.onboardingPromoTitle2)
                }
            }

            val onboardingPromoTitle3 = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontFamily = Fonts.poppinsFamily(),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(localization.onboardingPromoTitle3)
                }
            }

            val onboardingPromoTitle4 = buildAnnotatedString {
                withStyle(
                    style = SpanStyle(
                        fontFamily = Fonts.poppinsFamily(),
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(localization.onboardingPromoTitle4)
                }
            }

            val onboardingPromoLine3 = buildAnnotatedString {
                append(localization.onboardingPromoLine3)
                /*withStyle(style = SpanStyle(fontFamily = Fonts.poppinsFamily(), fontWeight = FontWeight.Bold)) {
                    append("multiplatformkickstarter.com")
                }*/
            }

            val onboardingPromoLine4 = buildAnnotatedString {
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

            val carouselItems: List<CarouselItem> = listOf(
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
                    Res.drawable.logo_sky_vector,
                    onboardingPromoTitle3,
                    onboardingPromoLine3,
                    localization.next
                ),
                CarouselItem(
                    Res.drawable.flying_around_the_world_cuate,
                    onboardingPromoTitle4,
                    onboardingPromoLine4,
                    localization.close
                ) { navigator.pop() }
            )
            val onboardingComponent = OnboardingComponent(carouselItems)
            onboardingComponent.DrawCarousel()
        }
    }
}

fun String.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        append(this@toAnnotatedString)
    }
}
