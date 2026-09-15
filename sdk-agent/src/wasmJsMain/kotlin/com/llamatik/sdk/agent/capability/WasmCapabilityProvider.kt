package com.llamatik.sdk.agent.capability

class WasmCapabilityProvider : PlatformCapabilityProvider {
    override fun discoverCapabilities(): Set<Capability> = setOf(
        Capability(KnownCapabilities.CLIPBOARD, "Clipboard", false),
        Capability(KnownCapabilities.BROWSER, "Browser", true),
        Capability(KnownCapabilities.OPEN_URL, "Open URL", true),
        Capability(KnownCapabilities.NETWORK, "Network", true),
        Capability(KnownCapabilities.CALENDAR, "Calendar", false),
        Capability(KnownCapabilities.REMINDERS, "Reminders", false),
        Capability(KnownCapabilities.CONTACTS, "Contacts", false),
        Capability(KnownCapabilities.SHARE, "Share", false),
        Capability(KnownCapabilities.NOTIFICATIONS, "Notifications", false),
        Capability(KnownCapabilities.STORAGE, "Storage", false),
        Capability(KnownCapabilities.OPEN_APPS, "Open Apps", false),
        Capability(KnownCapabilities.CAMERA, "Camera", false),
        Capability(KnownCapabilities.PHOTOS, "Photos", false),
        Capability(KnownCapabilities.LOCATION, "Location", false),
        Capability(KnownCapabilities.TORCH, "Torch", false),
        Capability(KnownCapabilities.WIFI, "WiFi", false),
        Capability(KnownCapabilities.BLUETOOTH, "Bluetooth", false),
        Capability(KnownCapabilities.MAPS, "Maps", false),
        Capability(KnownCapabilities.ACCESSIBILITY, "Accessibility", false),
    )
}
