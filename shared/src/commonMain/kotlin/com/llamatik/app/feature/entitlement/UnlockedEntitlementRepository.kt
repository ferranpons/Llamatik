package com.llamatik.app.feature.entitlement

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Always unlocked — used on Desktop and WASM.
class UnlockedEntitlementRepository : EntitlementRepository {
    override val isPremium: Flow<Boolean> = flowOf(true)
    override suspend fun canUseCompanionMode(): Boolean = true
    override suspend fun canImportCustomModels(): Boolean = true
    override suspend fun canUseAgentTools(): Boolean = true
    override suspend fun refreshEntitlements() = Unit
}
