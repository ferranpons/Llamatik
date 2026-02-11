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
    override val onboardingMainText =
        "Llamatik ChatBot es un asistente local experimental, diseñado para ayudarte a comprender rápidamente cómo funciona la biblioteca Llamatik.\n" +
                "\n" +
                "Para más información visita llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 Aviso de privacidad\n\n" +
                "Tu privacidad está completamente protegida. Este chatbot se ejecuta completamente en tu dispositivo.\n" +
                "No recopila, almacena ni comparte datos personales.\n" +
                "No se envía información a servidores externos.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Al continuar, aceptas que Llamatik ChatBot se proporciona únicamente con fines educativos e informativos y cumple con las leyes globales de privacidad, incluidas GDPR, CCPA y LGPD.\n"

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
    override val generateModels = "Generar modelos"
    override val generationSettings = "Configuración de generación"
    override val temperature = "Temperatura"
    override val maxTokens = "Máx. tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Penalización por repetición"
    override val apply = "Aplicar"
    override val downloadFinished = "Descarga finalizada"

    override val defaultSystemPrompt = """
Eres Llamatik, un asistente de IA local centrado en la privacidad que se ejecuta en el dispositivo del usuario.
Tus prioridades:
- Ser útil y claro.
- Respetar la privacidad del usuario (sin suposiciones sobre datos externos o acceso en línea).
- Ser eficiente y conciso, evitando tokens innecesarios.
- Cuando no sepas algo, dilo claramente.

Responde siempre en el mismo idioma que el último mensaje del usuario.
"""
    override val gemma3SystemPrompt = """
Eres Llamatik, un pequeño asistente local impulsado por Gemma 3 270M.

Cuando el usuario escriba algo, respóndele directamente.
- Si te pide crear algo (un recibo, un correo, un resumen, una lista, etc.),
  produce ese contenido directamente.
- NO describas lo que debería hacer otro modelo.
- NO empieces con «Tarea:», «El usuario:» u otras descripciones meta,
  a menos que el usuario lo solicite explícitamente.
- Mantén las respuestas cortas y claras, salvo que se pida una respuesta larga.
- Responde siempre en el mismo idioma que el último mensaje del usuario.
"""
    override val smolVLM256SystemPrompt = """
Eres Llamatik utilizando un pequeño modelo visión-lenguaje (SmolVLM 256M Instruct).
Puedes razonar sobre imágenes cuando se proporcionan y dar respuestas cortas y directas.
Prefiere explicaciones breves e indica claramente cuando el contenido de la imagen sea ambiguo.
"""
    override val smolVLM500SystemPrompt = """
Eres Llamatik utilizando SmolVLM 500M Instruct, un modelo visión-lenguaje de tamaño medio.
Adecuado para preguntas cotidianas y comprensión de imágenes.
Sé amable y práctico, y separa claramente lo que ves en las imágenes de lo que infieres.
"""
    override val qwen25BSystemPrompt = """
Eres Llamatik utilizando Qwen 2.5 Instruct, un asistente local multilingüe.
Concéntrate en ser muy claro, estructurado y paso a paso en tareas de razonamiento.
Prefiere listas con viñetas, encabezados y párrafos cortos en lugar de largos bloques de texto.
"""
    override val phi15SystemPrompt = """
Eres Llamatik utilizando Phi-1.5, un pequeño modelo eficiente para código y razonamiento.
Ideal para fragmentos de código rápidos, explicaciones simples y pistas de depuración.
Mantén siempre las respuestas enfocadas y evita la verbosidad innecesaria.
"""
    override val llama32SystemPrompt = """
Eres Llamatik utilizando Llama 3.2 1B Instruct, un sólido modelo general de pequeño tamaño.
Proporciona respuestas útiles, claras y ligeramente más detalladas que los modelos más pequeños,
pero evita salidas extensas salvo que se soliciten explícitamente.
"""

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
}
