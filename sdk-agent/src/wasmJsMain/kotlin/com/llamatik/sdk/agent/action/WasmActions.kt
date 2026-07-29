package com.llamatik.sdk.agent.action

/** WASM platform — most device actions are unsupported in the browser. */
class WasmOpenUrlAction : Action {
    override val id = "wasm.browser.open_url"
    override fun isSupported() = true
    override fun requiredPermissions() = emptySet<String>()
    override fun validate(context: ActionContext): ActionValidationResult {
        if (context.arguments["url"].isNullOrBlank())
            return ActionValidationResult(false, "Missing required argument: url")
        return ActionValidationResult(true)
    }
    override suspend fun execute(context: ActionContext): ActionResult = ActionResult.Unsupported
}

class WasmClipboardAction : Action {
    override val id = "wasm.clipboard.copy"
    override fun isSupported() = true
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
