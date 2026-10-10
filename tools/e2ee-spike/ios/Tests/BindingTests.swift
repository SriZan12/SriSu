import Foundation
import LibSignalClient
import XCTest

final class BindingTests: XCTestCase {
    private func publish(_ store: InMemorySignalProtocolStore) throws -> PreKeyBundle {
        let context = NullContext()
        let oneTime = PrivateKey.generate()
        let signed = PrivateKey.generate()
        let pq = KEMKeyPair.generate()
        let identity = try store.identityKeyPair(context: context)
        let signature = identity.privateKey.generateSignature(message: signed.publicKey.serialize())
        let pqSignature = identity.privateKey.generateSignature(message: pq.publicKey.serialize())
        try store.storePreKey(PreKeyRecord(id: 1, privateKey: oneTime), id: 1, context: context)
        try store.storeSignedPreKey(SignedPreKeyRecord(id: 2, timestamp: 1, privateKey: signed, signature: signature), id: 2, context: context)
        try store.storeKyberPreKey(KyberPreKeyRecord(id: 3, timestamp: 1, keyPair: pq, signature: pqSignature), id: 3, context: context)
        return try PreKeyBundle(
            registrationId: store.localRegistrationId(context: context), deviceId: 1,
            prekeyId: 1, prekey: oneTime.publicKey,
            signedPrekeyId: 2, signedPrekey: signed.publicKey, signedPrekeySignature: signature,
            identity: identity.identityKey, kyberPrekeyId: 3, kyberPrekey: pq.publicKey,
            kyberPrekeySignature: pqSignature
        )
    }

    func testNativeRoundTripReplayTamperAndSerializedSession() throws {
        let context = NullContext()
        let a = try ProtocolAddress(name: "synthetic-a:ios-room", deviceId: 1)
        let b = try ProtocolAddress(name: "synthetic-b:ios-room", deviceId: 1)
        let first = InMemorySignalProtocolStore()
        let second = InMemorySignalProtocolStore()
        try processPreKeyBundle(publish(second), for: b, ourAddress: a, sessionStore: first, identityStore: first, context: context)
        let body = Data("Synthetic नेपाली 🌱".utf8)
        let outgoing = try signalEncrypt(message: body, for: b, localAddress: a, sessionStore: first, identityStore: first, context: context)
        XCTAssertEqual(outgoing.messageType, .preKey)
        let received = try signalDecryptPreKey(message: PreKeySignalMessage(bytes: outgoing.serialize()), from: a, localAddress: b, sessionStore: second, identityStore: second, preKeyStore: second, signedPreKeyStore: second, kyberPreKeyStore: second, context: context)
        XCTAssertEqual(body, received)
        let reply = try signalEncrypt(message: body, for: a, localAddress: b, sessionStore: second, identityStore: second, context: context)
        XCTAssertEqual(reply.messageType, .whisper)
        XCTAssertEqual(body, try signalDecrypt(message: SignalMessage(bytes: reply.serialize()), from: b, to: a, sessionStore: first, identityStore: first, context: context))

        let restored = InMemorySignalProtocolStore(identity: try IdentityKeyPair(bytes: first.identityKeyPair(context: context).serialize()), registrationId: try first.localRegistrationId(context: context))
        try restored.storeSession(SessionRecord(bytes: XCTUnwrap(first.loadSession(for: b, context: context)).serialize()), for: b, context: context)
        _ = try restored.saveIdentity(XCTUnwrap(first.identity(for: b, context: context)), for: b, context: context)
        let afterRestart = try signalEncrypt(message: body, for: b, localAddress: a, sessionStore: restored, identityStore: restored, context: context)
        XCTAssertEqual(body, try signalDecrypt(message: SignalMessage(bytes: afterRestart.serialize()), from: a, to: b, sessionStore: second, identityStore: second, context: context))
        XCTAssertThrowsError(try signalDecrypt(message: SignalMessage(bytes: afterRestart.serialize()), from: a, to: b, sessionStore: second, identityStore: second, context: context))
        let future = try signalEncrypt(message: body, for: a, localAddress: b, sessionStore: second, identityStore: second, context: context)
        var tampered = future.serialize()
        tampered[tampered.count - 1] ^= 1
        XCTAssertThrowsError(try signalDecrypt(message: SignalMessage(bytes: tampered), from: b, to: a, sessionStore: restored, identityStore: restored, context: context))
        XCTAssertEqual(body, try signalDecrypt(message: SignalMessage(bytes: future.serialize()), from: b, to: a, sessionStore: restored, identityStore: restored, context: context))
        XCTAssertThrowsError(try processPreKeyBundle(publish(InMemorySignalProtocolStore()), for: b, ourAddress: a, sessionStore: restored, identityStore: restored, context: context))
    }
}
