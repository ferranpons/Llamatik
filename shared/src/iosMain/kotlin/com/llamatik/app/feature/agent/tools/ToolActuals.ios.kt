package com.llamatik.app.feature.agent.tools

import com.llamatik.app.feature.agent.AgentTool
import com.llamatik.app.feature.agent.AgentToolResult
import kotlinx.serialization.json.JsonObject
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class ReminderTool actual constructor() : AgentTool {
    override val id = "reminder"
    override val displayName = "Reminder"
    override val description = "Create a reminder via the Reminders app"
    override val schema = reminderSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val title = reminderExtractTitle(input) ?: return AgentToolResult.Failure("Missing title")
        // Open the Reminders app via URL scheme; deep creation requires EventKit entitlement.
        val url = NSURL(string = "x-apple-reminderkit://")
        return if (UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            AgentToolResult.Success("Opened Reminders app for: $title")
        } else {
            AgentToolResult.Failure("Reminders URL scheme not available")
        }
    }
}

actual class OpenAppTool actual constructor() : AgentTool {
    override val id = "open_app"
    override val displayName = "Open App"
    override val description = "Open an app via URL scheme"
    override val schema = openAppSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val target = openAppExtractTarget(input) ?: return AgentToolResult.Failure("Missing target URL scheme")
        val url = NSURL(string = target) ?: return AgentToolResult.Failure("Invalid URL scheme")
        return if (UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            AgentToolResult.Success("Opened: $target")
        } else {
            AgentToolResult.Failure("Cannot open URL scheme: $target")
        }
    }
}

actual class DeviceControlTool actual constructor() : AgentTool {
    override val id = "device_control"
    override val displayName = "Device Control"
    override val description = "Open system settings panels"
    override val schema = deviceControlSchema
    override fun isSupported() = true

    override suspend fun execute(input: JsonObject): AgentToolResult {
        val action = deviceControlExtractAction(input) ?: return AgentToolResult.Failure("Missing action")
        val urlString = when (action.lowercase()) {
            "settings" -> UIApplication.openSettingsURLString
            "wifi" -> "App-Prefs:root=WIFI"
            "bluetooth" -> "App-Prefs:root=Bluetooth"
            else -> return AgentToolResult.Failure("Unsupported action: $action")
        }
        val url = NSURL(string = urlString)
        return if (UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
            AgentToolResult.Success("Opened settings: $action")
        } else {
            AgentToolResult.Failure("Cannot open settings URL for: $action")
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
        return when (action.lowercase()) {
            "copy" -> {
                val text = systemInteractionExtractText(input) ?: return AgentToolResult.Failure("Missing text")
                platform.UIKit.UIPasteboard.generalPasteboard.string = text
                AgentToolResult.Success("Copied to clipboard")
            }
            "open_url" -> {
                val url = systemInteractionExtractUrl(input) ?: return AgentToolResult.Failure("Missing URL")
                val nsUrl = NSURL(string = url) ?: return AgentToolResult.Failure("Invalid URL")
                if (UIApplication.sharedApplication.canOpenURL(nsUrl)) {
                    UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any?>(), completionHandler = null)
                    AgentToolResult.Success("Opened URL: $url")
                } else {
                    AgentToolResult.Failure("Cannot open URL: $url")
                }
            }
            else -> AgentToolResult.Failure("Unsupported action: $action")
        }
    }
}
