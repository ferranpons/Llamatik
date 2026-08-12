package com.llamatik.sdk.agent.action

/**
 * iOS platform actions. IDs match BuiltInToolDefinitions tool IDs.
 * Full implementations require EventKit, UIKit, CNContactStore etc.
 * Currently stubbed — isSupported() reflects real capability, execute() returns Unsupported
 * until the EventKit/UIKit bridge is wired.
 */
class IosCalendarAction : Action {
    override val id = "calendar.create_event"
    override fun isSupported() = false // EventKit bridge not yet wired
    override fun requiredPermissions() = setOf("NSCalendarsUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosReminderAction : Action {
    override val id = "reminder.create"
    override fun isSupported() = false // EventKit EKReminder bridge not yet wired
    override fun requiredPermissions() = setOf("NSRemindersUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosOpenUrlAction : Action {
    override val id = "browser.open_url"
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
    override val id = "clipboard.copy"
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
    override val id = "share.content"
    override fun isSupported() = false // Requires UIViewController presentation context
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosNotificationAction : Action {
    override val id = "notifications.post"
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
    override val id = "contacts.search"
    override fun isSupported() = false // CNContactStore bridge not yet wired
    override fun requiredPermissions() = setOf("NSContactsUsageDescription")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["query"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: query")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class IosSettingsAction : Action {
    override val id = "settings.open"
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
