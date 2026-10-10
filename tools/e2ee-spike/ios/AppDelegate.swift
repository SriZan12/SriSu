import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        let view = UIViewController()
        view.view.backgroundColor = .systemBackground
        let label = UILabel()
        label.text = "SriSu native encryption evaluation"
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.view.centerYAnchor)
        ])
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = view
        window?.makeKeyAndVisible()
        return true
    }
}
