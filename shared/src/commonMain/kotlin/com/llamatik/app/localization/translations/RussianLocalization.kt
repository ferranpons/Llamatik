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
}
