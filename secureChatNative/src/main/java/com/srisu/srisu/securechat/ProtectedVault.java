package com.srisu.srisu.securechat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Protected chat records. AES-GCM keys stay in Android Keystore; callers use noBackupFilesDir. */
final class ProtectedVault implements AutoCloseable {
    static final class MissingKey extends Exception {}
    private final SQLiteDatabase db;
    private final SecretKey key;
    private final String scope;
    final File directory;

    ProtectedVault(File directory, String scope) throws Exception {
        this.directory = directory;
        this.scope = scope;
        File file = new File(directory, "records.sqlite");
        var keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        String alias = "com.srisu.couplechat.v1." + scope;
        if (!keystore.containsAlias(alias)) {
            if (file.exists()) throw new MissingKey();
            var generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
            generator.generateKey();
        }
        key = (SecretKey) keystore.getKey(alias, null);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create chat directory");
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        db.enableWriteAheadLogging();
        db.execSQL("PRAGMA synchronous=FULL");
        db.execSQL("CREATE TABLE IF NOT EXISTS records (key TEXT PRIMARY KEY NOT NULL, value BLOB NOT NULL)");
    }
    static void deleteStorageKey(String scope) throws Exception {
        var keystore = KeyStore.getInstance("AndroidKeyStore");
        keystore.load(null);
        keystore.deleteEntry("com.srisu.couplechat.v1." + scope);
    }
    synchronized <T> T transaction(boolean failBeforeCommit, Callable<T> work) throws Exception {
        if (db.inTransaction()) throw new IllegalStateException("Nested chat transaction");
        db.beginTransaction();
        try {
            T result = work.call();
            if (failBeforeCommit) throw new IllegalStateException("Injected pre-commit failure");
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
    synchronized byte[] get(String name) {
        try (Cursor cursor = db.rawQuery("SELECT value FROM records WHERE key=?", new String[] {name})) {
            if (!cursor.moveToFirst()) return null;
            byte[] sealed = cursor.getBlob(0);
            if (sealed.length < 28) throw new IllegalStateException("Invalid protected record");
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, sealed, 0, 12));
            cipher.updateAAD(context(name));
            return cipher.doFinal(sealed, 12, sealed.length - 12);
        } catch (Exception error) { throw new IllegalStateException("Unable to read protected record", error); }
    }
    synchronized void put(String name, byte[] value) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            cipher.updateAAD(context(name));
            byte[] ciphertext = cipher.doFinal(value);
            byte[] nonce = cipher.getIV();
            if (nonce.length != 12) throw new IllegalStateException("Unexpected GCM nonce size");
            byte[] sealed = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, sealed, nonce.length, ciphertext.length);
            var row = new ContentValues();
            row.put("key", name);
            row.put("value", sealed);
            if (db.insertWithOnConflict("records", null, row, SQLiteDatabase.CONFLICT_REPLACE) == -1) throw new IllegalStateException("Protected write failed");
        } catch (Exception error) { throw new IllegalStateException("Unable to write protected record", error); }
    }
    synchronized void remove(String name) { db.delete("records", "key=?", new String[] {name}); }
    synchronized List<String> keys(String prefix) {
        var result = new ArrayList<String>();
        try (Cursor cursor = db.rawQuery("SELECT key FROM records WHERE key>=? AND key<? ORDER BY key", new String[] {prefix, prefix + "\uffff"})) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }
    synchronized List<String> page(String prefix, String before, int limit) {
        var result = new ArrayList<String>();
        String ceiling = prefix + "\uffff";
        try (Cursor cursor = db.rawQuery("SELECT key FROM records WHERE key>=? AND key<? AND key<? ORDER BY key DESC LIMIT ?",
                new String[] {prefix, ceiling, before.isEmpty() ? ceiling : before, Integer.toString(limit)})) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }
    synchronized int count(String prefix) {
        try (Cursor cursor = db.rawQuery("SELECT count(*) FROM records WHERE key>=? AND key<?", new String[] {prefix, prefix + "\uffff"})) {
            cursor.moveToFirst(); return cursor.getInt(0);
        }
    }
    private byte[] context(String name) { return (scope + "\0" + name).getBytes(StandardCharsets.UTF_8); }
    @Override public synchronized void close() { db.close(); }
}
