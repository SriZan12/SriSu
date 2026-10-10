package com.srisu.srisu.securechat;

import android.content.Context;
import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.signal.libsignal.protocol.*;
import org.signal.libsignal.protocol.ecc.*;
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator;
import org.signal.libsignal.protocol.kem.*;
import org.signal.libsignal.protocol.message.*;
import org.signal.libsignal.protocol.state.*;

/** Native libsignal and protected persistence boundary. No network or logging. */
public final class SecureChatEngine {
    private final File root;
    private DurableStore store;
    private String account;
    private static final class Failure extends Exception {
        final String code;
        Failure(String code) { this.code = code; }
    }

    public SecureChatEngine(Context context) { root = new File(context.getNoBackupFilesDir(), "couple-chat-v1"); }

    public synchronized void close(String accountId) {
        if (accountId.equals(account) && store != null) { store.vault.close(); store = null; account = null; }
    }

    public synchronized String execute(String accountId, String command, String payload) {
        return executeInternal(accountId, command, payload, null);
    }

    public synchronized String executeBinary(String accountId, String command, String payload, byte[] bytes) {
        if (!command.equals("media_seal") && !command.equals("media_cache")) return "{\"ok\":false,\"code\":\"invalid_request\"}";
        return executeInternal(accountId, command, payload, bytes);
    }

    public synchronized byte[] readAttachment(String accountId, String descriptor, boolean decrypt) {
        try {
            if (descriptor.length()>262144) return null;
            selectAccount(accountId);
            return store.vault.transaction(false, () -> new AttachmentVault(store.vault).read(new JSONObject(descriptor),decrypt));
        } catch (Exception ignored) { return null; }
    }

    private void selectAccount(String accountId) throws Exception {
        if (!accountId.matches("[1-9][0-9]{0,18}")) throw new Failure("invalid_request");
        if (!accountId.equals(account)) {
            if (store != null) { store.vault.close(); store = null; account = null; }
            store = new DurableStore(new ProtectedVault(new File(root, accountId), accountId));
            account = accountId;
        }
    }

    private String executeInternal(String accountId, String command, String payload, byte[] binary) {
        try {
            if (!accountId.matches("[1-9][0-9]{0,18}") || payload.length() > 262144) throw new Failure("invalid_request");
            if (binary!=null && binary.length>AttachmentVault.MAX_PLAIN+28) throw new Failure("attachment_too_large");
            if (command.equals("reset")) {
                if (!new JSONObject(payload).optBoolean("confirm_history_loss", false)) throw new Failure("invalid_request");
                close(accountId);
                ProtectedVault.deleteStorageKey(accountId);
                File directory = new File(root, accountId);
                File[] mediaFiles = new File(directory,"media").listFiles();
                if (mediaFiles!=null) for (File file:mediaFiles) if (file.isFile() && !file.delete()) throw new Failure("storage_or_protocol_failure");
                for (String name : new String[] {"records.sqlite", "records.sqlite-wal", "records.sqlite-shm", "records.sqlite-journal"}) {
                    File file = new File(directory, name);
                    if (file.exists() && !file.delete()) throw new Failure("storage_or_protocol_failure");
                }
                return "{\"ok\":true,\"value\":null}";
            }
            selectAccount(accountId);
            JSONObject input = new JSONObject(payload);
            Object result = store.vault.transaction(false, () -> {
                if (command.equals("media_seal")) return new AttachmentVault(store.vault).seal(input,binary);
                if (command.equals("media_cache")) {new AttachmentVault(store.vault).receive(input,binary);return JSONObject.NULL;}
                return run(command,input);
            });
            return new JSONObject().put("ok", true).put("value", result == null ? JSONObject.NULL : result).toString();
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            String code = error instanceof Failure ? ((Failure) error).code
                : error instanceof ProtectedVault.MissingKey ? "storage_key_lost"
                : error instanceof UntrustedIdentityException ? "identity_changed"
                : error instanceof NoSessionException ? "session_missing"
                : error instanceof DuplicateMessageException ? "replay_rejected"
                : error instanceof org.json.JSONException ? (command.equals("open") ? "invalid_envelope" : "invalid_request")
                : error instanceof InvalidMessageException || error instanceof InvalidKeyException || error instanceof InvalidKeyIdException ? "invalid_ciphertext"
                : "storage_or_protocol_failure";
            return "{\"ok\":false,\"code\":\"" + code + "\"}";
        }
    }

    private Object run(String command, JSONObject input) throws Exception {
        switch (command) {
            case "media_release": new AttachmentVault(store.vault).release(input,false);return JSONObject.NULL;
            case "media_delete": new AttachmentVault(store.vault).release(input,true);return JSONObject.NULL;
            case "media_available": return new AttachmentVault(store.vault).available(input.getJSONArray("ids"));
            case "is_sealed": return store.vault.get("sealed/" + UUID.fromString(input.getString("room_id")) + "/" + UUID.fromString(input.getString("operation_id"))) != null;
            case "count": return store.vault.count(appKey(input.getString("prefix")));
            case "read_many": {
                JSONArray keys = input.getJSONArray("keys"), result = new JSONArray();
                if (keys.length() > 200) throw new Failure("invalid_request");
                for (int i = 0; i < keys.length(); i++) {
                    byte[] value = store.vault.get(appKey(keys.getString(i)));
                    result.put(value == null ? JSONObject.NULL : new String(value, StandardCharsets.UTF_8));
                }
                return result;
            }
            case "read": {
                byte[] value = store.vault.get(appKey(input.getString("key")));
                return value == null ? JSONObject.NULL : new String(value, StandardCharsets.UTF_8);
            }
            case "page": {
                String prefix = appKey(input.getString("prefix"));
                int limit = input.optInt("limit", 50);
                if (limit < 1 || limit > 100) throw new Failure("invalid_request");
                var result = new JSONArray();
                for (String key : store.vault.page(prefix, input.optString("before", ""), limit))
                    result.put(new JSONObject().put("key", key).put("value", new String(store.vault.get(key), StandardCharsets.UTF_8)));
                return result;
            }
            case "write": {
                JSONArray entries = input.getJSONArray("entries");
                if (entries.length() > 200) throw new Failure("invalid_request");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    String key = appKey(entry.getString("key"));
                    if (entry.isNull("value")) store.vault.remove(key);
                    else {
                        store.vault.put(key, entry.getString("value").getBytes(StandardCharsets.UTF_8));
                        // The projection and its processed marker commit together.
                        // Retain authenticated replay metadata, then drop the extra
                        // decrypted journal copy in that same SQLite transaction.
                        if (key.startsWith("app/processed/") && entry.getString("value").equals("true")) {
                            String journal = "opened/" + key.substring("app/processed/".length());
                            byte[] prior = store.vault.get(journal);
                            if (prior != null) { JSONObject saved = json(prior); saved.remove("content"); store.vault.put(journal,bytes(saved)); }
                        }
                    }
                }
                return JSONObject.NULL;
            }
            case "registration": return registration(false);
            case "replace": return registration(true);
            case "publication_needed": {
                byte[] last=store.vault.get("current-signed-public");
                return last==null || System.currentTimeMillis()-json(last).getLong("created_at")>=30L*86400000L;
            }
            case "publication": {
                String id = UUID.fromString(input.getString("operation_id")).toString();
                byte[] previous = store.vault.get("publication/" + id);
                if (previous != null) return json(previous);
                int count=input.optInt("prekey_count",10);
                if(count<0 || count>10)throw new Failure("invalid_request");
                JSONObject publication = generateKeys(count).put("device_id", registration(false).getString("device_id")).put("operation_id", id);
                store.vault.put("publication/" + id, bytes(publication));
                return publication;
            }
            case "trust": return trust(input, input.optBoolean("confirm_change", false));
            case "establish": {
                JSONObject bundle = input.getJSONObject("bundle");
                trust(new JSONObject().put("peer_user_id", bundle.getLong("user_id")).put("peer_identity", bundle.getString("identity_key")), false);
                JSONObject ec = bundle.getJSONObject("ec_prekey"), signed = bundle.getJSONObject("signed_prekey"), kyber = bundle.getJSONObject("kyber_prekey");
                var nativeBundle = new PreKeyBundle(bundle.getInt("registration_id"), 1, ec.getInt("id"), new ECPublicKey(decode(ec.getString("public_key"))),
                    signed.getInt("id"), new ECPublicKey(decode(signed.getString("public_key"))), decode(signed.getString("signature")),
                    new IdentityKey(decode(bundle.getString("identity_key"))), kyber.getInt("id"), new KEMPublicKey(decode(kyber.getString("public_key"))), decode(kyber.getString("signature")));
                var local = address(input.getString("room_id"), account, registration(false).getString("device_id"));
                var remote = address(input.getString("room_id"), bundle.getString("user_id"), bundle.getString("id"));
                // The client persists a claim ID; processing its bundle is idempotent locally too.
                String claim = UUID.fromString(input.getString("claim_id")).toString();
                if (store.vault.get("established/" + claim) == null) {
                    new SessionBuilder(store, remote, local).process(nativeBundle);
                    store.vault.put("established/" + claim, bytes(new JSONObject().put("peer", remote.getName())));
                }
                return JSONObject.NULL;
            }
            case "has_session": return store.containsSession(address(input.getString("room_id"), input.getString("peer_user_id"), input.getString("peer_device_id")));
            case "seal": return seal(input);
            case "open": return open(input);
            case "fingerprint": {
                var fingerprint = new NumericFingerprintGenerator(5200).createFor(2, account.getBytes(StandardCharsets.UTF_8),
                    store.getIdentityKeyPair().getPublicKey(), input.getString("peer_user_id").getBytes(StandardCharsets.UTF_8),
                    new IdentityKey(decode(input.getString("peer_identity"))));
                JSONObject result = new JSONObject().put("display", fingerprint.getDisplayableFingerprint().getDisplayText())
                    .put("qr", encode(fingerprint.getScannableFingerprint().getSerialized()));
                if(input.has("scan")) {
                    boolean matches=false;
                    try {byte[] scanned=decode(input.getString("scan"));
                        if(scanned.length<=512)matches=fingerprint.getScannableFingerprint().compareTo(scanned);
                    } catch(org.signal.libsignal.protocol.fingerprint.FingerprintParsingException | org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException | IllegalArgumentException invalid) {
                        // An invalid public QR is a mismatch, never a storage failure.
                    }
                    result.put("matches",matches);
                }
                return result;
            }
            default: throw new Failure("invalid_request");
        }
    }

    private String appKey(String key) throws Failure {
        if (!key.startsWith("app/") || !key.matches("[A-Za-z0-9/.:_-]{4,220}")) throw new Failure("invalid_request");
        return key;
    }

    private JSONObject registration(boolean replace) throws Exception {
        byte[] existing = store.vault.get("public-registration");
        if (!replace && existing != null) return json(existing);
        JSONObject result = generateKeys().put("device_id", UUID.randomUUID().toString())
            .put("registration_id", store.getLocalRegistrationId()).put("identity_key", encode(store.getIdentityKeyPair().getPublicKey().serialize()))
            .put("replace_device_id", JSONObject.NULL);
        store.vault.put("public-registration", bytes(result));
        return result;
    }

    private int nextKeyId() throws Exception {
        byte[] stored = store.vault.get("next-key-id");
        int id = stored == null ? 1 : Integer.parseInt(new String(stored, StandardCharsets.UTF_8));
        if (id >= Integer.MAX_VALUE - 100) throw new Failure("key_ids_exhausted");
        store.vault.put("next-key-id", Integer.toString(id + 1).getBytes(StandardCharsets.UTF_8));
        return id;
    }

    private JSONObject generateKeys() throws Exception {
        return generateKeys(10);
    }
    private JSONObject generateKeys(int count) throws Exception {
        var identity = store.getIdentityKeyPair();
        JSONObject signed;
        byte[] last = store.vault.get("current-signed-public");
        long now = System.currentTimeMillis();
        if (last == null || now - json(last).getLong("created_at") >= 30L * 86400000L) {
            int id = nextKeyId();
            var pair = ECKeyPair.generate();
            byte[] signature = identity.getPrivateKey().calculateSignature(pair.getPublicKey().serialize());
            store.storeSignedPreKey(id, new SignedPreKeyRecord(id, now, pair, signature));
            signed = new JSONObject().put("id", id).put("public_key", encode(pair.getPublicKey().serialize())).put("signature", encode(signature));
            store.vault.put("current-signed-public", bytes(new JSONObject().put("created_at", now).put("key", signed)));
        } else signed = json(last).getJSONObject("key");
        var ec = new JSONArray();
        var kyber = new JSONArray();
        for (int i = 0; i < count; i++) {
            int ecId = nextKeyId(), pqId = nextKeyId();
            var pair = ECKeyPair.generate();
            store.storePreKey(ecId, new PreKeyRecord(ecId, pair));
            ec.put(new JSONObject().put("id", ecId).put("public_key", encode(pair.getPublicKey().serialize())));
            var pq = KEMKeyPair.generate(KEMKeyType.KYBER_1024);
            byte[] signature = identity.getPrivateKey().calculateSignature(pq.getPublicKey().serialize());
            store.storeKyberPreKey(pqId, new KyberPreKeyRecord(pqId, now, pq, signature));
            kyber.put(new JSONObject().put("id", pqId).put("public_key", encode(pq.getPublicKey().serialize())).put("signature", encode(signature)));
        }
        return new JSONObject().put("signed_prekey", signed).put("ec_prekeys", ec).put("kyber_prekeys", kyber);
    }

    private Object trust(JSONObject input, boolean confirm) throws Exception {
        String peer = input.getString("peer_user_id");
        if (!peer.matches("[1-9][0-9]{0,18}") || peer.equals(account)) throw new Failure("invalid_request");
        byte[] identity = new IdentityKey(decode(input.getString("peer_identity"))).serialize();
        byte[] previous = store.vault.get("peer-identity/" + peer);
        if (previous != null && !MessageDigest.isEqual(previous, identity) && !confirm) throw new Failure("identity_changed");
        store.vault.put("peer-identity/" + peer, identity);
        store.vault.put("trusted-identity/" + peer + "/" + encode(MessageDigest.getInstance("SHA-256").digest(identity)), identity);
        return JSONObject.NULL;
    }

    private void trustIncoming(JSONObject input) throws Exception {
        String peer = input.getString("peer_user_id");
        if (!peer.matches("[1-9][0-9]{0,18}") || peer.equals(account)) throw new Failure("invalid_request");
        byte[] identity = new IdentityKey(decode(input.getString("peer_identity"))).serialize();
        String key = "trusted-identity/" + peer + "/" + encode(MessageDigest.getInstance("SHA-256").digest(identity));
        // Previously confirmed identities remain usable for authenticated delayed
        // packets. They never replace the current identity used for new sends.
        if (store.vault.get(key) == null) trust(input, false);
    }

    private JSONObject seal(JSONObject input) throws Exception {
        trust(input, false);
        JSONObject operation = new JSONObject(input.getJSONObject("operation").toString());
        if (operation.optJSONArray("attachments")!=null && operation.getJSONArray("attachments").length()==0) operation.remove("attachments");
        String room = UUID.fromString(input.getString("room_id")).toString();
        String id = UUID.fromString(operation.getString("operation_id")).toString();
        String localDevice = registration(false).getString("device_id");
        if (!localDevice.equals(operation.getString("device_id"))) throw new Failure("device_changed");
        var local = address(room, account, localDevice);
        var remote = address(room, input.getString("peer_user_id"), operation.getString("recipient_device_id"));
        JSONObject bound = new JSONObject().put("v", 1).put("room_id", room).put("actor_id", account)
            .put("operation", operation).put("payload", input.getJSONObject("content"));
        byte[] plaintext = bytes(bound);
        if (plaintext.length > 49152) throw new Failure("message_too_large");
        String request = encode(MessageDigest.getInstance("SHA-256").digest(plaintext));
        String key = "sealed/" + room + "/" + id;
        byte[] existing = store.vault.get(key);
        if (existing != null) {
            JSONObject old = json(existing);
            if (!old.getString("request").equals(request) || !old.getString("peer").equals(remote.getName())) throw new Failure("operation_conflict");
            return old.getJSONObject("wire");
        }
        var encrypted = new SessionCipher(store, local, remote).encrypt(plaintext);
        JSONObject wire = new JSONObject(operation.toString()).put("signal_type", encrypted.getType()).put("ciphertext", encode(encrypted.serialize()));
        store.vault.put(key, bytes(new JSONObject().put("request", request).put("peer", remote.getName()).put("wire", wire)));
        return wire;
    }

    private JSONObject open(JSONObject input) throws Exception {
        trustIncoming(input);
        JSONObject operation = input.getJSONObject("operation");
        String room = UUID.fromString(input.getString("room_id")).toString();
        String localDevice = registration(false).getString("device_id");
        if (!localDevice.equals(operation.getString("recipient_device_id")) || !input.getString("peer_user_id").equals(operation.getString("actor_id"))) throw new Failure("invalid_envelope");
        var local = address(room, account, localDevice);
        var remote = address(room, input.getString("peer_user_id"), operation.getString("sender_device_id"));
        byte[] ciphertext = decode(operation.getString("ciphertext"));
        if (ciphertext.length > 65536) throw new Failure("invalid_envelope");
        String id = UUID.fromString(operation.getString("operation_id")).toString();
        String key = "opened/" + room + "/" + id;
        String request = encode(MessageDigest.getInstance("SHA-256").digest(ciphertext));
        byte[] old = store.vault.get(key);
        if (old != null) {
            JSONObject saved = json(old);
            if (!saved.getString("request").equals(request) || !saved.getString("peer").equals(remote.getName())) throw new Failure("operation_conflict");
            JSONObject previous = saved.getJSONObject("operation");
            if (!attachments(operation).equals(attachments(previous))) throw new Failure("invalid_envelope");
            for (String field : new String[] {"actor_id", "sender_device_id", "recipient_device_id", "kind", "target_id", "reply_to", "version", "signal_type"})
                if (!operation.optString(field, "").equals(previous.optString(field, ""))) throw new Failure("invalid_envelope");
            if (!saved.has("content")) throw new Failure("already_processed");
            return saved.getJSONObject("content");
        }
        // Bind initial inbound sessions to the identity already checked by the
        // account-level trust record; a new address alone must not reset trust.
        store.saveIdentity(remote, new IdentityKey(decode(input.getString("peer_identity"))));
        var cipher = new SessionCipher(store, local, remote);
        int type = operation.getInt("signal_type");
        byte[] plain;
        if (type == CiphertextMessage.PREKEY_TYPE) plain = cipher.decrypt(new PreKeySignalMessage(ciphertext));
        else if (type == CiphertextMessage.WHISPER_TYPE) plain = cipher.decrypt(new SignalMessage(ciphertext));
        else throw new Failure("invalid_envelope");
        JSONObject bound = json(plain), inner = bound.getJSONObject("operation");
        if (!attachments(inner).equals(attachments(operation))) throw new Failure("invalid_envelope");
        if (bound.getInt("v") != 1 || !bound.getString("room_id").equals(room) || !bound.getString("actor_id").equals(operation.getString("actor_id"))) throw new Failure("invalid_envelope");
        for (String field : new String[] {"operation_id", "recipient_device_id", "target_id", "kind", "version"}) {
            if (!String.valueOf(inner.get(field)).equals(String.valueOf(operation.get(field)))) throw new Failure("invalid_envelope");
        }
        if (!inner.getString("device_id").equals(operation.getString("sender_device_id")) ||
            !inner.optString("reply_to", "").equals(operation.optString("reply_to", ""))) throw new Failure("invalid_envelope");
        JSONObject content = bound.getJSONObject("payload");
        store.vault.put(key, bytes(new JSONObject().put("request", request).put("peer", remote.getName()).put("operation", operation).put("content", content)));
        return content;
    }

    private SignalProtocolAddress address(String room, String user, String device) throws Exception {
        if (!user.matches("[1-9][0-9]{0,18}")) throw new Failure("invalid_request");
        return new SignalProtocolAddress("cc1:" + UUID.fromString(room) + ":" + user + ":" + UUID.fromString(device), 1);
    }
    private String attachments(JSONObject operation) throws Exception {
        JSONArray values=operation.optJSONArray("attachments");
        if(values==null)return "[]";
        if(values.length()>3)throw new Failure("invalid_envelope");
        JSONArray normalized=new JSONArray();
        for(int i=0;i<values.length();i++)normalized.put(UUID.fromString(values.getString(i)).toString());
        return normalized.toString();
    }
    private static String encode(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.NO_WRAP); }
    private static byte[] bytes(JSONObject value) { return value.toString().getBytes(StandardCharsets.UTF_8); }
    private static JSONObject json(byte[] value) throws Exception { return new JSONObject(new String(value, StandardCharsets.UTF_8)); }
}
