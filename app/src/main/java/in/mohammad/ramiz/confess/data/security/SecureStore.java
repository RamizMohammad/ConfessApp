package in.mohammad.ramiz.confess.data.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * SharedPreferences whose values are encrypted with an AES-256-GCM key that
 * lives in the Android Keystore (hardware-backed where the phone has it; the
 * key material never enters the app's memory). A copy of the prefs file is
 * useless on another phone or after the app is reinstalled.
 *
 * Stored value = base64(iv[12] || ciphertext+tag). The pref name is bound as
 * AAD so a value can't be moved under a different name. If the key is gone
 * (reinstall, keystore reset) values read as null and the caller signs in again.
 */
public final class SecureStore {

    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "confess_session_aes_v1";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SharedPreferences prefs;

    public SecureStore(@NonNull Context context, @NonNull String fileName) {
        prefs = context.getApplicationContext().getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }

    @Nullable
    public synchronized String get(@NonNull String name) {
        String stored = prefs.getString(name, null);
        if (stored == null) return null;
        try {
            byte[] blob = Base64.decode(stored, Base64.NO_WRAP);
            if (blob.length <= IV_BYTES) return null;
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES));
            c.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            byte[] plain = c.doFinal(blob, IV_BYTES, blob.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Tampered / key lost: treat as absent (and drop it so we don't retry forever).
            prefs.edit().remove(name).apply();
            return null;
        }
    }

    /** null removes the value. Returns false if encryption failed (nothing is stored then). */
    public synchronized boolean put(@NonNull String name, @Nullable String value) {
        if (value == null) {
            prefs.edit().remove(name).apply();
            return true;
        }
        try {
            Cipher c = Cipher.getInstance(TRANSFORM);
            c.init(Cipher.ENCRYPT_MODE, key());                 // Keystore picks a fresh random IV
            c.updateAAD(name.getBytes(StandardCharsets.UTF_8));
            byte[] ct = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = c.getIV();
            byte[] blob = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(ct, 0, blob, iv.length, ct.length);
            prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply();
            return true;
        } catch (Exception e) {
            prefs.edit().remove(name).apply();
            return false;
        }
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build());
        return gen.generateKey();
    }
}
