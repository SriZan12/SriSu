// Idempotent project setup, using the Xcode object model (not text replacement).
import Foundation
import PathKit
import XcodeProj
let root = Path(CommandLine.arguments[1])
let path = root + "iosApp/iosApp.xcodeproj"
let project = try XcodeProj(path: path)
let pbx = project.pbxproj
let app = pbx.nativeTargets.first { $0.name == "iosApp" }!
// XcodeProj 8.27 does not preserve Xcode's synchronized-folder configuration
// anchors. Keep the same xcconfig through a regular object-model reference so
// PRODUCT_NAME, versions and the release bundle identifier remain available.
let configPath = "Configuration/Config.xcconfig"
let baseConfig: PBXFileReference
if let existing = pbx.fileReferences.first(where: { $0.path == configPath }) {
    baseConfig = existing
} else {
    baseConfig = PBXFileReference(sourceTree: .sourceRoot, lastKnownFileType: "text.xcconfig", path: configPath)
    pbx.add(object: baseConfig)
}
if !pbx.projects.first!.mainGroup.children.contains(baseConfig) {
    pbx.projects.first!.mainGroup.children.append(baseConfig)
}
for config in app.buildConfigurationList!.buildConfigurations where config.baseConfiguration == nil {
    config.baseConfiguration = baseConfig
}
let products = Set(["FirebaseCore", "FirebaseMessaging", "FirebaseCrashlytics"])
// Link Firebase products only to the application target.
let oldProducts = pbx.buildFiles.compactMap { $0.product }.filter { products.contains($0.productName) }
for target in pbx.nativeTargets {
    target.packageProductDependencies?.removeAll { products.contains($0.productName) }
    for phase in target.buildPhases.compactMap({ $0 as? PBXFrameworksBuildPhase }) {
        for file in phase.files ?? [] where products.contains(file.product?.productName ?? "") {
            phase.files?.removeAll { $0 == file }; pbx.delete(object: file)
        }
    }
}
for dependency in Set(oldProducts) { pbx.delete(object: dependency) }
let package: XCRemoteSwiftPackageReference
if let existing = pbx.projects.first!.remotePackages.first(where: { $0.repositoryURL == "https://github.com/firebase/firebase-ios-sdk" }) {
    package = existing
} else {
    package = XCRemoteSwiftPackageReference(repositoryURL: "https://github.com/firebase/firebase-ios-sdk", versionRequirement: .exact("13.0.1"))
    pbx.add(object: package)
    pbx.projects.first!.remotePackages.append(package)
}
package.versionRequirement = .exact("13.0.1")
let frameworks = app.buildPhases.compactMap { $0 as? PBXFrameworksBuildPhase }.first!
for name in products.sorted() {
    let dependency = XCSwiftPackageProductDependency(productName: name, package: package)
    pbx.add(object: dependency); app.packageProductDependencies = (app.packageProductDependencies ?? []) + [dependency]
    let file = PBXBuildFile(product: dependency); pbx.add(object:file)
    frameworks.files = (frameworks.files ?? []) + [file]
}
for phase in pbx.shellScriptBuildPhases where phase.name?.contains("Crashlytics") == true || phase.shellScript?.contains("Crashlytics/run") == true || phase.shellScript?.contains("tools/firebase-symbols.sh") == true {
    for target in pbx.nativeTargets { target.buildPhases.removeAll { $0 == phase } }
    pbx.delete(object: phase)
}
let phase = PBXShellScriptBuildPhase(name: "SriSu Firebase release validation and symbols", inputPaths: [], shellPath: "/bin/sh", shellScript: "\"$SRCROOT/../tools/firebase-symbols.sh\"\n")
phase.showEnvVarsInLog = false
pbx.add(object: phase); app.buildPhases.append(phase)
for config in app.buildConfigurationList!.buildConfigurations {
    config.buildSettings["OTHER_LDFLAGS"] = ["$(inherited)", "-ObjC"]
    config.buildSettings["DEBUG_INFORMATION_FORMAT"] = "dwarf-with-dsym"
    config.buildSettings["CODE_SIGN_ENTITLEMENTS"] = "iosApp/SriSu.entitlements"
    config.buildSettings["SRISU_APS_ENVIRONMENT"] = config.name == "Release" ? "production" : "development"
    // The upload helper reads the linked binary and dSYM and runs only in configured releases.
    config.buildSettings["ENABLE_USER_SCRIPT_SANDBOXING"] = "NO"
}
var attributes = pbx.projects.first!.targetAttributes
var appAttributes = attributes[app] ?? [:]
appAttributes["SystemCapabilities"] = ["com.apple.Push": ["enabled": 1]]
attributes[app] = appAttributes; pbx.projects.first!.targetAttributes = attributes
try project.write(path:path)
// Debug has a separate plist for local-network access. Both configurations must
// disable collection and automatic registration before FirebaseApp.configure().
for name in ["Info.plist", "Info.Debug.plist"] {
    let plistPath = root + "iosApp/iosApp/\(name)"
    var plist = try PropertyListSerialization.propertyList(from:Data(contentsOf:plistPath.url),format:nil) as! [String:Any]
    plist["FirebaseCrashlyticsCollectionEnabled"] = false
    plist["FirebaseMessagingAutoInitEnabled"] = false
    plist["FirebaseMessagingInstallationIdEnabled"] = true
    plist["FirebaseAppDelegateProxyEnabled"] = false
    plist["FIREBASE_ANALYTICS_COLLECTION_ENABLED"] = false
    try PropertyListSerialization.data(fromPropertyList:plist,format:.xml,options:0).write(to:plistPath.url)
}
print("Configured Firebase only on iosApp, with opt-in diagnostics and APNs capability.")
