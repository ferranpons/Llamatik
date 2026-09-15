package com.llamatik.sdk.agent

import com.llamatik.sdk.agent.permissions.KnownPermissions
import com.llamatik.sdk.agent.permissions.PermissionManager
import com.llamatik.sdk.agent.permissions.PermissionPolicy
import com.llamatik.sdk.agent.permissions.PermissionRepository
import com.llamatik.sdk.agent.permissions.PermissionState
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionManagerTest {

    private fun manager(
        platformGranted: Boolean = true,
        policy: PermissionPolicy = PermissionPolicy(),
    ): PermissionManager {
        val repo = PermissionRepository(MapSettings())
        return PermissionManager(
            repository = repo,
            policy = policy,
            platformPermissionCheck = { platformGranted },
        )
    }

    @Test
    fun defaultPolicyIsAskEveryTime() = runTest {
        val m = manager()
        val decision = m.check(KnownPermissions.CALENDAR)
        assertEquals(PermissionState.ASK_EVERY_TIME, decision.state)
    }

    @Test
    fun grantedPermissionIsGranted() = runTest {
        val repo = PermissionRepository(MapSettings())
        repo.setDecision(KnownPermissions.CALENDAR, PermissionState.GRANTED)
        val m = PermissionManager(repo, PermissionPolicy(), platformPermissionCheck = { true })
        val decision = m.check(KnownPermissions.CALENDAR)
        assertEquals(PermissionState.GRANTED, decision.state)
    }

    @Test
    fun grantedButPlatformDeniedBecomesGranted() = runTest {
        // Platform check returns false; we report DENIED
        val repo = PermissionRepository(MapSettings())
        repo.setDecision(KnownPermissions.CALENDAR, PermissionState.GRANTED)
        val m = PermissionManager(repo, PermissionPolicy(), platformPermissionCheck = { false })
        val decision = m.check(KnownPermissions.CALENDAR)
        assertEquals(PermissionState.DENIED, decision.state)
    }

    @Test
    fun deniedPermissionIsDenied() = runTest {
        val repo = PermissionRepository(MapSettings())
        repo.setDecision(KnownPermissions.CONTACTS, PermissionState.DENIED)
        val m = PermissionManager(repo, PermissionPolicy(), platformPermissionCheck = { true })
        val decision = m.check(KnownPermissions.CONTACTS)
        assertEquals(PermissionState.DENIED, decision.state)
    }

    @Test
    fun policyOverrideApplied() = runTest {
        val policy = PermissionPolicy(overrides = mapOf(KnownPermissions.CLIPBOARD to PermissionState.GRANTED))
        val m = manager(policy = policy)
        // CLIPBOARD has policy override GRANTED (no stored decision)
        val decision = m.check(KnownPermissions.CLIPBOARD)
        assertEquals(PermissionState.GRANTED, decision.state)
    }

    @Test
    fun checkAllReturnsAllPermissions() = runTest {
        val m = manager()
        val results = m.checkAll(setOf(KnownPermissions.CALENDAR, KnownPermissions.CONTACTS))
        assertEquals(2, results.size)
    }
}
