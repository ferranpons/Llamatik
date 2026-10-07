package com.llamatik.sdk.agent.capability

class IosCapabilityProvider : PlatformCapabilityProvider {
    override fun discoverCapabilities(): Set<Capability> = setOf(
        // Currently stubbed — will become true once EventKit bridge is wired
        Capability(KnownCapabilities.CALENDAR, "Calendar", false),
        Capability(KnownCapabilities.REMINDERS, "Reminders", false),
        // UIPasteboard bridge not yet implemented
        Capability(KnownCapabilities.CLIPBOARD, "Clipboard", false),
        // UIActivityViewController requires presentation context
        Capability(KnownCapabilities.SHARE, "Share", false),
        // UIApplication.openURL — supported
        Capability(KnownCapabilities.BROWSER, "Browser", true),
        // CNContactStore bridge not yet wired
        Capability(KnownCapabilities.CONTACTS, "Contacts", false),
        // UNUserNotificationCenter — supported but permission required
        Capability(KnownCapabilities.NOTIFICATIONS, "Notifications", true),
        Capability(KnownCapabilities.STORAGE, "Storage", false),
        // URL schemes can open apps
        Capability(KnownCapabilities.OPEN_APPS, "Open Apps (URL schemes)", false),
        Capability(KnownCapabilities.OPEN_URL, "Open URL", true),
        Capability(KnownCapabilities.NETWORK, "Network", true),
        Capability(KnownCapabilities.CAMERA, "Camera", false),
        Capability(KnownCapabilities.PHOTOS, "Photos", false),
        Capability(KnownCapabilities.LOCATION, "Location", false),
        Capability(KnownCapabilities.MAPS, "Maps", false),
        Capability(KnownCapabilities.ACCESSIBILITY, "Accessibility", false),
        Capability(KnownCapabilities.TORCH, "Torch", false),
        Capability(KnownCapabilities.WIFI, "WiFi", false),
        Capability(KnownCapabilities.BLUETOOTH, "Bluetooth", false),
    )
}
