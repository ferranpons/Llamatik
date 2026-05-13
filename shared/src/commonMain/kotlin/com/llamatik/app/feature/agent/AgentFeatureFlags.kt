package com.llamatik.app.feature.agent

import com.russhwolf.settings.Settings

private const val FLAG_AGENT_ENABLED = "agent.enabled"
private const val FLAG_REMINDERS = "agent.reminders.enabled"
private const val FLAG_OPEN_APPS = "agent.openApps.enabled"
private const val FLAG_DEVICE_CONTROL = "agent.deviceControl.enabled"
private const val FLAG_SYSTEM_INTERACTION = "agent.systemInteraction.enabled"

class AgentFeatureFlags(private val settings: Settings) {
    fun isAgentEnabled(): Boolean = settings.getBoolean(FLAG_AGENT_ENABLED, false)
    fun isRemindersEnabled(): Boolean = settings.getBoolean(FLAG_REMINDERS, false)
    fun isOpenAppsEnabled(): Boolean = settings.getBoolean(FLAG_OPEN_APPS, false)
    fun isDeviceControlEnabled(): Boolean = settings.getBoolean(FLAG_DEVICE_CONTROL, false)
    fun isSystemInteractionEnabled(): Boolean = settings.getBoolean(FLAG_SYSTEM_INTERACTION, false)

    fun setAgentEnabled(value: Boolean) = settings.putBoolean(FLAG_AGENT_ENABLED, value)
    fun setRemindersEnabled(value: Boolean) = settings.putBoolean(FLAG_REMINDERS, value)
    fun setOpenAppsEnabled(value: Boolean) = settings.putBoolean(FLAG_OPEN_APPS, value)
    fun setDeviceControlEnabled(value: Boolean) = settings.putBoolean(FLAG_DEVICE_CONTROL, value)
    fun setSystemInteractionEnabled(value: Boolean) = settings.putBoolean(FLAG_SYSTEM_INTERACTION, value)
}
