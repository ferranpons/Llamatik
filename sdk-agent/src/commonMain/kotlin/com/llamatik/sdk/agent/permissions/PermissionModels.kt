package com.llamatik.sdk.agent.permissions

enum class PermissionState {
    GRANTED,
    DENIED,
    ASK_EVERY_TIME,
    REQUIRE_CONFIRMATION,
    NOT_SET,
}

data class PermissionDecision(
    val permissionId: String,
    val state: PermissionState,
)

data class PermissionPolicy(
    val defaultState: PermissionState = PermissionState.ASK_EVERY_TIME,
    val overrides: Map<String, PermissionState> = emptyMap(),
)

object KnownPermissions {
    const val CALENDAR = "calendar"
    const val CONTACTS = "contacts"
    const val REMINDERS = "reminders"
    const val CLIPBOARD = "clipboard"
    const val FILES = "files"
    const val NOTIFICATIONS = "notifications"
    const val OPEN_APPS = "open_apps"
    const val LOCATION = "location"
    const val SETTINGS = "settings"
    const val CAMERA = "camera"
    const val PHOTOS = "photos"
    const val NETWORK = "network"
    const val SHARE = "share"
    const val OPEN_URL = "open_url"
    const val ACCESSIBILITY = "accessibility"
}
