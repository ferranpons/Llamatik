# Migration Guide: Llamatik 2.0

## Overview

Llamatik 2.0 introduces a new `:sdk` module (`com.llamatik.sdk`) that extracts all reusable
business logic from the application layer into a clean, UI-agnostic, publishable library.

## Module Structure

```
:core          → com.llamatik.core  (JNI/C++ bridges — LlamaBridge, WhisperBridge, etc.)
  ↑
:sdk           → com.llamatik.sdk   (Business logic SDK — models, agents, RAG, chat)
  ↑
:shared        → com.llamatik.app   (Compose UI, navigation, ViewModels, DI)
  ↑
:composeApp                         (Entry point)
```

## Gradle Dependencies

### Before (1.x)

```kotlin
// Using the library module directly
implementation("com.llamatik:library:<version>")
```

### After (2.0)

```kotlin
// Core inference layer (JNI bridges)
implementation("com.llamatik:core:<version>")

// High-level SDK (business logic, models, agents, RAG)
implementation("com.llamatik:sdk:<version>")
```

## Package Migration

| 1.x Package                                    | 2.0 Package                         |
|------------------------------------------------|--------------------------------------|
| `com.llamatik.library.*`                       | `com.llamatik.core.*` or `com.llamatik.sdk.*` |
| `com.llamatik.app.feature.chatbot.model.*`     | `com.llamatik.sdk.model.*`          |
| `com.llamatik.app.feature.chatbot.utils.*`     | `com.llamatik.sdk.chat.*`           |
| `com.llamatik.app.feature.chatbot.repositories.*` | `com.llamatik.sdk.model.*` or `com.llamatik.sdk.chat.*` |
| `com.llamatik.app.feature.chatbot.download.*`  | `com.llamatik.sdk.download.*`       |
| `com.llamatik.app.feature.agent.*`             | `com.llamatik.sdk.agent.*`          |
| `com.llamatik.app.feature.entitlement.*`       | `com.llamatik.sdk.entitlement.*`    |
| `com.llamatik.app.common.usecases.*`           | `com.llamatik.sdk.usecase.*`        |
| `com.llamatik.app.platform.ServiceClient`      | `com.llamatik.sdk.http.LlamatikHttpClient` |

## Type Mapping

### Chat & Prompt

| 1.x                                     | 2.0                                          |
|-----------------------------------------|----------------------------------------------|
| `com.llamatik.app.feature.chatbot.utils.ChatMessage` | `com.llamatik.sdk.chat.ChatMessage` |
| `com.llamatik.app.feature.chatbot.utils.PromptTemplate` | `com.llamatik.sdk.chat.PromptTemplate` |
| `com.llamatik.app.feature.chatbot.utils.Plain` | `com.llamatik.sdk.chat.Plain` |
| `com.llamatik.app.feature.chatbot.utils.Gemma3` | `com.llamatik.sdk.chat.Gemma3` |
| `com.llamatik.app.feature.chatbot.utils.Llama3Instruct` | `com.llamatik.sdk.chat.Llama3Instruct` |
| `com.llamatik.app.feature.chatbot.utils.QwenChat` | `com.llamatik.sdk.chat.QwenChat` |
| `com.llamatik.app.feature.chatbot.utils.PromptRenderer` (app-localization-tied) | `com.llamatik.sdk.chat.PromptRenderer` (labels injected) |
| `com.llamatik.app.feature.chatbot.utils.ChatRunner` | `com.llamatik.sdk.chat.ChatRunner` |
| `com.llamatik.app.feature.chatbot.utils.chunkText()` | `com.llamatik.sdk.rag.chunkText()` |

### Models

| 1.x                                        | 2.0                                        |
|--------------------------------------------|--------------------------------------------|
| `com.llamatik.app.feature.chatbot.model.LlamaModel` | `com.llamatik.sdk.model.LlamaModel` |
| `com.llamatik.app.feature.chatbot.model.GenerateSettings` | `com.llamatik.sdk.model.GenerateSettings` |
| `com.llamatik.app.feature.chatbot.model.ModelSource` | `com.llamatik.sdk.model.ModelSource` |
| `com.llamatik.app.feature.chatbot.repositories.ModelsRepository` | `com.llamatik.sdk.model.ModelsRepository` |
| `com.llamatik.app.feature.chatbot.repositories.ChatHistoryRepository` | `com.llamatik.sdk.chat.ChatHistoryRepository` |
| `com.llamatik.app.feature.chatbot.repositories.ChatSession` | `com.llamatik.sdk.chat.ChatSession` |
| `com.llamatik.app.feature.chatbot.usecases.GetModelsUseCase` | `com.llamatik.sdk.model.GetModelsUseCase` |
| `com.llamatik.app.feature.chatbot.usecases.ImportModelUseCase` | `com.llamatik.sdk.model.ImportModelUseCase` |
| `com.llamatik.app.feature.chatbot.download.ModelDownloadOrchestrator` | `com.llamatik.sdk.download.ModelDownloadOrchestrator` |
| `com.llamatik.app.feature.chatbot.download.DefaultModelDownloadOrchestrator` | `com.llamatik.sdk.download.DefaultModelDownloadOrchestrator` |
| `com.llamatik.app.feature.chatbot.download.DownloadEvent` | `com.llamatik.sdk.download.DownloadEvent` |

### Agent

| 1.x                                        | 2.0                                        |
|--------------------------------------------|--------------------------------------------|
| `com.llamatik.app.feature.agent.AgentTool` | `com.llamatik.sdk.agent.AgentTool` |
| `com.llamatik.app.feature.agent.AgentToolResult` | `com.llamatik.sdk.agent.AgentToolResult` |
| `com.llamatik.app.feature.agent.AgentExecutor` | `com.llamatik.sdk.agent.AgentExecutor` |
| `com.llamatik.app.feature.agent.ToolRegistry` | `com.llamatik.sdk.agent.ToolRegistry` |
| `com.llamatik.app.feature.agent.ToolCallParser` | `com.llamatik.sdk.agent.ToolCallParser` |
| `com.llamatik.app.feature.agent.ParsedToolCall` | `com.llamatik.sdk.agent.ParsedToolCall` |
| `com.llamatik.app.feature.agent.AgentFeatureFlags` | `com.llamatik.sdk.agent.AgentFeatureFlags` |
| `com.llamatik.app.feature.agent.AgentActionLogRepository` | `com.llamatik.sdk.agent.AgentActionLogRepository` |
| `com.llamatik.app.feature.agent.ToolPermissionRepository` | `com.llamatik.sdk.agent.ToolPermissionRepository` |
| `com.llamatik.app.feature.agent.ToolPermission` | `com.llamatik.sdk.agent.ToolPermission` |
| `com.llamatik.app.feature.agent.AgentActionLog` | `com.llamatik.sdk.agent.AgentActionLog` |
| `com.llamatik.app.feature.agent.AgentActionStatus` | `com.llamatik.sdk.agent.AgentActionStatus` |

### RAG / Vector Store

| 1.x                                        | 2.0                                        |
|--------------------------------------------|--------------------------------------------|
| `com.llamatik.app.feature.chatbot.utils.VectorStoreItem` | `com.llamatik.sdk.rag.VectorStoreItem` |
| `com.llamatik.app.feature.chatbot.utils.VectorStoreData` | `com.llamatik.sdk.rag.VectorStoreData` |
| `com.llamatik.app.feature.chatbot.utils.PersistedRagStore` | `com.llamatik.sdk.rag.PersistedRagStore` |
| `com.llamatik.app.feature.chatbot.utils.retrieveContext()` | `com.llamatik.sdk.rag.retrieveContext()` |
| `com.llamatik.app.feature.chatbot.utils.cosineD()` | `com.llamatik.sdk.rag.cosineD()` |
| `com.llamatik.app.feature.chatbot.utils.mmr()` | `com.llamatik.sdk.rag.mmr()` |

### Entitlement

| 1.x                                        | 2.0                                        |
|--------------------------------------------|--------------------------------------------|
| `com.llamatik.app.feature.entitlement.EntitlementRepository` | `com.llamatik.sdk.entitlement.EntitlementRepository` |

## Breaking Changes

### `PromptRenderer` now requires explicit `Labels`

The renderer no longer depends on the app's `getCurrentLocalization()`. You must pass a `Labels`
object with the user-facing strings:

```kotlin
// 1.x
PromptRenderer.render(system, contexts, messages, template)

// 2.0
val labels = PromptRenderer.Labels(
    system = getString(R.string.system),
    user = getString(R.string.user),
    assistant = getString(R.string.assistant),
    relevantContext = getString(R.string.relevant_context),
)
PromptRenderer.render(system, contexts, messages, template, labels)
```

### `ModelsRepository` requires `LlamatikFileAccess` and `LlamatikHttpClient`

```kotlin
// 1.x — constructed with ServiceClient (global singleton)
val repo = ModelsRepository(serviceClient)

// 2.0 — depends on platform-specific LlamatikFileAccess
val repo = ModelsRepository(
    fileAccess = myFileAccess,   // implement LlamatikFileAccess
    httpClient = LlamatikHttpClient(),
)
```

### `ChatRunner` `Labels` injected, not hardcoded

```kotlin
// 1.x
ChatRunner.stream(session, system, contexts, messages, template, ...) { ... }

// 2.0
val labels = PromptRenderer.Labels(/* localised strings */)
ChatRunner.stream(session, system, contexts, messages, template, labels = labels) { ... }
```

---

## Agent Framework (2.0 New)

Llamatik 2.0 introduces `com.llamatik.sdk.framework` — a complete framework for building
agentic AI applications in Kotlin.

### Creating an Agent

```kotlin
val agent = Agent {
    name("Kotlin Assistant")
    systemPrompt("You are an expert Kotlin developer.")
    temperature(0.7f)
    maxTokens(1024)
}
```

### Registering Tools

```kotlin
val agent = Agent {
    name("Assistant")
    systemPrompt("You are helpful.")
    tools {
        builtIns()          // calculator, datetime, uuid, random
        calculator()        // or individually
        datetime()
    }
}
```

### Custom Tools

```kotlin
class WeatherTool : AgentTool {
    override val id = "weather"
    override val displayName = "Weather"
    override val description = "Returns the current weather for a city."
    override val schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("city") { put("type", "string") }
        }
    }
    override fun isSupported() = true
    override suspend fun execute(input: JsonObject): AgentToolResult {
        val city = input["city"]?.jsonPrimitive?.content ?: return AgentToolResult.Failure("Missing city")
        return AgentToolResult.Success("Sunny, 22°C in $city")
    }
}

val agent = Agent {
    tools { register(WeatherTool()) }
}
```

### Memory

```kotlin
// Unbounded conversation memory (default)
val agent = Agent {
    memory(ConversationMemory())
}

// Sliding-window memory — retains only last N messages, pins system message
val agent = Agent {
    memory(SlidingWindowMemory(windowSize = 20))
}

// Persistent memory — survives restarts; implement MemoryStorage for your platform
val agent = Agent {
    persistentMemory(storage = myStorage, key = "memory/chat.txt")
}
// After creation, restore a prior session:
// agent.memory.restore()
// After each turn you want to save:
// agent.memory.persist()

// Summary memory — compresses old messages via a summarizer call when window fills
val agent = Agent {
    summaryMemory(triggerSize = 40, compressCount = 20) { prompt ->
        // Call your LLM or a dedicated summarizer here; return the summary text
        assistant.run(prompt)
    }
}
// The agent automatically calls maybeCompress() after each completed turn.
```

### Workflow Approval Gates

```kotlin
// A gated step presents its output to the host before proceeding.
// Return true to allow the next step, false to stop.
val workflow = Workflow {
    step("research") { "Research the topic: Kotlin coroutines." }

    gatedStep("plan", gate = { stepName, proposal ->
        // Show proposal to user, return their decision
        userConfirms(stepName, proposal)
    }) { ctx ->
        val research = ctx["research"] ?: ""
        "Based on this research:\n$research\n\nCreate an implementation plan."
    }

    step("implement") { ctx ->
        val plan = ctx["plan"] ?: ""
        "Implement according to this plan:\n$plan"
    }
}
```

### Streaming

```kotlin
// Flow-based streaming (preferred)
agent.stream("Create a Compose screen.").collect { chunk ->
    when (chunk) {
        is AgentStreamChunk.Delta -> print(chunk.text)
        is AgentStreamChunk.ToolCallStarted -> println("[tool: ${chunk.toolId}]")
        is AgentStreamChunk.ToolResult -> println("[result: ${chunk.summary}]")
        is AgentStreamChunk.Done -> println("\nDone.")
        is AgentStreamChunk.Failure -> println("Error: ${chunk.message}")
    }
}

// Suspend run() — collects stream to a final string
val response = agent.run("Explain coroutines.")
println(response)
```

### Events

```kotlin
agent.events.collect { event ->
    when (event) {
        is AgentEvent.TurnStarted -> println("Turn started: ${event.userMessage}")
        is AgentEvent.Thinking -> println("Thinking…")
        is AgentEvent.ToolCallStarted -> println("Calling ${event.toolId}")
        is AgentEvent.TurnCompleted -> println("Done.")
        else -> Unit
    }
}
```

### Workflows

```kotlin
val workflow = Workflow {
    step("outline") { ctx ->
        "Create an outline for an article about Kotlin coroutines."
    }
    step("write") { ctx ->
        val outline = ctx["outline"] ?: ""
        "Write a full article based on this outline:\n$outline"
    }
    step("review") { ctx ->
        val article = ctx["write"] ?: ""
        "Review this article for clarity and conciseness:\n$article"
    }
}

val result = workflow.execute(agent)
println(result.finalOutput)
```

### Tool Type Alias

`AgentTool` can also be referenced as `Tool` for more idiomatic usage:

```kotlin
val myTool: Tool = WeatherTool()
```

---

## Agent Platform (2.0 New — `sdk-agent` module)

### Module

```kotlin
// build.gradle.kts
implementation("com.llamatik:sdk-agent:<version>")
```

### Architecture

```
User message
     ↓
AgentRuntime.processMessage()
     ↓
AgentPlanner  →  ExecutionPlan  (or ConversationalResponse)
     ↓
AgentExecutionEngine
  ├── PermissionManager.check()
  ├── ConfirmationHandler.requestConfirmation()
  ├── ActionRegistry.get()  →  Action.validate() / Action.execute()
  ├── ToolRegistry.get()    →  ToolDefinition.execute()  (fallback)
  └── AgentAuditRepository.append()
     ↓
LLM generates natural-language response
     ↓
AgentRuntimeEvent stream
```

### Building the Runtime

```kotlin
val runtime = AgentRuntime.Builder()
    .toolRegistry(toolRegistry)
    .actionRegistry(actionRegistry)
    .permissionManager(permissionManager)
    .agentMemory(agentMemory)
    .auditRepository(auditRepository)
    .confirmationHandler { request ->
        // Show UI, return true to confirm, false to cancel
        showConfirmationDialog(request)
    }
    .companionProfile(CompanionProfiles.Assistant)
    .capabilityProvider(AndroidCapabilityProvider())
    .platformId("android")
    .build()
```

### Processing a message

```kotlin
runtime.processMessage(userMessage, conversationHistory, sessionId).collect { event ->
    when (event) {
        is AgentRuntimeEvent.Planning -> showSpinner()
        is AgentRuntimeEvent.PlanReady -> showPlan(event.plan)
        is AgentRuntimeEvent.Executing -> showProgress(event.toolId)
        is AgentRuntimeEvent.StepCompleted -> logResult(event.result)
        is AgentRuntimeEvent.ConversationalResponse -> showResponse(event.text)
        is AgentRuntimeEvent.Completed -> showResponse(event.response)
        is AgentRuntimeEvent.Failed -> showError(event.message)
        else -> Unit
    }
}
```

### Registering a Custom Tool

```kotlin
class MyCustomTool(actionExecutor: suspend (ActionContext) -> ActionResult) 
    : DelegatingToolDefinition(actionExecutor) {
    override val id = "myapp.custom_action"
    override val displayName = "Custom Action"
    override val description = "Does something custom."
    override val schema = ToolSchema(listOf(
        ToolParameter("input", "string", "The input value"),
    ))
    override val metadata = ToolMetadata(
        category = ToolCategory.UTILITIES,
        capabilities = emptySet(),
        supportedPlatforms = setOf(SupportedPlatform.ALL),
    )
    override val requiredPermissions = emptySet<String>()
}

toolRegistry.registerTool(MyCustomTool { ctx ->
    val input = ctx.arguments["input"] ?: return@MyCustomTool ActionResult.Failure("Missing input")
    ActionResult.Success("Processed: $input")
})
```

### Companion Profiles

```kotlin
// Use a built-in profile
val profile = CompanionProfiles.Professional

// Or create a custom one
val custom = CompanionProfile(
    persona = CompanionPersona.ASSISTANT,
    systemPrompt = "You are a focused coding assistant.",
    responseStyle = "technical",
    memoryEnabled = true,
    planningAggression = 0.8f,
    verbosity = 0.4f,
    toolPreference = emptySet(),
)
```

### Companion Profiles available

| Profile | Style | Planning |
|---------|-------|----------|
| `CompanionProfiles.Assistant` | concise | 0.7 |
| `CompanionProfiles.Professional` | formal | 0.9 |
| `CompanionProfiles.Friendly` | casual | 0.5 |
| `CompanionProfiles.Companion` | empathetic | 0.4 |
| `CompanionProfiles.Pet` | playful | 0.3 |

### Permissions

```kotlin
// Check a permission
val decision = permissionManager.check(KnownPermissions.CALENDAR)

// Grant / deny
permissionManager.grant(KnownPermissions.CALENDAR)
permissionManager.deny(KnownPermissions.CONTACTS)
permissionManager.askEveryTime(KnownPermissions.FILES)
```

### Audit Log

```kotlin
val recentActions = auditRepository.getRecent(50)
val calendarActions = auditRepository.getByTool("calendar.create_event")
```

### Workflow Engine

```kotlin
val workflow = AgentWorkflow(
    id = "morning_routine",
    name = "Morning Routine",
    description = "Daily morning briefing workflow",
    steps = listOf(
        WorkflowStep(
            id = "weather",
            name = "Get Weather",
            toolId = "network.fetch",
            argumentsBuilder = { _ -> mapOf("url" to "https://api.weather.example/today") },
        ),
        WorkflowStep(
            id = "calendar",
            name = "Get Calendar",
            toolId = "calendar.list_events",
            argumentsBuilder = { _ -> mapOf("date" to "today") },
            dependsOn = emptyList(),
        ),
        WorkflowStep(
            id = "briefing",
            name = "Generate Briefing",
            toolId = "ai.summarise",
            argumentsBuilder = { outputs ->
                mapOf(
                    "weather" to (outputs["weather"] ?: ""),
                    "calendar" to (outputs["calendar"] ?: ""),
                )
            },
        ),
    ),
)

val engine = AgentWorkflowEngine { toolId, args ->
    runtime.executeTool(toolId, args)
}
engine.execute(workflow).collect { stepResult ->
    println("${stepResult.stepId}: ${stepResult.status}")
}
```

---

### Package Reference

| Type | Package |
|------|---------|
| `AgentRuntime`, `AgentRuntimeEvent` | `com.llamatik.sdk.agent.runtime` |
| `AgentPlanner`, `AgentExecutionEngine` | `com.llamatik.sdk.agent.runtime` |
| `ExecutionPlan`, `ExecutionStep`, `ExecutionResult` | `com.llamatik.sdk.agent.runtime` |
| `AgentContext` | `com.llamatik.sdk.agent.runtime` |
| `ToolRegistry`, `ActionRegistry`, `CapabilityRegistry`, `WorkflowRegistry` | `com.llamatik.sdk.agent.registry` |
| `ToolDefinition`, `ToolSchema`, `ToolResult`, `ToolMetadata` | `com.llamatik.sdk.agent.tools` |
| `ToolCategory`, `ToolCapability`, `SupportedPlatform` | `com.llamatik.sdk.agent.tools` |
| `DelegatingToolDefinition` | `com.llamatik.sdk.agent.builtintools` |
| `CreateCalendarEventTool`, `CreateReminderTool`, `OpenAppTool`, `OpenUrlTool` | `com.llamatik.sdk.agent.builtintools` |
| `ClipboardTool`, `ShareTool`, `NotificationTool`, `SearchContactsTool`, `OpenSettingsTool` | `com.llamatik.sdk.agent.builtintools` |
| `Action`, `ActionContext`, `ActionResult`, `ActionValidationResult` | `com.llamatik.sdk.agent.action` |
| `PermissionManager`, `PermissionRepository`, `PermissionState` | `com.llamatik.sdk.agent.permissions` |
| `KnownPermissions` | `com.llamatik.sdk.agent.permissions` |
| `PlatformCapabilityProvider`, `Capability`, `KnownCapabilities` | `com.llamatik.sdk.agent.capability` |
| `ConfirmationHandler`, `ConfirmationPolicy`, `ConfirmationRequest`, `RiskLevel` | `com.llamatik.sdk.agent.confirmation` |
| `AgentAuditEntry`, `AgentAuditRepository` | `com.llamatik.sdk.agent.audit` |
| `CompanionProfile`, `CompanionProfiles`, `CompanionPersona` | `com.llamatik.sdk.agent.companion` |
| `AgentMemory`, `MemoryEntry`, `MemoryType` | `com.llamatik.sdk.agent.memory` |
| `AgentWorkflow`, `WorkflowStep`, `AgentWorkflowEngine`, `WorkflowRepository` | `com.llamatik.sdk.agent.workflow` |
| `PlatformCapabilityProvider` (Android) | `com.llamatik.sdk.agent.capability` (androidMain) |
| `androidPlatformActions()` | `com.llamatik.sdk.agent.action` (androidMain) |
| `iosPlatformActions()` | `com.llamatik.sdk.agent.action` (iosMain) |
| `desktopPlatformActions()` | `com.llamatik.sdk.agent.action` (jvmMain) |
| `wasmPlatformActions()` | `com.llamatik.sdk.agent.action` (wasmJsMain) |

---

### SDK Framework Package Reference

| Type | Package |
|------|---------|
| `Agent` | `com.llamatik.sdk.framework` |
| `AgentConfig` | `com.llamatik.sdk.framework` |
| `Memory`, `ConversationMemory`, `SlidingWindowMemory` | `com.llamatik.sdk.framework` |
| `PersistentMemory`, `MemoryStorage` | `com.llamatik.sdk.framework` |
| `SummaryMemory` | `com.llamatik.sdk.framework` |
| `PromptPipeline`, `PromptStage` | `com.llamatik.sdk.framework` |
| `AgentEvent`, `AgentStreamChunk` | `com.llamatik.sdk.framework` |
| `Workflow`, `Step`, `WorkflowResult`, `ApprovalGate` | `com.llamatik.sdk.framework` |
| `Tool` (= `AgentTool`) | `com.llamatik.sdk.framework` |
| `CalculatorTool`, `DateTimeTool`, `UuidTool`, `RandomTool` | `com.llamatik.sdk.agent` |
| `ToolRegistry.registerBuiltIns()` | `com.llamatik.sdk.agent` |
