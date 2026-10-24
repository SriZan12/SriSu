import UIKit
import UserNotifications
import EventKit
import EventKitUI
import ComposeApp

final class IosPlanDevice: NSObject, PlanDevice, UNUserNotificationCenterDelegate, EKEventEditViewDelegate, UIAdaptivePresentationControllerDelegate {
    private let center = UNUserNotificationCenter.current()
    private var calendarResult: ((String) -> Void)?
    override init() { super.init(); center.delegate = self }
    func permission(result: @escaping (String) -> Void) {
        center.getNotificationSettings { settings in
            let status: String
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral: status = "granted"
            case .notDetermined: status = "unknown"
            default: status = "denied"
            }
            DispatchQueue.main.async { result(status) }
        }
    }
    func requestPermission(result: @escaping (String) -> Void) {
        center.requestAuthorization(options: [.alert,.sound]) { _,_ in self.permission(result: result) }
    }
    func replaceReminders(alarms: [PlanAlarm], result: @escaping (String?) -> Void) {
        center.getPendingNotificationRequests { requests in
            func matches(_ request: UNNotificationRequest) -> Bool {
                alarms.contains { alarm in alarm.id == request.identifier &&
                    (request.content.userInfo["at"] as? NSNumber)?.int64Value == alarm.atMillis &&
                    (request.content.userInfo["url"] as? String) == alarm.url }
            }
            self.center.removePendingNotificationRequests(withIdentifiers: requests.filter { $0.identifier.hasPrefix("plan-") && !matches($0) }.map(\.identifier))
            let group = DispatchGroup(); let lock = NSLock(); var failed = false
            group.enter()
            self.center.getDeliveredNotifications { delivered in
                self.center.removeDeliveredNotifications(withIdentifiers: delivered.filter { $0.request.identifier.hasPrefix("plan-") && !matches($0.request) }.map { $0.request.identifier })
                group.leave()
            }
            for alarm in alarms {
                let interval = Double(alarm.atMillis)/1000 - Date().timeIntervalSince1970
                guard interval > 0, !requests.contains(where: { $0.identifier == alarm.id && matches($0) }) else { continue }
                let content = UNMutableNotificationContent()
                content.title = "A plan together"
                content.body = "Open SriSu to check the latest details."
                content.sound = .default
                content.userInfo = ["url":alarm.url,"at":NSNumber(value:alarm.atMillis)]
                group.enter()
                self.center.add(UNNotificationRequest(identifier: alarm.id, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, interval), repeats: false))) { error in
                    lock.lock(); failed = failed || error != nil; lock.unlock(); group.leave()
                }
            }
            group.notify(queue: .main) { result(failed ? "This phone couldn’t schedule reminders. Try again." : nil) }
        }
    }
    func calendar(title: String, atMillis: Int64, place: String, result: @escaping (String) -> Void) {
        guard #available(iOS 17.0, *) else { result("Calendar handoff requires iOS 17 or later."); return }
        guard let presenter = presenter(), calendarResult == nil else { result("Close the current sheet and try again."); return }
        let store = EKEventStore(); let event = EKEvent(eventStore: store)
        event.title = title; event.startDate = Date(timeIntervalSince1970: Double(atMillis)/1000)
        event.endDate = event.startDate.addingTimeInterval(3600); event.location = place
        let editor = EKEventEditViewController(); editor.eventStore = store; editor.event = event; editor.editViewDelegate = self
        calendarResult = result; editor.presentationController?.delegate = self
        presenter.present(editor, animated: true)
    }
    func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
        let message = action == .saved
            ? "Calendar copy saved. Later plan changes won’t update this copy."
            : "Calendar copy wasn’t saved."
        controller.dismiss(animated: true) { self.finishCalendar(message) }
    }
    func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
        finishCalendar("Calendar copy wasn’t saved.")
    }
    private func finishCalendar(_ message: String) {
        let result = calendarResult; calendarResult = nil; result?(message)
    }
    func openPlace(place: String) {
        var components = URLComponents(string: "https://maps.apple.com/")!
        components.queryItems = [URLQueryItem(name:"q",value:place)]
        if let url = place.hasPrefix("https://") ? URL(string:place) : components.url { UIApplication.shared.open(url) }
    }
    private func presenter() -> UIViewController? {
        var top = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)?.rootViewController
        while let next = top?.presentedViewController { top = next }
        return top
    }
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) { completionHandler([.banner,.sound]) }
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        if let url = response.notification.request.content.userInfo["url"] as? String {
            DispatchQueue.main.async { PlatformEntry.shared.openNotification(url:url,deliveryId:response.notification.request.identifier) }
        }
        completionHandler()
    }
}
