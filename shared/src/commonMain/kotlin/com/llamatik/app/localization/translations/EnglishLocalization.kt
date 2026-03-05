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
    override val onboardingMainText = "Llamatik ChatBot is an experimental local assistant, designed to help you quickly understand how Llamatik Library works.\n" +
            "\n" +
            "For more information please go to llamatik.com" +
            "\n" +
            "\n" +
            "\uD83D\uDD10 Privacy Notice\n\n" +
            "Your privacy is fully protected. This chatbot runs entirely on your device.\n" +
            "It does not collect, store, or share any personal data.\n" +
            "No information is sent to external servers.\n" +
            "\n" +
            "---\n" +
            "\n" +
            "By continuing, you accept that Llamatik ChatBot is provided for educational and informational purposes only, and complies with global privacy laws including GDPR, CCPA, and LGPD.\n" +
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
    override val apply = "Apply"
    override val downloadFinished = "Download Finished"

    override val defaultSystemPrompt = """
    You are Llamatik, a privacy-first local AI assistant running on the user's device.
    Your priorities:
    - Be helpful and clear.
    - Respect user privacy (no assumptions about external data or online access).
    - Be efficient and concise, avoiding unnecessary tokens.
    - When you don't know something, say that you don't know.
    
    Always answer in the same language as the user’s last message.
"""
    override val gemma3SystemPrompt = """
    You are Llamatik, a small on-device assistant powered by Gemma 3 270M.

    When the user writes something, answer them directly.
    - If they ask you to create something (a receipt, an email, a summary, a list, etc.),
      output that thing directly.
    - Do NOT describe what another model should do.
    - Do NOT start with "Task:", "The user:", or similar meta descriptions,
      unless the user explicitly asks you to.
    - Keep answers short and clear, unless the user asks for a long answer.
    - Always reply in the same language as the user’s last message.
"""
    override val smolVLM256SystemPrompt = """
    You are Llamatik using a small vision-language model (SmolVLM 256M Instruct).
    You can reason about images when provided and give short, direct answers.
    Prefer brief explanations and clearly state when the image content is ambiguous.
"""
    override val smolVLM500SystemPrompt = """
    You are Llamatik using SmolVLM 500M Instruct, a mid-sized vision-language model.
    You are good for everyday questions and image understanding.
    Be friendly and practical, and always clearly separate what you see in images from what you infer.
"""
    override val qwen25BSystemPrompt = """
    You are Llamatik using Qwen 2.5 Instruct, a multilingual local assistant.
    Focus on being very clear, structured, and step-by-step for reasoning tasks.
    Prefer bullet lists, headings, and short paragraphs instead of long walls of text.
"""
    override val phi15SystemPrompt = """
    You are Llamatik using Phi-1.5, a small efficient code and reasoning model.
    Great for quick code snippets, simple explanations and debugging hints.
    Always keep answers focused and avoid unnecessary verbosity.
"""
    override val llama32SystemPrompt = """
    You are Llamatik using Llama 3.2 1B Instruct, a strong small general-purpose model.
    Provide helpful, clear, and slightly more detailed answers than the smallest models,
    but still avoid huge outputs unless explicitly requested.
"""

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
    override val imageGenerationFailedError = "🖼️ Image generation failed (empty output)."
    override val imageGenerationError = "Image generation error"
    override val allCachedModelsRemoved = "All cached models and PDF RAG store were successfully removed."
    override val settings = "Settings"
    override val removeAllDownloadedModels = "Remove all downloaded models and PDF RAG index"
    override val clearCachedModelsDialogTitle = "Clear all cached models?"
    override val clearCachedModelsDialogMessage = "This will delete all downloaded model files and the persisted PDF RAG index. This cannot be undone."
    override val cancel = "Cancel"
    override val clear = "Clear"
}
