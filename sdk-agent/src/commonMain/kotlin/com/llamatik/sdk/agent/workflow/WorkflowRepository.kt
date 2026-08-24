package com.llamatik.sdk.agent.workflow

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val WORKFLOW_KEY = "llamatik_workflows_v1"

@Serializable
data class StoredWorkflow(
    val id: String,
    val name: String,
    val description: String,
)

@Serializable
private data class WorkflowStore(val workflows: List<StoredWorkflow> = emptyList())

class WorkflowRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun saveWorkflowMetadata(workflow: AgentWorkflow) {
        val store = readStore()
        val existing = store.workflows.filterNot { it.id == workflow.id }
        val updated = existing + StoredWorkflow(workflow.id, workflow.name, workflow.description)
        writeStore(WorkflowStore(updated))
    }

    fun getSavedMetadata(): List<StoredWorkflow> = readStore().workflows

    fun deleteWorkflow(id: String) {
        val store = readStore()
        writeStore(WorkflowStore(store.workflows.filterNot { it.id == id }))
    }

    private fun readStore(): WorkflowStore {
        val raw = settings.getString(WORKFLOW_KEY, "")
        if (raw.isBlank()) return WorkflowStore()
        return runCatching { json.decodeFromString(WorkflowStore.serializer(), raw) }
            .getOrElse { WorkflowStore() }
    }

    private fun writeStore(store: WorkflowStore) {
        settings.putString(WORKFLOW_KEY, json.encodeToString(WorkflowStore.serializer(), store))
    }
}
