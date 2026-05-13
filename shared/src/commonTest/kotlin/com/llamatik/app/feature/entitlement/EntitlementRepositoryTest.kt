package com.llamatik.app.feature.entitlement

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnlockedEntitlementRepositoryTest {

    private val repo = UnlockedEntitlementRepository()

    @Test
    fun isPremium_alwaysTrue() = runTest {
        assertTrue(repo.isPremium.first())
    }

    @Test
    fun canUseCompanionMode_alwaysTrue() = runTest {
        assertTrue(repo.canUseCompanionMode())
    }

    @Test
    fun canImportCustomModels_alwaysTrue() = runTest {
        assertTrue(repo.canImportCustomModels())
    }

    @Test
    fun canUseAgentTools_alwaysTrue() = runTest {
        assertTrue(repo.canUseAgentTools())
    }

    @Test
    fun refreshEntitlements_noOp() = runTest {
        repo.refreshEntitlements()
        assertTrue(repo.isPremium.first())
    }
}

class MobileEntitlementRepositoryTest {

    private val settings = MapSettings()
    private val repo = MobileEntitlementRepository(settings)

    @Test
    fun isPremium_defaultFalse() = runTest {
        assertFalse(repo.isPremium.first())
    }

    @Test
    fun canUseCompanionMode_lockedByDefault() = runTest {
        assertFalse(repo.canUseCompanionMode())
    }

    @Test
    fun canImportCustomModels_lockedByDefault() = runTest {
        assertFalse(repo.canImportCustomModels())
    }

    @Test
    fun canUseAgentTools_lockedByDefault() = runTest {
        assertFalse(repo.canUseAgentTools())
    }

    @Test
    fun setDebugPremium_unlocksAllGates() = runTest {
        repo.setDebugPremium(true)
        assertTrue(repo.canUseCompanionMode())
        assertTrue(repo.canImportCustomModels())
        assertTrue(repo.canUseAgentTools())
    }

    @Test
    fun setDebugPremium_persistedAcrossRefresh() = runTest {
        repo.setDebugPremium(true)
        repo.refreshEntitlements()
        assertTrue(repo.isPremium.first())
    }

    @Test
    fun setDebugPremium_canBeLocked() = runTest {
        repo.setDebugPremium(true)
        repo.setDebugPremium(false)
        assertFalse(repo.isPremium.first())
        assertFalse(repo.canUseCompanionMode())
    }
}
