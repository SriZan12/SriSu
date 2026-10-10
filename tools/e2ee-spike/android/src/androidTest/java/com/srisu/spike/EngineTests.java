package com.srisu.spike;

import androidx.test.platform.app.InstrumentationRegistry;
import com.srisu.srisu.securechat.SecureChatEngine;
import org.json.*;
import org.junit.*;
import static org.junit.Assert.*;
import java.util.UUID;

/** Runs the production Android engine with real Keystore, SQLite and libsignal. */
public class EngineTests {
    SecureChatEngine engine;
    final String a = "800000000000000001", b = "800000000000000002";
    @Before public void setup() throws Exception {
        engine = new SecureChatEngine(InstrumentationRegistry.getInstrumentation().getTargetContext());
        call(a, "reset", new JSONObject().put("confirm_history_loss", true));
        call(b, "reset", new JSONObject().put("confirm_history_loss", true));
    }
    @After public void cleanup() throws Exception {
        call(a, "reset", new JSONObject().put("confirm_history_loss", true));
        call(b, "reset", new JSONObject().put("confirm_history_loss", true));
        engine.close(b);
    }
    Object call(String account, String command, JSONObject input) throws Exception {
        JSONObject result = new JSONObject(engine.execute(account, command, input.toString()));
        assertTrue("Native operation failed: " + result.optString("code"), result.getBoolean("ok"));
        return result.get("value");
    }
    void rejected(String account, String command, JSONObject input, String code) throws Exception {
        JSONObject result = new JSONObject(engine.execute(account, command, input.toString()));
        assertFalse(result.getBoolean("ok")); assertEquals(code, result.getString("code"));
    }
    JSONObject object(Object value) { return (JSONObject)value; }
    JSONObject bundle(JSONObject r, String user) throws Exception {
        return new JSONObject().put("id", r.getString("device_id")).put("user_id", Long.parseLong(user))
            .put("registration_id", r.getInt("registration_id")).put("identity_key", r.getString("identity_key"))
            .put("signed_prekey", r.getJSONObject("signed_prekey")).put("ec_prekey", r.getJSONArray("ec_prekeys").getJSONObject(0))
            .put("kyber_prekey", r.getJSONArray("kyber_prekeys").getJSONObject(0));
    }
    JSONObject peer(String room, String user, JSONObject registration) throws Exception {
        return new JSONObject().put("room_id", room).put("peer_user_id", user).put("peer_identity", registration.getString("identity_key"));
    }
    @Test public void durableRoundTripAndAuthenticatedRouting() throws Exception {
        JSONObject ra = object(call(a,"registration",new JSONObject())), rb = object(call(b,"registration",new JSONObject()));
        String room = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
        call(a,"establish",new JSONObject().put("room_id",room).put("claim_id",UUID.randomUUID().toString()).put("bundle",bundle(rb,b)));
        JSONObject operation = new JSONObject().put("device_id",ra.getString("device_id")).put("recipient_device_id",rb.getString("device_id"))
            .put("operation_id",id).put("target_id",id).put("kind","message").put("version",0).put("reply_to",JSONObject.NULL);
        JSONObject content = new JSONObject().put("version",1).put("content",new JSONObject().put("type","text").put("body","Synthetic नमस्ते 👋"));
        JSONObject input = peer(room,b,rb).put("operation",operation).put("content",content);
        JSONObject wire = object(call(a,"seal",input));
        engine.close(a); engine = new SecureChatEngine(InstrumentationRegistry.getInstrumentation().getTargetContext());
        assertEquals(wire.toString(), call(a,"seal",input).toString());
        JSONObject received = new JSONObject(wire.toString()).put("actor_id",a).put("sender_device_id",ra.getString("device_id"));
        JSONObject tampered = new JSONObject(received.toString()).put("kind","delete");
        rejected(b,"open",peer(room,a,ra).put("operation",tampered),"invalid_envelope");
        JSONObject opened = object(call(b,"open",peer(room,a,ra).put("operation",received)));
        assertEquals(content.toString(), opened.toString());
        engine.close(b);
        assertEquals(content.toString(), call(b,"open",peer(room,a,ra).put("operation",received)).toString());
        rejected(b,"open",peer(room,a,ra).put("operation",tampered),"invalid_envelope");
        JSONObject fa = object(call(a,"fingerprint",peer(room,b,rb))), fb = object(call(b,"fingerprint",peer(room,a,ra)));
        assertTrue(object(call(a,"fingerprint",peer(room,b,rb).put("scan",fb.getString("qr")))).getBoolean("matches"));
        assertTrue(object(call(b,"fingerprint",peer(room,a,ra).put("scan",fa.getString("qr")))).getBoolean("matches"));
        assertFalse(object(call(a,"fingerprint",peer(room,b,rb).put("scan","not a valid QR"))).getBoolean("matches"));
        assertEquals(true,call(a,"is_sealed",new JSONObject().put("room_id",room).put("operation_id",id)));
        JSONObject marker=new JSONObject().put("key","app/processed/"+room+"/"+id).put("value","true");
        rejected(b,"write",new JSONObject().put("entries",new JSONArray().put(marker)
            .put(new JSONObject().put("key","invalid-private-namespace").put("value","forbidden"))),"invalid_request");
        assertEquals(content.toString(),call(b,"open",peer(room,a,ra).put("operation",received)).toString());
        call(b,"write",new JSONObject().put("entries",new JSONArray().put(marker)));
        engine.close(b);
        rejected(b,"open",peer(room,a,ra).put("operation",received),"already_processed");
        rejected(b,"open",peer(room,a,ra).put("operation",tampered),"invalid_envelope");
        assertEquals(fa.getString("display"),fb.getString("display"));
        JSONObject changed = object(call("800000000000000003","registration",new JSONObject()));
        rejected(a,"trust",peer(room,b,changed),"identity_changed");
        String delayedId = UUID.randomUUID().toString();
        JSONObject delayedOperation = new JSONObject(operation.toString()).put("operation_id",delayedId).put("target_id",delayedId);
        JSONObject delayed = object(call(a,"seal",peer(room,b,rb).put("operation",delayedOperation).put("content",content)))
            .put("actor_id",a).put("sender_device_id",ra.getString("device_id"));
        call(b,"trust",peer(room,a,changed).put("confirm_change",true));
        assertEquals(content.toString(),call(b,"open",peer(room,a,ra).put("operation",delayed)).toString());
        rejected(b,"trust",peer(room,a,ra),"identity_changed");
        call("800000000000000003","reset",new JSONObject().put("confirm_history_loss",true));
    }
    @Test public void namespaceIsolationAtomicProjectionAndReset() throws Exception {
        call(a,"write",new JSONObject().put("entries",new JSONArray().put(new JSONObject().put("key","app/draft").put("value","synthetic"))));
        assertEquals(JSONObject.NULL,call(b,"read",new JSONObject().put("key","app/draft")));
        rejected(a,"read",new JSONObject().put("key","identity"),"invalid_request");
        rejected(a,"write",new JSONObject().put("entries",new JSONArray()
            .put(new JSONObject().put("key","app/draft").put("value","changed"))
            .put(new JSONObject().put("key","private-key").put("value","forbidden"))),"invalid_request");
        assertEquals("synthetic",call(a,"read",new JSONObject().put("key","app/draft")));
        assertEquals(1,((Number)call(a,"count",new JSONObject().put("prefix","app/"))).intValue());
        assertEquals("synthetic",((JSONArray)call(a,"read_many",new JSONObject().put("keys",new JSONArray().put("app/draft")))).getString(0));
        rejected(a,"reset",new JSONObject(),"invalid_request");
        call(a,"reset",new JSONObject().put("confirm_history_loss",true));
        assertEquals(JSONObject.NULL,call(a,"read",new JSONObject().put("key","app/draft")));
    }

    @Test public void quietRotationDoesNotRefillAFullPrekeyPoolAndRetriesExactly() throws Exception {
        JSONObject initial=object(call(a,"registration",new JSONObject()));
        assertEquals(false,call(a,"publication_needed",new JSONObject()));
        engine.close(a);
        com.srisu.srisu.securechat.RotationFixture.age(InstrumentationRegistry.getInstrumentation().getTargetContext(),a);
        assertEquals(true,call(a,"publication_needed",new JSONObject()));
        JSONObject input=new JSONObject().put("operation_id",UUID.randomUUID().toString()).put("prekey_count",0);
        JSONObject rotated=object(call(a,"publication",input));
        assertEquals(0,rotated.getJSONArray("ec_prekeys").length());assertEquals(0,rotated.getJSONArray("kyber_prekeys").length());
        assertNotEquals(initial.getJSONObject("signed_prekey").getInt("id"),rotated.getJSONObject("signed_prekey").getInt("id"));
        assertEquals(false,call(a,"publication_needed",new JSONObject()));
        engine.close(a);
        assertEquals(rotated.toString(),call(a,"publication",input).toString());
        assertEquals(initial.getString("identity_key"),object(call(a,"registration",new JSONObject())).getString("identity_key"));
    }

    @Test public void attachmentCiphertextIsImmutableAuthenticatedAndAccountScoped() throws Exception {
        byte[] plain=new byte[4096];for(int i=0;i<plain.length;i++)plain[i]=(byte)i;
        JSONObject context=new JSONObject().put("room_id",UUID.randomUUID().toString()).put("message_id",UUID.randomUUID().toString()).put("id",UUID.randomUUID().toString());
        JSONObject sealed=new JSONObject(engine.executeBinary(a,"media_seal",context.toString(),plain));
        assertTrue(sealed.getBoolean("ok"));JSONObject descriptor=sealed.getJSONObject("value");
        byte[] cipher=engine.readAttachment(a,descriptor.toString(),false);
        assertNotNull(cipher);assertEquals(plain.length+28,cipher.length);
        assertArrayEquals(plain,engine.readAttachment(a,descriptor.toString(),true));
        engine.close(a);
        JSONObject retry=new JSONObject(engine.executeBinary(a,"media_seal",context.toString(),plain));
        assertTrue(retry.getBoolean("ok"));assertTrue(descriptor.toString().equals(retry.getJSONObject("value").toString()));
        assertArrayEquals(cipher,engine.readAttachment(a,descriptor.toString(),false));
        assertNull(engine.readAttachment(b,descriptor.toString(),false));
        assertTrue(new JSONObject(engine.executeBinary(b,"media_cache",descriptor.toString(),cipher)).getBoolean("ok"));
        assertArrayEquals(plain,engine.readAttachment(b,descriptor.toString(),true));
        JSONObject wrong=new JSONObject(descriptor.toString()).put("message_id",UUID.randomUUID().toString());
        assertFalse(new JSONObject(engine.executeBinary(b,"media_cache",wrong.toString(),cipher)).getBoolean("ok"));
        byte[] altered=cipher.clone();altered[altered.length-1]^=1;
        assertFalse(new JSONObject(engine.executeBinary(b,"media_cache",descriptor.toString(),altered)).getBoolean("ok"));
        assertArrayEquals(plain,engine.readAttachment(b,descriptor.toString(),true));
        call(b,"media_delete",descriptor);
        assertNull(engine.readAttachment(b,descriptor.toString(),false));
        assertEquals(0,((JSONArray)call(b,"media_available",new JSONObject().put("ids",new JSONArray().put(descriptor.getString("id"))))).length());
        call(a,"media_release",descriptor);
        assertArrayEquals(plain,engine.readAttachment(a,descriptor.toString(),true));
        assertFalse(new JSONObject(engine.executeBinary(a,"media_seal",context.toString(),plain)).getBoolean("ok"));
    }
}
