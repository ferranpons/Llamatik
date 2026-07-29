package com.llamatik.sdk.agent.capability

class IosCapabilityProvider : PlatformCapabilityProvider {
    override fun discoverCapabilities(): Set<Capability> = setOf(
        Capability(KnownCapabilities.CALENDAR, "Calendar", true),
        Capability(KnownCapabilities.REMINDERS, "Reminders", true),
        Capability(KnownCapabilities.CLIPBOARD, "Clipboard", true),
        Capability(KnownCapabilities.SHARE, "Share", true),
        Capability(KnownCapabilities.BROWSER, "Browser", true),
        Capability(KnownCapabilities.CONTACTS, "Contacts", true),
        Capability(KnownCapabilities.NOTIFICATIONS, "Notifications", true),
        Capability(KnownCapabilities.STORAGE, "Storage", true),
        Capability(KnownCapabilities.OPEN_APPS, "Open Apps (URL schemes)", true),
        Capability(KnownCapabilities.OPEN_URL, "Open URL", true),
        Capability(KnownCapabilities.NETWORK, "Network", true),
        Capability(KnownCapabilities.CAMERA, "Camera", true),
        Capability(KnownCapabilities.PHOTOS, "Photos", true),
        Capability(KnownCapabilities.LOCATION, "Location", true),
        Capability(KnownCapabilities.MAPS, "Maps", true),
        Capability(KnownCapabilities.ACCESSIBILITY, "Accessibility", false),
        Capability(KnownCapabilities.TORCH, "Torch", true),
        Capability(KnownCapabilities.WIFI, "WiFi", false),
        Capability(KnownCapabilities.BLUETOOTH, "Bluetooth", true),
    )
}
