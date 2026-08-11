package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object EnglishLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Settings"
    override val next = "Next"
    override val close = "Close"
    override val previous = "Previous"

    override val welcome = "Welcome to Llamatik"

    override val backLabel = "Back"
    override val topAppBarActionIconDescription = "Settings"
    override val home = "Home"
    override val news = "News"

    override val onBoardingStartButton = "Start"
    override val onBoardingAlreadyHaveAnAccountButton = "I have an account"
    override val searchItems = "Search for Pets"
    override val backButton = "back"
    override val search = "Search"
    override val noItemFound = "Item not found"
    override val homeLastestNews = "Latest News"

    override val noResultsTitle = "The are no results right now"
    override val noResultsDescription =
        "Try later to search again it's possible the service is in high load right now. Sorry for the inconvenience."

    override val greetingMorning = "Good morning"
    override val greetingAfternoon = "Good afternoon"
    override val greetingEvening = "Good evening"
    override val greetingNight = "Good night"

    override val debugMenuTitle = "Debug Menu"
    override val featureNotAvailableMessage =
        "We are sorry but this Feature is not available right now. You can find the manuals and guides on every module detail on the modules tab."

    override val onboardingPromoTitle1 = "Run LLMs Offline"
    override val onboardingPromoTitle2 = "Private & Cloud-Free"
    override val onboardingPromoTitle3 = "Full Local Control"
    override val onboardingPromoTitle4 = "Open Source for Devs"

    override val onboardingPromoLine1 =
        "Llamatik brings powerful on-device AI to your Kotlin Multiplatform apps — fully offline, privacy-friendly, and lightning fast."

    override val onboardingPromoLine2 =
        "Build intelligent chatbots, copilots, and assistants without any cloud dependencies or network latency."

    override val onboardingPromoLine3 =
        "Use your own models, host your own vector stores, and stay in full control of your LLM stack — all in Kotlin."

    override val onboardingPromoLine4 =
        "Designed for developers. Powered by llama.cpp. Llamatik is open-source and ready to reshape local AI on mobile and desktop."

    override val feedItemTitle = "Feed Item"
    override val loading = "Loading..."
    override val profileImageDescription = "Profile Image"
    override val manuals = "Manuals"
    override val guides = "Guides"
    override val workInProgress = "WORK IN PROGRESS"
    override val dismiss = "dismiss"
    override val onboarding = "Onboarding"
    override val about = "About"
    override val chooseLanguage = "Choose Language"
    override val change = "Change"
    override val language = "Language: "

    override val viewAll = "View All"
    override val welcomeToThe = "Welcome to the "
    override val onboardingMainText = "Llamatik is a private, on-device AI assistant designed to help you chat, explore ideas, and get things done — all without relying on the cloud.\n" +
            "\n" +
            "Everything runs locally on your device, giving you full control over your data while reducing unnecessary energy usage from remote servers.\n" +
            "\n" +
            "For more information please visit llamatik.com" +
            "\n" +
            "\n" +
            "\uD83D\uDD10 Privacy Notice\n\n" +
            "Your privacy is fully protected. This app runs entirely on your device.\n" +
            "No personal data is collected, stored, or shared.\n" +
            "No information is sent to external servers.\n" +
            "\n" +
            "---\n" +
            "\n" +
            "By continuing, you acknowledge that Llamatik is provided as a local AI assistant and complies with global privacy regulations including GDPR, CCPA, and LGPD.\n" +
            "\n"

    override val actionContinue = "Continue"
    override val settingUpLlamatik = "Setting up Llamatik…"
    override val downloadingMainModels = "Downloading main models for the first time.\nThis may take a few minutes."
    override val progress = "Progress"
    override val me = "Me"

    override val suggestion1 = "Create a simple receipt for a video game console sale"
    override val suggestion2 = "Draft a polite reply to someone asking for a discount"
    override val suggestion3 = "Provide a brief overview of the most recent world news"
    override val suggestion4 = "Create a list of tips for selling items online"
    override val suggestion5 = "Give me a list of steps to prepare a simple invoice"
    override val suggestion6 = "Write a short story about a magical forest"
    override val askMeAnything = "Ask me something…"
    override val stop = "Stop"
    override val send = "Send"
    override val noModelSelected = "no model selected"
    override val current = "Current"
    override val select = "Select"
    override val delete = "Delete"
    override val download = "Download"
    override val downloading = "Downloading…"
    override val generateModels = "Generate Models"
    override val generationSettings = "Generation Settings"
    override val temperature = "Temperature"
    override val maxTokens = "Max Tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Repeat Penalty"
    override val contextLength = "Context Length"
    override val numThreads = "Threads"
    override val useMmap = "Memory Mapping (mmap)"
    override val flashAttention = "Flash Attention"
    override val batchSize = "Batch Size"
    override val apply = "Apply"
    override val downloadFinished = "Download Finished"

    override val defaultSystemPrompt = """
    You are a helpful AI assistant running fully on the user’s device.
    Your priorities:
    - Be helpful and clear.
    - Respect user privacy (no assumptions about external data or online access).
    - Be efficient and concise, avoiding unnecessary tokens.
    - When you don’t know something, say that you don’t know.
    """
    override val smolVLM256SystemPrompt = "You are a compact vision-language assistant. Describe the image and answer questions about it concisely."
    override val smolVLM500SystemPrompt = "You are a vision-language assistant. Analyze the provided image and answer questions about it clearly."

    override val assistant = "Assistant"
    override val user = "User"
    override val system = "System"
    override val relevantContext = "Relevant Context"
    override val defaultSystemPromptRendererMessage = "You are a helpful assistant. Use the provided context if it is relevant. " +
            "If the context is insufficient, say so briefly before answering."

    override val copy = "Copy"
    override val paste = "Paste"

    override val chatHistory = "Chat history"
    override val noChatsYet = "No chats yet"
    override val temporaryChat = "Temporary chat"
    override val messages = "messages"
    override val temporaryChatExplanation = "Temporary chat is enabled – this conversation won’t be saved on your device."
    override val voiceInput = "Voice input"
    override val listening = "Listening…"
    override val transcribing = "Transcribing…"
    override val embedModels = "Embed Models"
    override val sttModels = "Speech to Text Models"
    override val speak = "Speak"

    override val vlmModels = "Vision Models"
    override val imageGenerationModels = "Image Generation Models"
    override val failedToDecodeImageError = "🖼️ Failed to decode image."
    override val imageGeneration = "Image Generation"
    override val textGeneration = "Text Generation"
    override val embeddingModelNotLoaded = "No embedding model loaded"
    override val noEmbeddingModelLoaded = "Embedding model not loaded"
    override val recommended = "Recommended"
    override val pdfSelectFile = "Please select a PDF file."
    override val pdfExtractionError = "I couldn’t extract text from this PDF. If it’s a scanned PDF, it needs OCR."
    override val pdfEmbedModelNeededWarning = "To use PDF RAG, please download/load the embedding model: \"nomic-embed-text\" (Embed Models)."
    override val pdfNoUsableChunksError = "No usable text chunks were generated from this PDF."
    override val pdfFailedToComputeEmbeddingsError = "Failed to compute embeddings for this PDF. Please re-load the embedding model and try again."
    override val pdfIndexedForRAG = "✅ PDF indexed for RAG"
    override val pdfFailedToLoadPDFForRAG = "Failed to load PDF for RAG"
    override val failedToComputeEmbeddings = "Failed to compute embeddings for your question. Please re-load the embedding model and try again."
    override val thereIsAProblemWithAI = "There is a problem with the AI"
    override val iDontHaveEnoughInfoInSources = "I don't have enough information in my sources."
    override val imageModeEnabledButNoModelLoadedError = "🖼️ Image mode is enabled, but no Stable Diffusion model is loaded. Open Models and select a SD model."
    override val visionModeEnabledButNoModelLoadedError = "👁️ Vision mode is enabled, but no VLM model is loaded. Open Models and select a vision model."
    override val imageGenerationFailedError = "🖼️ Image generation failed (empty output)."
    override val imageGenerationError = "Image generation error"
    override val allCachedModelsRemoved = "All cached models and PDF RAG store were successfully removed."
    override val settings = "Settings"
    override val removeAllDownloadedModels = "Remove all downloaded models and PDF RAG index"
    override val clearCachedModelsDialogTitle = "Clear all cached models?"
    override val clearCachedModelsDialogMessage = "This will delete all downloaded model files and the persisted PDF RAG index. This cannot be undone."
    override val cancel = "Cancel"
    override val clear = "Clear"

    override val onboardingModelChoiceTitle = "Get Started with a Model"
    override val onboardingModelChoiceDescription = "To start chatting you'll need a local model. Download the recommended default, browse the catalog, or skip and add one later."
    override val onboardingDownloadDefaultModel = "Download default model"
    override val onboardingBrowseCatalog = "Browse model catalog"
    override val onboardingSkipForNow = "Skip for now"
    override val onboardingNoModelEmptyState = "No model loaded yet. Download or import a model to start chatting."
    override val onboardingNoModelEmptyStateAction = "Get a model"
    override val configure = "Configure"
    override val modelsTitle = "Models"

    override val newFolder = "New folder"
    override val folderName = "Folder name"
    override val createFolder = "Create folder"
    override val moveToFolder = "Move to folder"
    override val removeFromFolder = "Remove from folder"
    override val selectOrCreateFolder = "Select or create a folder"
    override val noFolderName = "Please enter a folder name"
    override val chatsInFolder = "Chats in folder"

    override val downloadFromUrl = "Download from URL"
    override val modelUrlLabel = "Model URL"
    override val modelNameLabel = "Model name"
    override val categoryLabel = "Category"
}
