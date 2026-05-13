package com.llamatik.app.feature.companion

import com.russhwolf.settings.Settings

private const val COMPANION_MODE_KEY = "companion_mode"
private const val COMPANION_ENABLED_KEY = "companion_enabled"

class CompanionRepository(private val settings: Settings) {

    fun isCompanionEnabled(): Boolean = settings.getBoolean(COMPANION_ENABLED_KEY, false)

    fun setCompanionEnabled(enabled: Boolean) {
        settings.putBoolean(COMPANION_ENABLED_KEY, enabled)
    }

    fun getCompanionMode(): CompanionMode {
        val name = settings.getString(COMPANION_MODE_KEY, CompanionMode.Assistant.name)
        return runCatching { CompanionMode.valueOf(name) }.getOrElse { CompanionMode.Assistant }
    }

    fun setCompanionMode(mode: CompanionMode) {
        settings.putString(COMPANION_MODE_KEY, mode.name)
    }
}
