package com.srisu.spike;

import static org.junit.Assert.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.junit.Test;

public class PersistenceTest {
    @Test public void atomicSendReceiveRestartAndProtectedFiles() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String scope = "spike-" + UUID.randomUUID();
        File root = new File(context.getNoBackupFilesDir(), scope);
        File aDir = new File(root, "a");
        File bDir = new File(root, "b");
        byte[] body = ("Protected synthetic नेपाली 🌱 " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        JSONObject sent;
        byte[] identity;
        try {
            try (var first = new DurablePeer(aDir, scope + "a", "synthetic-a:durable-room");
                 var second = new DurablePeer(bDir, scope + "b", "synthetic-b:durable-room")) {
                first.establish(second.publish(), second.address);
                byte[] before = first.store.loadSession(second.address).serialize();
                assertThrows(Exception.class, () -> first.send("one", body, second.address, true));
                assertNull(first.store.vault.get("outbox/one"));
                assertArrayEquals(before, first.store.loadSession(second.address).serialize());
                sent = first.send("one", body, second.address, false);
                identity = first.store.getIdentityKeyPair().serialize();
                assertThrows(Exception.class, () -> second.receive("one", sent, first.address, true));
                assertNull(second.store.vault.get("inbox/one"));
                assertTrue(second.store.containsPreKey(1));
            }
            try (var first = new DurablePeer(aDir, scope + "a", "synthetic-a:durable-room");
                 var second = new DurablePeer(bDir, scope + "b", "synthetic-b:durable-room")) {
                assertArrayEquals(DurablePeer.bytes(sent, "ciphertext"), DurablePeer.bytes(first.send("one", body, second.address, false), "ciphertext"));
                assertThrows(Exception.class, () -> first.send("one", new byte[] {1}, second.address, false));
                assertArrayEquals(body, second.receive("one", sent, first.address, false));
                assertArrayEquals(body, second.receive("one", sent, first.address, false));
                assertThrows(Exception.class, () -> second.receive("replay", sent, first.address, false));
                JSONObject reply = second.send("reply", body, first.address, false);
                assertArrayEquals(body, first.receive("reply", reply, second.address, false));
                try (var files = Files.walk(root.toPath())) {
                    for (Path file : files.filter(Files::isRegularFile).collect(Collectors.toList())) {
                        String disk = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                        assertFalse("Message appeared in database or side file", disk.contains(new String(body, StandardCharsets.ISO_8859_1)));
                        assertFalse("Identity appeared in database or side file", disk.contains(new String(identity, StandardCharsets.ISO_8859_1)));
                    }
                }
            }
            ProtectedVault.deleteEvaluationKey(scope + "b");
            assertThrows(Exception.class, () -> new DurablePeer(bDir, scope + "b", "synthetic-b:durable-room"));
        } finally {
            ProtectedVault.deleteEvaluationKey(scope + "a");
            ProtectedVault.deleteEvaluationKey(scope + "b");
            if (root.exists()) try (var paths = Files.walk(root.toPath())) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) Files.delete(path);
            }
        }
    }
}
