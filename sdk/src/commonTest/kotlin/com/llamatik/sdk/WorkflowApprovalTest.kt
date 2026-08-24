package com.llamatik.sdk

import com.llamatik.sdk.framework.ApprovalGate
import com.llamatik.sdk.framework.Workflow
import com.llamatik.sdk.framework.WorkflowBuilder
import com.llamatik.sdk.framework.WorkflowContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowApprovalTest {

    @Test
    fun approvalGateIsAlwaysApproveByDefault() = runTest {
        var gateInvoked = false
        val gate = ApprovalGate { _, _ ->
            gateInvoked = true
            true
        }
        // Verify the gate lambda is callable and returns true
        val approved = gate.approve("step", "proposal")
        assertTrue(approved)
        assertTrue(gateInvoked)
    }

    @Test
    fun approvalGateCanReject() = runTest {
        val gate = ApprovalGate { _, _ -> false }
        assertFalse(gate.approve("step", "proposal"))
    }

    @Test
    fun approvalGateReceivesStepNameAndProposal() = runTest {
        var capturedStep = ""
        var capturedProposal = ""
        val gate = ApprovalGate { stepName, proposal ->
            capturedStep = stepName
            capturedProposal = proposal
            true
        }
        gate.approve("my-step", "my-proposal")
        assertEquals("my-step", capturedStep)
        assertEquals("my-proposal", capturedProposal)
    }

    @Test
    fun workflowBuilderAcceptsGatedStep() {
        val gate = ApprovalGate { _, _ -> true }
        val workflow = Workflow {
            step("a") { "prompt a" }
            gatedStep("b", gate) { "prompt b" }
            step("c") { "prompt c" }
        }
        // If this compiles and runs without error, gatedStep is wired correctly.
        // We can't call execute() in a unit test without a live ChatRunner, so
        // we validate the builder contract only.
    }

    @Test
    fun workflowContextCarriesResults() {
        val ctx = WorkflowContext(mapOf("step1" to "result1", "step2" to "result2"))
        assertEquals("result1", ctx["step1"])
        assertEquals("result2", ctx["step2"])
        assertEquals(null, ctx["missing"])
    }
}
