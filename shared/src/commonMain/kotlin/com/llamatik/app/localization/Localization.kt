package com.llamatik.app.localization

import androidx.compose.runtime.Composable
import com.llamatik.app.localization.translations.CatalanLocalization
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
    val contextLength: String
    val numThreads: String
    val useMmap: String
    val flashAttention: String
    val batchSize: String
    val apply: String

    val defaultSystemPrompt: String
    val downloadFinished: String

    val smolVLM256SystemPrompt: String
    val smolVLM500SystemPrompt: String

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

    val vlmModels: String
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
    val visionModeEnabledButNoModelLoadedError: String
    val imageGenerationFailedError: String
    val imageGenerationError: String
    val allCachedModelsRemoved: String
    val settings: String
    val removeAllDownloadedModels: String
    val clearCachedModelsDialogTitle: String
    val clearCachedModelsDialogMessage: String
    val cancel: String
    val clear: String

    // Onboarding model choice (final page)
    val onboardingModelChoiceTitle: String
    val onboardingModelChoiceDescription: String
    val onboardingDownloadDefaultModel: String
    val onboardingBrowseCatalog: String
    val onboardingSkipForNow: String
    val onboardingNoModelEmptyState: String
    val onboardingNoModelEmptyStateAction: String

    val configure: String
    val modelsTitle: String

    val downloadFromUrl: String
    val modelUrlLabel: String
    val modelNameLabel: String
    val categoryLabel: String

    val newFolder: String
    val folderName: String
    val createFolder: String
    val moveToFolder: String
    val removeFromFolder: String
    val selectOrCreateFolder: String
    val noFolderName: String
    val chatsInFolder: String

    // Onboarding v2 — English defaults; individual translations can override
    val onboardingWelcomeSubtitle: String get() = "Local AI for a more independent future"
    val onboardingPrivateLabel: String get() = "Private"
    val onboardingOfflineLabel: String get() = "Offline"
    val onboardingOpenSourceLabel: String get() = "Open Source"
    val onboardingRunLLMsDescription: String get() = "Chat, create, and explore with powerful AI models directly on your device — no cloud, no limits."
    val onboardingAIToolkitTitle: String get() = "Your All-in-One AI Toolkit"
    val onboardingAIToolkitDescription: String get() = "Text generation, image creation, speech-to-text, text-to-speech and more — all in one app."
    val onboardingFeatureChat: String get() = "Chat"
    val onboardingFeatureImages: String get() = "Images"
    val onboardingFeatureSpeech: String get() = "Speech"
    val onboardingFeatureDocuments: String get() = "Documents"
    val onboardingBuiltForDevsTitle: String get() = "Built for Developers"
    val onboardingBuiltForDevsDescription: String get() = "Open source, Kotlin Multiplatform and powered by llama.cpp. Customize, extend and build your own AI apps."
    val onboardingPrivacyControlTitle: String get() = "Private & In Your Control"
    val onboardingPrivacyControlDescription: String get() = "Your data stays on your device. No cloud dependencies, no tracking, no network latency."
    val onboardingPrivacyBullet1: String get() = "100% offline"
    val onboardingPrivacyBullet2: String get() = "Your data, your rules"
    val onboardingPrivacyBullet3: String get() = "Works anywhere"
    val onboardingPrivacyBullet4: String get() = "No subscriptions required"
    val onboardingDownloadAIModelsTitle: String get() = "Download AI Models"
    val onboardingDownloadAIModelsDescription: String get() = "To get the best experience, you can download recommended models now or do it later."
    val onboardingDownloadSizeInfo: String get() = "Large files (1–8 GB)"
    val onboardingDownloadSizeGuide: String get() = "We'll guide you through the download and you can always add more models later from Settings."
    val onboardingDownloadModelsButton: String get() = "Download Models"
    val onboardingDoItLaterButton: String get() = "Do It Later"
    val onboardingReadyTitle: String get() = "You're Ready!"
    val onboardingReadySubtitle: String get() = "Let's build a more open and independent AI future together."
    val onboardingReadyBullet1: String get() = "Run LLMs offline"
    val onboardingReadyBullet2: String get() = "Create and explore"
    val onboardingReadyBullet3: String get() = "Keep your data private"
    val onboardingReadyBullet4: String get() = "Open source and for everyone"
    val onboardingGetStartedButton: String get() = "Get Started"
    val onboardingSkip: String get() = "Skip"
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
    JA,
    CA;

    companion object {
        val languages = listOf(EN, ES, IT, FR, DE, RU, CN, PT, HI, FA, JA, CA)
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
    AvailableLanguages.CA -> CatalanLocalization
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
        AvailableLanguages.CA -> "ca"
    }
}

fun getLanguageName(): String? {
    return when (getCurrentLanguage()) {
        AvailableLanguages.EN -> null
        AvailableLanguages.ES -> "Spanish"
        AvailableLanguages.IT -> "Italian"
        AvailableLanguages.FR -> "French"
        AvailableLanguages.DE -> "German"
        AvailableLanguages.RU -> "Russian"
        AvailableLanguages.CN -> "Chinese"
        AvailableLanguages.PT -> "Portuguese"
        AvailableLanguages.HI -> "Hindi"
        AvailableLanguages.FA -> "Persian"
        AvailableLanguages.JA -> "Japanese"
        AvailableLanguages.CA -> "Catalan"
    }
}

val AvailableLanguages.displayName: String
    get() = when (this) {
        AvailableLanguages.EN -> "English"
        AvailableLanguages.ES -> "Español"
        AvailableLanguages.IT -> "Italiano"
        AvailableLanguages.FR -> "Français"
        AvailableLanguages.DE -> "Deutsch"
        AvailableLanguages.RU -> "Русский"
        AvailableLanguages.CN -> "中文"
        AvailableLanguages.PT -> "Português"
        AvailableLanguages.HI -> "हिन्दी"
        AvailableLanguages.FA -> "فارسی"
        AvailableLanguages.JA -> "日本語"
        AvailableLanguages.CA -> "Català"
    }
