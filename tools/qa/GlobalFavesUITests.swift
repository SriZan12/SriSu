import XCTest
import UIKit

/// Native UIKit keyboard checks for a separate synthetic-only application bundle.
final class GlobalFavesUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        XCUIDevice.shared.orientation = .portrait
        app = XCUIApplication(bundleIdentifier: "com.srisu.explore.qa")
    }

    override func tearDownWithError() throws {
        if let app { app.terminate() }
        XCUIDevice.shared.orientation = .portrait
    }

    private func launch(_ arguments: [String] = []) {
        app.launchArguments = arguments + ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
    }

    private func element(_ label: String) -> XCUIElement {
        app.descendants(matching: .any).matching(NSPredicate(format: "label == %@ OR identifier == %@", label, label)).firstMatch
    }

    private func capture(_ name: String) {
        // Screen capture respects the current device orientation; app capture
        // can retain a portrait crop after a Compose controller rotates.
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = name; shot.lifetime = .keepAlways; add(shot)
    }

    private func inspectInput(_ name: String) {
        let tree = XCTAttachment(string: app.debugDescription)
        tree.name = name; tree.lifetime = .keepAlways; add(tree)
    }

    private func wait(_ description: String, timeout: TimeInterval = 10, _ check: @escaping () -> Bool) {
        let expectation = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in check() }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed, description)
    }

    private func reachable(_ target: XCUIElement) {
        for _ in 0..<6 {
            if target.exists && target.isHittable { return }
            app.swipeUp()
        }
        XCTAssertTrue(target.exists && target.isHittable, "Control must be reachable by scrolling")
    }

    private func editor() -> XCUIElement {
        // Compose exposes both single-line and multiline editors as TextView in
        // UIKit accessibility. Keep the query live while the sheet animates in.
        app.textViews.firstMatch
    }

    private func sendAction() -> XCUIElement {
        let matches = app.buttons.matching(NSPredicate(format: "label == %@", "Send appreciation"))
        return matches.allElementsBoundByIndex.last(where: { $0.isHittable }) ?? matches.firstMatch
    }

    private func settleKeyboard(_ anchor: XCUIElement, requireVisible: Bool = true) {
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10), "Real UIKit keyboard must be open")
        var oldAction = CGRect.null, oldKeyboard = CGRect.null
        var unchangedSince = Date()
        var loggedGeometry = false
        wait("Keyboard and action geometry must settle") { [self] in
            let keyboard = app.keyboards.firstMatch
            guard anchor.exists, keyboard.exists else { return false }
            let actionFrame = anchor.frame, keyboardFrame = keyboard.frame
            // An offscreen UIKit keyboard can remain in the accessibility tree
            // after disabling an editor. Existence alone is not an open IME.
            let windowFrame = app.windows.firstMatch.frame
            if !loggedGeometry {
                print("Synthetic keyboard geometry: anchor=\(actionFrame), keyboard=\(keyboardFrame), window=\(windowFrame)")
                inspectInput("keyboard-geometry-tree")
                capture("keyboard-geometry")
                loggedGeometry = true
            }
            if requireVisible {
                guard keyboardFrame.intersection(windowFrame).height > 100,
                    keyboardFrame.minY < windowFrame.maxY - 100 else { return false }
            }
            if actionFrame != oldAction || keyboardFrame != oldKeyboard {
                oldAction = actionFrame; oldKeyboard = keyboardFrame; unchangedSince = Date()
                return false
            }
            return Date().timeIntervalSince(unchangedSince) >= 0.35
        }
    }

    private func assertAboveKeyboard(_ action: XCUIElement, screenshot: String) {
        settleKeyboard(action)
        capture(screenshot)
        XCTAssertTrue(action.isHittable, "Send must be tappable with the keyboard open")
        XCTAssertLessThanOrEqual(action.frame.maxY, app.keyboards.firstMatch.frame.minY + 1,
            "Send overlaps native keyboard: action=\(action.frame), keyboard=\(app.keyboards.firstMatch.frame)")
    }

    func testSearchTypingClearSubmitAndBack() {
        launch()
        let entry = element("Names, or what they love")
        XCTAssertTrue(entry.waitForExistence(timeout: 30)); entry.tap()
        inspectInput("search-input-tree")
        let field = editor()
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        field.typeText("Maya")
        wait("Search input retains typed value") { (field.value as? String)?.contains("Maya") == true }
        capture("ios-search-keyboard")
        element("Clear search").tap()
        wait("Clear empties the search") { !(field.value as? String ?? "").contains("Maya") }
        field.typeText("Hiking")
        app.keyboards.buttons["Search"].tap()
        wait("Submit dismisses keyboard") { !self.app.keyboards.firstMatch.exists }
        field.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        element("Go back").tap()
        wait("Back dismisses search and keyboard") { !self.app.keyboards.firstMatch.exists && !self.app.textViews.firstMatch.exists }
        capture("ios-search-return")
    }

    func testLargeDarkAppreciationRetryPreservesDraftAndDismissesKeyboard() {
        launch(["-qa-viewer", "-qa-dark", "-qa-compact", "-UIPreferredContentSizeCategoryName",
            UIContentSizeCategory.accessibilityExtraExtraLarge.rawValue])
        let open = element("Send appreciation")
        XCTAssertTrue(open.waitForExistence(timeout: 30)); reachable(open); open.tap()
        inspectInput("appreciation-input-tree")
        let note = editor()
        XCTAssertTrue(note.waitForExistence(timeout: 10)); reachable(note); note.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        let draft = "A lovely little moment. Thank you for sharing."
        // Simulator can expose a hardware-focused, offscreen keyboard before
        // XCTest begins software typing. Settle the editor first, then require
        // the real on-screen keyboard in assertAboveKeyboard after typing.
        settleKeyboard(note, requireVisible: false)
        // Exercise the UIKit selection/composition bridge with an uninterrupted
        // sentence. Verify input before Send to distinguish typing from retries.
        note.typeText(draft)
        inspectInput("appreciation-typed-tree")
        XCTAssertTrue((note.value as? String ?? "").contains(draft), "Verify the complete draft before sending")
        let firstSend = sendAction()
        assertAboveKeyboard(firstSend, screenshot: "ios-appreciation-large-dark-keyboard")
        firstSend.tap()
        XCTAssertTrue(element("Connection interrupted. Try again.").waitForExistence(timeout: 10))
        inspectInput("appreciation-retry-tree")
        XCTAssertTrue((editor().value as? String ?? "").contains(draft), "Failed send must preserve the draft")
        editor().tap()
        settleKeyboard(editor(), requireVisible: false)
        // Re-enter text after a failed request to verify keyboard focus and the
        // preserved caret, rather than merely tapping Retry on an unfocused form.
        editor().typeText("!")
        wait("Retry editor accepts another character without losing the draft") {
            let value = self.editor().value as? String ?? ""
            return value.contains("!") && value.replacingOccurrences(of: "!", with: "").contains(draft)
        }
        let retry = sendAction()
        assertAboveKeyboard(retry, screenshot: "ios-appreciation-large-dark-retry")
        retry.tap()
        XCTAssertTrue(element("Your appreciation was sent").waitForExistence(timeout: 10))
        wait("Successful send closes editor and native keyboard") { !self.app.keyboards.firstMatch.exists && !self.app.textViews.firstMatch.exists }
        capture("ios-appreciation-sent")
    }

    func testLandscapeSearchAndKeyboardBack() {
        launch(["-qa-dark"])
        XCUIDevice.shared.orientation = .landscapeLeft
        let entry = element("Names, or what they love")
        XCTAssertTrue(entry.waitForExistence(timeout: 30)); reachable(entry); entry.tap()
        inspectInput("landscape-input-tree")
        let field = editor()
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
        field.typeText("Coffee")
        wait("Landscape input retains value") { (field.value as? String)?.contains("Coffee") == true }
        capture("ios-search-landscape-keyboard")
        element("Go back").tap()
        wait("Landscape Back dismisses keyboard") { !self.app.keyboards.firstMatch.exists }
    }
}
