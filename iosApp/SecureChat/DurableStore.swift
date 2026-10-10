import Foundation
import LibSignalClient

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
        guard let value = try vault.get(key) else { throw SignalError.invalidKeyIdentifier("chat record unavailable") }
        return value
    }
    func identityKeyPair(context: StoreContext) throws -> IdentityKeyPair { try IdentityKeyPair(bytes: required("identity")) }
    func localRegistrationId(context: StoreContext) throws -> UInt32 { try JSONDecoder().decode(UInt32.self, from: required("registration")) }
    func identity(for address: ProtocolAddress, context: StoreContext) throws -> IdentityKey? {
        try vault.get("trusted/" + addressKey(address)).map { try IdentityKey(bytes: $0) }
    }
    func saveIdentity(_ identity: IdentityKey, for address: ProtocolAddress, context: StoreContext) throws -> IdentityChange {
        if let old = try self.identity(for: address, context: context), old != identity { throw SecureChatEngine.Failure.identityChanged }
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
            guard let session = try loadSession(for: $0, context: context) else { throw SignalError.sessionNotFound("chat session unavailable") }
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
