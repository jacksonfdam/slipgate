import SlipgateKit
import SwiftUI

/// Hosts the Compose Multiplatform view controller built by the `:ios` Gradle module.
private struct ComposeContainer: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        EntryPointKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct SlipgateIosApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeContainer()
                .ignoresSafeArea()
                .preferredColorScheme(.dark)
        }
    }
}
