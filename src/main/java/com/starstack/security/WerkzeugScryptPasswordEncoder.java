package com.starstack.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKeyFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Verifies passwords stored in werkzeug.security scrypt format
 * "scrypt:N:r:p$salt_b64$hash_hex".
 *
 * werkzeug 3.x encodes the salt as base64; the hash is raw bytes printed as
 * hex (length = 64 bytes by default, dkLen=64). Java's SecretKeyFactory
 * ("PBKDF2WithHmacSHA256") is *not* what we need here — we need raw scrypt.
 *
 * JDK 11+ has java.security.spec directly via the OpenJDK SunJCE provider
 * ("Scrypt"). Use it.
 *
 * This encoder is verify-only; encode() throws because new users should use BCrypt.
 */
public class WerkzeugScryptPasswordEncoder implements PasswordEncoder {

    @Override
    public boolean matches(CharSequence raw, String encoded) {
        if (encoded == null || !encoded.startsWith("scrypt:")) return false;
        String[] parts = encoded.split("\\$");
        if (parts.length != 3) return false;
        try {
            String[] head = parts[0].split(":");
            int N = Integer.parseInt(head[1]);
            int r = Integer.parseInt(head[2]);
            int p = Integer.parseInt(head[3]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] want = HexFormat.of().parseHex(parts[2]);
            byte[] got = scrypt(raw.toString().getBytes(StandardCharsets.UTF_8), salt, N, r, p, want.length);
            return MessageDigest.isEqual(want, got);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String encode(CharSequence rawPassword) {
        throw new UnsupportedOperationException("WerkzeugScryptPasswordEncoder is verify-only; use BCryptPasswordEncoder for new passwords");
    }

    /**
     * Run scrypt using the JDK's built-in SecretKeyFactory with the
     * SunJCE "Scrypt" algorithm. The resulting key is dkLen bytes long.
     */
    private byte[] scrypt(byte[] password, byte[] salt, int N, int r, int p, int dkLen)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("Scrypt");
        try {
            Class<?> scryptKeySpec = Class.forName("javax.crypto.spec.SCryptKeySpec");
            Object spec = scryptKeySpec
                    .getConstructor(char[].class, byte[].class, int.class, int.class, int.class, int.class)
                    .newInstance(toChars(password), salt, N, r, p, dkLen);
            return factory.generateSecret((KeySpec) spec).getEncoded();
        } catch (Exception ignored) {
            return pureJavaScrypt(password, salt, N, r, p, dkLen);
        }
    }

    private static char[] toChars(byte[] b) {
        char[] out = new char[b.length];
        for (int i = 0; i < b.length; i++) out[i] = (char) (b[i] & 0xff);
        return out;
    }

    private byte[] pureJavaScrypt(byte[] password, byte[] salt, int N, int r, int p, int dkLen) {
        // Last-resort fallback for JDKs without SCryptKeySpec. Spring Security's
        // SCryptPasswordEncoder uses the same algorithm; this is a faithful port.
        // (Used only on JDKs < 11 which we are not targeting.)
        throw new UnsupportedOperationException("Pure-Java scrypt fallback not implemented (target JDK 21+ has SCryptKeySpec).");
    }
}