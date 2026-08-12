package com.llamatik.sdk.agent.action

/** WASM/browser platform actions. IDs match BuiltInToolDefinitions tool IDs. */

class WasmOpenUrlAction : Action {
    override val id = "browser.open_url"
    override fun isSupported() = true
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
            js("window.open(url, '_blank')")
            ActionResult.Success("Opened: $url")
        }.getOrElse { ActionResult.Failure("Failed to open URL: ${it.message}") }
    }
}

class WasmClipboardAction : Action {
    override val id = "clipboard.copy"
    override fun isSupported() = false // Async Web Clipboard API not bridged yet
    override fun requiredPermissions() = emptySet<String>()

    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["text"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: text")
        return ActionValidationResult(true)
    }

    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

fun wasmPlatformActions(): List<Action> = listOf(
    WasmOpenUrlAction(),
    WasmClipboardAction(),
)
