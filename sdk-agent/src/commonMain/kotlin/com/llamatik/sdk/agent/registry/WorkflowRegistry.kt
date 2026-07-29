package com.llamatik.sdk.agent.registry

import com.llamatik.sdk.agent.workflow.AgentWorkflow

class WorkflowRegistry {
    private val workflows = mutableMapOf<String, AgentWorkflow>()

    fun registerWorkflow(workflow: AgentWorkflow) {
        workflows[workflow.id] = workflow
    }

    fun unregisterWorkflow(id: String) {
        workflows.remove(id)
    }

    fun get(id: String): AgentWorkflow? = workflows[id]

    fun allWorkflows(): List<AgentWorkflow> = workflows.values.toList()
}
