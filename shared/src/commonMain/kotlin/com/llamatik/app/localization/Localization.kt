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
    val open: String
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
    val onboardingWelcomeSubtitle: String get() = "AI on your device. Private. Offline. Free."
    val onboardingPrivateLabel: String get() = "Private"
    val onboardingOfflineLabel: String get() = "Offline"
    val onboardingOpenSourceLabel: String get() = "Open Source"
    val onboardingRunLLMsDescription: String get() = "Run powerful AI models entirely on your device — no internet required, no data sent anywhere."
    val onboardingAIToolkitTitle: String get() = "Everything You Need"
    val onboardingAIToolkitDescription: String get() = "Chat, analyze images, transcribe audio, and process documents — all powered by local AI."
    val onboardingFeatureChat: String get() = "Chat"
    val onboardingFeatureImages: String get() = "Images"
    val onboardingFeatureSpeech: String get() = "Speech"
    val onboardingFeatureDocuments: String get() = "Documents"
    val onboardingBuiltForDevsTitle: String get() = "Transparent & Trustworthy"
    val onboardingBuiltForDevsDescription: String get() = "Llamatik is fully open source — anyone can see exactly how it works. No hidden code, no secret data collection."
    val onboardingPrivacyControlTitle: String get() = "Your Data, Your Rules"
    val onboardingPrivacyControlDescription: String get() = "Everything stays on your device. No accounts, no cloud, no tracking."
    val onboardingPrivacyBullet1: String get() = "Offline after setup"
    val onboardingPrivacyBullet2: String get() = "Your data never leaves your device"
    val onboardingPrivacyBullet3: String get() = "Works without Wi-Fi or mobile data"
    val onboardingPrivacyBullet4: String get() = "No subscriptions, no sign-ups"
    val onboardingDownloadAIModelsTitle: String get() = "Almost There!"
    val onboardingDownloadAIModelsDescription: String get() = "To start chatting you need to download an AI model. It runs fully offline on your device. The default model is about 800MB — you can explore others in Settings later."
    val onboardingDownloadSizeInfo: String get() = "One-time download (~800MB)"
    val onboardingDownloadSizeGuide: String get() = "Once downloaded, the model runs entirely on your device with no internet required."
    val onboardingDownloadModelsButton: String get() = "Download Default Model"
    val onboardingDoItLaterButton: String get() = "Do It Later"
    val onboardingReadyTitle: String get() = "You're Ready!"
    val onboardingReadySubtitle: String get() = "Start exploring what local AI can do — everything runs on your device."
    val onboardingReadyBullet1: String get() = "Run AI offline, anytime"
    val onboardingReadyBullet2: String get() = "Chat, create, and explore"
    val onboardingReadyBullet3: String get() = "Your data stays private"
    val onboardingReadyBullet4: String get() = "Free and open source"
    val onboardingGetStartedButton: String get() = "Get Started"
    val onboardingSkip: String get() = "Skip"

    val aboutDescription: String get() = "Llamatik is a Kotlin Multiplatform AI app built on top of the open-source Llamatik library. It delivers powerful on-device AI across all supported platforms — fully offline, privacy-preserving, through a unified Kotlin API."
    val aboutPoweredByTitle: String get() = "Powered by"
    val aboutLibraryTitle: String get() = "Built on the Llamatik Library"
    val aboutLicenseLabel: String get() = "MIT License · Open Source"
    val aboutLlmEngine: String get() = "LLM inference"
    val aboutSttEngine: String get() = "Speech-to-Text"
    val aboutImageEngine: String get() = "Image Generation"
    val aboutCorePkg: String get() = "Native inference bindings"
    val aboutSdkPkg: String get() = "AI SDK · Agent Framework · Assistant"
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
