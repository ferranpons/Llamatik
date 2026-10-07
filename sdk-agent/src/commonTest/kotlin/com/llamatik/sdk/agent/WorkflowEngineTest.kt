package com.llamatik.sdk.agent

import com.llamatik.sdk.agent.workflow.AgentWorkflow
import com.llamatik.sdk.agent.workflow.AgentWorkflowEngine
import com.llamatik.sdk.agent.workflow.RetryPolicy
import com.llamatik.sdk.agent.workflow.WorkflowStep
import com.llamatik.sdk.agent.workflow.WorkflowStepStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowEngineTest {

    private fun engine(executorFn: suspend (toolId: String, args: Map<String, String>) -> String): AgentWorkflowEngine =
        AgentWorkflowEngine(executorFn)

    @Test
    fun singleStepWorkflowSucceeds() = runTest {
        val e = engine { _, _ -> "result" }
        val workflow = AgentWorkflow(
            id = "w1", name = "Test", description = "",
            steps = listOf(WorkflowStep("s1", "Step 1", "calc", { _ -> emptyMap() })),
        )
        val results = e.execute(workflow).toList()
        assertEquals(2, results.size) // RUNNING + SUCCEEDED
        assertEquals(WorkflowStepStatus.RUNNING, results[0].status)
        assertEquals(WorkflowStepStatus.SUCCEEDED, results[1].status)
        assertEquals("result", results[1].output)
    }

    @Test
    fun multiStepWorkflowOutputsChained() = runTest {
        val outputs = mutableListOf<Map<String, String>>()
        val e = engine { toolId, args ->
            outputs += args
            "out_$toolId"
        }
        val workflow = AgentWorkflow(
            id = "w2", name = "Chain", description = "",
            steps = listOf(
                WorkflowStep("s1", "Step 1", "tool_a", { _ -> emptyMap() }),
                WorkflowStep("s2", "Step 2", "tool_b", { prev -> mapOf("prev" to (prev["s1"] ?: "")) }),
            ),
        )
        e.execute(workflow).toList()
        assertEquals("out_tool_a", outputs[1]["prev"])
    }

    @Test
    fun failedStepStopsWorkflowWhenStopOnError() = runTest {
        var secondStepRan = false
        val e = engine { toolId, _ ->
            if (toolId == "fail") throw RuntimeException("oops")
            secondStepRan = true
            "ok"
        }
        val workflow = AgentWorkflow(
            id = "w3", name = "Fail", description = "",
            steps = listOf(
                WorkflowStep("s1", "Fail step", "fail", { _ -> emptyMap() }),
                WorkflowStep("s2", "Second step", "ok", { _ -> emptyMap() }),
            ),
            stopOnError = true,
        )
        val results = e.execute(workflow).toList()
        assertTrue(results.any { it.status == WorkflowStepStatus.FAILED })
        assertTrue(!secondStepRan)
    }

    @Test
    fun conditionFalseSkipsStep() = runTest {
        var skippableRan = false
        val e = engine { _, _ ->
            skippableRan = true
            "ok"
        }
        val workflow = AgentWorkflow(
            id = "w4", name = "Cond", description = "",
            steps = listOf(
                WorkflowStep("s1", "Cond step", "tool", { _ -> emptyMap() },
                    condition = { false }),
            ),
        )
        val results = e.execute(workflow).toList()
        assertEquals(1, results.size)
        assertEquals(WorkflowStepStatus.SKIPPED, results[0].status)
        assertTrue(!skippableRan)
    }
}
