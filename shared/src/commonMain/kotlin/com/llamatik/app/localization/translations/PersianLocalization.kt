package com.llamatik.app.localization.translations

import com.llamatik.app.localization.Localization

internal object PersianLocalization : Localization {

    override val appName = "Llamatik"

    override val actionSettings = "تنظیمات"
    override val next = "بعدی"
    override val close = "بستن"
    override val previous = "قبلی"

    override val welcome = "به Llamatik خوش آمدید"

    override val backLabel = "بازگشت"
    override val topAppBarActionIconDescription = "تنظیمات"
    override val home = "خانه"
    override val news = "اخبار"

    override val onBoardingStartButton = "شروع"
    override val onBoardingAlreadyHaveAnAccountButton = "قبلاً حساب دارم"
    override val searchItems = "جستجو"
    override val backButton = "بازگشت"
    override val search = "جستجو"
    override val noItemFound = "موردی یافت نشد"
    override val homeLastestNews = "آخرین اخبار"

    override val noResultsTitle = "در حال حاضر نتیجه‌ای وجود ندارد"
    override val noResultsDescription =
        "لطفاً بعداً دوباره تلاش کنید. ممکن است سرویس تحت بار زیاد باشد."

    override val greetingMorning = "صبح بخیر"
    override val greetingAfternoon = "بعدازظهر بخیر"
    override val greetingEvening = "عصر بخیر"
    override val greetingNight = "شب بخیر"

    override val debugMenuTitle = "منوی اشکال‌زدایی"
    override val featureNotAvailableMessage =
        "متأسفیم، این قابلیت در حال حاضر در دسترس نیست."

    override val onboardingPromoTitle1 = "اجرای LLM به صورت آفلاین"
    override val onboardingPromoTitle2 = "خصوصی و بدون کلود"
    override val onboardingPromoTitle3 = "کنترل کامل محلی"
    override val onboardingPromoTitle4 = "متن‌باز برای توسعه‌دهندگان"

    override val onboardingPromoLine1 =
        "Llamatik هوش مصنوعی قدرتمند را به صورت کاملاً آفلاین به برنامه‌های شما می‌آورد."

    override val onboardingPromoLine2 =
        "چت‌بات‌ها و دستیارهای هوشمند بدون وابستگی به کلود بسازید."

    override val onboardingPromoLine3 =
        "از مدل‌های خود استفاده کنید و کنترل کامل داشته باشید."

    override val onboardingPromoLine4 =
        "طراحی شده برای توسعه‌دهندگان. مبتنی بر llama.cpp."

    override val feedItemTitle = "مورد"
    override val loading = "در حال بارگذاری..."
    override val profileImageDescription = "تصویر پروفایل"
    override val manuals = "راهنماها"
    override val guides = "دستورالعمل‌ها"
    override val workInProgress = "در حال توسعه"
    override val dismiss = "بستن"
    override val onboarding = "معرفی"
    override val about = "درباره"
    override val chooseLanguage = "انتخاب زبان"
    override val change = "تغییر"
    override val language = "زبان: "

    override val viewAll = "مشاهده همه"
    override val welcomeToThe = "خوش آمدید به "

    override val onboardingMainText =
        "Llamatik ChatBot یک دستیار محلی آزمایشی است.\n\n" +
                "🔒 اطلاعیه حریم خصوصی\n\n" +
                "این چت‌بات کاملاً روی دستگاه شما اجرا می‌شود.\n" +
                "هیچ داده‌ای ارسال یا ذخیره نمی‌شود.\n"

    override val actionContinue = "ادامه"
    override val settingUpLlamatik = "در حال راه‌اندازی Llamatik…"
    override val downloadingMainModels =
        "در حال دانلود مدل‌های اصلی برای اولین بار.\nممکن است چند دقیقه طول بکشد."
    override val progress = "پیشرفت"
    override val me = "من"

    override val suggestion1 = "یک رسید ساده ایجاد کن"
    override val suggestion2 = "یک پاسخ مودبانه برای درخواست تخفیف بنویس"
    override val suggestion3 = "خلاصه‌ای از اخبار اخیر جهان ارائه بده"
    override val suggestion4 = "لیستی از نکات فروش آنلاین ایجاد کن"
    override val suggestion5 = "مراحل تهیه فاکتور ساده را بگو"
    override val suggestion6 = "داستان کوتاهی درباره جنگل جادویی بنویس"

    override val askMeAnything = "هر چیزی بپرس…"
    override val stop = "توقف"
    override val send = "ارسال"
    override val noModelSelected = "مدلی انتخاب نشده"
    override val current = "فعلی"
    override val select = "انتخاب"
    override val delete = "حذف"
    override val download = "دانلود"
    override val downloading = "در حال دانلود…"
    override val generateModels = "ایجاد مدل‌ها"
    override val generationSettings = "تنظیمات تولید"
    override val temperature = "دما"
    override val maxTokens = "حداکثر توکن"
    override val topP = "Top P"
    override val topK = "Top K"
    override val repeatPenalty = "جریمه تکرار"
    override val apply = "اعمال"
    override val downloadFinished = "دانلود کامل شد"

    override val defaultSystemPrompt = EnglishLocalization.defaultSystemPrompt
    override val gemma3SystemPrompt = EnglishLocalization.gemma3SystemPrompt
    override val smolVLM256SystemPrompt = EnglishLocalization.smolVLM256SystemPrompt
    override val smolVLM500SystemPrompt = EnglishLocalization.smolVLM500SystemPrompt
    override val qwen25BSystemPrompt = EnglishLocalization.qwen25BSystemPrompt
    override val phi15SystemPrompt = EnglishLocalization.phi15SystemPrompt
    override val llama32SystemPrompt = EnglishLocalization.llama32SystemPrompt

    override val assistant = "دستیار"
    override val user = "کاربر"
    override val system = "سیستم"
    override val relevantContext = "زمینه مرتبط"
    override val defaultSystemPromptRendererMessage =
        "شما یک دستیار مفید هستید. اگر زمینه مرتبط است از آن استفاده کنید."

    override val copy = "کپی"
    override val paste = "چسباندن"

    override val chatHistory = "تاریخچه چت"
    override val noChatsYet = "هنوز چتی وجود ندارد"
    override val temporaryChat = "چت موقت"
    override val messages = "پیام‌ها"
    override val temporaryChatExplanation =
        "چت موقت فعال است – این گفتگو ذخیره نخواهد شد."
    override val voiceInput = "ورودی صوتی"
    override val listening = "در حال گوش دادن…"
    override val transcribing = "در حال تبدیل گفتار به متن…"
    override val embedModels = "مدل‌های امبد"
    override val sttModels = "مدل‌های گفتار به متن"
    override val speak = "صحبت کن"
}
