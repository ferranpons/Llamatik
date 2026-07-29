package com.llamatik.sdk.agent.action

/**
 * Android platform action stubs.
 *
 * Implement each object by injecting the required Android Context/Service via
 * [AndroidActionDependencies] and passing it to the agent runtime on Android.
 *
 * Example wiring in Application.kt:
 *   AgentRuntime.Builder()
 *       .actionDependencies(AndroidActionDependencies(context))
 *       .build()
 */
class AndroidCalendarAction : Action {
    override val id = "android.calendar.create_event"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.WRITE_CALENDAR")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        if (context.arguments["date"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: date")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult {
        // Real implementation: use ContentResolver + CalendarContract.Events
        return ActionResult.Unsupported
    }
}

class AndroidReminderAction : Action {
    override val id = "android.reminder.create"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.SET_ALARM")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidOpenAppAction : Action {
    override val id = "android.apps.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["app_name"].isNullOrBlank() && context.arguments["package_id"].isNullOrBlank())
            return ActionValidationResult(false, "Missing app_name or package_id")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidOpenUrlAction : Action {
    override val id = "android.browser.open_url"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidClipboardAction : Action {
    override val id = "android.clipboard.copy"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidNotificationAction : Action {
    override val id = "android.notifications.post"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.POST_NOTIFICATIONS")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidShareAction : Action {
    override val id = "android.share.content"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidContactsAction : Action {
    override val id = "android.contacts.search"
    override fun isSupported() = true
    override fun requiredPermissions() = setOf("android.permission.READ_CONTACTS")
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["query"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: query")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

class AndroidSettingsAction : Action {
    override val id = "android.settings.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext) = ActionValidationResult(true)
    override suspend fun execute(context: ActionContext): ActionResult =
        ActionResult.Unsupported
}

/** Returns all Android platform actions for registration. */
fun androidPlatformActions(): List<Action> = listOf(
    AndroidCalendarAction(),
    AndroidReminderAction(),
    AndroidOpenAppAction(),
    AndroidOpenUrlAction(),
    AndroidClipboardAction(),
    AndroidNotificationAction(),
    AndroidShareAction(),
    AndroidContactsAction(),
    AndroidSettingsAction(),
)
