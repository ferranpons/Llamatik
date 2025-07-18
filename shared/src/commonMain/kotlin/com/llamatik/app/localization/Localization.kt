package com.llamatik.app.localization

import androidx.compose.runtime.Composable
import com.llamatik.app.localization.translations.ChineseLocalization
import com.llamatik.app.localization.translations.DeutschLocalization
import com.llamatik.app.localization.translations.EnglishLocalization
import com.llamatik.app.localization.translations.FrenchLocalization
import com.llamatik.app.localization.translations.ItalianLocalization
import com.llamatik.app.localization.translations.RussianLocalization
import com.llamatik.app.localization.translations.SpanishLocalization

interface Localization {
    val appName: String

    val actionSettings: String
    val next: String
    val close: String
    val previous: String
    val welcome: String


    val backLabel: String
    val topAppBarActionIconDescription: String
    val home: String
    val news: String

    val onBoardingStartButton: String
    val onBoardingAlreadyHaveAnAccountButton: String
    val searchItems: String
    val backButton: String
    val search: String
    val noItemFound: String

    val homeLastestNews: String

    val noResultsTitle: String
    val noResultsDescription: String

    val greetingMorning: String
    val greetingAfternoon: String
    val greetingEvening: String
    val greetingNight: String

    val debugMenuTitle: String
    val featureNotAvailableMessage: String

    val onboardingPromoTitle1: String
    val onboardingPromoTitle2: String
    val onboardingPromoTitle3: String
    val onboardingPromoTitle4: String

    val onboardingPromoLine1: String
    val onboardingPromoLine2: String
    val onboardingPromoLine3: String
    val onboardingPromoLine4: String

    val feedItemTitle: String


    val loading: String
    val profileImageDescription: String
    val manuals: String
    val guides: String
    val workInProgress: String
    val dismiss: String
    val onboarding: String
    val about: String
    val chooseLanguage: String
    val change: String
    val language: String

}

enum class AvailableLanguages {
    DE,
    EN,
    ES,
    IT,
    FR,
    RU,
    CN;

    companion object {
        val languages = listOf(EN, ES, IT, FR, DE, RU, CN)
    }
}

expect fun getCurrentLanguage(): AvailableLanguages

@Composable
expect fun SetLanguage(language: AvailableLanguages)

fun getCurrentLocalization() = when (getCurrentLanguage()) {
    AvailableLanguages.EN -> EnglishLocalization
    AvailableLanguages.ES -> SpanishLocalization
    AvailableLanguages.IT -> ItalianLocalization
    AvailableLanguages.FR -> FrenchLocalization
    AvailableLanguages.DE -> DeutschLocalization
    AvailableLanguages.RU -> RussianLocalization
    AvailableLanguages.CN -> ChineseLocalization
}
