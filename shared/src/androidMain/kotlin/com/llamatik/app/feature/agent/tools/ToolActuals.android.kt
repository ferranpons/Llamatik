package com.llamatik.app.feature.agent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.llamatik.app.feature.agent.AgentTool
import com.llamatik.app.feature.agent.AgentToolResult
import kotlinx.serialization.json.JsonObject
import org.koin.mp.KoinPlatform

actual class ReminderTool actual constructor() : AgentTool {
    override val id = "reminder"
    override val displayName = "Reminder"
    override val description = "Create a reminder using the system clock/alarm app"
    override val schema = reminderSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val title = reminderExtractTitle(input) ?: return AgentToolResult.Failure("Missing title")
        return try {
            val context = KoinPlatform.getKoin().get<Context>()
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, title)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AgentToolResult.Success("Reminder set: $title")
        } catch (e: Exception) {
            AgentToolResult.Failure("Failed to set reminder: ${e.message}")
        }
    }
}

actual class OpenAppTool actual constructor() : AgentTool {
    override val id = "open_app"
    override val displayName = "Open App"
    override val description = "Open an installed app by package name or URI"
    override val schema = openAppSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val target = openAppExtractTarget(input) ?: return AgentToolResult.Failure("Missing target")
        return try {
            val context = KoinPlatform.getKoin().get<Context>()
            val pm = context.packageManager
            // Try as package name first
            val launchIntent = pm.getLaunchIntentForPackage(target)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                AgentToolResult.Success("Opened app: $target")
            } else {
                // Fall back to URI intent (e.g. market:// or https://)
                val uri = Uri.parse(target)
                val uriIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uriIntent)
                AgentToolResult.Success("Opened URI: $target")
            }
        } catch (e: Exception) {
            AgentToolResult.Failure("Failed to open app: ${e.message}")
        }
    }
}

actual class DeviceControlTool actual constructor() : AgentTool {
    override val id = "device_control"
    override val displayName = "Device Control"
    override val description = "Open system settings panels (no restricted toggles)"
    override val schema = deviceControlSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val action = deviceControlExtractAction(input) ?: return AgentToolResult.Failure("Missing action")
        return try {
            val context = KoinPlatform.getKoin().get<Context>()
            val settingsAction = when (action.lowercase()) {
                "settings", "main" -> Settings.ACTION_SETTINGS
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "date_time" -> Settings.ACTION_DATE_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                else -> return AgentToolResult.Failure("Unsupported action: $action")
            }
            val intent = Intent(settingsAction).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AgentToolResult.Success("Opened settings: $action")
        } catch (e: Exception) {
            AgentToolResult.Failure("Failed to open settings: ${e.message}")
        }
    }
}

actual class SystemInteractionTool actual constructor() : AgentTool {
    override val id = "system_interaction"
    override val displayName = "System Interaction"
    override val description = "Copy to clipboard, open URL, or share text"
    override val schema = systemInteractionSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val action = systemInteractionExtractAction(input) ?: return AgentToolResult.Failure("Missing action")
        val context = KoinPlatform.getKoin().get<Context>()
        return try {
            when (action.lowercase()) {
                "copy" -> {
                    val text = systemInteractionExtractText(input) ?: return AgentToolResult.Failure("Missing text")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("llamatik", text))
                    AgentToolResult.Success("Copied to clipboard")
                }
                "open_url" -> {
                    val url = systemInteractionExtractUrl(input) ?: return AgentToolResult.Failure("Missing URL")
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    AgentToolResult.Success("Opened URL: $url")
                }
                "share" -> {
                    val text = systemInteractionExtractText(input) ?: return AgentToolResult.Failure("Missing text")
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    AgentToolResult.Success("Share dialog opened")
                }
                else -> AgentToolResult.Failure("Unsupported action: $action")
            }
        } catch (e: Exception) {
            AgentToolResult.Failure("System interaction failed: ${e.message}")
        }
    }
}
