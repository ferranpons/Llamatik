package com.llamatik.app.feature.companion

import com.russhwolf.settings.Settings

private const val COMPANION_MODE_KEY = "companion_mode"
private const val COMPANION_CHOSEN_KEY = "companion_chosen"

class CompanionRepository(private val settings: Settings) {

    fun hasChosen(): Boolean = settings.getBoolean(COMPANION_CHOSEN_KEY, false)

    fun getCompanionMode(): CompanionMode {
        val name = settings.getString(COMPANION_MODE_KEY, CompanionMode.Friend.name)
        return runCatching { CompanionMode.valueOf(name) }.getOrElse { CompanionMode.Friend }
    }

    fun setCompanionMode(mode: CompanionMode) {
        settings.putString(COMPANION_MODE_KEY, mode.name)
        settings.putBoolean(COMPANION_CHOSEN_KEY, true)
    }

    fun reset() {
        settings.putBoolean(COMPANION_CHOSEN_KEY, false)
    }
}
