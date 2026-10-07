package com.llamatik.sdk.agent

import com.llamatik.sdk.agent.audit.AgentAuditEntry
import com.llamatik.sdk.agent.audit.AgentAuditRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditRepositoryTest {

    private fun repo() = AgentAuditRepository(MapSettings())

    private fun entry(toolId: String, success: Boolean = true) = AgentAuditEntry(
        id = "${toolId}_1",
        toolId = toolId,
        toolDisplayName = toolId,
        argumentsSummary = "",
        durationMs = 10L,
        success = success,
        platform = "test",
        riskLevel = "LOW",
        createdAtMs = 1000L,
        sessionId = "s1",
    )

    @Test
    fun emptyRepoReturnsNoEntries() = runTest {
        assertTrue(repo().getAll().isEmpty())
    }

    @Test
    fun appendAndRetrieve() = runTest {
        val r = repo()
        r.append(entry("calendar.create_event"))
        val all = r.getAll()
        assertEquals(1, all.size)
        assertEquals("calendar.create_event", all[0].toolId)
    }

    @Test
    fun clearAllRemovesEntries() = runTest {
        val r = repo()
        r.append(entry("t1"))
        r.append(entry("t2"))
        r.clearAll()
        assertTrue(r.getAll().isEmpty())
    }

    @Test
    fun getByToolFilters() = runTest {
        val r = repo()
        r.append(entry("calendar.create_event"))
        r.append(entry("reminder.create"))
        val cal = r.getByTool("calendar.create_event")
        assertEquals(1, cal.size)
        assertEquals("calendar.create_event", cal[0].toolId)
    }
}
