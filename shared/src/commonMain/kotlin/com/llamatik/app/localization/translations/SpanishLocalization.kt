package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object SpanishLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Configuración"
    override val next = "Siguiente"
    override val close = "Cerrar"
    override val previous = "Anterior"

    override val welcome = "Bienvenido a Llamatik"
    override val backLabel = "Atrás"
    override val topAppBarActionIconDescription = "Configuración"
    override val home = "Inicio"
    override val news = "Noticias"
    override val onBoardingStartButton = "Comenzar"
    override val onBoardingAlreadyHaveAnAccountButton = "Ya tengo una cuenta"
    override val searchItems = "Buscar mascotas"
    override val backButton = "atrás"
    override val search = "Buscar"
    override val noItemFound = "Elemento no encontrado"
    override val homeLastestNews = "Últimas noticias"
    override val noResultsTitle = "No hay resultados en este momento"
    override val noResultsDescription =
        "Intenta nuevamente más tarde para realizar una búsqueda. Es posible que el servicio esté bajo carga en este momento. Disculpa las molestias."

    override val greetingMorning = "Buenos días"
    override val greetingAfternoon = "Buenas tardes"
    override val greetingEvening = "Buenas noches"
    override val greetingNight = "Buenas noches"

    override val debugMenuTitle = "Menú de Depuración"
    override val featureNotAvailableMessage =
        "Lo sentimos, pero esta función no está disponible en este momento. Puedes encontrar los manuales y guías en cada módulo de la pestaña de módulos."

    override val onboardingPromoTitle1 = "Ejecuta LLMs sin conexión"
    override val onboardingPromoTitle2 = "Privado y sin nube"
    override val onboardingPromoTitle3 = "Control total local"
    override val onboardingPromoTitle4 = "Código abierto para devs"

    override val onboardingPromoLine1 =
        "Llamatik lleva IA potente y local a tus apps Kotlin Multiplatform — totalmente offline y respetuosa con tu privacidad."

    override val onboardingPromoLine2 =
        "Crea asistentes inteligentes y chatbots sin depender de la nube ni sufrir latencia de red."

    override val onboardingPromoLine3 =
        "Usa tus propios modelos, controla tus vectores y gestiona toda tu pila LLM directamente desde Kotlin."

    override val onboardingPromoLine4 =
        "Pensado para desarrolladores. Basado en llama.cpp. Llamatik es open source y está listo para transformar la IA local."

    override val feedItemTitle = "Noticia"
    override val loading = "Cargando..."
    override val profileImageDescription = "Imagen de perfil"
    override val manuals = "Manuales"
    override val guides = "Guías"
    override val workInProgress = "TRABAJO EN CURSO"
    override val dismiss = "cerrar"
    override val onboarding = "Introducción"
    override val about = "Acerca de"
    override val chooseLanguage = "Elegir idioma"
    override val change = "Cambiar"
    override val language = "Idioma: "

    override val viewAll = "Ver todo"
    override val welcomeToThe = "Bienvenido a "
    override val onboardingMainText = "Llamatik es un asistente de IA privado que funciona directamente en tu dispositivo, diseñado para chatear, explorar ideas y ayudarte a realizar tareas — sin depender de la nube.\n" +
            "\n" +
            "Todo se ejecuta localmente en tu dispositivo, dándote control total sobre tus datos y reduciendo el consumo energético de servidores remotos.\n" +
            "\n" +
            "Para más información visita llamatik.com" +
            "\n" +
            "\n" +
            "\uD83D\uDD10 Aviso de privacidad\n\n" +
            "Tu privacidad está totalmente protegida. Esta aplicación funciona completamente en tu dispositivo.\n" +
            "No se recopilan, almacenan ni comparten datos personales.\n" +
            "No se envía ninguna información a servidores externos.\n" +
            "\n" +
            "---\n" +
            "\n" +
            "Al continuar, reconoces que Llamatik se ofrece como un asistente de IA local y cumple con normativas globales de privacidad como GDPR, CCPA y LGPD.\n" +
            "\n"

    override val actionContinue = "Continuar"
    override val settingUpLlamatik = "Configurando Llamatik…"
    override val downloadingMainModels =
        "Descargando los modelos principales por primera vez.\nEsto puede tardar unos minutos."
    override val progress = "Progreso"
    override val me = "Yo"

    override val suggestion1 = "Crear un recibo sencillo para la venta de una consola de videojuegos"
    override val suggestion2 = "Redactar una respuesta educada a alguien que pide un descuento"
    override val suggestion3 = "Proporcionar un breve resumen de las noticias mundiales más recientes"
    override val suggestion4 = "Crear una lista de consejos para vender artículos en línea"
    override val suggestion5 = "Dame una lista de pasos para preparar una factura sencilla"
    override val suggestion6 = "Escribir una historia corta sobre un bosque mágico"
    override val askMeAnything = "Pregúntame algo…"
    override val stop = "Detener"
    override val send = "Enviar"
    override val noModelSelected = "ningún modelo seleccionado"
    override val current = "Actual"
    override val select = "Seleccionar"
    override val delete = "Eliminar"
    override val download = "Descargar"
    override val downloading = "Descargando…"
    override val generateModels = "Modelos de generación"
    override val generationSettings = "Configuración de generación"
    override val temperature = "Temperatura"
    override val maxTokens = "Máx. tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Penalización por repetición"
    override val contextLength = "Longitud de contexto"
    override val numThreads = "Hilos"
    override val useMmap = "Mapeo de memoria (mmap)"
    override val flashAttention = "Flash Attention"
    override val batchSize = "Tamaño de lote"
    override val apply = "Aplicar"
    override val downloadFinished = "Descarga finalizada"

    override val defaultSystemPrompt = """
Eres un asistente de IA que se ejecuta completamente en el dispositivo del usuario.
Tus prioridades:
- Ser útil y claro.
- Respetar la privacidad del usuario (sin suposiciones sobre datos externos o acceso en línea).
- Ser eficiente y conciso, evitando tokens innecesarios.
- Cuando no sepas algo, dilo claramente.
    """
    override val smolVLM256SystemPrompt = "Eres un asistente de visión compacto. Describe la imagen y responde preguntas sobre ella de forma concisa."
    override val smolVLM500SystemPrompt = "Eres un asistente de visión y lenguaje. Analiza la imagen proporcionada y responde preguntas sobre ella de forma clara."

    override val assistant = "Asistente"
    override val user = "Usuario"
    override val system = "Sistema"
    override val relevantContext = "Contexto relevante"
    override val defaultSystemPromptRendererMessage =
        "Eres un asistente útil. Usa el contexto proporcionado si es relevante. " +
                "Si el contexto es insuficiente, indícalo brevemente antes de responder."

    override val copy = "Copiar"
    override val paste = "Pegar"

    override val chatHistory = "Historial de chats"
    override val noChatsYet = "Aún no hay chats"
    override val temporaryChat = "Chat temporal"
    override val messages = "mensajes"
    override val temporaryChatExplanation = "El chat temporal está activado: esta conversación no se guardará en tu dispositivo."
    override val voiceInput = "Entrada de voz"
    override val listening = "Escuchando…"
    override val transcribing = "Transcribiendo…"
    override val embedModels = "Modelos de embeddings"
    override val sttModels = "Modelos de reconocimiento de voz"
    override val speak = "Hablar"

    override val vlmModels = "Modelos de visión"
    override val imageGenerationModels = "Modelos de generación de imágenes"
    override val failedToDecodeImageError = "🖼️ Error al decodificar la imagen."
    override val imageGeneration = "Generación de imágenes"
    override val textGeneration = "Generación de texto"
    override val embeddingModelNotLoaded = "Ningún modelo de embeddings cargado"
    override val noEmbeddingModelLoaded = "Modelo de embeddings no cargado"
    override val recommended = "Recomendado"
    override val pdfSelectFile = "Por favor, selecciona un archivo PDF."
    override val pdfExtractionError = "No se pudo extraer texto de este PDF. Si es un PDF escaneado, necesita OCR."
    override val pdfEmbedModelNeededWarning = "Para usar PDF RAG, descarga/carga el modelo de embeddings \"nomic-embed-text\" (Modelos de Embedding)."
    override val pdfNoUsableChunksError = "No se generaron fragmentos de texto utilizables a partir de este PDF."
    override val pdfFailedToComputeEmbeddingsError = "No se pudieron calcular los embeddings para este PDF. Vuelve a cargar el modelo de embeddings e inténtalo de nuevo."
    override val pdfIndexedForRAG = "✅ PDF indexado para RAG"
    override val pdfFailedToLoadPDFForRAG = "Error al cargar el PDF para RAG"
    override val failedToComputeEmbeddings = "No se pudieron calcular los embeddings para tu pregunta. Vuelve a cargar el modelo de embeddings e inténtalo de nuevo."
    override val thereIsAProblemWithAI = "Hay un problema con la IA"
    override val iDontHaveEnoughInfoInSources = "No tengo suficiente información en mis fuentes."
    override val imageModeEnabledButNoModelLoadedError = "🖼️ El modo imagen está activado, pero no hay ningún modelo Stable Diffusion cargado. Abre Modelos y selecciona un modelo SD."
    override val visionModeEnabledButNoModelLoadedError = "👁️ El modo visión está activado, pero no hay ningún modelo VLM cargado. Abre Modelos y selecciona un modelo de visión."
    override val imageGenerationFailedError = "🖼️ La generación de imagen falló (salida vacía)."
    override val imageGenerationError = "Error de generación de imagen"
    override val allCachedModelsRemoved = "Todos los modelos en caché y el almacenamiento PDF RAG se eliminaron correctamente."
    override val settings = "Configuración"
    override val removeAllDownloadedModels = "Eliminar todos los modelos descargados y el índice PDF RAG"
    override val clearCachedModelsDialogTitle = "¿Borrar todos los modelos en caché?"
    override val clearCachedModelsDialogMessage = "Esto eliminará todos los archivos de modelos descargados y el índice PDF RAG guardado. Esta acción no se puede deshacer."
    override val cancel = "Cancelar"
    override val clear = "Borrar"

    override val onboardingModelChoiceTitle = "Get Started with a Model"
    override val onboardingModelChoiceDescription = "To start chatting you'll need a local model. Download the recommended default, browse the catalog, or skip and add one later."
    override val onboardingDownloadDefaultModel = "Download default model"
    override val onboardingBrowseCatalog = "Browse model catalog"
    override val onboardingSkipForNow = "Skip for now"
    override val onboardingNoModelEmptyState = "No model loaded yet. Download or import a model to start chatting."
    override val onboardingNoModelEmptyStateAction = "Get a model"
    override val configure = "Configurar"
    override val open = "Abrir"
    override val modelsTitle = "Modelos"

    override val newFolder = "Nueva carpeta"
    override val folderName = "Nombre de la carpeta"
    override val createFolder = "Crear carpeta"
    override val moveToFolder = "Mover a carpeta"
    override val removeFromFolder = "Quitar de la carpeta"
    override val selectOrCreateFolder = "Selecciona o crea una carpeta"
    override val noFolderName = "Por favor introduce un nombre"
    override val chatsInFolder = "Chats en la carpeta"

    override val downloadFromUrl = "Descargar desde URL"
    override val modelUrlLabel = "URL del modelo"
    override val modelNameLabel = "Nombre del modelo"
    override val categoryLabel = "Categoría"

    override val aboutDescription = "Llamatik es una app de IA Kotlin Multiplatform construida sobre la librería open source Llamatik. Ofrece potente IA local en todas las plataformas compatibles — totalmente offline y con privacidad total, mediante una API Kotlin unificada."
    override val aboutPoweredByTitle = "Desarrollado con"
    override val aboutLibraryTitle = "Construido sobre la Librería Llamatik"
    override val aboutLicenseLabel = "Licencia MIT · Código abierto"
    override val aboutLlmEngine = "Inferencia LLM"
    override val aboutSttEngine = "Voz a texto"
    override val aboutImageEngine = "Generación de imágenes"
    override val aboutCorePkg = "Bindings de inferencia nativa"
    override val aboutSdkPkg = "AI SDK · Agent Framework · Assistant"

    override val onboardingWelcomeSubtitle = "IA en tu dispositivo. Privada. Sin conexión. Gratis."
    override val onboardingPrivateLabel = "Privado"
    override val onboardingOfflineLabel = "Sin conexión"
    override val onboardingOpenSourceLabel = "Código abierto"
    override val onboardingRunLLMsDescription = "Ejecuta potentes modelos de IA directamente en tu dispositivo — sin internet, sin enviar datos."
    override val onboardingAIToolkitTitle = "Todo lo que necesitas"
    override val onboardingAIToolkitDescription = "Chatea, analiza imágenes, transcribe audio y procesa documentos — todo con IA local."
    override val onboardingFeatureChat = "Chat"
    override val onboardingFeatureImages = "Imágenes"
    override val onboardingFeatureSpeech = "Voz"
    override val onboardingFeatureDocuments = "Documentos"
    override val onboardingBuiltForDevsTitle = "Transparente y confiable"
    override val onboardingBuiltForDevsDescription = "Llamatik es completamente de código abierto — cualquiera puede ver exactamente cómo funciona. Sin código oculto, sin recopilación secreta de datos."
    override val onboardingPrivacyControlTitle = "Tus datos, tus reglas"
    override val onboardingPrivacyControlDescription = "Todo se queda en tu dispositivo. Sin cuentas, sin nube, sin rastreo."
    override val onboardingPrivacyBullet1 = "Sin conexión tras la configuración"
    override val onboardingPrivacyBullet2 = "Tus datos nunca salen de tu dispositivo"
    override val onboardingPrivacyBullet3 = "Funciona sin Wi-Fi ni datos móviles"
    override val onboardingPrivacyBullet4 = "Sin suscripciones, sin registro"
    override val onboardingDownloadAIModelsTitle = "¡Ya casi!"
    override val onboardingDownloadAIModelsDescription = "Para empezar a chatear necesitas descargar un modelo de IA. Se ejecuta completamente sin conexión en tu dispositivo. El modelo predeterminado pesa unos 800 MB — puedes explorar otros en Ajustes más adelante."
    override val onboardingDownloadSizeInfo = "Descarga única (~800 MB)"
    override val onboardingDownloadSizeGuide = "Una vez descargado, el modelo se ejecuta completamente en tu dispositivo sin necesidad de internet."
    override val onboardingDownloadModelsButton = "Descargar modelo predeterminado"
    override val onboardingDoItLaterButton = "Más tarde"
    override val onboardingReadyTitle = "¡Todo listo!"
    override val onboardingReadySubtitle = "Empieza a explorar lo que la IA local puede hacer — todo se ejecuta en tu dispositivo."
    override val onboardingReadyBullet1 = "Usa la IA sin conexión, cuando quieras"
    override val onboardingReadyBullet2 = "Chatea, crea y explora"
    override val onboardingReadyBullet3 = "Tus datos permanecen privados"
    override val onboardingReadyBullet4 = "Gratuito y de código abierto"
    override val onboardingGetStartedButton = "Comenzar"
    override val onboardingSkip = "Omitir"
}
