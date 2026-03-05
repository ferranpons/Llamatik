package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object PortugueseLocalization : Localization {

    override val appName = "Llamatik"

    override val actionSettings = "Configurações"
    override val next = "Próximo"
    override val close = "Fechar"
    override val previous = "Anterior"
    override val welcome = "Bem-vindo ao Llamatik"

    override val backLabel = "Voltar"
    override val topAppBarActionIconDescription = "Configurações"
    override val home = "Início"
    override val news = "Notícias"

    override val onBoardingStartButton = "Começar"
    override val onBoardingAlreadyHaveAnAccountButton = "Já tenho uma conta"
    override val searchItems = "Pesquisar"
    override val backButton = "voltar"
    override val search = "Pesquisar"
    override val noItemFound = "Item não encontrado"
    override val homeLastestNews = "Últimas Notícias"

    override val noResultsTitle = "Não há resultados no momento"
    override val noResultsDescription =
        "Tente novamente mais tarde. O serviço pode estar sobrecarregado."

    override val greetingMorning = "Bom dia"
    override val greetingAfternoon = "Boa tarde"
    override val greetingEvening = "Boa noite"
    override val greetingNight = "Boa noite"

    override val debugMenuTitle = "Menu de Debug"
    override val featureNotAvailableMessage =
        "Desculpe, este recurso não está disponível no momento."

    override val onboardingPromoTitle1 = "Execute LLMs Offline"
    override val onboardingPromoTitle2 = "Privado e Sem Nuvem"
    override val onboardingPromoTitle3 = "Controle Total Local"
    override val onboardingPromoTitle4 = "Open Source para Devs"

    override val onboardingPromoLine1 =
        "Llamatik traz IA poderosa no dispositivo para seus apps Kotlin Multiplatform — totalmente offline."

    override val onboardingPromoLine2 =
        "Construa chatbots e assistentes sem dependência de nuvem."

    override val onboardingPromoLine3 =
        "Use seus próprios modelos e mantenha controle total do seu stack de IA."

    override val onboardingPromoLine4 =
        "Projetado para desenvolvedores. Powered by llama.cpp."

    override val feedItemTitle = "Item"
    override val loading = "Carregando..."
    override val profileImageDescription = "Imagem de Perfil"
    override val manuals = "Manuais"
    override val guides = "Guias"
    override val workInProgress = "EM DESENVOLVIMENTO"
    override val dismiss = "fechar"
    override val onboarding = "Introdução"
    override val about = "Sobre"
    override val chooseLanguage = "Escolher idioma"
    override val change = "Alterar"
    override val language = "Idioma: "

    override val viewAll = "Ver tudo"
    override val welcomeToThe = "Bem-vindo ao "

    override val onboardingMainText = """
O Llamatik ChatBot é um assistente local experimental.

🔒 Aviso de Privacidade

Este chatbot funciona totalmente no seu dispositivo.
Nenhum dado é enviado para servidores externos.
"""

    override val actionContinue = "Continuar"
    override val settingUpLlamatik = "Configurando Llamatik…"
    override val downloadingMainModels = "Baixando modelos principais pela primeira vez."
    override val progress = "Progresso"
    override val me = "Eu"

    override val suggestion1 = "Criar um recibo simples"
    override val suggestion2 = "Escrever uma resposta educada"
    override val suggestion3 = "Resumo das últimas notícias"
    override val suggestion4 = "Dicas para vender online"
    override val suggestion5 = "Passos para criar uma fatura"
    override val suggestion6 = "História curta sobre floresta mágica"

    override val askMeAnything = "Pergunte algo…"
    override val stop = "Parar"
    override val send = "Enviar"
    override val noModelSelected = "nenhum modelo selecionado"
    override val current = "Atual"
    override val select = "Selecionar"
    override val delete = "Excluir"
    override val download = "Baixar"
    override val downloading = "Baixando…"
    override val generateModels = "Gerar Modelos"
    override val generationSettings = "Configurações de Geração"
    override val temperature = "Temperatura"
    override val maxTokens = "Máx Tokens"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "Penalidade de Repetição"
    override val apply = "Aplicar"
    override val downloadFinished = "Download concluído"

    override val defaultSystemPrompt = EnglishLocalization.defaultSystemPrompt
    override val gemma3SystemPrompt = EnglishLocalization.gemma3SystemPrompt
    override val smolVLM256SystemPrompt = EnglishLocalization.smolVLM256SystemPrompt
    override val smolVLM500SystemPrompt = EnglishLocalization.smolVLM500SystemPrompt
    override val qwen25BSystemPrompt = EnglishLocalization.qwen25BSystemPrompt
    override val phi15SystemPrompt = EnglishLocalization.phi15SystemPrompt
    override val llama32SystemPrompt = EnglishLocalization.llama32SystemPrompt

    override val assistant = "Assistente"
    override val user = "Usuário"
    override val system = "Sistema"
    override val relevantContext = "Contexto Relevante"
    override val defaultSystemPromptRendererMessage = EnglishLocalization.defaultSystemPromptRendererMessage

    override val copy = "Copiar"
    override val paste = "Colar"

    override val chatHistory = "Histórico"
    override val noChatsYet = "Nenhum chat ainda"
    override val temporaryChat = "Chat temporário"
    override val messages = "mensagens"
    override val temporaryChatExplanation =
        "Chat temporário ativado – esta conversa não será salva."
    override val voiceInput = "Entrada de voz"
    override val listening = "Escutando…"
    override val transcribing = "Transcrevendo…"
    override val embedModels = "Modelos de Embedding"
    override val sttModels = "Modelos de Voz"
    override val speak = "Falar"

    override val imageGenerationModels = "Modelos de geração de imagem"
    override val failedToDecodeImageError = "🖼️ Falha ao decodificar a imagem."
    override val imageGeneration = "Geração de imagem"
    override val textGeneration = "Geração de texto"
    override val embeddingModelNotLoaded = "Nenhum modelo de embedding carregado"
    override val noEmbeddingModelLoaded = "Modelo de embedding não carregado"
    override val recommended = "Recomendado"
    override val pdfSelectFile = "Por favor, selecione um arquivo PDF."
    override val pdfExtractionError = "Não foi possível extrair texto deste PDF. Se for um PDF digitalizado, é necessário OCR."
    override val pdfEmbedModelNeededWarning = "Para usar PDF RAG, baixe/carregue o modelo de embedding \"nomic-embed-text\" (Modelos de Embedding)."
    override val pdfNoUsableChunksError = "Nenhum trecho de texto utilizável foi gerado deste PDF."
    override val pdfFailedToComputeEmbeddingsError = "Falha ao calcular embeddings para este PDF. Recarregue o modelo de embedding e tente novamente."
    override val pdfIndexedForRAG = "✅ PDF indexado para RAG"
    override val pdfFailedToLoadPDFForRAG = "Falha ao carregar PDF para RAG"
    override val failedToComputeEmbeddings = "Falha ao calcular embeddings para sua pergunta. Recarregue o modelo de embedding e tente novamente."
    override val thereIsAProblemWithAI = "Há um problema com a IA"
    override val iDontHaveEnoughInfoInSources = "Não tenho informações suficientes nas minhas fontes."
    override val imageModeEnabledButNoModelLoadedError = "🖼️ O modo de imagem está ativado, mas nenhum modelo Stable Diffusion foi carregado. Abra Modelos e selecione um modelo SD."
    override val imageGenerationFailedError = "🖼️ Falha na geração de imagem (saída vazia)."
    override val imageGenerationError = "Erro na geração de imagem"
    override val allCachedModelsRemoved = "Todos os modelos em cache e o armazenamento PDF RAG foram removidos com sucesso."
    override val settings = "Configurações"
    override val removeAllDownloadedModels = "Remover todos os modelos baixados e o índice PDF RAG"
    override val clearCachedModelsDialogTitle = "Limpar todos os modelos em cache?"
    override val clearCachedModelsDialogMessage = "Isso excluirá todos os arquivos de modelo baixados e o índice PDF RAG persistido. Esta ação não pode ser desfeita."
    override val cancel = "Cancelar"
    override val clear = "Limpar"
}
