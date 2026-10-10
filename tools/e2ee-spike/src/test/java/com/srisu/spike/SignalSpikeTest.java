package com.srisu.spike;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.Test;
import org.signal.libsignal.protocol.*;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.message.*;
import org.signal.libsignal.protocol.state.*;
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore;

/** Binding evaluation only. Does not claim Android/iOS or protected persistence. */
public class SignalSpikeTest {
    private static final SecureRandom RANDOM = new SecureRandom();

    private static InMemorySignalProtocolStore account() {
        return new InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1 + RANDOM.nextInt(16380));
    }

    private static PreKeyBundle publish(InMemorySignalProtocolStore store) throws Exception {
        var oneTime = ECKeyPair.generate();
        var signed = ECKeyPair.generate();
        var pq = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
        var signing = store.getIdentityKeyPair().getPrivateKey();
        byte[] signedSignature = signing.calculateSignature(signed.getPublicKey().serialize());
        byte[] pqSignature = signing.calculateSignature(pq.getPublicKey().serialize());
        store.storePreKey(1, new PreKeyRecord(1, oneTime));
        store.storeSignedPreKey(2, new SignedPreKeyRecord(2, System.currentTimeMillis(), signed, signedSignature));
        store.storeKyberPreKey(3, new KyberPreKeyRecord(3, System.currentTimeMillis(), pq, pqSignature));
        return new PreKeyBundle(store.getLocalRegistrationId(), 1, 1, oneTime.getPublicKey(),
            2, signed.getPublicKey(), signedSignature, store.getIdentityKeyPair().getPublicKey(),
            3, pq.getPublicKey(), pqSignature);
    }

    private static byte[] decrypt(SessionCipher cipher, CiphertextMessage message) throws Exception {
        return message.getType() == CiphertextMessage.PREKEY_TYPE
            ? cipher.decrypt(new PreKeySignalMessage(message.serialize()))
            : cipher.decrypt(new SignalMessage(message.serialize()));
    }

    @Test public void nativeBindingRoundTripSessionSerializationReplayAndIdentityChange() throws Exception {
        var a = new SignalProtocolAddress("synthetic-account-a:relationship-1", 1);
        var b = new SignalProtocolAddress("synthetic-account-b:relationship-1", 1);
        var first = account();
        var second = account();
        new SessionBuilder(first, b, a).process(publish(second));
        var sender = new SessionCipher(first, a, b);
        var recipient = new SessionCipher(second, b, a);
        byte[] body = "Synthetic नेपाली 🌱".getBytes(StandardCharsets.UTF_8);
        var outgoing = sender.encrypt(body);
        assertFalse(java.util.Arrays.equals(body, outgoing.serialize()));
        assertArrayEquals(body, decrypt(recipient, outgoing));
        var response = recipient.encrypt(body);
        assertArrayEquals(body, decrypt(sender, response));

        // Reconstruct native stores from serialized session state after both peers
        // have established sessions. A real app must commit this with its outbox.
        var restored = new InMemorySignalProtocolStore(
            new IdentityKeyPair(first.getIdentityKeyPair().serialize()), first.getLocalRegistrationId());
        restored.storeSession(b, new SessionRecord(first.loadSession(b).serialize()));
        restored.saveIdentity(b, first.getIdentity(b));
        var resumedSender = new SessionCipher(restored, a, b);
        var afterRestart = resumedSender.encrypt(body);
        assertArrayEquals(body, decrypt(recipient, afterRestart));
        assertThrows(DuplicateMessageException.class, () -> decrypt(recipient, afterRestart));

        var future = recipient.encrypt(body);
        byte[] tampered = future.serialize();
        tampered[tampered.length - 1] ^= 1;
        assertThrows(InvalidMessageException.class, () -> resumedSender.decrypt(new SignalMessage(tampered)));
        assertArrayEquals(body, decrypt(resumedSender, future));
        assertThrows(UntrustedIdentityException.class,
            () -> new SessionBuilder(restored, b, a).process(publish(account())));
    }

    @Test public void delayedMessagesUseLibrarySkippedKeys() throws Exception {
        var a = new SignalProtocolAddress("synthetic-a:relationship-2", 1);
        var b = new SignalProtocolAddress("synthetic-b:relationship-2", 1);
        var first = account(); var second = account();
        new SessionBuilder(first, b, a).process(publish(second));
        var sender = new SessionCipher(first, a, b);
        var recipient = new SessionCipher(second, b, a);
        decrypt(recipient, sender.encrypt(new byte[] {0}));
        decrypt(sender, recipient.encrypt(new byte[] {0}));
        var delayed = sender.encrypt(new byte[] {1});
        var latest = sender.encrypt(new byte[] {2});
        assertArrayEquals(new byte[] {2}, decrypt(recipient, latest));
        assertArrayEquals(new byte[] {1}, decrypt(recipient, delayed));
    }
}
