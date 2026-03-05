package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object JapaneseLocalization : Localization {

    override val appName = "Llamatik"

    override val actionSettings = "設定"
    override val next = "次へ"
    override val close = "閉じる"
    override val previous = "前へ"

    override val welcome = "Llamatikへようこそ"

    override val backLabel = "戻る"
    override val topAppBarActionIconDescription = "設定"
    override val home = "ホーム"
    override val news = "ニュース"

    override val onBoardingStartButton = "開始"
    override val onBoardingAlreadyHaveAnAccountButton = "アカウントを持っています"
    override val searchItems = "ペットを検索"
    override val backButton = "戻る"
    override val search = "検索"
    override val noItemFound = "見つかりませんでした"
    override val homeLastestNews = "最新ニュース"

    override val noResultsTitle = "現在、結果がありません"
    override val noResultsDescription =
        "後でもう一度試してください。現在サービスが混雑している可能性があります。ご不便をおかけして申し訳ありません。"

    override val greetingMorning = "おはようございます"
    override val greetingAfternoon = "こんにちは"
    override val greetingEvening = "こんばんは"
    override val greetingNight = "おやすみなさい"

    override val debugMenuTitle = "デバッグメニュー"
    override val featureNotAvailableMessage =
        "申し訳ありません。この機能は現在利用できません。各モジュールの詳細（モジュールタブ）からマニュアルとガイドをご確認ください。"

    override val onboardingPromoTitle1 = "LLMをオフラインで実行"
    override val onboardingPromoTitle2 = "プライベート＆クラウド不要"
    override val onboardingPromoTitle3 = "完全なローカル制御"
    override val onboardingPromoTitle4 = "開発者向けオープンソース"

    override val onboardingPromoLine1 =
        "Llamatikは、Kotlin Multiplatformアプリに強力なオンデバイスAIを提供します — 完全オフラインで、プライバシーに配慮し、高速です。"

    override val onboardingPromoLine2 =
        "クラウド依存やネットワーク遅延なしで、賢いチャットボットやコパイロット、アシスタントを構築できます。"

    override val onboardingPromoLine3 =
        "独自モデルやベクターストアを利用し、LLMスタックをすべてKotlinで完全にコントロールできます。"

    override val onboardingPromoLine4 =
        "開発者のために設計。llama.cpp搭載。Llamatikはオープンソースで、モバイルとデスクトップのローカルAIを変革します。"

    override val feedItemTitle = "フィード項目"
    override val loading = "読み込み中..."
    override val profileImageDescription = "プロフィール画像"
    override val manuals = "マニュアル"
    override val guides = "ガイド"
    override val workInProgress = "開発中"
    override val dismiss = "閉じる"
    override val onboarding = "オンボーディング"
    override val about = "このアプリについて"
    override val chooseLanguage = "言語を選択"
    override val change = "変更"
    override val language = "言語: "

    override val viewAll = "すべて表示"
    override val welcomeToThe = "ようこそ "

    override val onboardingMainText =
        "Llamatik ChatBotは、Llamatikライブラリの動作を素早く理解するために設計された、実験的なローカルアシスタントです。\n" +
                "\n" +
                "詳細は llamatik.com をご覧ください\n" +
                "\n" +
                "\n" +
                "🔒 プライバシーに関するお知らせ\n\n" +
                "あなたのプライバシーは完全に保護されます。このチャットボットはデバイス上でのみ動作します。\n" +
                "個人データの収集・保存・共有は行いません。\n" +
                "外部サーバーへ情報は送信されません。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "続行することで、Llamatik ChatBotが教育・情報提供目的で提供され、GDPR、CCPA、LGPDなどのプライバシー法に準拠していることに同意したものとみなされます。\n" +
                "\n"

    override val actionContinue = "続行"
    override val settingUpLlamatik = "Llamatikをセットアップ中…"
    override val downloadingMainModels =
        "初回のメインモデルをダウンロードしています。\n数分かかる場合があります。"
    override val progress = "進捗"
    override val me = "自分"

    override val suggestion1 = "ゲーム機を販売するための簡単な領収書を作って"
    override val suggestion2 = "値引きをお願いされた人に丁寧な返信文を作って"
    override val suggestion3 = "最新の世界ニュースを簡単にまとめて"
    override val suggestion4 = "オンラインで物を売るコツをリストにして"
    override val suggestion5 = "簡単な請求書を作る手順を教えて"
    override val suggestion6 = "魔法の森の短編ストーリーを書いて"
    override val askMeAnything = "何か聞いてください…"
    override val stop = "停止"
    override val send = "送信"
    override val noModelSelected = "モデルが選択されていません"
    override val current = "現在"
    override val select = "選択"
    override val delete = "削除"
    override val download = "ダウンロード"
    override val downloading = "ダウンロード中…"
    override val generateModels = "モデルを生成"
    override val generationSettings = "生成設定"
    override val temperature = "温度"
    override val maxTokens = "最大トークン"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "繰り返しペナルティ"
    override val apply = "適用"
    override val downloadFinished = "ダウンロード完了"

    // Keep prompts in English for predictable model behavior (same approach as your other translations)
    override val defaultSystemPrompt = EnglishLocalization.defaultSystemPrompt
    override val gemma3SystemPrompt = EnglishLocalization.gemma3SystemPrompt
    override val smolVLM256SystemPrompt = EnglishLocalization.smolVLM256SystemPrompt
    override val smolVLM500SystemPrompt = EnglishLocalization.smolVLM500SystemPrompt
    override val qwen25BSystemPrompt = EnglishLocalization.qwen25BSystemPrompt
    override val phi15SystemPrompt = EnglishLocalization.phi15SystemPrompt
    override val llama32SystemPrompt = EnglishLocalization.llama32SystemPrompt

    override val assistant = "アシスタント"
    override val user = "ユーザー"
    override val system = "システム"
    override val relevantContext = "関連コンテキスト"
    override val defaultSystemPromptRendererMessage =
        "あなたは役に立つアシスタントです。関連する場合は提供されたコンテキストを使用してください。コンテキストが不十分な場合は、その旨を簡潔に述べてから回答してください。"

    override val copy = "コピー"
    override val paste = "貼り付け"

    override val chatHistory = "チャット履歴"
    override val noChatsYet = "まだチャットはありません"
    override val temporaryChat = "一時チャット"
    override val messages = "メッセージ"
    override val temporaryChatExplanation =
        "一時チャットが有効です — この会話はデバイスに保存されません。"
    override val voiceInput = "音声入力"
    override val listening = "聞き取り中…"
    override val transcribing = "文字起こし中…"
    override val embedModels = "埋め込みモデル"
    override val sttModels = "音声→テキストモデル"
    override val speak = "話す"

    override val imageGenerationModels = "画像生成モデル"
    override val failedToDecodeImageError = "🖼️ 画像のデコードに失敗しました。"
    override val imageGeneration = "画像生成"
    override val textGeneration = "テキスト生成"
    override val embeddingModelNotLoaded = "埋め込みモデルが読み込まれていません"
    override val noEmbeddingModelLoaded = "埋め込みモデルが未読み込みです"
    override val recommended = "おすすめ"
    override val pdfSelectFile = "PDFファイルを選択してください。"
    override val pdfExtractionError = "このPDFからテキストを抽出できませんでした。スキャンされたPDFの場合はOCRが必要です。"
    override val pdfEmbedModelNeededWarning = "PDF RAGを使用するには、埋め込みモデル「nomic-embed-text」（埋め込みモデル）をダウンロード/読み込みしてください。"
    override val pdfNoUsableChunksError = "このPDFから使用可能なテキストチャンクが生成されませんでした。"
    override val pdfFailedToComputeEmbeddingsError = "このPDFの埋め込み計算に失敗しました。埋め込みモデルを再読み込みして再試行してください。"
    override val pdfIndexedForRAG = "✅ PDFはRAG用にインデックス化されました"
    override val pdfFailedToLoadPDFForRAG = "RAG用PDFの読み込みに失敗しました"
    override val failedToComputeEmbeddings = "質問の埋め込み計算に失敗しました。埋め込みモデルを再読み込みして再試行してください。"
    override val thereIsAProblemWithAI = "AIに問題があります"
    override val iDontHaveEnoughInfoInSources = "情報源に十分な情報がありません。"
    override val imageModeEnabledButNoModelLoadedError = "🖼️ 画像モードが有効ですが、Stable Diffusionモデルが読み込まれていません。モデルを開いてSDモデルを選択してください。"
    override val imageGenerationFailedError = "🖼️ 画像生成に失敗しました（出力が空です）。"
    override val imageGenerationError = "画像生成エラー"
    override val allCachedModelsRemoved = "すべてのキャッシュ済みモデルとPDF RAGストアが正常に削除されました。"
    override val settings = "設定"
    override val removeAllDownloadedModels = "ダウンロード済みモデルとPDF RAGインデックスをすべて削除"
    override val clearCachedModelsDialogTitle = "すべてのキャッシュ済みモデルを削除しますか？"
    override val clearCachedModelsDialogMessage = "これにより、ダウンロード済みのすべてのモデルファイルと保存されたPDF RAGインデックスが削除されます。この操作は元に戻せません。"
    override val cancel = "キャンセル"
    override val clear = "削除"
}
