package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object DeutschLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Einstellungen"
    override val next = "Weiter"
    override val close = "Verschließen"
    override val previous = "Zurück"

    override val welcome = "Willkommen beim inoffiziellen DCS-Begleiter"

    override val backLabel = "Zurück"
    override val topAppBarActionIconDescription = "Einstellungen"
    override val home = "Startseite"
    override val news = "Hochladen"
    override val onBoardingStartButton = "Start"
    override val onBoardingAlreadyHaveAnAccountButton = "Ich habe bereits ein Konto"
    override val searchItems = "Nach Haustieren suchen"
    override val backButton = "Zurück"
    override val search = "Suche"
    override val noItemFound = "Artikel nicht gefunden"

    override val homeLastestNews = "Meine letzte Suche"
    override val noResultsTitle = "Es gibt derzeit keine Ergebnisse"
    override val noResultsDescription =
        "Versuchen Sie später erneut zu suchen. Es ist möglich, dass der Dienst derzeit stark ausgelastet ist. Entschuldigen Sie die Unannehmlichkeiten."

    override val greetingMorning = "Guten Morgen"
    override val greetingAfternoon = "Guten Tag"
    override val greetingEvening = "Guten Abend"
    override val greetingNight = "Gute Nacht"
    override val debugMenuTitle = "Debug-Menü"
    override val featureNotAvailableMessage =
        "Es tut uns leid, aber diese Funktion ist derzeit nicht verfügbar. Sie können die Handbücher und Leitfäden für jedes Modul auf der Modulseite finden."

    override val onboardingPromoTitle1 = "LLMs offline ausführen"
    override val onboardingPromoTitle2 = "Privat & ohne Cloud"
    override val onboardingPromoTitle3 = "Volle lokale Kontrolle"
    override val onboardingPromoTitle4 = "Open Source für Entwickler"

    override val onboardingPromoLine1 =
        "Llamatik bringt leistungsstarke On-Device-KI in deine Kotlin-Multiplatform-Apps — komplett offline und datenschutzfreundlich."

    override val onboardingPromoLine2 =
        "Erstelle intelligente Chatbots, Assistenten und Co-Piloten ohne Cloud-Abhängigkeiten oder Netzwerklatenz."

    override val onboardingPromoLine3 =
        "Nutze eigene Modelle, verwalte deine Vektordatenbank und behalte volle Kontrolle über deinen LLM-Stack — alles in Kotlin."

    override val onboardingPromoLine4 =
        "Entwicklerfreundlich. Betrieben mit llama.cpp. Llamatik ist Open Source und bereit für die Zukunft lokaler KI."

    override val feedItemTitle = "Feed-Element"

    override val loading = "Wird geladen..."
    override val profileImageDescription = "Profilbild"
    override val manuals = "Handbücher"
    override val guides = "Anleitungen"
    override val workInProgress = "IN ARBEIT"
    override val dismiss = "schließen"
    override val onboarding = "Einführung"
    override val about = "Über uns"
    override val chooseLanguage = "Sprache wählen"
    override val change = "Ändern"
    override val language = "Sprache: "
}
