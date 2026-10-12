package com.triasoft.socialpublisher.security;

import com.triasoft.socialpublisher.config.JwtProperties;
import com.triasoft.socialpublisher.entity.SocialClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClientTokenServiceTest {

    private final JwtProperties properties = new JwtProperties("test-secret-key-at-least-32-bytes-long!!", 60_000);
    private final ClientTokenService service = new ClientTokenService(properties);

    @Test
    void issuedTokenRoundTripsToTheSameClientId() {
        SocialClient client = new SocialClient();
        client.setId(42L);
        client.setClientId("garage-api");

        String token = service.issueToken(client);

        Optional<Long> extracted = service.extractClientDbId(token);
        assertThat(extracted).contains(42L);
    }

    @Test
    void garbageTokenExtractsNothing() {
        assertThat(service.extractClientDbId("not-a-real-jwt")).isEmpty();
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        ClientTokenService otherService = new ClientTokenService(new JwtProperties("completely-different-secret-value-32b", 60_000));
        SocialClient client = new SocialClient();
        client.setId(1L);
        client.setClientId("x");
        String token = otherService.issueToken(client);

        assertThat(service.extractClientDbId(token)).isEmpty();
    }
}
