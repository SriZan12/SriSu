import Foundation
import XCTest
@testable import SignalSpike

/// Evaluation target only. No relay capability is part of the production app.
final class ProductionRelayTests: XCTestCase {
    func testExchange() throws {
        let folder = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let input = folder.appendingPathComponent("engine-input.json")
        guard FileManager.default.fileExists(atPath: input.path) else { throw XCTSkip("Use the native transport runner") }
        let commands = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: input)) as? [[String: Any]])
        defer { try? FileManager.default.removeItem(at: input) }
        let engine = SignalSpike.SecureChatEngine()
        var results: [[String: Any]] = []
        for command in commands {
            let account = try XCTUnwrap(command["account"] as? String), name = try XCTUnwrap(command["command"] as? String)
            let payload = try JSONSerialization.data(withJSONObject: XCTUnwrap(command["input"]), options: [.sortedKeys])
            if name == "read_media" {
                let input = try XCTUnwrap(command["input"] as? [String: Any])
                let descriptor = try JSONSerialization.data(withJSONObject:XCTUnwrap(input["descriptor"]),options:[.sortedKeys])
                let data = engine.readAttachment(accountId:account,descriptor:String(decoding:descriptor,as:UTF8.self),decrypt:input["decrypt"] as? Bool ?? false)
                results.append(["ok":data != nil,"value":data?.base64EncodedString() as Any? ?? NSNull()])
            } else {
                let raw: String
                if let binary = command["binary"] as? String {
                    raw = engine.executeBinary(accountId:account,command:name,payload:String(decoding:payload,as:UTF8.self),bytes:try XCTUnwrap(Data(base64Encoded:binary)))
                } else { raw = engine.execute(accountId: account, command: name, payload: String(decoding: payload, as: UTF8.self)) }
                results.append(try XCTUnwrap(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any]))
            }
            engine.close(accountId: account)
        }
        try JSONSerialization.data(withJSONObject: results, options: [.sortedKeys]).write(to: folder.appendingPathComponent("engine-output.json"), options: .atomic)
        XCTAssertEqual(results.count, commands.count)
    }
}
