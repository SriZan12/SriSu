import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    
    init(){
        NativePlanRuntime.shared.install(value: IosPlanDevice())
        NativeChatMediaRuntime.shared.installFactory { IosChatMedia() }
        NativeChatVideoRuntime.shared.install { native in (native as! IosChatMedia).videoViewController() }
        Platform_iosKt.debugBuild()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView().onOpenURL { url in PlatformEntry.shared.openUrl(url: url.absoluteString) }
        }
    }
}
