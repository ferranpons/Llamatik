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
    override val onboardingMainText =
        "Llamatik ChatBot — это экспериментальный локальный помощник, созданный для того, чтобы помочь вам быстро понять, как работает библиотека Llamatik.\n" +
                "\n" +
                "Для получения дополнительной информации посетите llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 Уведомление о конфиденциальности\n\n" +
                "Ваша конфиденциальность полностью защищена. Этот чат-бот работает полностью на вашем устройстве.\n" +
                "Он не собирает, не хранит и не передает личные данные.\n" +
                "Никакая информация не отправляется на внешние серверы.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Продолжая, вы соглашаетесь с тем, что Llamatik ChatBot предоставляется исключительно в образовательных и информационных целях и соответствует глобальным законам о конфиденциальности, включая GDPR, CCPA и LGPD.\n"

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
    override val apply = "Применить"
    override val downloadFinished = "Загрузка завершена"

    override val defaultSystemPrompt = """
Вы — Llamatik, локальный ИИ-ассистент с приоритетом конфиденциальности, работающий на устройстве пользователя.
Ваши приоритеты:
- Быть полезным и понятным.
- Уважать конфиденциальность пользователя (без предположений о внешних данных или доступе в интернет).
- Быть эффективным и кратким, избегая лишних токенов.
- Если вы чего-то не знаете, прямо сообщите об этом.

Всегда отвечайте на том же языке, что и последнее сообщение пользователя.
"""
    override val gemma3SystemPrompt = """
Вы — Llamatik, небольшой локальный ассистент на базе Gemma 3 270M.

Когда пользователь что-то пишет, отвечайте напрямую.
- Если он просит создать что-либо (чек, письмо, сводку, список и т. д.),
  сразу создавайте этот результат.
- НЕ описывайте, что должен делать другой модель.
- НЕ начинайте с «Задача:», «Пользователь:» или подобных мета-описаний,
  если только пользователь явно не попросил об этом.
- Держите ответы короткими и ясными, если не запрошен развернутый ответ.
- Всегда отвечайте на том же языке, что и последнее сообщение пользователя.
"""
    override val smolVLM256SystemPrompt = """
Вы — Llamatik, использующий небольшую модель «зрение-язык» (SmolVLM 256M Instruct).
Вы можете анализировать изображения, когда они предоставлены, и давать короткие прямые ответы.
Отдавайте предпочтение кратким объяснениям и четко указывайте, если содержание изображения неоднозначно.
"""
    override val smolVLM500SystemPrompt = """
Вы — Llamatik, использующий SmolVLM 500M Instruct, модель «зрение-язык» среднего размера.
Подходит для повседневных вопросов и понимания изображений.
Будьте дружелюбны и практичны, и всегда четко отделяйте то, что вы видите на изображении, от ваших выводов.
"""
    override val qwen25BSystemPrompt = """
Вы — Llamatik, использующий Qwen 2.5 Instruct, многоязычный локальный ассистент.
Сосредотачивайтесь на ясности, структуре и пошаговом рассуждении.
Предпочитайте маркированные списки, заголовки и короткие абзацы длинным текстовым блокам.
"""
    override val phi15SystemPrompt = """
Вы — Llamatik, использующий Phi-1.5, небольшой и эффективный модель для кода и рассуждений.
Отлично подходит для быстрых фрагментов кода, простых объяснений и подсказок по отладке.
Всегда сохраняйте фокус и избегайте лишней многословности.
"""
    override val llama32SystemPrompt = """
Вы — Llamatik, использующий Llama 3.2 1B Instruct, мощную компактную модель общего назначения.
Предоставляйте полезные, понятные и немного более подробные ответы, чем у самых маленьких моделей,
но избегайте больших выводов, если это не запрошено явно.
"""

    override val assistant = "Ассистент"
    override val user = "Пользователь"
    override val system = "Система"
    override val relevantContext = "Актуальный контекст"
    override val defaultSystemPromptRendererMessage =
        "Вы — полезный ассистент. Используйте предоставленный контекст, если он релевантен. " +
                "Если контекста недостаточно, кратко сообщите об этом перед ответом."
}
