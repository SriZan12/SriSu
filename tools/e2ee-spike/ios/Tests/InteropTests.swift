import Foundation
import LibSignalClient
import XCTest

/// Run each phase in a separate simulator process. Only public prekeys and
/// ciphertext cross the host relay; private state stays in the platform vault.
final class InteropTests: XCTestCase {
    private var exchange: URL { FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("exchange") }
    private func peer(_ room: String) throws -> DurablePeer {
        let config = exchange.appendingPathComponent("run-id")
        guard FileManager.default.fileExists(atPath: config.path) else { throw XCTSkip("Run the cross-platform phase runner") }
        let run = try String(contentsOf: config, encoding: .utf8).trimmingCharacters(in: .whitespacesAndNewlines)
        guard UUID(uuidString: run) != nil else { throw DurablePeer.Failure.invalidEnvelope }
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("interop-" + run).appendingPathComponent(room)
        return try DurablePeer(directory: root, scope: run + ":ios:" + room, name: "synthetic-ios:" + room)
    }
    private func remote(_ room: String) throws -> ProtocolAddress { try ProtocolAddress(name: "synthetic-android:" + room, deviceId: 1) }
    private func read<T: Decodable>(_ file: String, as type: T.Type) throws -> T { try JSONDecoder().decode(type, from: Data(contentsOf: exchange.appendingPathComponent(file))) }
    private func write<T: Encodable>(_ value: T, _ file: String) throws { try JSONEncoder().encode(value).write(to: exchange.appendingPathComponent(file), options: .atomic) }

    func test01Publish() throws {
        try write(peer("android-first").publish(), "ios-bundle.json")
        _ = try peer("ios-first")
    }
    func test02Initiate() throws {
        let local = try peer("ios-first")
        let recipient = try remote("ios-first")
        try local.establish(read("android-bundle.json", as: PublicBundle.self), with: recipient)
        try write(local.send(id: "initial", body: Data("ios to android नमस्ते 🌿".utf8), to: recipient), "ios-initial.json")
    }
    func test03ReceiveAndReply() throws {
        let local = try peer("android-first")
        let sender = try remote("android-first")
        let message = try read("android-initial.json", as: WireMessage.self)
        XCTAssertEqual(Data("android to ios नेपाली 🌱".utf8), try local.receive(id: "initial", message: message, from: sender))
        try write(local.send(id: "reply", body: Data("ios reply नमस्ते".utf8), to: sender), "ios-reply.json")
    }
    func test04ResumeAndSendDelayed() throws {
        let local = try peer("ios-first")
        let sender = try remote("ios-first")
        XCTAssertEqual(Data("android reply नेपाली".utf8), try local.receive(id: "reply", message: read("android-reply.json", as: WireMessage.self), from: sender))
        let delayed = try local.send(id: "delayed", body: Data("ios delayed".utf8), to: sender)
        let latest = try local.send(id: "latest", body: Data("ios latest".utf8), to: sender)
        XCTAssertEqual(latest, try local.send(id: "latest", body: Data("ios latest".utf8), to: sender))
        try write(["delayed": delayed, "latest": latest], "ios-later.json")
    }
    func test05OutOfOrderTamperAndReplay() throws {
        let local = try peer("android-first")
        let sender = try remote("android-first")
        let messages = try read("android-later.json", as: [String: WireMessage].self)
        let latest = try XCTUnwrap(messages["latest"])
        var corrupt = latest.ciphertext
        corrupt[corrupt.count - 1] ^= 1
        XCTAssertThrowsError(try local.receive(id: "latest", message: WireMessage(type: latest.type, ciphertext: corrupt), from: sender))
        XCTAssertEqual(Data("android latest".utf8), try local.receive(id: "latest", message: latest, from: sender))
        XCTAssertEqual(Data("android delayed".utf8), try local.receive(id: "delayed", message: XCTUnwrap(messages["delayed"]), from: sender))
        XCTAssertEqual(Data("android latest".utf8), try local.receive(id: "latest", message: latest, from: sender))
        XCTAssertThrowsError(try local.receive(id: "replay", message: latest, from: sender))
    }
}
