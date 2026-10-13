import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(SriSuNotifications.self) var notifications
    
    init(){
        NativeChatRuntime.shared.install(value: IosChatDriver())
        NativeChatMediaRuntime.shared.install(value: IosChatMedia())
        Platform_iosKt.debugBuild()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView().onOpenURL { url in
                #if DEBUG
                if url.scheme == "srisu", url.host == "diagnostics-test",
                   UserDefaults.standard.bool(forKey: "srisu.diagnostics") {
                    if url.path == "/fatal" { fatalError("SriSu controlled test crash") }
                    if url.path == "/nonfatal" { CrashReporter.shared.report(code:.testNonfatal) }
                    return
                }
                #endif
                PlatformEntry.shared.openUrl(url: url.absoluteString)
            }
        }
    }
}
