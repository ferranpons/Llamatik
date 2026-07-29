package com.llamatik.sdk.framework

import co.touchlab.kermit.Logger

data class WorkflowContext(
    val results: Map<String, String> = emptyMap(),
) {
    operator fun get(stepName: String): String? = results[stepName]
}

fun interface Step {
    suspend fun prompt(context: WorkflowContext): String
}

data class StepOutput(
    val stepName: String,
    val output: String,
    val error: String? = null,
) {
    val succeeded: Boolean get() = error == null
}

data class WorkflowResult(
    val stepOutputs: List<StepOutput>,
    val completed: Boolean,
) {
    val outputs: Map<String, String> get() = stepOutputs.filter { it.succeeded }.associate { it.stepName to it.output }
    val finalOutput: String? get() = stepOutputs.lastOrNull { it.succeeded }?.output
}

@AgentDsl
class WorkflowBuilder {
    private val steps = mutableListOf<Pair<String, Step>>()
    private var stopOnError: Boolean = true

    fun step(name: String, block: Step) {
        steps += name to block
    }

    fun step(block: Step) {
        steps += "step${steps.size + 1}" to block
    }

    fun continueOnError() { stopOnError = false }

    internal fun build(): Workflow = Workflow(steps.toList(), stopOnError)
}

class Workflow internal constructor(
    private val namedSteps: List<Pair<String, Step>>,
    private val stopOnError: Boolean = true,
) {
    suspend fun execute(agent: Agent): WorkflowResult {
        val outputs = mutableListOf<StepOutput>()
        var ctx = WorkflowContext()

        for ((name, step) in namedSteps) {
            val prompt = runCatching { step.prompt(ctx) }.getOrElse {
                val err = "Step '$name' prompt failed: ${it.message}"
                Logger.e(err)
                outputs += StepOutput(name, "", err)
                if (stopOnError) return WorkflowResult(outputs, completed = false)
                ctx = ctx.copy(results = ctx.results + (name to ""))
                continue
            }

            val result = runCatching { agent.run(prompt) }.getOrElse {
                val err = "Step '$name' run failed: ${it.message}"
                Logger.e(err)
                outputs += StepOutput(name, "", err)
                if (stopOnError) return WorkflowResult(outputs, completed = false)
                ctx = ctx.copy(results = ctx.results + (name to ""))
                continue
            }

            outputs += StepOutput(name, result)
            ctx = ctx.copy(results = ctx.results + (name to result))
            Logger.d("Workflow step '$name' completed (${result.length} chars)")
        }

        return WorkflowResult(outputs, completed = true)
    }
}

fun Workflow(block: WorkflowBuilder.() -> Unit): Workflow =
    WorkflowBuilder().apply(block).build()
