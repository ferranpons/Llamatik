import SwiftUI
import shared

@main
struct iOSApp: App {

    init() {
        HelperKt.doInitKoin()
        ReviewEntryPoint.shared.notifyAppLaunched()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
