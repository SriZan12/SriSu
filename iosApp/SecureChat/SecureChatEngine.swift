import CryptoKit
import Foundation
import LibSignalClient

/// Native libsignal and account-scoped protected persistence. No network or logs.
final class SecureChatEngine {
    enum Failure: String, Error {
        case invalidRequest = "invalid_request", identityChanged = "identity_changed"
        case conflict = "operation_conflict", invalidEnvelope = "invalid_envelope"
        case deviceChanged = "device_changed", oversized = "message_too_large"
        case keyIdsExhausted = "key_ids_exhausted"
        case alreadyProcessed = "already_processed"
    }
    private var account: String?
    private var storage: DurableStore?
    private let lock = NSRecursiveLock()
    private let context = NullContext()
    private var store: DurableStore { storage! }

    func close(accountId: String) {
        lock.lock(); defer { lock.unlock() }
        if account == accountId { storage = nil; account = nil }
    }

    func execute(accountId: String, command: String, payload: String) -> String {
        executeInternal(accountId: accountId,command: command,payload: payload,binary:nil)
    }

    func executeBinary(accountId: String, command: String, payload: String, bytes: Data) -> String {
        guard command == "media_seal" || command == "media_cache" else { return "{\"ok\":false,\"code\":\"invalid_request\"}" }
        return executeInternal(accountId:accountId,command:command,payload:payload,binary:bytes)
    }

    func readAttachment(accountId: String, descriptor: String, decrypt: Bool) -> Data? {
        lock.lock(); defer { lock.unlock() }
        do {
            guard descriptor.utf8.count <= 262144 else { throw Failure.invalidRequest }
            try selectAccount(accountId)
            return try store.vault.transaction { try AttachmentVault(store.vault).read(object(Data(descriptor.utf8)),decrypt:decrypt) }
        } catch { return nil }
    }

    private func selectAccount(_ accountId: String) throws {
        guard validAccount(accountId) else { throw Failure.invalidRequest }
        if account != accountId {
            storage = nil; account = nil
            let root = try FileManager.default.url(for: .applicationSupportDirectory,in:.userDomainMask,appropriateFor:nil,create:true)
            storage = try DurableStore(ProtectedVault(directory:root.appendingPathComponent("couple-chat-v1").appendingPathComponent(accountId),scope:accountId))
            account = accountId
        }
    }

    private func executeInternal(accountId: String, command: String, payload: String, binary: Data?) -> String {
        lock.lock(); defer { lock.unlock() }
        do {
            guard validAccount(accountId), payload.utf8.count <= 262144 else { throw Failure.invalidRequest }
            guard (binary?.count ?? 0) <= AttachmentVault.maxPlain+28 else { throw Failure.oversized }
            if command == "reset" {
                guard try object(Data(payload.utf8))["confirm_history_loss"] as? Bool == true else { throw Failure.invalidRequest }
                close(accountId: accountId)
                try ProtectedVault.deleteStorageKey(scope: accountId)
                let root = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                let directory = root.appendingPathComponent("couple-chat-v1").appendingPathComponent(accountId)
                if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
                return "{\"ok\":true,\"value\":null}"
            }
            try selectAccount(accountId)
            let input = try object(Data(payload.utf8))
            let result = try store.vault.transaction { () -> Any in
                if command == "media_seal", let binary { return try AttachmentVault(store.vault).seal(input,binary) }
                if command == "media_cache", let binary { try AttachmentVault(store.vault).receive(input,binary); return NSNull() }
                return try run(command,input)
            }
            return String(decoding: try data(["ok": true, "value": result]), as: UTF8.self)
        } catch {
            let code: String
            if let failure = error as? Failure { code = failure.rawValue }
            else if let failure = error as? ProtectedVault.Failure, case .missingKey = failure { code = "storage_key_lost" }
            else if let failure = error as? SignalError {
                switch failure {
                case .untrustedIdentity: code = "identity_changed"
                case .sessionNotFound: code = "session_missing"
                case .duplicatedMessage: code = "replay_rejected"
                default: code = "invalid_ciphertext"
                }
            } else { code = "storage_or_protocol_failure" }
            return "{\"ok\":false,\"code\":\"\(code)\"}"
        }
    }

    private func run(_ command: String, _ input: [String: Any]) throws -> Any {
        switch command {
        case "media_release": try AttachmentVault(store.vault).release(input,delete:false); return NSNull()
        case "media_delete": try AttachmentVault(store.vault).release(input,delete:true); return NSNull()
        case "media_available": return try AttachmentVault(store.vault).available(input)
        case "is_sealed": return try store.vault.get("sealed/" + uuid(string(input,"room_id")) + "/" + uuid(string(input,"operation_id"))) != nil
        case "count": return try store.vault.count(prefix: appKey(string(input, "prefix")))
        case "read_many":
            guard let keys = input["keys"] as? [String], keys.count <= 200 else { throw Failure.invalidRequest }
            return try keys.map { key -> Any in
                guard let value = try store.vault.get(appKey(key)) else { return NSNull() }
                return String(decoding: value, as: UTF8.self)
            }
        case "read":
            guard let value = try store.vault.get(appKey(string(input, "key"))) else { return NSNull() }
            return String(decoding: value, as: UTF8.self)
        case "page":
            let prefix = try appKey(string(input, "prefix")), limit = (input["limit"] as? Int) ?? 50
            guard (1...100).contains(limit) else { throw Failure.invalidRequest }
            return try store.vault.page(prefix: prefix, before: input["before"] as? String ?? "", limit: limit).map { key in
                ["key": key, "value": String(decoding: try required(key), as: UTF8.self)]
            }
        case "write":
            guard let entries = input["entries"] as? [[String: Any]], entries.count <= 200 else { throw Failure.invalidRequest }
            for entry in entries {
                let key = try appKey(string(entry, "key"))
                if entry["value"] is NSNull { try store.vault.remove(key) }
                else {
                    let value = try string(entry,"value")
                    try store.vault.put(key, Data(value.utf8))
                    if key.hasPrefix("app/processed/") && value == "true" {
                        let journal = "opened/" + key.dropFirst("app/processed/".count)
                        if let previous = try store.vault.get(journal) {
                            var saved = try object(previous); saved.removeValue(forKey:"content")
                            try store.vault.put(journal,data(saved))
                        }
                    }
                }
            }
            return NSNull()
        case "registration": return try registration(replace: false)
        case "replace": return try registration(replace: true)
        case "publication_needed":
            guard let last=try store.vault.get("current-signed-public"),let created=try object(last)["created_at"] as? UInt64 else {return true}
            return UInt64(Date().timeIntervalSince1970*1000)>=created+30*86400000
        case "publication":
            let id = try uuid(string(input, "operation_id"))
            if let previous = try store.vault.get("publication/" + id) { return try object(previous) }
            let count=input["prekey_count"] as? Int ?? 10
            guard (0...10).contains(count) else {throw Failure.invalidRequest}
            var publication = try generateKeys(count:count)
            publication["device_id"] = try registration(replace: false)["device_id"]
            publication["operation_id"] = id
            try store.vault.put("publication/" + id, data(publication))
            return publication
        case "trust":
            try trust(input, confirm: input["confirm_change"] as? Bool ?? false); return NSNull()
        case "establish":
            let bundle = try nested(input, "bundle")
            try trust(["peer_user_id": string(bundle, "user_id"), "peer_identity": string(bundle, "identity_key")])
            let ec = try nested(bundle, "ec_prekey"), signed = try nested(bundle, "signed_prekey"), kyber = try nested(bundle, "kyber_prekey")
            let native = try PreKeyBundle(registrationId: uint(bundle, "registration_id"), deviceId: 1,
                prekeyId: uint(ec, "id"), prekey: PublicKey(decoded(ec, "public_key")),
                signedPrekeyId: uint(signed, "id"), signedPrekey: PublicKey(decoded(signed, "public_key")),
                signedPrekeySignature: decoded(signed, "signature"), identity: IdentityKey(bytes: decoded(bundle, "identity_key")),
                kyberPrekeyId: uint(kyber, "id"), kyberPrekey: KEMPublicKey(decoded(kyber, "public_key")), kyberPrekeySignature: decoded(kyber, "signature"))
            let local = try address(room: string(input, "room_id"), user: account!, device: string(registration(replace: false), "device_id"))
            let remote = try address(room: string(input, "room_id"), user: string(bundle, "user_id"), device: string(bundle, "id"))
            let claim = try uuid(string(input, "claim_id"))
            if try store.vault.get("established/" + claim) == nil {
                try processPreKeyBundle(native, for: remote, ourAddress: local, sessionStore: store, identityStore: store, context: context)
                try store.vault.put("established/" + claim, data(["peer": remote.name]))
            }
            return NSNull()
        case "has_session":
            return try store.loadSession(for: address(room: string(input, "room_id"), user: string(input, "peer_user_id"), device: string(input, "peer_device_id")), context: context) != nil
        case "seal": return try seal(input)
        case "open": return try open(input)
        case "fingerprint":
            let fingerprint = try NumericFingerprintGenerator(iterations: 5200).create(version: 2,
                localIdentifier: Data(account!.utf8), localKey: store.identityKeyPair(context: context).publicKey,
                remoteIdentifier: Data(string(input, "peer_user_id").utf8), remoteKey: PublicKey(decoded(input, "peer_identity")))
            var result: [String:Any] = ["display": fingerprint.displayable.formatted, "qr": fingerprint.scannable.encoding.base64EncodedString()]
            if let scan=input["scan"] as? String {
                let bytes=Data(base64Encoded:scan)
                result["matches"] = bytes != nil && bytes!.count<=512 && ((try? fingerprint.scannable.compare(againstEncoding:bytes!)) == true)
            }
            return result
        default: throw Failure.invalidRequest
        }
    }

    private func registration(replace: Bool) throws -> [String: Any] {
        if !replace, let value = try store.vault.get("public-registration") { return try object(value) }
        var result = try generateKeys()
        result["device_id"] = UUID().uuidString.lowercased()
        result["registration_id"] = try store.localRegistrationId(context: context)
        result["identity_key"] = try store.identityKeyPair(context: context).identityKey.serialize().base64EncodedString()
        result["replace_device_id"] = NSNull()
        try store.vault.put("public-registration", data(result))
        return result
    }

    private func nextKeyId() throws -> UInt32 {
        let current = try store.vault.get("next-key-id")
        let id = current.flatMap { UInt32(String(decoding: $0, as: UTF8.self)) } ?? 1
        guard id < UInt32(Int32.max) - 100 else { throw Failure.keyIdsExhausted }
        try store.vault.put("next-key-id", Data(String(id + 1).utf8))
        return id
    }

    private func generateKeys(count:Int = 10) throws -> [String: Any] {
        let identity = try store.identityKeyPair(context: context)
        let last = try store.vault.get("current-signed-public").map { try object($0) }
        let now = UInt64(Date().timeIntervalSince1970 * 1000)
        let signed: [String: Any]
        if let last, let created = last["created_at"] as? UInt64, now < created + 30 * 86400000 {
            signed = try nested(last, "key")
        } else {
            let id = try nextKeyId(), pair = PrivateKey.generate()
            let signature = identity.privateKey.generateSignature(message: pair.publicKey.serialize())
            try store.storeSignedPreKey(SignedPreKeyRecord(id: id, timestamp: now, privateKey: pair, signature: signature), id: id, context: context)
            signed = ["id": id, "public_key": pair.publicKey.serialize().base64EncodedString(), "signature": signature.base64EncodedString()]
            try store.vault.put("current-signed-public", data(["created_at": now, "key": signed]))
        }
        var ec: [[String: Any]] = [], kyber: [[String: Any]] = []
        for _ in 0..<count {
            let ecId = try nextKeyId(), pqId = try nextKeyId(), pair = PrivateKey.generate(), pq = KEMKeyPair.generate()
            try store.storePreKey(PreKeyRecord(id: ecId, privateKey: pair), id: ecId, context: context)
            ec.append(["id": ecId, "public_key": pair.publicKey.serialize().base64EncodedString()])
            let signature = identity.privateKey.generateSignature(message: pq.publicKey.serialize())
            try store.storeKyberPreKey(KyberPreKeyRecord(id: pqId, timestamp: now, keyPair: pq, signature: signature), id: pqId, context: context)
            kyber.append(["id": pqId, "public_key": pq.publicKey.serialize().base64EncodedString(), "signature": signature.base64EncodedString()])
        }
        return ["signed_prekey": signed, "ec_prekeys": ec, "kyber_prekeys": kyber]
    }

    private func trust(_ input: [String: Any], confirm: Bool = false) throws {
        let peer = try string(input, "peer_user_id")
        guard validAccount(peer), peer != account else { throw Failure.invalidRequest }
        let identity = try IdentityKey(bytes: decoded(input, "peer_identity")).serialize()
        if let previous = try store.vault.get("peer-identity/" + peer), previous != identity, !confirm { throw Failure.identityChanged }
        try store.vault.put("peer-identity/" + peer, identity)
        try store.vault.put("trusted-identity/" + peer + "/" + Data(SHA256.hash(data: identity)).base64EncodedString(), identity)
    }

    private func trustIncoming(_ input: [String: Any]) throws {
        let peer = try string(input, "peer_user_id")
        guard validAccount(peer), peer != account else { throw Failure.invalidRequest }
        let identity = try IdentityKey(bytes: decoded(input, "peer_identity")).serialize()
        let key = "trusted-identity/" + peer + "/" + Data(SHA256.hash(data: identity)).base64EncodedString()
        if try store.vault.get(key) == nil { try trust(input) }
    }

    private func seal(_ input: [String: Any]) throws -> [String: Any] {
        try trust(input)
        var operation = try nested(input, "operation")
        if let attachments = operation["attachments"] as? [String], attachments.isEmpty { operation.removeValue(forKey:"attachments") }
        let room = try uuid(string(input, "room_id")), id = try uuid(string(operation, "operation_id"))
        let device = try string(registration(replace: false), "device_id")
        guard try device == string(operation, "device_id") else { throw Failure.deviceChanged }
        let local = try address(room: room, user: account!, device: device)
        let remote = try address(room: room, user: string(input, "peer_user_id"), device: string(operation, "recipient_device_id"))
        let bound: [String: Any] = ["v": 1, "room_id": room, "actor_id": account!, "operation": operation, "payload": try nested(input, "content")]
        let plain = try data(bound)
        guard plain.count <= 49152 else { throw Failure.oversized }
        let request = Data(SHA256.hash(data: plain)).base64EncodedString(), key = "sealed/\(room)/\(id)"
        if let old = try store.vault.get(key) {
            let saved = try object(old)
            guard try string(saved, "request") == request, try string(saved, "peer") == remote.name else { throw Failure.conflict }
            return try nested(saved, "wire")
        }
        let encrypted = try signalEncrypt(message: plain, for: remote, localAddress: local, sessionStore: store, identityStore: store, context: context)
        var wire = operation
        wire["signal_type"] = encrypted.messageType.rawValue
        wire["ciphertext"] = encrypted.serialize().base64EncodedString()
        try store.vault.put(key, data(["request": request, "peer": remote.name, "wire": wire]))
        return wire
    }

    private func open(_ input: [String: Any]) throws -> [String: Any] {
        try trustIncoming(input)
        let operation = try nested(input, "operation"), room = try uuid(string(input, "room_id"))
        let device = try string(registration(replace: false), "device_id"), peer = try string(input, "peer_user_id")
        guard try device == string(operation, "recipient_device_id"), try peer == string(operation, "actor_id") else { throw Failure.invalidEnvelope }
        let local = try address(room: room, user: account!, device: device)
        let remote = try address(room: room, user: peer, device: string(operation, "sender_device_id"))
        let cipher = try decoded(operation, "ciphertext")
        guard cipher.count <= 65536 else { throw Failure.invalidEnvelope }
        let id = try uuid(string(operation, "operation_id")), key = "opened/\(room)/\(id)", request = Data(SHA256.hash(data: cipher)).base64EncodedString()
        if let old = try store.vault.get(key) {
            let saved = try object(old), previous = try nested(saved, "operation")
            guard try attachments(previous) == attachments(operation) else { throw Failure.invalidEnvelope }
            guard try string(saved, "request") == request, try string(saved, "peer") == remote.name else { throw Failure.conflict }
            for field in ["actor_id", "sender_device_id", "recipient_device_id", "kind", "target_id", "reply_to", "version", "signal_type"] {
                guard optionalString(previous, field) == optionalString(operation, field) else { throw Failure.invalidEnvelope }
            }
            guard saved["content"] != nil else { throw Failure.alreadyProcessed }
            return try nested(saved, "content")
        }
        _ = try store.saveIdentity(IdentityKey(bytes: decoded(input, "peer_identity")), for: remote, context: context)
        let plain: Data
        switch try uint(operation, "signal_type") {
        case 3:
            plain = try signalDecryptPreKey(message: PreKeySignalMessage(bytes: cipher), from: remote, localAddress: local, sessionStore: store,
                identityStore: store, preKeyStore: store, signedPreKeyStore: store, kyberPreKeyStore: store, context: context)
        case 2:
            plain = try signalDecrypt(message: SignalMessage(bytes: cipher), from: remote, to: local, sessionStore: store, identityStore: store, context: context)
        default: throw Failure.invalidEnvelope
        }
        let bound = try object(plain), inner = try nested(bound, "operation")
        guard try attachments(inner) == attachments(operation) else { throw Failure.invalidEnvelope }
        guard try uint(bound, "v") == 1, try string(bound, "room_id") == room, try string(bound, "actor_id") == string(operation, "actor_id") else { throw Failure.invalidEnvelope }
        for field in ["operation_id", "recipient_device_id", "target_id", "kind", "version", "reply_to"] {
            guard optionalString(inner, field) == optionalString(operation, field) else { throw Failure.invalidEnvelope }
        }
        guard try string(inner, "device_id") == string(operation, "sender_device_id") else { throw Failure.invalidEnvelope }
        let content = try nested(bound, "payload")
        try store.vault.put(key, data(["request": request, "peer": remote.name, "operation": operation, "content": content]))
        return content
    }

    private func attachments(_ operation: [String: Any]) throws -> [String] {
        guard let values = operation["attachments"] else { return [] }
        guard let array = values as? [String], array.count <= 3 else { throw Failure.invalidEnvelope }
        return try array.map { try uuid($0) }
    }

    private func validAccount(_ value: String) -> Bool { value.range(of: "^[1-9][0-9]{0,18}$", options: .regularExpression) != nil }
    private func appKey(_ value: String) throws -> String {
        guard value.hasPrefix("app/"), value.range(of: "^[A-Za-z0-9/.:_-]{4,220}$", options: .regularExpression) != nil else { throw Failure.invalidRequest }
        return value
    }
    private func uuid(_ value: String) throws -> String {
        guard let id = UUID(uuidString: value) else { throw Failure.invalidRequest }; return id.uuidString.lowercased()
    }
    private func address(room: String, user: String, device: String) throws -> ProtocolAddress {
        guard validAccount(user) else { throw Failure.invalidRequest }
        return try ProtocolAddress(name: "cc1:\(uuid(room)):\(user):\(uuid(device))", deviceId: 1)
    }
    private func data(_ value: [String: Any]) throws -> Data { try JSONSerialization.data(withJSONObject: value, options: [.sortedKeys]) }
    private func object(_ value: Data) throws -> [String: Any] {
        guard let result = try JSONSerialization.jsonObject(with: value) as? [String: Any] else { throw Failure.invalidRequest }; return result
    }
    private func nested(_ value: [String: Any], _ name: String) throws -> [String: Any] {
        guard let result = value[name] as? [String: Any] else { throw Failure.invalidRequest }; return result
    }
    private func optionalString(_ value: [String: Any], _ name: String) -> String {
        if let text = value[name] as? String { return text }; if let number = value[name] as? NSNumber { return number.stringValue }; return ""
    }
    private func string(_ value: [String: Any], _ name: String) throws -> String {
        let result = optionalString(value, name); guard !result.isEmpty else { throw Failure.invalidRequest }; return result
    }
    private func uint(_ value: [String: Any], _ name: String) throws -> UInt32 {
        guard let result = UInt32(try string(value, name)) else { throw Failure.invalidRequest }; return result
    }
    private func decoded(_ value: [String: Any], _ name: String) throws -> Data {
        guard let result = Data(base64Encoded: try string(value, name)) else { throw Failure.invalidRequest }; return result
    }
    private func required(_ name: String) throws -> Data {
        guard let result = try store.vault.get(name) else { throw Failure.invalidRequest }; return result
    }
}
