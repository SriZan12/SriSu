import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    
    init(){
        NativeChatRuntime.shared.install(value: IosChatDriver())
        NativeChatMediaRuntime.shared.installFactory { IosChatMedia() }
        NativeChatVideoRuntime.shared.install { driver in (driver as! IosChatMedia).videoViewController() }
        Platform_iosKt.debugBuild()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView().onOpenURL { url in PlatformEntry.shared.openUrl(url: url.absoluteString) }
        }
    }
}
