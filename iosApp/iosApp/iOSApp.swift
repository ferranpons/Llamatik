import SwiftUI
import shared

@main
struct iOSApp: App {

    init() {
        HelperKt.doInitKoin()

        let koin = KoinKt.koin
        let manager = koin.get(objCClass: ReviewRequestManager.self) as! ReviewRequestManager
        ReviewEntryPoint.shared.setManager(reviewRequestManager: manager)

        ReviewEntryPoint.shared.notifyAppLaunched()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
