package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object FrenchLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "Paramètres"
    override val next = "Suivant"
    override val close = "Fermer"
    override val previous = "Précédent"

    override val welcome = "Bienvenue dans le Llamatik"
    override val backLabel = "Retour"
    override val topAppBarActionIconDescription = "Paramètres"
    override val home = "Accueil"
    override val news = "Télécharger"
    override val onBoardingStartButton = "Démarrer"
    override val onBoardingAlreadyHaveAnAccountButton = "J'ai un compte"
    override val searchItems = "Rechercher des animaux de compagnie"
    override val backButton = "retour"
    override val search = "Rechercher"
    override val noItemFound = "Aucun élément trouvé"
    override val homeLastestNews = "Ma dernière recherche"
    override val noResultsTitle = "Il n'y a pas de résultats pour le moment"
    override val noResultsDescription =
        "Essayez de refaire une recherche plus tard. Il est possible que le service soit fortement sollicité actuellement. Désolé pour la gêne occasionnée."

    override val greetingMorning = "Bonjour"
    override val greetingAfternoon = "Bonne après-midi"
    override val greetingEvening = "Bonsoir"
    override val greetingNight = "Bonne nuit"

    override val debugMenuTitle = "Menu de Débogage"
    override val featureNotAvailableMessage =
        "Nous sommes désolés, mais cette fonctionnalité n'est pas disponible pour le moment. Vous pouvez trouver les manuels et guides pour chaque module dans l'onglet modules."

    override val onboardingPromoTitle1 = "Exécutez des LLM hors ligne"
    override val onboardingPromoTitle2 = "Confidentiel et sans cloud"
    override val onboardingPromoTitle3 = "Contrôle local complet"
    override val onboardingPromoTitle4 = "Open source pour les devs"

    override val onboardingPromoLine1 =
        "Llamatik apporte une IA puissante et locale à vos apps Kotlin Multiplatform — totalement hors ligne et respectueuse de la vie privée."

    override val onboardingPromoLine2 =
        "Créez des chatbots, copilotes et assistants intelligents sans dépendre du cloud ni subir de latence réseau."

    override val onboardingPromoLine3 =
        "Utilisez vos propres modèles, hébergez vos vecteurs et gardez le contrôle total de votre pile LLM — tout en Kotlin."

    override val onboardingPromoLine4 =
        "Pensé pour les développeurs. Propulsé par llama.cpp. Llamatik est open source et prêt à transformer l’IA locale sur mobile et desktop."

    override val feedItemTitle = "Feed Item"

    override val loading = "Chargement..."
    override val profileImageDescription = "Image de profil"
    override val manuals = "Manuels"
    override val guides = "Guides"
    override val workInProgress = "TRAVAIL EN COURS"
    override val dismiss = "rejeter"
    override val onboarding = "Accueil"
    override val about = "À propos"
    override val chooseLanguage = "Choisir la langue"
    override val change = "Changer"
    override val language = "Langue : "

    override val viewAll = "Tout voir"
    override val welcomeToThe = "Bienvenue sur "
    override val onboardingMainText =
        "Llamatik ChatBot est un assistant local expérimental, conçu pour vous aider à comprendre rapidement le fonctionnement de la bibliothèque Llamatik.\n" +
                "\n" +
                "Pour plus d’informations, veuillez visiter llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 Avis de confidentialité\n\n" +
                "Votre vie privée est entièrement protégée. Ce chatbot fonctionne entièrement sur votre appareil.\n" +
                "Il ne collecte, ne stocke ni ne partage aucune donnée personnelle.\n" +
                "Aucune information n’est envoyée à des serveurs externes.\n" +
                "\n" +
                "---\n" +
                "\n" +
                "En continuant, vous acceptez que Llamatik ChatBot soit fourni uniquement à des fins éducatives et informatives, et qu’il respecte les lois mondiales sur la protection de la vie privée, notamment le RGPD, le CCPA et la LGPD.\n"

    override val actionContinue = "Continuer"
    override val settingUpLlamatik = "Configuration de Llamatik…"
    override val downloadingMainModels =
        "Téléchargement des modèles principaux pour la première fois.\nCela peut prendre quelques minutes."
    override val progress = "Progression"
    override val me = "Moi"

    override val suggestion1 = "Créer un reçu simple pour la vente d’une console de jeux vidéo"
    override val suggestion2 = "Rédiger une réponse polie à une demande de remise"
    override val suggestion3 = "Fournir un bref aperçu de l’actualité mondiale récente"
    override val suggestion4 = "Créer une liste de conseils pour vendre des articles en ligne"
    override val suggestion5 = "Donne-moi une liste d’étapes pour préparer une facture simple"
    override val suggestion6 = "Écrire une courte histoire sur une forêt magique"
    override val askMeAnything = "Demande-moi quelque chose…"
    override val stop = "Arrêter"
    override val send = "Envoyer"
    override val noModelSelected = "aucun modèle sélectionné"
    override val current = "Actuel"
    override val select = "Sélectionner"
    override val delete = "Supprimer"
    override val download = "Télécharger"
    override val downloading = "Téléchargement…"
    override val generateModels = "Générer des modèles"
    override val generationSettings = "Paramètres de génération"
    override val temperature = "Température"
    override val maxTokens = "Nombre max de tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Pénalité de répétition"
    override val apply = "Appliquer"
    override val downloadFinished = "Téléchargement terminé"

    override val defaultSystemPrompt = """
Vous êtes Llamatik, un assistant IA local axé sur la confidentialité, fonctionnant sur l’appareil de l’utilisateur.
Vos priorités :
- Être utile et clair.
- Respecter la confidentialité de l’utilisateur (aucune supposition sur des données externes ou un accès en ligne).
- Être efficace et concis, en évitant les tokens inutiles.
- Lorsque vous ne savez pas quelque chose, dites-le clairement.

Répondez toujours dans la même langue que le dernier message de l’utilisateur.
"""
    override val gemma3SystemPrompt = """
Vous êtes Llamatik, un petit assistant local fonctionnant avec Gemma 3 270M.

Lorsque l’utilisateur écrit quelque chose, répondez-lui directement.
- S’il vous demande de créer quelque chose (un reçu, un e-mail, un résumé, une liste, etc.),
  produisez directement ce contenu.
- Ne décrivez PAS ce qu’un autre modèle devrait faire.
- Ne commencez PAS par « Tâche : », « L’utilisateur : » ou des descriptions similaires,
  sauf si l’utilisateur le demande explicitement.
- Gardez des réponses courtes et claires, sauf si une réponse longue est demandée.
- Répondez toujours dans la même langue que le dernier message de l’utilisateur.
"""
    override val smolVLM256SystemPrompt = """
Vous êtes Llamatik utilisant un petit modèle vision-langage (SmolVLM 256M Instruct).
Vous pouvez raisonner à partir d’images lorsqu’elles sont fournies et donner des réponses courtes et directes.
Privilégiez des explications brèves et indiquez clairement lorsque le contenu de l’image est ambigu.
"""
    override val smolVLM500SystemPrompt = """
Vous êtes Llamatik utilisant SmolVLM 500M Instruct, un modèle vision-langage de taille intermédiaire.
Vous êtes adapté aux questions du quotidien et à la compréhension d’images.
Soyez amical et pratique, et séparez toujours clairement ce que vous voyez dans les images de ce que vous en déduisez.
"""
    override val qwen25BSystemPrompt = """
Vous êtes Llamatik utilisant Qwen 2.5 Instruct, un assistant local multilingue.
Concentrez-vous sur la clarté, la structure et le raisonnement étape par étape.
Privilégiez les listes à puces, les titres et les paragraphes courts plutôt que de longs blocs de texte.
"""
    override val phi15SystemPrompt = """
Vous êtes Llamatik utilisant Phi-1.5, un petit modèle efficace pour le code et le raisonnement.
Idéal pour des extraits de code rapides, des explications simples et des conseils de débogage.
Gardez toujours les réponses ciblées et évitez toute verbosité inutile.
"""
    override val llama32SystemPrompt = """
Vous êtes Llamatik utilisant Llama 3.2 1B Instruct, un modèle généraliste compact et performant.
Fournissez des réponses utiles, claires et légèrement plus détaillées que celles des plus petits modèles,
tout en évitant les sorties volumineuses sauf demande explicite.
"""

    override val assistant = "Assistant"
    override val user = "Utilisateur"
    override val system = "Système"
    override val relevantContext = "Contexte pertinent"
    override val defaultSystemPromptRendererMessage =
        "Vous êtes un assistant utile. Utilisez le contexte fourni s’il est pertinent. " +
                "Si le contexte est insuffisant, indiquez-le brièvement avant de répondre."

    override val copy = "Copier"
    override val paste = "Coller"

    override val chatHistory = "Historique des chats"
    override val noChatsYet = "Aucune discussion pour l’instant"
    override val temporaryChat = "Chat temporaire"
    override val messages = "messages"
    override val temporaryChatExplanation = "Le chat temporaire est activé : cette conversation ne sera pas enregistrée sur votre appareil."
}
