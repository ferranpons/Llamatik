package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object RussianLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Настройки"
    override val next = "Далее"
    override val close = "Закрыть"
    override val previous = "Назад"

    override val welcome = "Добро пожаловать в Llamatik"
    override val backLabel = "Назад"
    override val topAppBarActionIconDescription = "Настройки"
    override val home = "Главная"
    override val news = "Новости"
    override val onBoardingStartButton = "Начать"
    override val onBoardingAlreadyHaveAnAccountButton = "У меня есть аккаунт"
    override val searchItems = "Искать домашних животных"
    override val backButton = "Назад"
    override val search = "Поиск"
    override val noItemFound = "Элемент не найден"
    override val homeLastestNews = "Последние новости"
    override val noResultsTitle = "Результаты не найдены"
    override val noResultsDescription =
        "Попробуйте поискать позже, возможно, сервис перегружен. Извините за неудобства."

    override val greetingMorning = "Доброе утро"
    override val greetingAfternoon = "Добрый день"
    override val greetingEvening = "Добрый вечер"
    override val greetingNight = "Доброй ночи"

    override val debugMenuTitle = "Меню отладки"
    override val featureNotAvailableMessage =
        "К сожалению, эта функция сейчас недоступна. Вы можете найти руководства и инструкции для каждого модуля на вкладке 'Модули'."

    override val onboardingPromoTitle1 = "Запуск LLM в оффлайне"
    override val onboardingPromoTitle2 = "Конфиденциально, без облака"
    override val onboardingPromoTitle3 = "Полный локальный контроль"
    override val onboardingPromoTitle4 = "Опен-сорс для разработчиков"

    override val onboardingPromoLine1 =
        "Llamatik приносит мощный офлайн-ИИ в ваши Kotlin Multiplatform приложения — полностью автономно и конфиденциально."

    override val onboardingPromoLine2 =
        "Создавайте интеллектуальных помощников, чат-ботов и автопилотов без зависимости от облака и сетевых задержек."

    override val onboardingPromoLine3 =
        "Используйте собственные модели и векторные хранилища, контролируйте весь стек LLM — прямо из Kotlin."

    override val onboardingPromoLine4 =
        "Создано для разработчиков. Основано на llama.cpp. Llamatik — это open-source платформа для локального ИИ на мобильных и десктопных устройствах."

    override val feedItemTitle = "Элемент ленты"
    override val loading = "Загрузка..."
    override val profileImageDescription = "Изображение профиля"
    override val manuals = "Руководства"
    override val guides = "Инструкции"
    override val workInProgress = "РАБОТА В ПРОЦЕССЕ"
    override val dismiss = "закрыть"
    override val onboarding = "Введение"
    override val about = "О приложении"
    override val chooseLanguage = "Выбрать язык"
    override val change = "Изменить"
    override val language = "Язык: "

    override val viewAll = "Показать все"
    override val welcomeToThe = "Добро пожаловать в "
    override val onboardingMainText = "Llamatik — это приватный AI-ассистент, работающий прямо на вашем устройстве, который помогает общаться, исследовать идеи и выполнять задачи — без использования облака.\n" +
            "\n" +
            "Все работает локально на вашем устройстве, давая вам полный контроль над данными и снижая энергопотребление удалённых серверов.\n" +
            "\n" +
            "Для получения дополнительной информации посетите llamatik.com" +
            "\n" +
            "\n" +
            "\uD83D\uDD10 Уведомление о конфиденциальности\n\n" +
            "Ваша конфиденциальность полностью защищена. Приложение работает исключительно на вашем устройстве.\n" +
            "Никакие личные данные не собираются, не хранятся и не передаются.\n" +
            "Никакая информация не отправляется на внешние серверы.\n" +
            "\n" +
            "---\n" +
            "\n" +
            "Продолжая, вы подтверждаете, что Llamatik предоставляется как локальный AI-ассистент и соответствует мировым стандартам конфиденциальности, включая GDPR, CCPA и LGPD.\n" +
            "\n"

    override val actionContinue = "Продолжить"
    override val settingUpLlamatik = "Настройка Llamatik…"
    override val downloadingMainModels =
        "Загрузка основных моделей в первый раз.\nЭто может занять несколько минут."
    override val progress = "Прогресс"
    override val me = "Я"

    override val suggestion1 = "Создать простой чек для продажи игровой консоли"
    override val suggestion2 = "Составить вежливый ответ на просьбу о скидке"
    override val suggestion3 = "Предоставить краткий обзор последних мировых новостей"
    override val suggestion4 = "Создать список советов по продаже товаров онлайн"
    override val suggestion5 = "Дай мне список шагов для подготовки простого счета"
    override val suggestion6 = "Написать короткий рассказ о волшебном лесу"
    override val askMeAnything = "Спроси меня о чем угодно…"
    override val stop = "Стоп"
    override val send = "Отправить"
    override val noModelSelected = "модель не выбрана"
    override val current = "Текущий"
    override val select = "Выбрать"
    override val delete = "Удалить"
    override val download = "Скачать"
    override val downloading = "Загрузка…"
    override val generateModels = "Создать модели"
    override val generationSettings = "Настройки генерации"
    override val temperature = "Температура"
    override val maxTokens = "Макс. токенов"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Штраф за повторение"
    override val contextLength = "Длина контекста"
    override val numThreads = "Потоки"
    override val useMmap = "Отображение памяти (mmap)"
    override val flashAttention = "Flash Attention"
    override val batchSize = "Размер пакета"
    override val apply = "Применить"
    override val downloadFinished = "Загрузка завершена"

    override val defaultSystemPrompt = """
Вы — локальный ИИ-ассистент, работающий полностью на устройстве пользователя.
Ваши приоритеты:
- Быть полезным и понятным.
- Уважать конфиденциальность пользователя (без предположений о внешних данных или доступе в интернет).
- Быть эффективным и кратким, избегая лишних токенов.
- Если вы чего-то не знаете, прямо сообщите об этом.
    """
    override val smolVLM256SystemPrompt = "Вы — компактный визуально-языковой ассистент. Опишите изображение и кратко ответьте на вопросы о нём."
    override val smolVLM500SystemPrompt = "Вы — визуально-языковой ассистент. Проанализируйте предоставленное изображение и чётко ответьте на вопросы."

    override val assistant = "Ассистент"
    override val user = "Пользователь"
    override val system = "Система"
    override val relevantContext = "Актуальный контекст"
    override val defaultSystemPromptRendererMessage =
        "Вы — полезный ассистент. Используйте предоставленный контекст, если он релевантен. " +
                "Если контекста недостаточно, кратко сообщите об этом перед ответом."

    override val copy = "Копировать"
    override val paste = "Вставить"

    override val chatHistory = "История чатов"
    override val noChatsYet = "Пока нет чатов"
    override val temporaryChat = "Временный чат"
    override val messages = "сообщений"
    override val temporaryChatExplanation = "Включён временный чат — этот диалог не будет сохранён на устройстве."
    override val voiceInput = "Голосовой ввод"
    override val listening = "Идёт запись…"
    override val transcribing = "Выполняется расшифровка…"
    override val embedModels = "Модели эмбеддингов"
    override val sttModels = "Модели распознавания речи"
    override val speak = "Озвучить"

    override val vlmModels = "Модели зрения"
    override val imageGenerationModels = "Модели генерации изображений"
    override val failedToDecodeImageError = "🖼️ Не удалось декодировать изображение."
    override val imageGeneration = "Генерация изображений"
    override val textGeneration = "Генерация текста"
    override val embeddingModelNotLoaded = "Модель эмбеддингов не загружена"
    override val noEmbeddingModelLoaded = "Модель эмбеддингов не загружена"
    override val recommended = "Рекомендуется"
    override val pdfSelectFile = "Пожалуйста, выберите PDF-файл."
    override val pdfExtractionError = "Не удалось извлечь текст из этого PDF. Если это сканированный PDF, требуется OCR."
    override val pdfEmbedModelNeededWarning = "Чтобы использовать PDF RAG, загрузите/подключите модель эмбеддингов \"nomic-embed-text\" (Модели эмбеддингов)."
    override val pdfNoUsableChunksError = "Из этого PDF не удалось создать пригодные текстовые фрагменты."
    override val pdfFailedToComputeEmbeddingsError = "Не удалось вычислить эмбеддинги для этого PDF. Перезагрузите модель эмбеддингов и попробуйте снова."
    override val pdfIndexedForRAG = "✅ PDF проиндексирован для RAG"
    override val pdfFailedToLoadPDFForRAG = "Не удалось загрузить PDF для RAG"
    override val failedToComputeEmbeddings = "Не удалось вычислить эмбеддинги для вашего вопроса. Перезагрузите модель эмбеддингов и попробуйте снова."
    override val thereIsAProblemWithAI = "Возникла проблема с ИИ"
    override val iDontHaveEnoughInfoInSources = "У меня недостаточно информации в источниках."
    override val imageModeEnabledButNoModelLoadedError = "🖼️ Режим изображения включён, но модель Stable Diffusion не загружена. Откройте раздел «Модели» и выберите модель SD."
    override val visionModeEnabledButNoModelLoadedError = "👁️ Режим зрения включён, но VLM-модель не загружена. Откройте раздел «Модели» и выберите модель зрения."
    override val imageGenerationFailedError = "🖼️ Ошибка генерации изображения (пустой результат)."
    override val imageGenerationError = "Ошибка генерации изображения"
    override val allCachedModelsRemoved = "Все кэшированные модели и хранилище PDF RAG успешно удалены."
    override val settings = "Настройки"
    override val removeAllDownloadedModels = "Удалить все загруженные модели и индекс PDF RAG"
    override val clearCachedModelsDialogTitle = "Очистить все кэшированные модели?"
    override val clearCachedModelsDialogMessage = "Это удалит все загруженные файлы моделей и сохранённый индекс PDF RAG. Это действие нельзя отменить."
    override val cancel = "Отмена"
    override val clear = "Очистить"
}
