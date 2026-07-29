package com.llamatik.sdk.agent.action

/**
 * Desktop (JVM) platform action stubs.
 * Real implementations use java.awt.Desktop, java.awt.Toolkit.getSystemClipboard, etc.
 */
class DesktopOpenUrlAction : Action {
    override val id = "desktop.browser.open_url"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class DesktopClipboardAction : Action {
    override val id = "desktop.clipboard.copy"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class DesktopNotificationAction : Action {
    override val id = "desktop.notifications.post"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class DesktopOpenFileAction : Action {
    override val id = "desktop.files.open"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["path"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: path")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

fun desktopPlatformActions(): List<Action> = listOf(
    DesktopOpenUrlAction(),
    DesktopClipboardAction(),
    DesktopNotificationAction(),
    DesktopOpenFileAction(),
)
