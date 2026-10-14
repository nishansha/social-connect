package com.triasoft.socialconnect.controller;

import com.triasoft.socialconnect.dto.TokenRq;
import com.triasoft.socialconnect.dto.TokenRs;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.exception.ApiException;
import com.triasoft.socialconnect.repository.SocialClientRepository;
import com.triasoft.socialconnect.security.ClientTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock
    private SocialClientRepository clientRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ClientTokenService clientTokenService;

    private OAuthController controller;
    private SocialClient client;

    @BeforeEach
    void setUp() {
        controller = new OAuthController(clientRepository, passwordEncoder, clientTokenService);
        client = new SocialClient();
        client.setId(1L);
        client.setClientId("garage-api");
        client.setClientSecretHash("hashed-secret");
        client.setActive(true);
    }

    private TokenRq request(String clientId, String secret) {
        TokenRq rq = new TokenRq();
        rq.setClientId(clientId);
        rq.setClientSecret(secret);
        rq.setGrantType("client_credentials");
        return rq;
    }

    @Test
    void issuesTokenForValidCredentials() {
        when(clientRepository.findByClientIdAndActiveTrue("garage-api")).thenReturn(Optional.of(client));
        when(passwordEncoder.matches("correct-secret", "hashed-secret")).thenReturn(true);
        when(clientTokenService.issueToken(client)).thenReturn("signed.jwt.token");
        when(clientTokenService.expirationSeconds()).thenReturn(900L);

        TokenRs response = controller.token(request("garage-api", "correct-secret")).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
    }

    @Test
    void rejectsWrongSecret() {
        when(clientRepository.findByClientIdAndActiveTrue("garage-api")).thenReturn(Optional.of(client));
        when(passwordEncoder.matches("wrong-secret", "hashed-secret")).thenReturn(false);

        assertThatThrownBy(() -> controller.token(request("garage-api", "wrong-secret")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsUnknownClientId() {
        when(clientRepository.findByClientIdAndActiveTrue("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.token(request("unknown", "whatever")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsUnsupportedGrantType() {
        TokenRq rq = request("garage-api", "correct-secret");
        rq.setGrantType("password");

        assertThatThrownBy(() -> controller.token(rq))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
