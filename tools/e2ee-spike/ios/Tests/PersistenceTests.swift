import Foundation
import LibSignalClient
import XCTest

final class PersistenceTests: XCTestCase {
    func testAtomicSendReceiveRestartAndProtectedFiles() throws {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent(UUID().uuidString)
        let firstScope = "spike-a-" + UUID().uuidString
        let secondScope = "spike-b-" + UUID().uuidString
        defer {
            // Only synthetic keys/directories created by this test are removed.
            do {
                try ProtectedVault.deleteEvaluationKey(scope: firstScope)
                try ProtectedVault.deleteEvaluationKey(scope: secondScope)
                try FileManager.default.removeItem(at: root)
            } catch { XCTFail("Evaluation cleanup failed") }
        }
        var first = try DurablePeer(directory: root.appendingPathComponent("a"), scope: firstScope, name: "synthetic-a:durable-room")
        var second = try DurablePeer(directory: root.appendingPathComponent("b"), scope: secondScope, name: "synthetic-b:durable-room")
        try first.establish(second.publish(), with: second.address)
        let body = Data(("Protected synthetic नेपाली 🌱 " + UUID().uuidString).utf8)
        let before = try XCTUnwrap(first.store.loadSession(for: second.address, context: NullContext())).serialize()
        XCTAssertThrowsError(try first.send(id: "one", body: body, to: second.address, failBeforeCommit: true))
        XCTAssertNil(try first.store.vault.get("outbox/one"))
        XCTAssertEqual(before, try first.store.loadSession(for: second.address, context: NullContext())?.serialize())
        let sent = try first.send(id: "one", body: body, to: second.address)

        first = try DurablePeer(directory: root.appendingPathComponent("a"), scope: firstScope, name: first.address.name)
        XCTAssertEqual(sent, try first.send(id: "one", body: body, to: second.address))
        XCTAssertThrowsError(try first.send(id: "one", body: Data("different".utf8), to: second.address))
        XCTAssertThrowsError(try second.receive(id: "one", message: sent, from: first.address, failBeforeCommit: true))
        XCTAssertNil(try second.store.vault.get("inbox/one"))
        XCTAssertNotNil(try second.store.loadPreKey(id: 1, context: NullContext()))
        second = try DurablePeer(directory: root.appendingPathComponent("b"), scope: secondScope, name: second.address.name)
        XCTAssertEqual(body, try second.receive(id: "one", message: sent, from: first.address))
        XCTAssertEqual(body, try second.receive(id: "one", message: sent, from: first.address))
        XCTAssertThrowsError(try second.receive(id: "replay", message: sent, from: first.address))
        let reply = try second.send(id: "reply", body: body, to: first.address)
        XCTAssertEqual(body, try first.receive(id: "reply", message: reply, from: second.address))

        let secret = try first.store.identityKeyPair(context: NullContext()).serialize()
        for folder in [root.appendingPathComponent("a"), root.appendingPathComponent("b")] {
            XCTAssertEqual(try folder.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
            for file in try FileManager.default.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil) {
                let bytes = try Data(contentsOf: file)
                XCTAssertNil(bytes.range(of: body), "Plaintext appeared in a database or side file")
                XCTAssertNil(bytes.range(of: secret), "Identity material appeared in a database or side file")
            }
        }
        try ProtectedVault.deleteEvaluationKey(scope: secondScope)
        XCTAssertThrowsError(try DurablePeer(directory: root.appendingPathComponent("b"), scope: secondScope, name: second.address.name))
    }
}
