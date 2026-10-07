package com.llamatik.app.feature.entitlement

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Placeholder for Android/iOS — no real billing yet.
// Set ENTITLEMENT_DEBUG_PREMIUM_KEY = true in Settings to unlock all gates during QA/dev.
internal const val ENTITLEMENT_DEBUG_PREMIUM_KEY = "entitlement_debug_premium"

class MobileEntitlementRepository(
    private val settings: Settings,
) : EntitlementRepository {
    private val _isPremium = MutableStateFlow(readPremium())
    override val isPremium: Flow<Boolean> = _isPremium.asStateFlow()

    private fun readPremium(): Boolean = settings.getBoolean(ENTITLEMENT_DEBUG_PREMIUM_KEY, false)

    override suspend fun canUseCompanionMode(): Boolean = _isPremium.value
    override suspend fun canImportCustomModels(): Boolean = _isPremium.value
    override suspend fun canUseAgentTools(): Boolean = _isPremium.value

    override suspend fun refreshEntitlements() {
        _isPremium.value = readPremium()
    }

    fun setDebugPremium(enabled: Boolean) {
        settings.putBoolean(ENTITLEMENT_DEBUG_PREMIUM_KEY, enabled)
        _isPremium.value = enabled
    }
}
