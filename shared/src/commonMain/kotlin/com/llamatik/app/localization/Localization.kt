package com.llamatik.app.localization

import androidx.compose.runtime.Composable
import com.llamatik.app.localization.translations.ChineseLocalization
import com.llamatik.app.localization.translations.DeutschLocalization
import com.llamatik.app.localization.translations.EnglishLocalization
import com.llamatik.app.localization.translations.FrenchLocalization
import com.llamatik.app.localization.translations.HindiLocalization
import com.llamatik.app.localization.translations.ItalianLocalization
import com.llamatik.app.localization.translations.JapaneseLocalization
import com.llamatik.app.localization.translations.PersianLocalization
import com.llamatik.app.localization.translations.PortugueseLocalization
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

    val viewAll: String
    val welcomeToThe: String

    val onboardingMainText: String
    val actionContinue: String
    val settingUpLlamatik: String
    val downloadingMainModels: String
    val progress: String
    val me: String

    val suggestion1: String
    val suggestion2: String
    val suggestion3: String
    val suggestion4: String
    val suggestion5: String
    val suggestion6: String
    val askMeAnything: String
    val stop: String
    val send: String
    val noModelSelected: String
    val current: String
    val select: String
    val delete: String
    val download: String
    val downloading: String
    val generateModels: String
    val generationSettings: String
    val temperature: String
    val maxTokens: String
    val topP: String
    val topK: String
    val repeatPenalty: String
    val apply: String

    val defaultSystemPrompt: String
    val downloadFinished: String

    val gemma3SystemPrompt: String
    val smolVLM256SystemPrompt: String
    val smolVLM500SystemPrompt: String
    val qwen25BSystemPrompt: String
    val phi15SystemPrompt: String
    val llama32SystemPrompt: String

    val relevantContext: String
    val system: String
    val user: String
    val assistant: String
    val defaultSystemPromptRendererMessage: String

    val copy: String
    val paste: String

    val chatHistory: String
    val noChatsYet: String
    val temporaryChat: String
    val messages: String
    val temporaryChatExplanation: String
    val voiceInput: String
    val listening: String
    val transcribing: String
    val embedModels: String
    val sttModels: String
    val speak: String

    val imageGenerationModels: String
    val failedToDecodeImageError: String
    val imageGeneration: String
    val textGeneration: String
    val noEmbeddingModelLoaded: String
    val embeddingModelNotLoaded: String
    val recommended: String
    val pdfSelectFile: String
    val pdfExtractionError: String
    val pdfEmbedModelNeededWarning: String
    val pdfNoUsableChunksError: String
    val pdfFailedToComputeEmbeddingsError: String
    val pdfIndexedForRAG: String
    val pdfFailedToLoadPDFForRAG: String
    val failedToComputeEmbeddings: String
    val thereIsAProblemWithAI: String
    val iDontHaveEnoughInfoInSources: String
    val imageModeEnabledButNoModelLoadedError: String
    val imageGenerationFailedError: String
    val imageGenerationError: String
    val allCachedModelsRemoved: String
    val settings: String
    val removeAllDownloadedModels: String
    val clearCachedModelsDialogTitle: String
    val clearCachedModelsDialogMessage: String
    val cancel: String
    val clear: String
}

enum class AvailableLanguages {
    DE,
    EN,
    ES,
    IT,
    FR,
    RU,
    CN,
    PT,
    HI,
    FA,
    JA;

    companion object {
        val languages = listOf(EN, ES, IT, FR, DE, RU, CN, PT, HI, FA, JA)
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
    AvailableLanguages.PT -> PortugueseLocalization
    AvailableLanguages.HI -> HindiLocalization
    AvailableLanguages.FA -> PersianLocalization
    AvailableLanguages.JA -> JapaneseLocalization
}

fun getLanguageCode(): String? {
    return when (getCurrentLanguage()) {
        AvailableLanguages.EN -> "en"
        AvailableLanguages.ES -> "es"
        AvailableLanguages.IT -> "it"
        AvailableLanguages.FR -> "fr"
        AvailableLanguages.DE -> "de"
        AvailableLanguages.RU -> "ru"
        AvailableLanguages.CN -> "zh"
        AvailableLanguages.PT -> "pt"
        AvailableLanguages.HI -> "hi"
        AvailableLanguages.FA -> "fa"
        AvailableLanguages.JA -> "ja"
    }
}
