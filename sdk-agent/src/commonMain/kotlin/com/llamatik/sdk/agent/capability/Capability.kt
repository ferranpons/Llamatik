package com.llamatik.sdk.agent.capability

data class Capability(
    val id: String,
    val displayName: String,
    val isAvailable: Boolean,
)

object KnownCapabilities {
    const val CALENDAR = "calendar"
    const val REMINDERS = "reminders"
    const val CLIPBOARD = "clipboard"
    const val SHARE = "share"
    const val BROWSER = "browser"
    const val MAPS = "maps"
    const val CONTACTS = "contacts"
    const val NOTIFICATIONS = "notifications"
    const val ACCESSIBILITY = "accessibility"
    const val STORAGE = "storage"
    const val OPEN_APPS = "open_apps"
    const val WIFI = "wifi"
    const val BLUETOOTH = "bluetooth"
    const val TORCH = "torch"
    const val LOCATION = "location"
    const val CAMERA = "camera"
    const val PHOTOS = "photos"
    const val NETWORK = "network"
    const val OPEN_URL = "open_url"
}

interface PlatformCapabilityProvider {
    fun discoverCapabilities(): Set<Capability>
}
