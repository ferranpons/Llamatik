package com.llamatik.sdk.agent.builtintools

import com.llamatik.sdk.agent.action.ActionContext
import com.llamatik.sdk.agent.action.ActionResult
import com.llamatik.sdk.agent.confirmation.RiskLevel
import com.llamatik.sdk.agent.permissions.KnownPermissions
import com.llamatik.sdk.agent.tools.SupportedPlatform
import com.llamatik.sdk.agent.tools.ToolAvailability
import com.llamatik.sdk.agent.tools.ToolCapability
import com.llamatik.sdk.agent.tools.ToolCategory
import com.llamatik.sdk.agent.tools.ToolDefinition
import com.llamatik.sdk.agent.tools.ToolMetadata
import com.llamatik.sdk.agent.tools.ToolParameter
import com.llamatik.sdk.agent.tools.ToolResult
import com.llamatik.sdk.agent.tools.ToolSchema

abstract class DelegatingToolDefinition(
    private val actionExecutor: suspend (ActionContext) -> ActionResult,
    private val supportedCheck: () -> Boolean = { true },
) : ToolDefinition {

    override val availability: ToolAvailability
        get() = if (supportedCheck()) ToolAvailability.Available
        else ToolAvailability.unavailable("Not supported on this platform")

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val ctx = ActionContext(arguments, sessionId = "", callerToolId = id)
        return when (val result = actionExecutor(ctx)) {
            is ActionResult.Success -> ToolResult.Success(result.summary, result.data)
            is ActionResult.Failure -> ToolResult.Failure(result.message)
            ActionResult.Unsupported -> ToolResult.Unsupported
            ActionResult.PermissionDenied -> ToolResult.PermissionDenied
        }
    }
}

class CreateCalendarEventTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "calendar.create_event"
    override val displayName = "Create Calendar Event"
    override val description = "Creates a new event in the user's calendar."
    override val schema = ToolSchema(listOf(
        ToolParameter("title", "string", "Event title"),
        ToolParameter("date", "string", "Date in YYYY-MM-DD format"),
        ToolParameter("time", "string", "Time in HH:MM format", required = false),
        ToolParameter("duration_minutes", "string", "Duration in minutes", required = false, defaultValue = "60"),
        ToolParameter("notes", "string", "Optional notes", required = false),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.CALENDAR,
        capabilities = setOf(ToolCapability.CALENDAR_WRITE),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.CALENDAR)
}

class CreateReminderTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "reminder.create"
    override val displayName = "Create Reminder"
    override val description = "Creates a reminder for the user."
    override val schema = ToolSchema(listOf(
        ToolParameter("title", "string", "Reminder title"),
        ToolParameter("date", "string", "Date in YYYY-MM-DD format", required = false),
        ToolParameter("time", "string", "Time in HH:MM format", required = false),
        ToolParameter("notes", "string", "Optional notes", required = false),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.REMINDER,
        capabilities = setOf(ToolCapability.REMINDER_CREATE),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.REMINDERS)
}

class OpenAppTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "apps.open"
    override val displayName = "Open App"
    override val description = "Opens an installed application by name or package."
    override val schema = ToolSchema(listOf(
        ToolParameter("app_name", "string", "Name of the app to open"),
        ToolParameter("package_id", "string", "Package/bundle ID (optional)", required = false),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.APPS,
        capabilities = setOf(ToolCapability.OPEN_APPS),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.OPEN_APPS)
}

class OpenUrlTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "browser.open_url"
    override val displayName = "Open URL"
    override val description = "Opens a URL in the default browser."
    override val schema = ToolSchema(listOf(
        ToolParameter("url", "string", "URL to open"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.BROWSER,
        capabilities = setOf(ToolCapability.OPEN_URL),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS, SupportedPlatform.JVM),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.OPEN_URL)
}

class ClipboardTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "clipboard.copy"
    override val displayName = "Copy to Clipboard"
    override val description = "Copies text to the system clipboard."
    override val schema = ToolSchema(listOf(
        ToolParameter("text", "string", "Text to copy to clipboard"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.CLIPBOARD,
        capabilities = setOf(ToolCapability.CLIPBOARD_WRITE),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS, SupportedPlatform.JVM),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.CLIPBOARD)
}

class ShareTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "share.content"
    override val displayName = "Share"
    override val description = "Shares text or a URL using the system share sheet."
    override val schema = ToolSchema(listOf(
        ToolParameter("text", "string", "Text or URL to share"),
        ToolParameter("title", "string", "Optional share title", required = false),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.UTILITIES,
        capabilities = setOf(ToolCapability.SHARE),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.SHARE)
}

class NotificationTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "notifications.post"
    override val displayName = "Show Notification"
    override val description = "Posts a local notification to the user."
    override val schema = ToolSchema(listOf(
        ToolParameter("title", "string", "Notification title"),
        ToolParameter("body", "string", "Notification body text"),
        ToolParameter("channel_id", "string", "Notification channel", required = false, defaultValue = "default"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.NOTIFICATIONS,
        capabilities = setOf(ToolCapability.NOTIFICATIONS_POST),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.NOTIFICATIONS)
}

class SearchContactsTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "contacts.search"
    override val displayName = "Search Contacts"
    override val description = "Searches for a contact by name."
    override val schema = ToolSchema(listOf(
        ToolParameter("query", "string", "Name to search for"),
        ToolParameter("limit", "string", "Max results", required = false, defaultValue = "5"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.CONTACTS,
        capabilities = setOf(ToolCapability.CONTACTS_READ),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.CONTACTS)
}

class OpenSettingsTool(
    actionExecutor: suspend (ActionContext) -> ActionResult,
    supportedCheck: () -> Boolean = { true },
) : DelegatingToolDefinition(actionExecutor, supportedCheck) {
    override val id = "settings.open"
    override val displayName = "Open Settings"
    override val description = "Opens a system settings panel."
    override val schema = ToolSchema(listOf(
        ToolParameter("panel", "string", "Settings panel to open (e.g. wifi, bluetooth, notifications, general)",
            required = false, defaultValue = "general"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.SETTINGS,
        capabilities = setOf(ToolCapability.SETTINGS_READ),
        supportedPlatforms = setOf(SupportedPlatform.ANDROID, SupportedPlatform.IOS),
        requiresConfirmation = false,
        riskLevel = RiskLevel.LOW.name,
    )
    override val requiredPermissions = setOf(KnownPermissions.SETTINGS)
}
