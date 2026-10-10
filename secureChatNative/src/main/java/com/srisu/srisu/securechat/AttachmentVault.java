package com.srisu.srisu.securechat;

import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

/** One bounded AES-256-GCM file with a fresh key; no custom chunks or ratchet.
 * Wire layout is CryptoKit combined: 12-byte nonce, ciphertext, 16-byte tag.
 * Only ciphertext is written to this evictable, account-scoped cache. */
final class AttachmentVault {
    static final int MAX_PLAIN = 16 * 1024 * 1024;
    private final ProtectedVault vault;
    private final File directory;
    AttachmentVault(ProtectedVault vault) throws Exception {
        this.vault=vault;
        directory=new File(vault.directory,"media");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("media_storage_unavailable");
        File[] leftovers=directory.listFiles();
        if(leftovers==null)throw new IOException("media_storage_unavailable");
        for(File path:leftovers) {
            if(path.getName().startsWith("encrypted-") && path.getName().endsWith(".part")) {
                if(!path.delete())throw new IOException("media_storage_unavailable");
            } else if(path.getName().endsWith(".pin")) {
                String id=path.getName().substring(0,path.getName().length()-4);
                if(vault.get("media/sealed/"+id)==null) {
                    File orphan=new File(directory,id+".bin");
                    if(orphan.exists() && !orphan.delete())throw new IOException("media_storage_unavailable");
                    if(!path.delete())throw new IOException("media_storage_unavailable");
                }
            }
        }
    }
    private static String id(JSONObject value,String key) throws Exception { return UUID.fromString(value.getString(key)).toString(); }
    private static byte[] context(JSONObject value) throws Exception {
        return ("SriSu/attachment/v1|"+id(value,"room_id")+"|"+id(value,"message_id")+"|"+id(value,"id")).getBytes(StandardCharsets.UTF_8);
    }
    private static String digest(byte[] value) throws Exception {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder text=new StringBuilder(64); for(byte b:hash) text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }
    private File file(JSONObject value) throws Exception { return new File(directory,id(value,"id")+".bin"); }
    private File pin(JSONObject value) throws Exception { return new File(directory,id(value,"id")+".pin"); }
    JSONObject seal(JSONObject input,byte[] plaintext) throws Exception {
        if (plaintext==null || plaintext.length<1 || plaintext.length>MAX_PLAIN) throw new IOException("media_size_limit");
        byte[] associated=context(input);
        String key="media/sealed/"+id(input,"id"), request=digest(plaintext);
        byte[] previous=vault.get(key);
        if(previous!=null) {
            JSONObject record=new JSONObject(new String(previous,StandardCharsets.UTF_8));
            if(record.optBoolean("released",false))throw new IOException("media_operation_finished");
            JSONObject descriptor=record.getJSONObject("descriptor");
            if(!request.equals(record.getString("request")) || !Arrays.equals(associated,context(descriptor))) throw new IOException("media_operation_conflict");
            read(descriptor,false); // A lost local file is explicit, never re-encrypted under the same upload ID.
            return descriptor;
        }
        KeyGenerator generator=KeyGenerator.getInstance("AES");generator.init(256);
        SecretKey secret=generator.generateKey();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secret);cipher.updateAAD(associated);
        byte[] encrypted=cipher.doFinal(plaintext), nonce=cipher.getIV();
        if(nonce.length!=12) throw new IOException("media_cipher_unavailable");
        byte[] combined=new byte[encrypted.length+12];System.arraycopy(nonce,0,combined,0,12);System.arraycopy(encrypted,0,combined,12,encrypted.length);
        JSONObject descriptor=new JSONObject().put("id",id(input,"id")).put("room_id",id(input,"room_id")).put("message_id",id(input,"message_id"))
            .put("algorithm","aes256gcm-v1").put("key",Base64.encodeToString(secret.getEncoded(),Base64.NO_WRAP))
            .put("ciphertext_size",combined.length).put("sha256",digest(combined));
        save(descriptor,combined,true);
        vault.put(key,new JSONObject().put("request",request).put("descriptor",descriptor).toString().getBytes(StandardCharsets.UTF_8));
        return descriptor;
    }
    void receive(JSONObject descriptor,byte[] ciphertext) throws Exception {
        authenticate(descriptor,ciphertext); // Integrity before cache publication or decoder access.
        save(descriptor,ciphertext,false);
    }
    byte[] read(JSONObject descriptor,boolean decrypt) throws Exception {
        File path=file(descriptor);
        if(!path.exists() || path.length()<29 || path.length()>MAX_PLAIN+28) throw new IOException("media_unavailable");
        byte[] value;
        try(FileInputStream stream=new FileInputStream(path); ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[65536];int count,total=0;
            while((count=stream.read(buffer))!=-1) { total+=count; if(total>MAX_PLAIN+28) throw new IOException("media_size_limit");bytes.write(buffer,0,count); }
            value=bytes.toByteArray();
        }
        validate(descriptor,value);
        path.setLastModified(System.currentTimeMillis());
        return decrypt?authenticate(descriptor,value):value;
    }
    private void validate(JSONObject descriptor,byte[] ciphertext) throws Exception {
        if(ciphertext==null || ciphertext.length<29 || ciphertext.length>MAX_PLAIN+28 ||
            descriptor.getInt("ciphertext_size")!=ciphertext.length || !descriptor.getString("sha256").equals(digest(ciphertext)) ||
            !descriptor.getString("algorithm").equals("aes256gcm-v1")) throw new IOException("media_integrity_failed");
    }
    private byte[] authenticate(JSONObject descriptor,byte[] ciphertext) throws Exception {
        validate(descriptor,ciphertext);
        byte[] key=Base64.decode(descriptor.getString("key"),Base64.NO_WRAP);
        if(key.length!=32) throw new IOException("media_integrity_failed");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(ciphertext,0,12)));
        cipher.updateAAD(context(descriptor));
        return cipher.doFinal(ciphertext,12,ciphertext.length-12);
    }
    private void save(JSONObject descriptor,byte[] bytes,boolean pinned) throws Exception {
        File destination=file(descriptor);
        if(destination.exists()) {
            if(!Arrays.equals(read(descriptor,false),bytes)) throw new IOException("media_operation_conflict");
        } else {
            makeSpace(bytes.length);
            File temporary=File.createTempFile("encrypted-",".part",directory);
            try {
                try(FileOutputStream stream=new FileOutputStream(temporary)) {stream.write(bytes);stream.getFD().sync();}
                if(!temporary.renameTo(destination)) throw new IOException("media_storage_unavailable");
            } finally { if(temporary.exists()) temporary.delete(); }
        }
        if(pinned && !pin(descriptor).exists() && !pin(descriptor).createNewFile()) throw new IOException("media_storage_unavailable");
    }
    private void makeSpace(int requested) throws Exception {
        File[] files=directory.listFiles((dir,name)->name.endsWith(".bin"));
        if(files==null) throw new IOException("media_storage_unavailable");
        long size=0;int count=files.length;for(File f:files)size+=f.length();
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));
        for(File f:files) {
            if(size+requested<=256L*1024*1024 && count<200) break;
            File pinned=new File(directory,f.getName().replace(".bin",".pin"));
            if(!pinned.exists()) {long old=f.length();if(f.delete()){size-=old;count--;}}
        }
        if(size+requested>256L*1024*1024 || count>=200) throw new IOException("media_storage_full");
    }
    void release(JSONObject input,boolean delete) throws Exception {
        if(pin(input).exists() && !pin(input).delete()) throw new IOException("media_storage_unavailable");
        if(delete && file(input).exists() && !file(input).delete()) throw new IOException("media_storage_unavailable");
        String key="media/sealed/"+id(input,"id");
        byte[] existing=vault.get(key);
        if(existing!=null) {
            JSONObject record=new JSONObject(new String(existing,StandardCharsets.UTF_8));
            record.remove("descriptor");record.put("released",true);
            vault.put(key,record.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
    JSONArray available(JSONArray ids) throws Exception {
        if(ids.length()>200)throw new IOException("media_size_limit");
        JSONArray result=new JSONArray();
        for(int i=0;i<ids.length();i++) {
            String id=UUID.fromString(ids.getString(i)).toString();File path=new File(directory,id+".bin");
            if(path.isFile() && path.length()>=29 && path.length()<=MAX_PLAIN+28)result.put(id);
        }
        return result;
    }
    static void erase(ProtectedVault vault) throws Exception {
        File directory=new File(vault.directory,"media");
        File[] files=directory.listFiles(); if(files==null)return;
        for(File file:files)if(file.isFile()&&!file.delete())throw new IOException("media_storage_unavailable");
    }
}
