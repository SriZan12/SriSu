import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    var body: some View {
        ZStack {
            ComposeView()
                .ignoresSafeArea(edges: .all)
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .accessibilityHidden(scenePhase != .active)
            if scenePhase != .active {
                Color(uiColor: .systemBackground).ignoresSafeArea()
                Text("SriSu").font(.title)
            }
        }
    }
}



