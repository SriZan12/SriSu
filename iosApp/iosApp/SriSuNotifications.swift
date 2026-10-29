import Foundation
import UIKit
import UserNotifications
import Security
import FirebaseCore
import FirebaseMessaging
import FirebaseCrashlytics
import ComposeApp

/// All callbacks cross to Kotlin on the main queue; no payloads or provider errors are logged.
final class SriSuNotifications: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate, NotificationPlatform {
    static var current: SriSuNotifications?
    private var fid: String?
    private var permission = "unavailable"
    private var registering = false
    private var unregistering = false
    private var activeBinding: String?
    private var renewBinding = false
    private let defaults = UserDefaults.standard
    private var configured: Bool { FirebaseApp.app() != nil }
    private struct Binding: Codable { var id: String; var proof: String; var version: Int64; var identity: String }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        Self.current = self
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
            Messaging.messaging().delegate = self
            Crashlytics.crashlytics().setCustomValue(CrashReporter.shared.environment,forKey:"environment")
            Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(defaults.bool(forKey:"srisu.diagnostics"))
            if !defaults.bool(forKey:"srisu.diagnostics") { Crashlytics.crashlytics().deleteUnsentReports() }
        }
        UNUserNotificationCenter.current().delegate = self
        NativeNotifications.shared.install(value:self)
        return true
    }
    func applicationDidBecomeActive(_ application: UIApplication) { refresh() }
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        guard configured else { return }
        Messaging.messaging().apnsToken = token
        registerFCM()
    }
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        registering = false
        // Expected provider/network/provisioning failure, not a crash report.
    }
    func messaging(_ messaging: Messaging, didReceiveRegistration installationID: String?) {
        DispatchQueue.main.async { if self.activeBinding != nil && !self.unregistering { self.fid = installationID; self.publish() } }
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
        withCompletionHandler completion: @escaping (UNNotificationPresentationOptions) -> Void) {
        if isPlanReminder(notification.request) {
            completion([.banner, .sound])
            return
        }
        receive(notification.request.content.userInfo, tapped:false)
        completion([]) // Shared authenticated banner owns foreground presentation.
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completion: @escaping () -> Void) {
        let request = response.notification.request
        if isPlanReminder(request) {
            if let url = request.content.userInfo["url"] as? String {
                DispatchQueue.main.async { PlatformEntry.shared.openNotification(url:url, deliveryId:request.identifier) }
            }
        } else {
            receive(request.content.userInfo, tapped:true)
        }
        completion()
    }
    private func isPlanReminder(_ request: UNNotificationRequest) -> Bool {
        // Only locally scheduled reminders may supply the existing typed URL handoff.
        !(request.trigger is UNPushNotificationTrigger) && request.identifier.hasPrefix("plan-")
    }
    private func receive(_ data:[AnyHashable:Any], tapped:Bool) {
        DispatchQueue.main.async {
            NativeNotifications.shared.receive(schema:data["schema"] as? String, deliveryId:data["delivery_id"] as? String,
                expiresAt:data["expires_at"] as? String, tapped:tapped)
        }
    }
    func refresh() {
        UNUserNotificationCenter.current().getNotificationSettings { settings in
            DispatchQueue.main.async {
                switch settings.authorizationStatus {
                case .authorized, .provisional, .ephemeral: self.permission = "granted"
                case .notDetermined: self.permission = "not_determined"
                default: self.permission = "denied"
                }
                self.publish()
                if self.configured && self.activeBinding != nil && self.permission == "granted" {
                    UIApplication.shared.registerForRemoteNotifications()
                    if Messaging.messaging().apnsToken != nil { self.registerFCM() }
                }
            }
        }
    }
    private func registerFCM() {
        guard configured, activeBinding != nil, permission == "granted", fid == nil, !registering, !unregistering else { return }
        registering = true
        Messaging.messaging().isAutoInitEnabled = true
        Messaging.messaging().register { _ in DispatchQueue.main.async { self.registering = false } }
    }
    private func publish() {
        NativeNotifications.shared.update(configured:configured, permission:permission,
            diagnostics:defaults.bool(forKey:"srisu.diagnostics"), promptDismissed:defaults.bool(forKey:"srisu.notification.prompt"))
    }
    func requestPermission() {
        guard configured else { return }
        UNUserNotificationCenter.current().requestAuthorization(options:[.alert,.sound,.badge]) { _,_ in
            DispatchQueue.main.async { self.refresh() }
        }
    }
    func openSettings() {
        if let url=URL(string:UIApplication.openNotificationSettingsURLString) { UIApplication.shared.open(url) }
    }
    func dismissPrompt() { defaults.set(true,forKey:"srisu.notification.prompt");publish() }
    func registration(binding:String) -> InstallationRequest? {
        if activeBinding != binding { activeBinding=binding; refresh() }
        guard let fid else { return nil }
        let query:[String:Any] = [kSecClass as String:kSecClassGenericPassword,kSecAttrService as String:"com.srisu.notifications.v1",kSecAttrAccount as String:"binding"]
        var result: CFTypeRef?
        let status=SecItemCopyMatching(query.merging([kSecReturnData as String:true]) { _,new in new } as CFDictionary,&result)
        var saved:Binding
        if status == errSecSuccess, let data=result as? Data, let decoded=try? JSONDecoder().decode(Binding.self,from:data) {
            saved=decoded
        } else if status == errSecItemNotFound {
            var bytes=[UInt8](repeating:0,count:32)
            guard SecRandomCopyBytes(kSecRandomDefault,bytes.count,&bytes)==errSecSuccess else { return nil }
            let proof=Data(bytes).base64EncodedString().replacingOccurrences(of:"+",with:"-").replacingOccurrences(of:"/",with:"_").replacingOccurrences(of:"=",with:"")
            saved=Binding(id:UUID().uuidString.lowercased(),proof:proof,version:0,identity:"")
        } else { return nil } // Locked/unavailable keychain must not create an alternative identity.
        let identity="\(binding):\(fid):\(permission)"
        if saved.identity != identity || renewBinding {
            saved.version += 1;saved.identity=identity
            guard let data=try? JSONEncoder().encode(saved) else { return nil }
            let attributes:[String:Any]=[kSecValueData as String:data,kSecAttrAccessible as String:kSecAttrAccessibleWhenUnlockedThisDeviceOnly]
            let updated=SecItemUpdate(query as CFDictionary,attributes as CFDictionary)
            if updated == errSecItemNotFound {
                guard SecItemAdd(query.merging(attributes) { _,new in new } as CFDictionary,nil)==errSecSuccess else { return nil }
            } else if updated != errSecSuccess { return nil }
        }
        renewBinding=false
        return InstallationRequest(installationId:saved.id,proof:saved.proof,version:saved.version,platform:"ios",targetKind:"fid",target:fid,permitted:permission=="granted")
    }
    func renewRegistration() {renewBinding=true;publish()}
    func clearAccount() {
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        activeBinding=nil;fid=nil
        if configured {
            Messaging.messaging().isAutoInitEnabled=false
            unregistering=true
            Messaging.messaging().unregister { _ in DispatchQueue.main.async { self.unregistering=false;self.refresh() } }
        }
        setDiagnostics(enabled:false)
    }
    func setDiagnostics(enabled:Bool) {
        guard configured else { return }
        let crash=Crashlytics.crashlytics()
        crash.setCrashlyticsCollectionEnabled(false)
        crash.deleteUnsentReports()
        crash.setUserID("")
        defaults.set(enabled,forKey:"srisu.diagnostics")
        crash.setCrashlyticsCollectionEnabled(enabled)
        publish()
    }
    func report(code:DiagnosticCode) {
        guard configured,defaults.bool(forKey:"srisu.diagnostics") else { return }
        // Never wrap an arbitrary NSError, Kotlin Throwable, or their messages/causes/userInfo.
        let model=ExceptionModel(name:"SriSuDiagnostic",reason:code.name)
        Crashlytics.crashlytics().record(exceptionModel:model)
    }
}
