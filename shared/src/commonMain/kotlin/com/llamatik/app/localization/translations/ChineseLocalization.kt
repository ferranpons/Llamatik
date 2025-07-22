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
}
