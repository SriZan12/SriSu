import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    
    init(){
        NativeChatRuntime.shared.install(value: IosChatDriver())
        NativeChatMediaRuntime.shared.install(value: IosChatMedia())
        Platform_iosKt.debugBuild()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
