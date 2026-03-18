package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object CatalanLocalization : Localization {

    override val appName = "Llamatik"

    override val actionSettings = "Configuració"
    override val next = "Següent"
    override val close = "Tancar"
    override val previous = "Anterior"

    override val welcome = "Benvingut a Llamatik"

    override val backLabel = "Enrere"
    override val topAppBarActionIconDescription = "Configuració"
    override val home = "Inici"
    override val news = "Notícies"

    override val onBoardingStartButton = "Començar"
    override val onBoardingAlreadyHaveAnAccountButton = "Ja tinc un compte"
    override val searchItems = "Cercar"
    override val backButton = "enrere"
    override val search = "Cercar"
    override val noItemFound = "No s'ha trobat cap element"
    override val homeLastestNews = "Últimes notícies"

    override val noResultsTitle = "No hi ha resultats ara mateix"
    override val noResultsDescription =
        "Torna-ho a provar més tard. És possible que el servei estigui saturat en aquest moment."

    override val greetingMorning = "Bon dia"
    override val greetingAfternoon = "Bona tarda"
    override val greetingEvening = "Bona tarda"
    override val greetingNight = "Bona nit"

    override val debugMenuTitle = "Menú de depuració"
    override val featureNotAvailableMessage =
        "Aquesta funció no està disponible ara mateix. Pots consultar manuals i guies als detalls dels mòduls."

    override val onboardingPromoTitle1 = "Executa IA offline"
    override val onboardingPromoTitle2 = "Privat i sense núvol"
    override val onboardingPromoTitle3 = "Control total local"
    override val onboardingPromoTitle4 = "Codi obert"

    override val onboardingPromoLine1 = ""
    override val onboardingPromoLine2 = ""
    override val onboardingPromoLine3 = ""
    override val onboardingPromoLine4 = ""

    override val feedItemTitle = "Element"
    override val loading = "Carregant..."
    override val profileImageDescription = "Imatge de perfil"
    override val manuals = "Manuals"
    override val guides = "Guies"
    override val workInProgress = "EN DESENVOLUPAMENT"
    override val dismiss = "tancar"
    override val onboarding = "Introducció"
    override val about = "Sobre"
    override val chooseLanguage = "Escull idioma"
    override val change = "Canviar"
    override val language = "Idioma: "

    override val viewAll = "Veure tot"
    override val welcomeToThe = "Benvingut a "

    override val onboardingMainText =
        "Llamatik és un assistent d'IA privat que funciona directament al teu dispositiu, dissenyat per xatejar, explorar idees i ajudar-te a fer tasques — sense dependre del núvol.\n" +
                "\n" +
                "Tot s'executa localment al teu dispositiu, donant-te control total sobre les teves dades i reduint el consum energètic dels servidors remots.\n" +
                "\n" +
                "Per a més informació visita llamatik.com" +
                "\n" +
                "\n" +
                "\uD83D\uDD10 Avís de privacitat\n\n" +
                "La teva privacitat està totalment protegida. Aquesta aplicació funciona completament al teu dispositiu.\n" +
                "No es recopilen, emmagatzemen ni comparteixen dades personals.\n" +
                "No s'envia cap informació a servidors externs.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "En continuar, reconeixes que Llamatik s'ofereix com un assistent d'IA local i compleix amb normatives globals de privacitat com el GDPR, CCPA i LGPD.\n" +
                "\n"

    override val actionContinue = "Continuar"
    override val settingUpLlamatik = "Configurant Llamatik…"
    override val downloadingMainModels = "Descarregant models inicials.\nAixò pot trigar uns minuts."
    override val progress = "Progrés"
    override val me = "Jo"

    override val suggestion1 = "Crea un rebut simple per a una venda"
    override val suggestion2 = "Escriu una resposta educada demanant descompte"
    override val suggestion3 = "Resumeix les últimes notícies"
    override val suggestion4 = "Consells per vendre online"
    override val suggestion5 = "Passos per crear una factura"
    override val suggestion6 = "Escriu una història curta"
    override val askMeAnything = "Pregunta'm qualsevol cosa…"
    override val stop = "Atura"
    override val send = "Enviar"
    override val noModelSelected = "cap model seleccionat"
    override val current = "Actual"
    override val select = "Seleccionar"
    override val delete = "Eliminar"
    override val download = "Descarregar"
    override val downloading = "Descarregant…"
    override val generateModels = "Generar models"
    override val generationSettings = "Configuració"
    override val temperature = "Temperatura"
    override val maxTokens = "Tokens màx."
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Penalització"
    override val apply = "Aplicar"

    override val defaultSystemPrompt = """
    Ets Llamatik, un assistent d'IA local centrat en la privacitat que s'executa al dispositiu de l'usuari.
    Les teves prioritats són:
    - Ser útil i clar.
    - Respectar la privacitat de l'usuari (sense suposicions sobre dades externes ni accés a internet).
    - Ser eficient i concís, evitant tokens innecessaris.
    - Quan no sàpigues alguna cosa, digues-ho.
    
    Respon sempre en el mateix idioma que l'últim missatge de l'usuari.
"""

    override val downloadFinished = "Descàrrega completada"

    override val gemma3SystemPrompt = """
    Ets Llamatik, un petit assistent que s'executa al dispositiu impulsat per Gemma 3 270M.

    Quan l'usuari escrigui alguna cosa, respon-li directament.
    - Si et demana que creïs alguna cosa (un rebut, un correu electrònic, un resum, una llista, etc.),
      genera directament aquest contingut.
    - NO descriguis què hauria de fer un altre model.
    - NO comencis amb "Tasca:", "L'usuari:", ni descripcions similars,
      tret que l'usuari t'ho demani explícitament.
    - Mantén les respostes curtes i clares, tret que l'usuari demani una resposta llarga.
    - Respon sempre en el mateix idioma que l'últim missatge de l'usuari.
"""

    override val smolVLM256SystemPrompt = """
    Ets Llamatik utilitzant un petit model de visió-llenguatge (SmolVLM 256M Instruct).
    Pots raonar sobre imatges quan se't proporcionin i donar respostes curtes i directes.
    Prefereix explicacions breus i indica clarament quan el contingut de la imatge sigui ambigu.
"""

    override val smolVLM500SystemPrompt = """
    Ets Llamatik utilitzant SmolVLM 500M Instruct, un model de visió-llenguatge de mida mitjana.
    Ets adequat per a preguntes quotidianes i per entendre imatges.
    Sigues amable i pràctic, i separa sempre amb claredat el que veus a les imatges del que dedueixes.
"""

    override val qwen25BSystemPrompt = """
    Ets Llamatik utilitzant Qwen 2.5 Instruct, un assistent local multilingüe.
    Centra't a ser molt clar, estructurat i pas a pas en tasques de raonament.
    Prefereix llistes amb punts, encapçalaments i paràgrafs curts en lloc de blocs llargs de text.
"""

    override val phi15SystemPrompt = """
    Ets Llamatik utilitzant Phi-1.5, un model petit i eficient per a codi i raonament.
    És ideal per a fragments de codi ràpids, explicacions senzilles i pistes de depuració.
    Mantén sempre les respostes enfocades i evita una verbositat innecessària.
"""

    override val llama32SystemPrompt = """
    Ets Llamatik utilitzant Llama 3.2 1B Instruct, un model petit però potent d'ús general.
    Proporciona respostes útils, clares i lleugerament més detallades que les dels models més petits,
    però evita respostes massa llargues tret que l'usuari ho demani explícitament.
"""
    override val relevantContext = "Context rellevant"
    override val system = "Sistema"
    override val user = "Usuari"
    override val assistant = "Assistent"
    override val defaultSystemPromptRendererMessage = EnglishLocalization.defaultSystemPromptRendererMessage

    override val copy = "Copiar"
    override val paste = "Enganxar"

    override val chatHistory = "Historial"
    override val noChatsYet = "Encara no hi ha xats"
    override val temporaryChat = "Xat temporal"
    override val messages = "missatges"
    override val temporaryChatExplanation = "Aquest xat no es guardarà al dispositiu."
    override val voiceInput = "Entrada de veu"
    override val listening = "Escoltant…"
    override val transcribing = "Transcrivint…"
    override val embedModels = "Models d'embedding"
    override val sttModels = "Models de veu"
    override val speak = "Parlar"

    override val imageGenerationModels = "Models d'imatge"
    override val failedToDecodeImageError = "Error en decodificar la imatge."
    override val imageGeneration = "Generació d'imatges"
    override val textGeneration = "Generació de text"
    override val embeddingModelNotLoaded = "Cap model carregat"
    override val noEmbeddingModelLoaded = "Model no carregat"
    override val recommended = "Recomanat"

    override val pdfSelectFile = "Selecciona un PDF"
    override val pdfExtractionError = "No s'ha pogut extreure el text."
    override val pdfEmbedModelNeededWarning = "Cal un model d'embedding."
    override val pdfNoUsableChunksError = "No hi ha contingut usable."
    override val pdfFailedToComputeEmbeddingsError = "Error en embeddings."
    override val pdfIndexedForRAG = "PDF indexat"
    override val pdfFailedToLoadPDFForRAG = "Error carregant PDF"
    override val failedToComputeEmbeddings = "Error embeddings"
    override val thereIsAProblemWithAI = "Problema amb la IA"
    override val iDontHaveEnoughInfoInSources = "No tinc prou informació."

    override val imageModeEnabledButNoModelLoadedError = "Mode imatge sense model."
    override val imageGenerationFailedError = "Error generant imatge."
    override val imageGenerationError = "Error d'imatge"
    override val allCachedModelsRemoved = "Models eliminats"

    override val settings = "Configuració"
    override val removeAllDownloadedModels = "Eliminar models"
    override val clearCachedModelsDialogTitle = "Eliminar tot?"
    override val clearCachedModelsDialogMessage = "Això eliminarà tots els models."
    override val cancel = "Cancel·lar"
    override val clear = "Eliminar"
}