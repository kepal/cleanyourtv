package com.simonegiansante.screentime;

import android.util.Base64;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** The PIN is never kept as plain text: a random salt plus a PBKDF2 hash of it, both stored in Prefs. */
final class PinUtil {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;

    private PinUtil() {
    }

    static String randomSaltBase64() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    static String hash(String pin, String saltBase64) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = f.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean matches(String pin, String saltBase64, String expectedHash) {
        if (saltBase64 == null || expectedHash == null) return false;
        return hash(pin, saltBase64).equals(expectedHash);
    }
}
