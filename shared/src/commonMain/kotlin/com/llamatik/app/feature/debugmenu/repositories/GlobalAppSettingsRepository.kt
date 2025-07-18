package com.llamatik.app.feature.debugmenu.repositories

import com.llamatik.app.localization.AvailableLanguages
import com.llamatik.app.localization.Localization
import com.llamatik.app.platform.Environment
import com.llamatik.app.platform.ServerEnvironment
import com.russhwolf.settings.Settings

const val ENVIRONMENT_KEY = "ENVIRONMENT_KEY"
const val LANGUAGE_KEY = "LANGUAGE_KEY"
const val MOCKED_CONTENT_KEY = "MOCKED_CONTENT_KEY"
const val MOCKED_USER_KEY = "MOCKED_USER_KEY"

class GlobalAppSettingsRepository(
    private val settings: Settings,
    private val localization: Localization
) {
    fun getCurrentEnvironment(): Environment {
        return when (settings.getString(ENVIRONMENT_KEY, ServerEnvironment.PRODUCTION.name)) {
            ServerEnvironment.PRODUCTION.name -> {
                ServerEnvironment.PRODUCTION
            }

            ServerEnvironment.PREPRODUCTION.name -> {
                ServerEnvironment.PREPRODUCTION
            }

            else -> {
                ServerEnvironment.LOCALHOST
            }
        }
    }

    fun getCurrentLanguage(): AvailableLanguages {
        return when (settings.getString(LANGUAGE_KEY, AvailableLanguages.EN.name)) {
            AvailableLanguages.EN.name -> AvailableLanguages.EN
            AvailableLanguages.ES.name -> AvailableLanguages.ES
            AvailableLanguages.FR.name -> AvailableLanguages.FR
            AvailableLanguages.IT.name -> AvailableLanguages.IT
            AvailableLanguages.DE.name -> AvailableLanguages.DE
            else -> {
                AvailableLanguages.EN
            }
        }
    }

    fun isMockedContentEnabled(): Boolean {
        return settings.getBoolean(MOCKED_CONTENT_KEY, false)
    }

    fun setMockedContentCheckStatus(checked: Boolean) {
        settings.putBoolean(MOCKED_CONTENT_KEY, checked)
    }

    fun setSelectedEnvironment(environment: Environment) {
        settings.putString(ENVIRONMENT_KEY, environment.name)
    }

    fun setSelectedLanguage(language: AvailableLanguages) {
        settings.putString(LANGUAGE_KEY, language.name)
    }

    fun isMockedUserEnabled(): Boolean {
        return settings.getBoolean(MOCKED_USER_KEY, false)
    }
}
