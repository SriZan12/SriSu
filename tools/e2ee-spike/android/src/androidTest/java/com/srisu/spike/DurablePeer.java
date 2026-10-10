package com.srisu.spike;

import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;
import org.signal.libsignal.protocol.*;
import org.signal.libsignal.protocol.ecc.*;
import org.signal.libsignal.protocol.kem.*;
import org.signal.libsignal.protocol.message.*;
import org.signal.libsignal.protocol.state.*;

final class DurablePeer implements AutoCloseable {
    final DurableStore store;
    final SignalProtocolAddress address;
    DurablePeer(File directory, String scope, String name) throws Exception {
        store = new DurableStore(new ProtectedVault(directory, scope));
        address = new SignalProtocolAddress(name, 1);
    }
    static String b64(byte[] value) { return Base64.encodeToString(value, Base64.NO_WRAP); }
    static byte[] bytes(JSONObject json, String name) throws Exception { return Base64.decode(json.getString(name), Base64.NO_WRAP); }
    static byte[] encode(JSONObject json) { return json.toString().getBytes(StandardCharsets.UTF_8); }
    static JSONObject decode(byte[] value) throws Exception { return new JSONObject(new String(value, StandardCharsets.UTF_8)); }

    JSONObject publish() throws Exception {
        return store.vault.transaction(false, () -> {
            byte[] existing = store.vault.get("published");
            if (existing != null) return decode(existing);
            var oneTime = ECKeyPair.generate();
            var signed = ECKeyPair.generate();
            var pq = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
            var identity = store.getIdentityKeyPair();
            byte[] signature = identity.getPrivateKey().calculateSignature(signed.getPublicKey().serialize());
            byte[] pqSignature = identity.getPrivateKey().calculateSignature(pq.getPublicKey().serialize());
            store.storePreKey(1, new PreKeyRecord(1, oneTime));
            store.storeSignedPreKey(2, new SignedPreKeyRecord(2, 1, signed, signature));
            store.storeKyberPreKey(3, new KyberPreKeyRecord(3, 1, pq, pqSignature));
            var result = new JSONObject().put("registrationId", store.getLocalRegistrationId())
                .put("identity", b64(identity.getPublicKey().serialize())).put("prekey", b64(oneTime.getPublicKey().serialize()))
                .put("signedPrekey", b64(signed.getPublicKey().serialize())).put("signature", b64(signature))
                .put("kyber", b64(pq.getPublicKey().serialize())).put("kyberSignature", b64(pqSignature));
            store.vault.put("published", encode(result));
            return result;
        });
    }
    void establish(JSONObject bundle, SignalProtocolAddress remote) throws Exception {
        store.vault.transaction(false, () -> {
            var nativeBundle = new PreKeyBundle(bundle.getInt("registrationId"), 1, 1, new ECPublicKey(bytes(bundle, "prekey")),
                2, new ECPublicKey(bytes(bundle, "signedPrekey")), bytes(bundle, "signature"), new IdentityKey(bytes(bundle, "identity")),
                3, new KEMPublicKey(bytes(bundle, "kyber")), bytes(bundle, "kyberSignature"));
            new SessionBuilder(store, remote, address).process(nativeBundle);
            return null;
        });
    }
    JSONObject send(String id, byte[] body, SignalProtocolAddress remote, boolean failBeforeCommit) throws Exception {
        return store.vault.transaction(failBeforeCommit, () -> {
            String digest = b64(MessageDigest.getInstance("SHA-256").digest(body));
            byte[] prior = store.vault.get("outbox/" + id);
            if (prior != null) {
                var saved = decode(prior);
                if (!saved.getString("request").equals(digest) || !saved.getString("peer").equals(remote.getName())) throw new IllegalStateException("Operation ID conflict");
                return saved.getJSONObject("message");
            }
            var encrypted = new SessionCipher(store, address, remote).encrypt(body);
            var result = new JSONObject().put("type", encrypted.getType()).put("ciphertext", b64(encrypted.serialize()));
            store.vault.put("outbox/" + id, encode(new JSONObject().put("request", digest).put("peer", remote.getName()).put("message", result)));
            return result;
        });
    }
    byte[] receive(String id, JSONObject message, SignalProtocolAddress remote, boolean failBeforeCommit) throws Exception {
        return store.vault.transaction(failBeforeCommit, () -> {
            byte[] prior = store.vault.get("inbox/" + id);
            if (prior != null) {
                var saved = decode(prior);
                var old = saved.getJSONObject("message");
                if (!saved.getString("peer").equals(remote.getName()) || old.getInt("type") != message.getInt("type")
                    || !MessageDigest.isEqual(bytes(old, "ciphertext"), bytes(message, "ciphertext"))) throw new IllegalStateException("Envelope ID conflict");
                return bytes(saved, "body");
            }
            var cipher = new SessionCipher(store, address, remote);
            byte[] body;
            int type = message.getInt("type");
            if (type == CiphertextMessage.PREKEY_TYPE) body = cipher.decrypt(new PreKeySignalMessage(bytes(message, "ciphertext")));
            else if (type == CiphertextMessage.WHISPER_TYPE) body = cipher.decrypt(new SignalMessage(bytes(message, "ciphertext")));
            else throw new IllegalStateException("Unsupported encrypted envelope");
            store.vault.put("inbox/" + id, encode(new JSONObject().put("message", message).put("peer", remote.getName()).put("body", b64(body))));
            return body;
        });
    }
    public void close() { store.vault.close(); }
}
