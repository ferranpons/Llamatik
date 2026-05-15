package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object ItalianLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Impostazioni"
    override val next = "Avanti"
    override val close = "Chiudere"
    override val previous = "Precedente"

    override val welcome = "Benvenuto nel Llamatik"
    override val backLabel = "Indietro"
    override val topAppBarActionIconDescription = "Impostazioni"
    override val home = "Home"
    override val news = "Carica"
    override val onBoardingStartButton = "Inizia"
    override val onBoardingAlreadyHaveAnAccountButton = "Ho un account"
    override val searchItems = "Cerca animali domestici"
    override val backButton = "indietro"
    override val search = "Cerca"
    override val noItemFound = "Elemento non trovato"
    override val homeLastestNews = "La mia ultima ricerca"
    override val noResultsTitle = "Non ci sono risultati al momento"
    override val noResultsDescription =
        "Prova più tardi a cercare di nuovo. È possibile che il servizio sia in carico elevato in questo momento. Ci scusiamo per l'inconveniente."

    override val greetingMorning = "Buongiorno"
    override val greetingAfternoon = "Buon pomeriggio"
    override val greetingEvening = "Buona serata"
    override val greetingNight = "Buona notte"

    override val debugMenuTitle = "Menu di debug"
    override val featureNotAvailableMessage =
        "Ci dispiace, ma questa funzionalità non è attualmente disponibile. Puoi trovare i manuali e le guide per ogni modulo nella scheda moduli."

    override val onboardingPromoTitle1 = "Esegui LLM offline"
    override val onboardingPromoTitle2 = "Privacy senza cloud"
    override val onboardingPromoTitle3 = "Controllo completo locale"
    override val onboardingPromoTitle4 = "Open source per sviluppatori"

    override val onboardingPromoLine1 =
        "Llamatik porta una potente IA locale nelle tue app Kotlin Multiplatform — completamente offline e attenta alla privacy."

    override val onboardingPromoLine2 =
        "Costruisci chatbot, copiloti e assistenti intelligenti senza cloud e senza latenza di rete."

    override val onboardingPromoLine3 =
        "Usa i tuoi modelli, gestisci i tuoi archivi vettoriali e mantieni il pieno controllo dello stack LLM — tutto in Kotlin."

    override val onboardingPromoLine4 =
        "Progettato per gli sviluppatori. Basato su llama.cpp. Llamatik è open source e pronto a rivoluzionare l’IA locale su mobile e desktop."

    override val feedItemTitle = "Elemento di feed"
    override val loading = "Caricamento..."
    override val profileImageDescription = "Immagine del profilo"
    override val manuals = "Manuali"
    override val guides = "Guide"
    override val workInProgress = "LAVORI IN CORSO"
    override val dismiss = "chiudi"
    override val onboarding = "Benvenuto"
    override val about = "Informazioni"
    override val chooseLanguage = "Scegli la lingua"
    override val change = "Cambia"
    override val language = "Lingua: "

    override val viewAll = "Vedi tutto"
    override val welcomeToThe = "Benvenuto su "
    override val onboardingMainText = "Llamatik è un assistente AI privato che funziona direttamente sul tuo dispositivo, progettato per chattare, esplorare idee e portare a termine attività — senza dipendere dal cloud.\n" +
            "\n" +
            "Tutto funziona localmente sul tuo dispositivo, offrendoti il pieno controllo dei tuoi dati e riducendo il consumo energetico dei server remoti.\n" +
            "\n" +
            "Per maggiori informazioni visita llamatik.com" +
            "\n" +
            "\n" +
            "\uD83D\uDD10 Informativa sulla privacy\n\n" +
            "La tua privacy è completamente protetta. Questa app funziona interamente sul tuo dispositivo.\n" +
            "Nessun dato personale viene raccolto, memorizzato o condiviso.\n" +
            "Nessuna informazione viene inviata a server esterni.\n" +
            "\n" +
            "---\n" +
            "\n" +
            "Continuando, riconosci che Llamatik è fornito come assistente AI locale e rispetta le normative globali sulla privacy come GDPR, CCPA e LGPD.\n" +
            "\n"

    override val actionContinue = "Continua"
    override val settingUpLlamatik = "Configurazione di Llamatik…"
    override val downloadingMainModels =
        "Download dei modelli principali per la prima volta.\nPotrebbe richiedere alcuni minuti."
    override val progress = "Avanzamento"
    override val me = "Io"

    override val suggestion1 = "Creare una semplice ricevuta per la vendita di una console per videogiochi"
    override val suggestion2 = "Scrivere una risposta educata a qualcuno che chiede uno sconto"
    override val suggestion3 = "Fornire una breve panoramica delle ultime notizie mondiali"
    override val suggestion4 = "Creare un elenco di consigli per vendere articoli online"
    override val suggestion5 = "Dammi un elenco di passaggi per preparare una fattura semplice"
    override val suggestion6 = "Scrivere un breve racconto su una foresta magica"
    override val askMeAnything = "Chiedimi qualcosa…"
    override val stop = "Stop"
    override val send = "Invia"
    override val noModelSelected = "nessun modello selezionato"
    override val current = "Attuale"
    override val select = "Seleziona"
    override val delete = "Elimina"
    override val download = "Scarica"
    override val downloading = "Download in corso…"
    override val generateModels = "Genera modelli"
    override val generationSettings = "Impostazioni di generazione"
    override val temperature = "Temperatura"
    override val maxTokens = "Token massimi"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Penalità di ripetizione"
    override val contextLength = "Lunghezza contesto"
    override val numThreads = "Thread"
    override val useMmap = "Mappatura memoria (mmap)"
    override val flashAttention = "Flash Attention"
    override val batchSize = "Dimensione batch"
    override val apply = "Applica"
    override val downloadFinished = "Download completato"

    override val defaultSystemPrompt = """
Sei un assistente IA locale che funziona completamente sul dispositivo dell’utente.
Le tue priorità:
- Essere utile e chiaro.
- Rispettare la privacy dell’utente (nessuna supposizione su dati esterni o accesso online).
- Essere efficiente e conciso, evitando token inutili.
- Quando non sai qualcosa, dichiaralo apertamente.
    """
    override val smolVLM256SystemPrompt = "Sei un assistente visivo compatto. Descrivi l'immagine e rispondi alle domande in modo conciso."
    override val smolVLM500SystemPrompt = "Sei un assistente visivo-linguistico. Analizza l'immagine fornita e rispondi alle domande in modo chiaro."

    override val assistant = "Assistente"
    override val user = "Utente"
    override val system = "Sistema"
    override val relevantContext = "Contesto rilevante"
    override val defaultSystemPromptRendererMessage =
        "Sei un assistente utile. Usa il contesto fornito se è rilevante. " +
                "Se il contesto è insufficiente, dillo brevemente prima di rispondere."

    override val copy = "Copia"
    override val paste = "Incolla"

    override val chatHistory = "Cronologia chat"
    override val noChatsYet = "Nessuna chat ancora"
    override val temporaryChat = "Chat temporanea"
    override val messages = "messaggi"
    override val temporaryChatExplanation = "La chat temporanea è attiva: questa conversazione non verrà salvata sul dispositivo."
    override val voiceInput = "Input vocale"
    override val listening = "In ascolto…"
    override val transcribing = "Trascrizione in corso…"
    override val embedModels = "Modelli di embedding"
    override val sttModels = "Modelli di riconoscimento vocale"
    override val speak = "Parla"

    override val vlmModels = "Modelli di visione"
    override val imageGenerationModels = "Modelli di generazione immagini"
    override val failedToDecodeImageError = "🖼️ Impossibile decodificare l'immagine."
    override val imageGeneration = "Generazione immagini"
    override val textGeneration = "Generazione testo"
    override val embeddingModelNotLoaded = "Nessun modello di embedding caricato"
    override val noEmbeddingModelLoaded = "Modello di embedding non caricato"
    override val recommended = "Consigliato"
    override val pdfSelectFile = "Seleziona un file PDF."
    override val pdfExtractionError = "Non è stato possibile estrarre il testo da questo PDF. Se è un PDF scansionato, è necessario l'OCR."
    override val pdfEmbedModelNeededWarning = "Per usare PDF RAG, scarica/carica il modello di embedding: \"nomic-embed-text\" (Modelli di embedding)."
    override val pdfNoUsableChunksError = "Nessun blocco di testo utilizzabile generato da questo PDF."
    override val pdfFailedToComputeEmbeddingsError = "Calcolo degli embedding per questo PDF non riuscito. Ricarica il modello di embedding e riprova."
    override val pdfIndexedForRAG = "✅ PDF indicizzato per RAG"
    override val pdfFailedToLoadPDFForRAG = "Caricamento del PDF per RAG non riuscito"
    override val failedToComputeEmbeddings = "Calcolo degli embedding per la tua domanda non riuscito. Ricarica il modello di embedding e riprova."
    override val thereIsAProblemWithAI = "C'è un problema con l'IA"
    override val iDontHaveEnoughInfoInSources = "Non ho informazioni sufficienti nelle mie fonti."
    override val imageModeEnabledButNoModelLoadedError = "🖼️ La modalità immagine è attiva, ma nessun modello Stable Diffusion è caricato. Apri Modelli e seleziona un modello SD."
    override val visionModeEnabledButNoModelLoadedError = "👁️ La modalità visione è attiva, ma nessun modello VLM è caricato. Apri Modelli e seleziona un modello di visione."
    override val imageGenerationFailedError = "🖼️ Generazione immagine non riuscita (output vuoto)."
    override val imageGenerationError = "Errore di generazione immagine"
    override val allCachedModelsRemoved = "Tutti i modelli memorizzati nella cache e l'indice PDF RAG sono stati rimossi con successo."
    override val settings = "Impostazioni"
    override val removeAllDownloadedModels = "Rimuovi tutti i modelli scaricati e l'indice PDF RAG"
    override val clearCachedModelsDialogTitle = "Cancellare tutti i modelli memorizzati?"
    override val clearCachedModelsDialogMessage = "Questo eliminerà tutti i file modello scaricati e l'indice PDF RAG salvato. L'operazione non può essere annullata."
    override val cancel = "Annulla"
    override val clear = "Cancella"

    override val onboardingModelChoiceTitle = "Get Started with a Model"
    override val onboardingModelChoiceDescription = "To start chatting you'll need a local model. Download the recommended default, browse the catalog, or skip and add one later."
    override val onboardingDownloadDefaultModel = "Download default model"
    override val onboardingBrowseCatalog = "Browse model catalog"
    override val onboardingSkipForNow = "Skip for now"
    override val onboardingNoModelEmptyState = "No model loaded yet. Download or import a model to start chatting."
    override val onboardingNoModelEmptyStateAction = "Get a model"
    override val configure = "Configura"
}
