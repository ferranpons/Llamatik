package com.llamatik.sdk.entitlement

import kotlinx.coroutines.flow.Flow

interface EntitlementRepository {
    val isPremium: Flow<Boolean>
    suspend fun canUseCompanionMode(): Boolean
    suspend fun canImportCustomModels(): Boolean
    suspend fun canUseAgentTools(): Boolean
    suspend fun refreshEntitlements()
}
