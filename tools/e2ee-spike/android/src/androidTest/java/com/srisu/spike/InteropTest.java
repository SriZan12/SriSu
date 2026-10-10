package com.srisu.spike;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.signal.libsignal.protocol.SignalProtocolAddress;

public class InteropTest {
    private File exchange() {
        return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "exchange");
    }
    private DurablePeer peer(String room) throws Exception {
        File config = new File(exchange(), "run-id");
        Assume.assumeTrue("Run the cross-platform phase runner", config.isFile());
        String run = new String(Files.readAllBytes(config.toPath()), StandardCharsets.UTF_8).trim();
        UUID.fromString(run);
        File root = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getNoBackupFilesDir(), "interop-" + run);
        return new DurablePeer(new File(root, room), run + ":android:" + room, "synthetic-android:" + room);
    }
    private SignalProtocolAddress remote(String room) { return new SignalProtocolAddress("synthetic-ios:" + room, 1); }
    private JSONObject read(String name) throws Exception { return DurablePeer.decode(Files.readAllBytes(new File(exchange(), name).toPath())); }
    private void write(String name, JSONObject value) throws Exception { Files.write(new File(exchange(), name).toPath(), DurablePeer.encode(value)); }
    private byte[] body(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    @Test public void phase01Publish() throws Exception {
        try (var local = peer("ios-first")) { write("android-bundle.json", local.publish()); }
        try (var local = peer("android-first")) { assertNotNull(local.store.getIdentityKeyPair()); }
    }
    @Test public void phase02Initiate() throws Exception {
        try (var local = peer("android-first")) {
            var recipient = remote("android-first");
            local.establish(read("ios-bundle.json"), recipient);
            write("android-initial.json", local.send("initial", body("android to ios नेपाली 🌱"), recipient, false));
        }
    }
    @Test public void phase03ReceiveAndReply() throws Exception {
        try (var local = peer("ios-first")) {
            var sender = remote("ios-first");
            assertArrayEquals(body("ios to android नमस्ते 🌿"), local.receive("initial", read("ios-initial.json"), sender, false));
            write("android-reply.json", local.send("reply", body("android reply नेपाली"), sender, false));
        }
    }
    @Test public void phase04ResumeAndSendDelayed() throws Exception {
        try (var local = peer("android-first")) {
            var sender = remote("android-first");
            assertArrayEquals(body("ios reply नमस्ते"), local.receive("reply", read("ios-reply.json"), sender, false));
            var delayed = local.send("delayed", body("android delayed"), sender, false);
            var latest = local.send("latest", body("android latest"), sender, false);
            assertArrayEquals(DurablePeer.bytes(latest, "ciphertext"), DurablePeer.bytes(local.send("latest", body("android latest"), sender, false), "ciphertext"));
            write("android-later.json", new JSONObject().put("delayed", delayed).put("latest", latest));
        }
    }
    @Test public void phase05OutOfOrderTamperAndReplay() throws Exception {
        try (var local = peer("ios-first")) {
            var sender = remote("ios-first");
            var messages = read("ios-later.json");
            var latest = messages.getJSONObject("latest");
            byte[] corrupt = DurablePeer.bytes(latest, "ciphertext");
            corrupt[corrupt.length - 1] ^= 1;
            var tampered = new JSONObject().put("type", latest.getInt("type")).put("ciphertext", DurablePeer.b64(corrupt));
            assertThrows(Exception.class, () -> local.receive("latest", tampered, sender, false));
            assertArrayEquals(body("ios latest"), local.receive("latest", latest, sender, false));
            assertArrayEquals(body("ios delayed"), local.receive("delayed", messages.getJSONObject("delayed"), sender, false));
            assertArrayEquals(body("ios latest"), local.receive("latest", latest, sender, false));
            assertThrows(Exception.class, () -> local.receive("replay", latest, sender, false));
        }
    }
}
