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

/**
 * Callback invoked before a gated step executes, allowing the host to inspect the proposed
 * output (from the previous step or the initial prompt) and approve or reject it.
 *
 * Return `true` to allow execution to continue, `false` to stop the workflow.
 */
fun interface ApprovalGate {
    suspend fun approve(stepName: String, proposal: String): Boolean
}

/** Sealed step descriptor stored inside the workflow. */
internal sealed interface StepEntry {
    val name: String

    data class Plain(override val name: String, val step: Step) : StepEntry
    data class Gated(override val name: String, val step: Step, val gate: ApprovalGate) : StepEntry
}

@AgentDsl
class WorkflowBuilder {
    private val steps = mutableListOf<StepEntry>()
    private var stopOnError: Boolean = true

    fun step(name: String, block: Step) {
        steps += StepEntry.Plain(name, block)
    }

    fun step(block: Step) {
        steps += StepEntry.Plain("step${steps.size + 1}", block)
    }

    /**
     * A gated step: the agent runs the step to produce a proposal, then [gate] is called
     * with the proposal text. If [gate] returns `false` the workflow stops (treated as a
     * rejection). The proposal is still recorded in [WorkflowContext] so later steps can
     * inspect it, but [StepOutput.error] is set to `"rejected"`.
     */
    fun gatedStep(name: String, gate: ApprovalGate, block: Step) {
        steps += StepEntry.Gated(name, block, gate)
    }

    fun continueOnError() { stopOnError = false }

    internal fun build(): Workflow = Workflow(steps.toList(), stopOnError)
}

class Workflow internal constructor(
    private val steps: List<StepEntry>,
    private val stopOnError: Boolean = true,
) {
    suspend fun execute(agent: Agent): WorkflowResult {
        val outputs = mutableListOf<StepOutput>()
        var ctx = WorkflowContext()

        for (entry in steps) {
            val prompt = runCatching { entry.step().prompt(ctx) }.getOrElse {
                val err = "Step '${entry.name}' prompt failed: ${it.message}"
                Logger.e(err)
                outputs += StepOutput(entry.name, "", err)
                if (stopOnError) return WorkflowResult(outputs, completed = false)
                ctx = ctx.copy(results = ctx.results + (entry.name to ""))
                continue
            }

            val result = runCatching { agent.run(prompt) }.getOrElse {
                val err = "Step '${entry.name}' run failed: ${it.message}"
                Logger.e(err)
                outputs += StepOutput(entry.name, "", err)
                if (stopOnError) return WorkflowResult(outputs, completed = false)
                ctx = ctx.copy(results = ctx.results + (entry.name to ""))
                continue
            }

            if (entry is StepEntry.Gated) {
                val approved = runCatching { entry.gate.approve(entry.name, result) }.getOrElse { false }
                if (!approved) {
                    Logger.d("Workflow step '${entry.name}' rejected by approval gate")
                    outputs += StepOutput(entry.name, result, error = "rejected")
                    ctx = ctx.copy(results = ctx.results + (entry.name to result))
                    return WorkflowResult(outputs, completed = false)
                }
            }

            outputs += StepOutput(entry.name, result)
            ctx = ctx.copy(results = ctx.results + (entry.name to result))
            Logger.d("Workflow step '${entry.name}' completed (${result.length} chars)")
        }

        return WorkflowResult(outputs, completed = true)
    }
}

private fun StepEntry.step(): Step = when (this) {
    is StepEntry.Plain -> step
    is StepEntry.Gated -> step
}

fun Workflow(block: WorkflowBuilder.() -> Unit): Workflow =
    WorkflowBuilder().apply(block).build()
