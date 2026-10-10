package com.srisu.srisu.securechat;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Test target only: age a synthetic account's protected public-key timestamp. */
public final class RotationFixture {
    public static void age(Context context,String account) throws Exception {
        if(!account.startsWith("8000000000000000"))throw new IllegalArgumentException("Synthetic account required");
        try(var vault=new ProtectedVault(new File(context.getNoBackupFilesDir(),"couple-chat-v1/"+account),account)) {
            vault.transaction(false,()-> {
                var record=new JSONObject(new String(vault.get("current-signed-public"),StandardCharsets.UTF_8));
                record.put("created_at",System.currentTimeMillis()-31L*86400000L);
                vault.put("current-signed-public",record.toString().getBytes(StandardCharsets.UTF_8));
                return null;
            });
        }
    }
}
