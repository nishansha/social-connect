package com.triasoft.socialconnect.security;

import com.triasoft.socialconnect.config.EncryptionProperties;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCipherTest {

    private static String randomBase64Key32Bytes() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void encryptThenDecryptRoundTrips() {
        TokenCipher cipher = new TokenCipher(new EncryptionProperties(randomBase64Key32Bytes()));

        String plaintext = "EAAG1234longMetaPageAccessTokenValueThatExceedsRsaCapacity...";
        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void sameInputEncryptsDifferentlyEachTime() {
        TokenCipher cipher = new TokenCipher(new EncryptionProperties(randomBase64Key32Bytes()));
        String plaintext = "same-token-value";

        assertThat(cipher.encrypt(plaintext)).isNotEqualTo(cipher.encrypt(plaintext));
    }
}
