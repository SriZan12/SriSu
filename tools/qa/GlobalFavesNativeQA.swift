import UIKit
import ComposeApp

enum ChatMediaError: Error { case invalid }

/// Separate simulator-only application. Never reads a real SriSu account.
@main final class GlobalFavesQA: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let args = ProcessInfo.processInfo.arguments
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.overrideUserInterfaceStyle = args.contains("-qa-dark") ? .dark : .light
        window?.rootViewController = GlobalFavesNativeQAKt.globalFavesQaController(
            viewer: args.contains("-qa-viewer"), large: args.contains("-qa-large"),
            dark: args.contains("-qa-dark"), compact: args.contains("-qa-compact"))
        window?.makeKeyAndVisible()
        return true
    }
}
