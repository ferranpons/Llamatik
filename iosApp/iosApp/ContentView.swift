import SwiftUI
import shared


struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = Main_iosKt.homeScreenViewController()
        ReviewEntryPoint.shared.setContext(context: vc)
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}