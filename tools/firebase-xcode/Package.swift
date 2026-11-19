// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "SriSuFirebaseSetup", platforms: [.macOS(.v13)],
    dependencies: [.package(url: "https://github.com/tuist/XcodeProj.git", exact: "8.27.7")],
    targets: [.executableTarget(name: "SriSuFirebaseSetup", dependencies: ["XcodeProj"], path: "Sources")])
