package com.llamatik.sdk.agent.action

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** Desktop (JVM) platform actions. IDs match BuiltInToolDefinitions tool IDs. */

class DesktopOpenUrlAction : Action {
    override val id = "browser.open_url"
    override fun isSupported() = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        var url = context.arguments["url"] ?: return ActionResult.Failure("Missing url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return runCatching {
            Desktop.getDesktop().browse(URI(url))
            ActionResult.Success("Opened: $url")
        }.getOrElse { ActionResult.Failure("Failed to open URL: ${it.message}") }
    }
}

class DesktopClipboardAction : Action {
    override val id = "clipboard.copy"
    override fun isSupported() = runCatching { Toolkit.getDefaultToolkit().systemClipboard != null }.getOrElse { false }
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val text = context.arguments["text"] ?: return ActionResult.Failure("Missing text")
        return runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
            ActionResult.Success("Copied to clipboard")
        }.getOrElse { ActionResult.Failure("Clipboard write failed: ${it.message}") }
    }
}

class DesktopNotificationAction : Action {
    override val id = "notifications.post"
    override fun isSupported() = false
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["title"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: title")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class DesktopOpenFileAction : Action {
    override val id = "files.open"
    override fun isSupported() = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["path"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: path")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult {
        val path = context.arguments["path"] ?: return ActionResult.Failure("Missing path")
        return runCatching {
            Desktop.getDesktop().open(File(path))
            ActionResult.Success("Opened file: $path")
        }.getOrElse { ActionResult.Failure("Failed to open file: ${it.message}") }
    }
}

fun desktopPlatformActions(): List<Action> = listOf(
    DesktopOpenUrlAction(),
    DesktopClipboardAction(),
    DesktopNotificationAction(),
    DesktopOpenFileAction(),
)
