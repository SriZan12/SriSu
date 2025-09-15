import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    
    init(){
        Platform_iosKt.debugBuild()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
