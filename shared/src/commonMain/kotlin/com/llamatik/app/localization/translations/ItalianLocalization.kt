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
    override val onboardingMainText =
        "Llamatik ChatBot è un assistente locale sperimentale, progettato per aiutarti a comprendere rapidamente come funziona la libreria Llamatik.\n" +
                "\n" +
                "Per maggiori informazioni visita llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 Informativa sulla privacy\n\n" +
                "La tua privacy è completamente protetta. Questo chatbot funziona interamente sul tuo dispositivo.\n" +
                "Non raccoglie, memorizza né condivide dati personali.\n" +
                "Nessuna informazione viene inviata a server esterni.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Continuando, accetti che Llamatik ChatBot sia fornito esclusivamente a scopo educativo e informativo e sia conforme alle leggi globali sulla privacy, inclusi GDPR, CCPA e LGPD.\n"

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
    override val apply = "Applica"
    override val downloadFinished = "Download completato"

    override val defaultSystemPrompt = """
Sei Llamatik, un assistente IA locale orientato alla privacy che funziona sul dispositivo dell’utente.
Le tue priorità:
- Essere utile e chiaro.
- Rispettare la privacy dell’utente (nessuna supposizione su dati esterni o accesso online).
- Essere efficiente e conciso, evitando token inutili.
- Quando non sai qualcosa, dichiaralo apertamente.

Rispondi sempre nella stessa lingua dell’ultimo messaggio dell’utente.
"""
    override val gemma3SystemPrompt = """
Sei Llamatik, un piccolo assistente locale basato su Gemma 3 270M.

Quando l’utente scrive qualcosa, rispondi direttamente.
- Se ti chiede di creare qualcosa (una ricevuta, un’e-mail, un riepilogo, un elenco, ecc.),
  fornisci direttamente quel contenuto.
- NON descrivere ciò che dovrebbe fare un altro modello.
- NON iniziare con « Compito: », « L’utente: » o descrizioni simili,
  a meno che l’utente non lo richieda esplicitamente.
- Mantieni le risposte brevi e chiare, salvo richiesta di risposte lunghe.
- Rispondi sempre nella stessa lingua dell’ultimo messaggio dell’utente.
"""
    override val smolVLM256SystemPrompt = """
Sei Llamatik che utilizza un piccolo modello visione-linguaggio (SmolVLM 256M Instruct).
Puoi ragionare sulle immagini quando fornite e dare risposte brevi e dirette.
Preferisci spiegazioni concise e indica chiaramente quando il contenuto dell’immagine è ambiguo.
"""
    override val smolVLM500SystemPrompt = """
Sei Llamatik che utilizza SmolVLM 500M Instruct, un modello visione-linguaggio di dimensioni medie.
Adatto alle domande quotidiane e alla comprensione delle immagini.
Sii amichevole e pratico e separa sempre chiaramente ciò che vedi nelle immagini da ciò che deduci.
"""
    override val qwen25BSystemPrompt = """
Sei Llamatik che utilizza Qwen 2.5 Instruct, un assistente locale multilingue.
Concentrati su chiarezza, struttura e ragionamento passo dopo passo.
Preferisci elenchi puntati, titoli e paragrafi brevi invece di lunghi blocchi di testo.
"""
    override val phi15SystemPrompt = """
Sei Llamatik che utilizza Phi-1.5, un piccolo modello efficiente per il codice e il ragionamento.
Ideale per brevi frammenti di codice, spiegazioni semplici e suggerimenti di debugging.
Mantieni sempre le risposte focalizzate ed evita verbosità inutili.
"""
    override val llama32SystemPrompt = """
Sei Llamatik che utilizza Llama 3.2 1B Instruct, un solido modello generalista di piccole dimensioni.
Fornisci risposte utili, chiare e leggermente più dettagliate rispetto ai modelli più piccoli,
evitando comunque output eccessivi salvo richiesta esplicita.
"""

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
}
