package com.triasoft.socialconnect.security;

import com.triasoft.socialconnect.config.EncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM, for Page access tokens at rest — not RSA/PKCS1 (that caps plaintext at roughly
 * keysize/8 - 11 bytes, too small for a real Page token) and not a password-style one-way hash
 * (the plaintext token must be recoverable to call Meta's API with it).
 */
@Component
public class TokenCipher {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;

    public TokenCipher(EncryptionProperties properties) {
        byte[] keyBytes = Base64.getDecoder().decode(properties.key());
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] ivAndCiphertext = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, iv.length);
            byte[] ciphertext = new byte[ivAndCiphertext.length - iv.length];
            System.arraycopy(ivAndCiphertext, iv.length, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token", e);
        }
    }
}
