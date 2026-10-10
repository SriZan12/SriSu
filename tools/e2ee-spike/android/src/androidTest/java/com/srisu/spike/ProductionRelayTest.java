package com.srisu.spike;

import androidx.test.platform.app.InstrumentationRegistry;
import com.srisu.srisu.securechat.SecureChatEngine;
import org.json.*;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import android.util.Base64;
import static org.junit.Assert.*;

/** Evaluation APK only: executes production-engine batches supplied by the native
 * transport runner. Never include this relay or synthetic credentials in SriSu. */
public class ProductionRelayTest {
    @Test public void exchange() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = new File(context.getFilesDir(), "engine-input.json");
        org.junit.Assume.assumeTrue("Use the native transport runner",input.exists());
        JSONArray commands = new JSONArray(new String(Files.readAllBytes(input.toPath()),StandardCharsets.UTF_8)), results = new JSONArray();
        SecureChatEngine engine = new SecureChatEngine(context);
        try {
            for (int i=0;i<commands.length();i++) {
                JSONObject command = commands.getJSONObject(i);
                String user = command.getString("account");
                JSONObject result;
                if(command.getString("command").equals("read_media")) {
                    JSONObject value=command.getJSONObject("input");
                    byte[] bytes=engine.readAttachment(user,value.getJSONObject("descriptor").toString(),value.getBoolean("decrypt"));
                    result=new JSONObject().put("ok",bytes!=null).put("value",bytes==null?JSONObject.NULL:Base64.encodeToString(bytes,Base64.NO_WRAP));
                } else if(command.has("binary")) {
                    result=new JSONObject(engine.executeBinary(user,command.getString("command"),command.getJSONObject("input").toString(),Base64.decode(command.getString("binary"),Base64.NO_WRAP)));
                } else result = new JSONObject(engine.execute(user,command.getString("command"),command.getJSONObject("input").toString()));
                results.put(result);
                engine.close(user);
            }
        } finally { input.delete(); }
        Files.write(new File(context.getFilesDir(),"engine-output.json").toPath(), results.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(commands.length(),results.length());
    }
}
