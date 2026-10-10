import CryptoKit
import Foundation
import LibSignalClient

struct PublicBundle: Codable {
    let registrationId: UInt32
    let identity: Data
    let prekey: Data
    let signedPrekey: Data
    let signature: Data
    let kyber: Data
    let kyberSignature: Data

    func native() throws -> PreKeyBundle {
        try PreKeyBundle(registrationId: registrationId, deviceId: 1, prekeyId: 1,
                         prekey: PublicKey(prekey), signedPrekeyId: 2, signedPrekey: PublicKey(signedPrekey),
                         signedPrekeySignature: signature, identity: IdentityKey(bytes: identity),
                         kyberPrekeyId: 3, kyberPrekey: KEMPublicKey(kyber), kyberPrekeySignature: kyberSignature)
    }
}

struct WireMessage: Codable, Equatable {
    let type: UInt8
    let ciphertext: Data
}

final class DurableStore: IdentityKeyStore, SessionStore, PreKeyStore, SignedPreKeyStore, KyberPreKeyStore {
    let vault: ProtectedVault
    init(_ vault: ProtectedVault) throws {
        self.vault = vault
        try vault.transaction {
            if try vault.get("identity") == nil {
                try vault.put("identity", IdentityKeyPair.generate().serialize())
                try vault.put("registration", JSONEncoder().encode(UInt32.random(in: 1...16380)))
            }
        }
    }
    private func addressKey(_ address: ProtocolAddress) -> String { "\(address.name)/\(address.deviceId)" }
    private func required(_ key: String) throws -> Data {
        guard let value = try vault.get(key) else { throw SignalError.invalidKeyIdentifier("evaluation record unavailable") }
        return value
    }
    func identityKeyPair(context: StoreContext) throws -> IdentityKeyPair { try IdentityKeyPair(bytes: required("identity")) }
    func localRegistrationId(context: StoreContext) throws -> UInt32 { try JSONDecoder().decode(UInt32.self, from: required("registration")) }
    func identity(for address: ProtocolAddress, context: StoreContext) throws -> IdentityKey? {
        try vault.get("trusted/" + addressKey(address)).map { try IdentityKey(bytes: $0) }
    }
    func saveIdentity(_ identity: IdentityKey, for address: ProtocolAddress, context: StoreContext) throws -> IdentityChange {
        if let old = try self.identity(for: address, context: context), old != identity { throw DurablePeer.Failure.identityChanged }
        try vault.put("trusted/" + addressKey(address), identity.serialize())
        return .newOrUnchanged
    }
    func isTrustedIdentity(_ identity: IdentityKey, for address: ProtocolAddress, direction: Direction, context: StoreContext) throws -> Bool {
        guard let old = try self.identity(for: address, context: context) else { return true }
        return old == identity
    }
    func loadSession(for address: ProtocolAddress, context: StoreContext) throws -> SessionRecord? {
        try vault.get("session/" + addressKey(address)).map { try SessionRecord(bytes: $0) }
    }
    func loadExistingSessions(for addresses: [ProtocolAddress], context: StoreContext) throws -> [SessionRecord] {
        try addresses.map {
            guard let session = try loadSession(for: $0, context: context) else { throw SignalError.sessionNotFound("evaluation session unavailable") }
            return session
        }
    }
    func storeSession(_ record: SessionRecord, for address: ProtocolAddress, context: StoreContext) throws { try vault.put("session/" + addressKey(address), record.serialize()) }
    func loadPreKey(id: UInt32, context: StoreContext) throws -> PreKeyRecord { try PreKeyRecord(bytes: required("prekey/\(id)")) }
    func storePreKey(_ record: PreKeyRecord, id: UInt32, context: StoreContext) throws { try vault.put("prekey/\(id)", record.serialize()) }
    func removePreKey(id: UInt32, context: StoreContext) throws { try vault.remove("prekey/\(id)") }
    func loadSignedPreKey(id: UInt32, context: StoreContext) throws -> SignedPreKeyRecord { try SignedPreKeyRecord(bytes: required("signed/\(id)")) }
    func storeSignedPreKey(_ record: SignedPreKeyRecord, id: UInt32, context: StoreContext) throws { try vault.put("signed/\(id)", record.serialize()) }
    func loadKyberPreKey(id: UInt32, context: StoreContext) throws -> KyberPreKeyRecord { try KyberPreKeyRecord(bytes: required("kyber/\(id)")) }
    func storeKyberPreKey(_ record: KyberPreKeyRecord, id: UInt32, context: StoreContext) throws { try vault.put("kyber/\(id)", record.serialize()) }
    func markKyberPreKeyUsed(id: UInt32, signedPreKeyId: UInt32, baseKey: PublicKey, context: StoreContext) throws { try vault.remove("kyber/\(id)") }
}

/// One synthetic device, one relationship. All private data remains in the vault.
/// Cross-platform fixtures contain public bundles and ciphertext only.
final class DurablePeer {
    enum Failure: Error { case conflict, identityChanged, invalidEnvelope }
    private struct Outgoing: Codable { let request: Data; let peer: String; let message: WireMessage }
    private struct Incoming: Codable { let message: WireMessage; let peer: String; let body: Data }
    let store: DurableStore
    let address: ProtocolAddress
    private let context = NullContext()

    init(directory: URL, scope: String, name: String) throws {
        store = try DurableStore(ProtectedVault(directory: directory, scope: scope))
        address = try ProtocolAddress(name: name, deviceId: 1)
    }
    func publish() throws -> PublicBundle {
        try store.vault.transaction {
            if let bytes = try store.vault.get("published") { return try JSONDecoder().decode(PublicBundle.self, from: bytes) }
            let oneTime = PrivateKey.generate()
            let signed = PrivateKey.generate()
            let pq = KEMKeyPair.generate()
            let identity = try store.identityKeyPair(context: context)
            let signature = identity.privateKey.generateSignature(message: signed.publicKey.serialize())
            let pqSignature = identity.privateKey.generateSignature(message: pq.publicKey.serialize())
            try store.storePreKey(PreKeyRecord(id: 1, privateKey: oneTime), id: 1, context: context)
            try store.storeSignedPreKey(SignedPreKeyRecord(id: 2, timestamp: 1, privateKey: signed, signature: signature), id: 2, context: context)
            try store.storeKyberPreKey(KyberPreKeyRecord(id: 3, timestamp: 1, keyPair: pq, signature: pqSignature), id: 3, context: context)
            let bundle = PublicBundle(registrationId: try store.localRegistrationId(context: context), identity: identity.identityKey.serialize(), prekey: oneTime.publicKey.serialize(), signedPrekey: signed.publicKey.serialize(), signature: signature, kyber: pq.publicKey.serialize(), kyberSignature: pqSignature)
            try store.vault.put("published", JSONEncoder().encode(bundle))
            return bundle
        }
    }
    func establish(_ bundle: PublicBundle, with remote: ProtocolAddress) throws {
        try store.vault.transaction {
            try processPreKeyBundle(bundle.native(), for: remote, ourAddress: address, sessionStore: store, identityStore: store, context: context)
        }
    }
    func send(id: String, body: Data, to remote: ProtocolAddress, failBeforeCommit: Bool = false) throws -> WireMessage {
        try store.vault.transaction(failBeforeCommit: failBeforeCommit) {
            let request = Data(SHA256.hash(data: body))
            if let prior = try store.vault.get("outbox/" + id) {
                let saved = try JSONDecoder().decode(Outgoing.self, from: prior)
                guard saved.request == request, saved.peer == remote.name else { throw Failure.conflict }
                return saved.message
            }
            let encrypted = try signalEncrypt(message: body, for: remote, localAddress: address, sessionStore: store, identityStore: store, context: context)
            let message = WireMessage(type: encrypted.messageType.rawValue, ciphertext: encrypted.serialize())
            try store.vault.put("outbox/" + id, JSONEncoder().encode(Outgoing(request: request, peer: remote.name, message: message)))
            return message
        }
    }
    func receive(id: String, message: WireMessage, from remote: ProtocolAddress, failBeforeCommit: Bool = false) throws -> Data {
        try store.vault.transaction(failBeforeCommit: failBeforeCommit) {
            if let prior = try store.vault.get("inbox/" + id) {
                let saved = try JSONDecoder().decode(Incoming.self, from: prior)
                guard saved.message == message, saved.peer == remote.name else { throw Failure.conflict }
                return saved.body
            }
            let body: Data
            switch CiphertextMessage.MessageType(rawValue: message.type) {
            case .preKey:
                body = try signalDecryptPreKey(message: PreKeySignalMessage(bytes: message.ciphertext), from: remote, localAddress: address, sessionStore: store, identityStore: store, preKeyStore: store, signedPreKeyStore: store, kyberPreKeyStore: store, context: context)
            case .whisper:
                body = try signalDecrypt(message: SignalMessage(bytes: message.ciphertext), from: remote, to: address, sessionStore: store, identityStore: store, context: context)
            default: throw Failure.invalidEnvelope
            }
            try store.vault.put("inbox/" + id, JSONEncoder().encode(Incoming(message: message, peer: remote.name, body: body)))
            return body
        }
    }
}
