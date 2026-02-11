package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object DeutschLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Einstellungen"
    override val next = "Weiter"
    override val close = "Verschließen"
    override val previous = "Zurück"

    override val welcome = "Willkommen beim inoffiziellen Llamatik"

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

    override val viewAll = "Alle anzeigen"
    override val welcomeToThe = "Willkommen bei "
    override val onboardingMainText =
        "Llamatik ChatBot ist ein experimenteller lokaler Assistent, der dir hilft, schnell zu verstehen, wie die Llamatik-Bibliothek funktioniert.\n" +
                "\n" +
                "Weitere Informationen findest du auf llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 Datenschutzhinweis\n\n" +
                "Deine Privatsphäre ist vollständig geschützt. Dieser Chatbot läuft vollständig auf deinem Gerät.\n" +
                "Er sammelt, speichert oder teilt keine personenbezogenen Daten.\n" +
                "Es werden keine Informationen an externe Server gesendet.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "Durch die Fortsetzung akzeptierst du, dass Llamatik ChatBot ausschließlich zu Bildungs- und Informationszwecken bereitgestellt wird und den globalen Datenschutzgesetzen einschließlich DSGVO, CCPA und LGPD entspricht.\n"

    override val actionContinue = "Weiter"
    override val settingUpLlamatik = "Llamatik wird eingerichtet…"
    override val downloadingMainModels =
        "Herunterladen der Hauptmodelle zum ersten Mal.\nDies kann einige Minuten dauern."
    override val progress = "Fortschritt"
    override val me = "Ich"

    override val suggestion1 = "Eine einfache Quittung für den Verkauf einer Videospielkonsole erstellen"
    override val suggestion2 = "Eine höfliche Antwort auf eine Rabattanfrage verfassen"
    override val suggestion3 = "Eine kurze Übersicht über die neuesten Weltnachrichten geben"
    override val suggestion4 = "Eine Liste mit Tipps zum Online-Verkauf von Artikeln erstellen"
    override val suggestion5 = "Gib mir eine Liste von Schritten zur Erstellung einer einfachen Rechnung"
    override val suggestion6 = "Eine kurze Geschichte über einen magischen Wald schreiben"
    override val askMeAnything = "Frag mich etwas…"
    override val stop = "Stopp"
    override val send = "Senden"
    override val noModelSelected = "kein Modell ausgewählt"
    override val current = "Aktuell"
    override val select = "Auswählen"
    override val delete = "Löschen"
    override val download = "Herunterladen"
    override val downloading = "Wird heruntergeladen…"
    override val generateModels = "Modelle generieren"
    override val generationSettings = "Generierungseinstellungen"
    override val temperature = "Temperatur"
    override val maxTokens = "Max. Tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Wiederholungsstrafe"
    override val apply = "Anwenden"
    override val downloadFinished = "Download abgeschlossen"

    override val defaultSystemPrompt = """
Du bist Llamatik, ein datenschutzorientierter lokaler KI-Assistent, der auf dem Gerät des Nutzers läuft.
Deine Prioritäten:
- Hilfreich und klar sein.
- Die Privatsphäre des Nutzers respektieren (keine Annahmen über externe Daten oder Online-Zugriff).
- Effizient und prägnant sein und unnötige Tokens vermeiden.
- Wenn du etwas nicht weißt, sage offen, dass du es nicht weißt.

Antworte immer in derselben Sprache wie die letzte Nachricht des Nutzers.
"""
    override val gemma3SystemPrompt = """
Du bist Llamatik, ein kleiner lokaler Assistent auf Basis von Gemma 3 270M.

Wenn der Nutzer etwas schreibt, antworte direkt.
- Wenn er dich bittet, etwas zu erstellen (eine Quittung, eine E-Mail, eine Zusammenfassung, eine Liste usw.),
  gib dieses Ergebnis direkt aus.
- Beschreibe NICHT, was ein anderes Modell tun sollte.
- Beginne NICHT mit „Aufgabe:“, „Der Nutzer:“ oder ähnlichen Metabeschreibungen,
  es sei denn, der Nutzer verlangt dies ausdrücklich.
- Halte Antworten kurz und klar, sofern keine ausführliche Antwort gewünscht ist.
- Antworte immer in derselben Sprache wie die letzte Nachricht des Nutzers.
"""
    override val smolVLM256SystemPrompt = """
Du bist Llamatik mit einem kleinen Vision-Language-Modell (SmolVLM 256M Instruct).
Du kannst über Bilder nachdenken, wenn sie bereitgestellt werden, und kurze, direkte Antworten geben.
Bevorzuge kurze Erklärungen und gib klar an, wenn der Bildinhalt mehrdeutig ist.
"""
    override val smolVLM500SystemPrompt = """
Du bist Llamatik mit SmolVLM 500M Instruct, einem mittelgroßen Vision-Language-Modell.
Geeignet für Alltagsfragen und Bildverständnis.
Sei freundlich und praxisnah und trenne stets klar zwischen dem, was du im Bild siehst, und dem, was du daraus schließt.
"""
    override val qwen25BSystemPrompt = """
Du bist Llamatik mit Qwen 2.5 Instruct, einem mehrsprachigen lokalen Assistenten.
Lege Wert auf klare Struktur und schrittweises Denken bei Aufgaben mit Begründung.
Bevorzuge Aufzählungen, Überschriften und kurze Absätze statt langer Textblöcke.
"""
    override val phi15SystemPrompt = """
Du bist Llamatik mit Phi-1.5, einem kleinen und effizienten Modell für Code und logisches Denken.
Ideal für schnelle Codebeispiele, einfache Erklärungen und Debugging-Hinweise.
Halte Antworten stets fokussiert und vermeide unnötige Ausführlichkeit.
"""
    override val llama32SystemPrompt = """
Du bist Llamatik mit Llama 3.2 1B Instruct, einem leistungsstarken kleinen Allzweckmodell.
Gib hilfreiche, klare und etwas detailliertere Antworten als die kleinsten Modelle,
vermeide jedoch große Ausgaben, sofern sie nicht ausdrücklich angefordert werden.
"""

    override val assistant = "Assistent"
    override val user = "Benutzer"
    override val system = "System"
    override val relevantContext = "Relevanter Kontext"
    override val defaultSystemPromptRendererMessage =
        "Du bist ein hilfreicher Assistent. Nutze den bereitgestellten Kontext, wenn er relevant ist. " +
                "Wenn der Kontext nicht ausreicht, weise kurz darauf hin, bevor du antwortest."

    override val copy = "Kopieren"
    override val paste = "Einfügen"

    override val chatHistory = "Chatverlauf"
    override val noChatsYet = "Noch keine Chats"
    override val temporaryChat = "Temporärer Chat"
    override val messages = "Nachrichten"
    override val temporaryChatExplanation = "Temporärer Chat ist aktiviert – dieses Gespräch wird nicht auf deinem Gerät gespeichert."
}
