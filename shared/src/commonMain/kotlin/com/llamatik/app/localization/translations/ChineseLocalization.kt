package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object ChineseLocalization : Localization {
    override val appName = "Llamatik"

    override val actionSettings = "设置"
    override val next = "下一步"
    override val close = "关闭"
    override val previous = "上一步"
    override val welcome = "欢迎来到 Llamatik"


    override val backLabel = "返回"
    override val topAppBarActionIconDescription = "设置"
    override val home = "首页"
    override val news = "新闻"

    override val onBoardingStartButton = "开始"
    override val onBoardingAlreadyHaveAnAccountButton = "我有一个账户"
    override val searchItems = "搜索宠物"
    override val backButton = "返回"
    override val search = "搜索"
    override val noItemFound = "未找到项目"
    override val homeLastestNews = "最新新闻"

    override val noResultsTitle = "当前没有结果"
    override val noResultsDescription = "稍后再试，可能服务负载较高。对此造成的不便我们深感抱歉。"

    override val greetingMorning = "早上好"
    override val greetingAfternoon = "下午好"
    override val greetingEvening = "晚上好"
    override val greetingNight = "晚安"

    override val debugMenuTitle = "调试菜单"
    override val featureNotAvailableMessage =
        "很抱歉，此功能目前不可用。您可以在模块选项卡中找到每个模块的手册和指南。"

    override val onboardingPromoTitle1 = "离线运行大语言模型"
    override val onboardingPromoTitle2 = "隐私至上，无需云端"
    override val onboardingPromoTitle3 = "完全本地控制"
    override val onboardingPromoTitle4 = "面向开发者的开源项目"

    override val onboardingPromoLine1 =
        "Llamatik 将强大的本地 AI 带入 Kotlin Multiplatform 应用 — 完全离线，注重隐私，运行高效。"

    override val onboardingPromoLine2 =
        "无需依赖云服务，无网络延迟，轻松构建智能聊天机器人、助手和 AI 功能。"

    override val onboardingPromoLine3 =
        "支持自定义模型和本地向量数据库，让你完全掌控 LLM 技术栈 — 纯 Kotlin 实现。"

    override val onboardingPromoLine4 =
        "专为开发者设计，基于 llama.cpp。Llamatik 是开源项目，重塑移动与桌面端的本地 AI。"

    override val feedItemTitle = "动态项"

    override val loading = "加载中..."
    override val profileImageDescription = "个人头像"
    override val manuals = "手册"
    override val guides = "指南"
    override val workInProgress = "正在进行"
    override val dismiss = "关闭"
    override val onboarding = "入门"
    override val about = "关于"
    override val chooseLanguage = "选择语言"
    override val change = "更改"
    override val language = "语言："

    override val viewAll = "查看全部"
    override val welcomeToThe = "欢迎使用"
    override val onboardingMainText =
        "Llamatik ChatBot 是一个实验性的本地助手，旨在帮助你快速了解 Llamatik 库的工作方式。\n" +
                "\n" +
                "更多信息请访问 llamatik.com\n" +
                "\n" +
                "\n" +
                "🔐 隐私声明\n\n" +
                "你的隐私受到完全保护。此聊天机器人完全在你的设备上运行。\n" +
                "不会收集、存储或共享任何个人数据。\n" +
                "不会向外部服务器发送任何信息。\n" +
                "\n" +
                "---\n" +
                "\n" +
                "继续使用即表示你同意：Llamatik ChatBot 仅用于教育和信息目的，并符合包括 GDPR、CCPA 和 LGPD 在内的全球隐私法规。\n"

    override val actionContinue = "继续"
    override val settingUpLlamatik = "正在设置 Llamatik…"
    override val downloadingMainModels =
        "首次下载主要模型。\n这可能需要几分钟。"
    override val progress = "进度"
    override val me = "我"

    override val suggestion1 = "创建一份用于出售游戏主机的简单收据"
    override val suggestion2 = "起草一封礼貌回复，回应对方的折扣请求"
    override val suggestion3 = "提供最近世界新闻的简要概述"
    override val suggestion4 = "创建一份在线出售物品的技巧清单"
    override val suggestion5 = "给我一份准备简单发票的步骤列表"
    override val suggestion6 = "写一个关于魔法森林的短篇故事"
    override val askMeAnything = "问我任何问题…"
    override val stop = "停止"
    override val send = "发送"
    override val noModelSelected = "未选择模型"
    override val current = "当前"
    override val select = "选择"
    override val delete = "删除"
    override val download = "下载"
    override val downloading = "正在下载…"
    override val generateModels = "生成模型"
    override val generationSettings = "生成设置"
    override val temperature = "温度"
    override val maxTokens = "最大 Token 数"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "重复惩罚"
    override val apply = "应用"
    override val downloadFinished = "下载完成"

    override val defaultSystemPrompt = """
你是 Llamatik，一个以隐私优先的本地 AI 助手，运行在用户的设备上。
你的优先事项：
- 提供有帮助且清晰的回答。
- 尊重用户隐私（不假设任何外部数据或在线访问）。
- 高效且简洁，避免不必要的 token。
- 当你不知道答案时，明确说明你不知道。

始终使用与用户最后一条消息相同的语言回答。
"""
    override val gemma3SystemPrompt = """
你是 Llamatik，一个由 Gemma 3 270M 驱动的小型本地助手。

当用户输入内容时，直接进行回答。
- 如果用户要求你创建某些内容（收据、电子邮件、摘要、列表等），
  请直接输出该内容。
- 不要描述其他模型应该做什么。
- 不要以“任务：”“用户：”或类似的元描述开头，
  除非用户明确要求。
- 除非用户要求长回答，否则请保持回答简短清晰。
- 始终使用与用户最后一条消息相同的语言回复。
"""
    override val smolVLM256SystemPrompt = """
你是使用小型视觉语言模型（SmolVLM 256M Instruct）的 Llamatik。
在提供图像时，你可以对图像进行推理，并给出简短直接的回答。
优先使用简要说明，并在图像内容不明确时清楚说明。
"""
    override val smolVLM500SystemPrompt = """
你是使用 SmolVLM 500M Instruct 的 Llamatik，一个中等规模的视觉语言模型。
适合日常问题和图像理解。
保持友好和实用，并始终清楚地区分你在图像中看到的内容与推断的内容。
"""
    override val qwen25BSystemPrompt = """
你是使用 Qwen 2.5 Instruct 的 Llamatik，一个多语言本地助手。
在推理任务中注重清晰、结构化和逐步思考。
优先使用项目符号、标题和短段落，而不是冗长的文本块。
"""
    override val phi15SystemPrompt = """
你是使用 Phi-1.5 的 Llamatik，一个高效的小型代码与推理模型。
非常适合快速代码片段、简单解释和调试提示。
始终保持回答聚焦，避免不必要的冗长。
"""
    override val llama32SystemPrompt = """
你是使用 Llama 3.2 1B Instruct 的 Llamatik，一个强大的小型通用模型。
提供有帮助、清晰且比最小模型稍微更详细的回答，
但除非明确要求，否则避免生成过长的输出。
"""

    override val assistant = "助手"
    override val user = "用户"
    override val system = "系统"
    override val relevantContext = "相关上下文"
    override val defaultSystemPromptRendererMessage =
        "你是一个有帮助的助手。如有相关上下文，请加以使用。若上下文不足，请在回答前简要说明。"

    override val copy = "复制"
    override val paste = "粘贴"

    override val chatHistory = "聊天记录"
    override val noChatsYet = "暂无聊天"
    override val temporaryChat = "临时聊天"
    override val messages = "条消息"
    override val temporaryChatExplanation = "已开启临时聊天，本次对话不会保存在设备上。"
}
