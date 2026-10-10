package com.srisu.srisu.securechat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.signal.libsignal.protocol.*;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord;
import org.signal.libsignal.protocol.state.*;

final class DurableStore implements SignalProtocolStore {
    final ProtectedVault vault;
    DurableStore(ProtectedVault vault) throws Exception {
        this.vault = vault;
        vault.transaction(false, () -> {
            if (vault.get("identity") == null) {
                vault.put("identity", IdentityKeyPair.generate().serialize());
                vault.put("registration", Integer.toString(1 + new SecureRandom().nextInt(16380)).getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
    }
    private static String address(SignalProtocolAddress value) { return value.getName() + "/" + value.getDeviceId(); }
    private static <T> T checked(Callable<T> work) {
        try { return work.call(); } catch (Exception error) { throw new IllegalStateException("Protocol record unavailable", error); }
    }
    private byte[] required(String name) throws InvalidKeyIdException {
        byte[] bytes = vault.get(name);
        if (bytes == null) throw new InvalidKeyIdException("Chat key unavailable");
        return bytes;
    }
    public IdentityKeyPair getIdentityKeyPair() { return checked(() -> new IdentityKeyPair(required("identity"))); }
    public int getLocalRegistrationId() { return Integer.parseInt(new String(vault.get("registration"), StandardCharsets.UTF_8)); }
    public IdentityKey getIdentity(SignalProtocolAddress remote) {
        byte[] value = vault.get("trusted/" + address(remote));
        return value == null ? null : checked(() -> new IdentityKey(value));
    }
    public IdentityChange saveIdentity(SignalProtocolAddress remote, IdentityKey key) {
        IdentityKey old = getIdentity(remote);
        if (old != null && !old.equals(key)) throw new IllegalStateException("Partner identity changed");
        vault.put("trusted/" + address(remote), key.serialize());
        return IdentityChange.NEW_OR_UNCHANGED;
    }
    public boolean isTrustedIdentity(SignalProtocolAddress remote, IdentityKey key, Direction direction) {
        IdentityKey old = getIdentity(remote);
        return old == null || old.equals(key);
    }
    public SessionRecord loadSession(SignalProtocolAddress remote) {
        byte[] value = vault.get("session/" + address(remote));
        return value == null ? new SessionRecord() : checked(() -> new SessionRecord(value));
    }
    public List<SessionRecord> loadExistingSessions(List<SignalProtocolAddress> addresses) throws NoSessionException {
        var result = new ArrayList<SessionRecord>();
        for (var remote : addresses) {
            if (!containsSession(remote)) throw new NoSessionException(remote, "Chat session unavailable");
            result.add(loadSession(remote));
        }
        return result;
    }
    public List<Integer> getSubDeviceSessions(String name) {
        var result = new ArrayList<Integer>();
        String prefix = "session/" + name + "/";
        for (String item : vault.keys(prefix)) {
            int device = Integer.parseInt(item.substring(prefix.length()));
            if (device != 1) result.add(device);
        }
        return result;
    }
    public void storeSession(SignalProtocolAddress remote, SessionRecord record) { vault.put("session/" + address(remote), record.serialize()); }
    public boolean containsSession(SignalProtocolAddress remote) { return vault.get("session/" + address(remote)) != null; }
    public void deleteSession(SignalProtocolAddress remote) { vault.remove("session/" + address(remote)); }
    public void deleteAllSessions(String name) { for (String item : vault.keys("session/" + name + "/")) vault.remove(item); }
    public PreKeyRecord loadPreKey(int id) throws InvalidKeyIdException { byte[] value = required("prekey/" + id); return checked(() -> new PreKeyRecord(value)); }
    public void storePreKey(int id, PreKeyRecord record) { vault.put("prekey/" + id, record.serialize()); }
    public boolean containsPreKey(int id) { return vault.get("prekey/" + id) != null; }
    public void removePreKey(int id) { vault.remove("prekey/" + id); }
    public SignedPreKeyRecord loadSignedPreKey(int id) throws InvalidKeyIdException { byte[] value = required("signed/" + id); return checked(() -> new SignedPreKeyRecord(value)); }
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        var result = new ArrayList<SignedPreKeyRecord>();
        for (String name : vault.keys("signed/")) result.add(checked(() -> new SignedPreKeyRecord(vault.get(name))));
        return result;
    }
    public void storeSignedPreKey(int id, SignedPreKeyRecord record) { vault.put("signed/" + id, record.serialize()); }
    public boolean containsSignedPreKey(int id) { return vault.get("signed/" + id) != null; }
    public void removeSignedPreKey(int id) { vault.remove("signed/" + id); }
    public KyberPreKeyRecord loadKyberPreKey(int id) throws InvalidKeyIdException { byte[] value = required("kyber/" + id); return checked(() -> new KyberPreKeyRecord(value)); }
    public List<KyberPreKeyRecord> loadKyberPreKeys() {
        var result = new ArrayList<KyberPreKeyRecord>();
        for (String name : vault.keys("kyber/")) result.add(checked(() -> new KyberPreKeyRecord(vault.get(name))));
        return result;
    }
    public void storeKyberPreKey(int id, KyberPreKeyRecord record) { vault.put("kyber/" + id, record.serialize()); }
    public boolean containsKyberPreKey(int id) { return vault.get("kyber/" + id) != null; }
    public void markKyberPreKeyUsed(int id, int signedId, ECPublicKey baseKey) { vault.remove("kyber/" + id); }
    public void storeSenderKey(SignalProtocolAddress remote, UUID id, SenderKeyRecord record) { vault.put("sender/" + address(remote) + "/" + id, record.serialize()); }
    public SenderKeyRecord loadSenderKey(SignalProtocolAddress remote, UUID id) {
        byte[] value = vault.get("sender/" + address(remote) + "/" + id);
        return value == null ? null : checked(() -> new SenderKeyRecord(value));
    }
}
