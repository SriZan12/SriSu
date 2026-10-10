import Foundation
import XCTest
@testable import SignalSpike

/// Executes the exact production engine in the test host, not a simulated cipher.
final class EngineTests: XCTestCase {
    var engine = SignalSpike.SecureChatEngine()
    let a = "800000000000000011", b = "800000000000000012"
    func encode(_ value: Any) throws -> String { String(decoding: try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]), as: UTF8.self) }
    func response(_ user: String, _ command: String, _ input: [String: Any] = [:]) throws -> [String: Any] {
        let output = engine.execute(accountId: user, command: command, payload: try encode(input))
        return try XCTUnwrap(JSONSerialization.jsonObject(with: Data(output.utf8)) as? [String: Any])
    }
    func call(_ user: String, _ command: String, _ input: [String: Any] = [:]) throws -> Any {
        let result = try response(user, command, input)
        XCTAssertEqual(result["ok"] as? Bool, true, "Native operation failed: \(result["code"] ?? "unknown")")
        return try XCTUnwrap(result["value"])
    }
    func rejected(_ user: String, _ command: String, _ input: [String: Any], _ code: String) throws {
        let result = try response(user, command, input)
        XCTAssertEqual(result["ok"] as? Bool, false); XCTAssertEqual(result["code"] as? String, code)
    }
    override func setUpWithError() throws {
        // Let XCTest identify native Keychain status without exposing key material.
        try SignalSpike.ProtectedVault.deleteStorageKey(scope: a)
        _ = try call(a, "reset", ["confirm_history_loss": true]); _ = try call(b, "reset", ["confirm_history_loss": true])
    }
    override func tearDownWithError() throws {
        _ = try call(a, "reset", ["confirm_history_loss": true]); _ = try call(b, "reset", ["confirm_history_loss": true])
        engine.close(accountId: b)
    }
    func bundle(_ r: [String: Any], _ user: String) -> [String: Any] {
        ["id": r["device_id"]!, "user_id": Int64(user)!, "registration_id": r["registration_id"]!, "identity_key": r["identity_key"]!,
         "signed_prekey": r["signed_prekey"]!, "ec_prekey": (r["ec_prekeys"] as! [[String: Any]])[0], "kyber_prekey": (r["kyber_prekeys"] as! [[String: Any]])[0]]
    }
    func peer(_ room: String, _ user: String, _ r: [String: Any]) -> [String: Any] {
        ["room_id": room, "peer_user_id": user, "peer_identity": r["identity_key"]!]
    }
    func testDurableRoundTripAndAuthenticatedRouting() throws {
        let ra = try XCTUnwrap(call(a,"registration") as? [String: Any]), rb = try XCTUnwrap(call(b,"registration") as? [String: Any])
        let room = UUID().uuidString.lowercased(), id = UUID().uuidString.lowercased()
        _ = try call(a,"establish",["room_id":room,"claim_id":UUID().uuidString.lowercased(),"bundle":bundle(rb,b)])
        let operation: [String: Any] = ["device_id":ra["device_id"]!,"recipient_device_id":rb["device_id"]!,"operation_id":id,
            "target_id":id,"kind":"message","version":0,"reply_to":NSNull()]
        let content: [String: Any] = ["version":1,"content":["type":"text","body":"Synthetic नमस्ते 👋"]]
        var input = peer(room,b,rb); input["operation"] = operation; input["content"] = content
        let wire = try XCTUnwrap(call(a,"seal",input) as? [String: Any])
        engine.close(accountId: a); engine = SignalSpike.SecureChatEngine()
        XCTAssertEqual(try encode(wire),try encode(call(a,"seal",input)))
        var received = wire; received["actor_id"] = a; received["sender_device_id"] = ra["device_id"]!
        var tampered = received; tampered["kind"] = "delete"
        var incoming = peer(room,a,ra); incoming["operation"] = tampered
        try rejected(b,"open",incoming,"invalid_envelope")
        incoming["operation"] = received
        XCTAssertEqual(try encode(content), try encode(call(b,"open",incoming)))
        engine.close(accountId: b)
        XCTAssertEqual(try encode(content), try encode(call(b,"open",incoming)))
        incoming["operation"] = tampered; try rejected(b,"open",incoming,"invalid_envelope")
        let fa = try XCTUnwrap(call(a,"fingerprint",peer(room,b,rb)) as? [String: Any]), fb = try XCTUnwrap(call(b,"fingerprint",peer(room,a,ra)) as? [String: Any])
        XCTAssertEqual(fa["display"] as? String, fb["display"] as? String)
        var scanA=peer(room,b,rb);scanA["scan"]=fb["qr"]
        var scanB=peer(room,a,ra);scanB["scan"]=fa["qr"]
        XCTAssertEqual((try call(a,"fingerprint",scanA) as? [String:Any])?["matches"] as? Bool,true)
        XCTAssertEqual((try call(b,"fingerprint",scanB) as? [String:Any])?["matches"] as? Bool,true)
        scanA["scan"]="not a valid QR"
        XCTAssertEqual((try call(a,"fingerprint",scanA) as? [String:Any])?["matches"] as? Bool,false)
        XCTAssertEqual(try call(a,"is_sealed",["room_id":room,"operation_id":id]) as? Bool,true)
        let marker=["key":"app/processed/\(room)/\(id)","value":"true"]
        try rejected(b,"write",["entries":[marker,["key":"invalid-private-namespace","value":"forbidden"]]],"invalid_request")
        incoming["operation"] = received
        XCTAssertEqual(try encode(content),try encode(call(b,"open",incoming)))
        _ = try call(b,"write",["entries":[marker]])
        engine.close(accountId:b)
        try rejected(b,"open",incoming,"already_processed")
        incoming["operation"] = tampered
        try rejected(b,"open",incoming,"invalid_envelope")
        let changed = try XCTUnwrap(call("800000000000000013","registration") as? [String: Any])
        try rejected(a,"trust",peer(room,b,changed),"identity_changed")
        let delayedId = UUID().uuidString.lowercased()
        var delayedOperation = operation; delayedOperation["operation_id"] = delayedId; delayedOperation["target_id"] = delayedId
        var delayedInput = peer(room,b,rb); delayedInput["operation"] = delayedOperation; delayedInput["content"] = content
        var delayed = try XCTUnwrap(call(a,"seal",delayedInput) as? [String: Any]); delayed["actor_id"] = a; delayed["sender_device_id"] = ra["device_id"]!
        var confirmation = peer(room,a,changed); confirmation["confirm_change"] = true
        _ = try call(b,"trust",confirmation)
        var oldIncoming = peer(room,a,ra); oldIncoming["operation"] = delayed
        XCTAssertEqual(try encode(content),try encode(call(b,"open",oldIncoming)))
        try rejected(b,"trust",peer(room,a,ra),"identity_changed")
        _ = try call("800000000000000013","reset",["confirm_history_loss":true])
    }
    func testNamespaceIsolationAtomicProjectionAndReset() throws {
        _ = try call(a,"write",["entries":[["key":"app/draft","value":"synthetic"]]])
        XCTAssertTrue(try call(b,"read",["key":"app/draft"]) is NSNull)
        try rejected(a,"read",["key":"identity"],"invalid_request")
        try rejected(a,"write",["entries":[["key":"app/draft","value":"changed"],["key":"private-key","value":"forbidden"]]],"invalid_request")
        XCTAssertEqual(try call(a,"read",["key":"app/draft"]) as? String,"synthetic")
        XCTAssertEqual(try call(a,"count",["prefix":"app/"]) as? Int,1)
        XCTAssertEqual(try call(a,"read_many",["keys":["app/draft"]]) as? [String],["synthetic"])
        try rejected(a,"reset",[:],"invalid_request")
        _ = try call(a,"reset",["confirm_history_loss":true])
        XCTAssertTrue(try call(a,"read",["key":"app/draft"]) is NSNull)
    }
    func testQuietRotationDoesNotRefillFullPrekeyPoolAndRetriesExactly() throws {
        let initial=try XCTUnwrap(call(a,"registration") as? [String:Any])
        XCTAssertEqual(try call(a,"publication_needed") as? Bool,false)
        engine.close(accountId:a)
        let root=try FileManager.default.url(for:.applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true)
        let vault=try SignalSpike.ProtectedVault(directory:root.appendingPathComponent("couple-chat-v1").appendingPathComponent(a),scope:a)
        try vault.transaction {
            var record=try XCTUnwrap(JSONSerialization.jsonObject(with:XCTUnwrap(vault.get("current-signed-public"))) as? [String:Any])
            record["created_at"] = UInt64((Date().timeIntervalSince1970-31*86400)*1000)
            try vault.put("current-signed-public",JSONSerialization.data(withJSONObject:record))
        }
        XCTAssertEqual(try call(a,"publication_needed") as? Bool,true)
        let input:[String:Any] = ["operation_id":UUID().uuidString.lowercased(),"prekey_count":0]
        let rotated=try XCTUnwrap(call(a,"publication",input) as? [String:Any])
        XCTAssertEqual((rotated["ec_prekeys"] as? [Any])?.count,0);XCTAssertEqual((rotated["kyber_prekeys"] as? [Any])?.count,0)
        XCTAssertNotEqual((initial["signed_prekey"] as? [String:Any])?["id"] as? Int,(rotated["signed_prekey"] as? [String:Any])?["id"] as? Int)
        XCTAssertEqual(try call(a,"publication_needed") as? Bool,false)
        engine.close(accountId:a)
        XCTAssertEqual(try encode(rotated),try encode(call(a,"publication",input)))
        XCTAssertEqual((try call(a,"registration") as? [String:Any])?["identity_key"] as? String,initial["identity_key"] as? String)
    }

    func testAttachmentCiphertextIsImmutableAuthenticatedAndAccountScoped() throws {
        let plain = Data((0..<4096).map { UInt8(truncatingIfNeeded:$0) })
        let context: [String: Any] = ["room_id":UUID().uuidString.lowercased(),"message_id":UUID().uuidString.lowercased(),"id":UUID().uuidString.lowercased()]
        func binary(_ user: String,_ command: String,_ input: [String: Any],_ bytes: Data) throws -> [String: Any] {
            let result = engine.executeBinary(accountId:user,command:command,payload:try encode(input),bytes:bytes)
            return try XCTUnwrap(JSONSerialization.jsonObject(with:Data(result.utf8)) as? [String: Any])
        }
        let sealed = try binary(a,"media_seal",context,plain)
        XCTAssertEqual(sealed["ok"] as? Bool,true)
        let descriptor = try XCTUnwrap(sealed["value"] as? [String: Any]), raw = try encode(descriptor)
        let cipher = try XCTUnwrap(engine.readAttachment(accountId:a,descriptor:raw,decrypt:false))
        XCTAssertEqual(cipher.count,plain.count+28);XCTAssertEqual(engine.readAttachment(accountId:a,descriptor:raw,decrypt:true),plain)
        engine.close(accountId:a)
        let retry = try binary(a,"media_seal",context,plain)
        XCTAssertEqual(retry["ok"] as? Bool,true)
        XCTAssertTrue(try encode(XCTUnwrap(retry["value"])) == raw)
        XCTAssertEqual(engine.readAttachment(accountId:a,descriptor:raw,decrypt:false),cipher)
        XCTAssertNil(engine.readAttachment(accountId:b,descriptor:raw,decrypt:false))
        XCTAssertEqual(try binary(b,"media_cache",descriptor,cipher)["ok"] as? Bool,true)
        XCTAssertEqual(engine.readAttachment(accountId:b,descriptor:raw,decrypt:true),plain)
        var wrong = descriptor;wrong["message_id"] = UUID().uuidString.lowercased()
        XCTAssertEqual(try binary(b,"media_cache",wrong,cipher)["ok"] as? Bool,false)
        var altered = cipher;altered[altered.count-1] ^= 1
        XCTAssertEqual(try binary(b,"media_cache",descriptor,altered)["ok"] as? Bool,false)
        XCTAssertEqual(engine.readAttachment(accountId:b,descriptor:raw,decrypt:true),plain)
        _ = try call(b,"media_delete",descriptor)
        XCTAssertNil(engine.readAttachment(accountId:b,descriptor:raw,decrypt:false))
        XCTAssertEqual(try call(b,"media_available",["ids":[descriptor["id"]!]]) as? [String],[])
        _ = try call(a,"media_release",descriptor)
        XCTAssertEqual(engine.readAttachment(accountId:a,descriptor:raw,decrypt:true),plain)
        XCTAssertEqual(try binary(a,"media_seal",context,plain)["ok"] as? Bool,false)
    }
}
