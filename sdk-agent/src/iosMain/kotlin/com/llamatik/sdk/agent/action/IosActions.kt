package com.llamatik.sdk.agent.action

/**
 * iOS platform action stubs.
 *
 * Real implementations use EventKit, Reminders, UIApplication URL schemes,
 * UIPasteboard, UIActivityViewController, UserNotifications, CNContactStore.
 * Inject via the agent runtime builder on iOS.
 */
class IosCalendarAction : Action {
    override val id = "ios.calendar.create_event"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("NSCalendarsUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosReminderAction : Action {
    override val id = "ios.reminder.create"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("NSRemindersUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosOpenUrlAction : Action {
    override val id = "ios.browser.open_url"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosClipboardAction : Action {
    override val id = "ios.clipboard.copy"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosShareAction : Action {
    override val id = "ios.share.content"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosNotificationAction : Action {
    override val id = "ios.notifications.post"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("NSUserNotificationsUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosContactsAction : Action {
    override val id = "ios.contacts.search"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("NSContactsUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["query"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: query")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosSettingsAction : Action {
    override val id = "ios.settings.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext) = ActionValidationResult(true)
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

fun iosPlatformActions(): List<Action> = listOf(
    IosCalendarAction(),
    IosReminderAction(),
    IosOpenUrlAction(),
    IosClipboardAction(),
    IosShareAction(),
    IosNotificationAction(),
    IosContactsAction(),
    IosSettingsAction(),
)
