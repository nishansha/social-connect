package com.triasoft.socialpublisher.service;

import com.triasoft.socialpublisher.client.MetaOAuthClient;
import com.triasoft.socialpublisher.config.EncryptionProperties;
import com.triasoft.socialpublisher.dto.OAuthPendingSelectionRs;
import com.triasoft.socialpublisher.dto.OAuthStartRq;
import com.triasoft.socialpublisher.dto.OAuthStartRs;
import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.entity.SocialOauthConnection;
import com.triasoft.socialpublisher.enums.SocialOauthStatus;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.repository.SocialAccountConfigRepository;
import com.triasoft.socialpublisher.repository.SocialOauthConnectionRepository;
import com.triasoft.socialpublisher.security.TokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaOAuthServiceTest {

    @Mock
    private SocialOauthConnectionRepository connectionRepository;
    @Mock
    private SocialAccountConfigRepository accountConfigRepository;
    @Mock
    private MetaOAuthClient metaOAuthClient;

    private TokenCipher tokenCipher;
    private MetaOAuthService service;
    private SocialClient client;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        tokenCipher = new TokenCipher(new EncryptionProperties(Base64.getEncoder().encodeToString(key)));
        service = new MetaOAuthService(connectionRepository, accountConfigRepository, metaOAuthClient, tokenCipher);

        client = new SocialClient();
        client.setId(1L);

        org.mockito.Mockito.lenient().when(connectionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(accountConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void start_createsPendingConnectionAndReturnsAuthorizationUrl() {
        when(metaOAuthClient.authorizationUrl(anyString())).thenReturn("https://www.facebook.com/v21.0/dialog/oauth?state=abc");

        OAuthStartRq request = new OAuthStartRq();
        request.setAccountRef("dealer-1");
        request.setPlatform(SocialPlatform.INSTAGRAM);
        request.setReturnUrl("https://garage.example.com/settings/social-accounts");

        OAuthStartRs result = service.start(client, request);

        assertThat(result.getAuthorizationUrl()).contains("dialog/oauth");
        ArgumentCaptor<SocialOauthConnection> captor = ArgumentCaptor.forClass(SocialOauthConnection.class);
        org.mockito.Mockito.verify(connectionRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountRef()).isEqualTo("dealer-1");
        assertThat(captor.getValue().getStatus()).isEqualTo(SocialOauthStatus.STARTED);
        assertThat(captor.getValue().getStateToken()).isNotBlank();
    }

    private SocialOauthConnection pendingConnection() {
        SocialOauthConnection connection = new SocialOauthConnection();
        connection.setId(1L);
        connection.setStateToken("state-1");
        connection.setSocialClientId(1L);
        connection.setAccountRef("dealer-1");
        connection.setPlatform(SocialPlatform.INSTAGRAM);
        connection.setReturnUrl("https://garage.example.com/settings/social-accounts");
        connection.setStatus(SocialOauthStatus.STARTED);
        connection.setExpiresAt(Instant.now().plusSeconds(600));
        return connection;
    }

    @Test
    void handleCallback_singlePage_finishesImmediatelyAndRedirectsConnected() {
        SocialOauthConnection connection = pendingConnection();
        when(connectionRepository.findByStateToken("state-1")).thenReturn(Optional.of(connection));
        when(metaOAuthClient.exchangeCodeForUserToken("auth-code"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("short-lived", 3600));
        when(metaOAuthClient.exchangeForLongLivedToken("short-lived"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("long-lived", 5_184_000));
        when(metaOAuthClient.listPages("long-lived"))
                .thenReturn(List.of(new MetaOAuthClient.PageOption("page-1", "Autobooks", "page-token-1", "ig-biz-1")));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.empty());

        String redirectTo = service.handleCallback("auth-code", "state-1");

        assertThat(redirectTo).contains("connected=1");
        assertThat(connection.getStatus()).isEqualTo(SocialOauthStatus.COMPLETED);

        ArgumentCaptor<SocialAccountConfig> captor = ArgumentCaptor.forClass(SocialAccountConfig.class);
        org.mockito.Mockito.verify(accountConfigRepository).save(captor.capture());
        SocialAccountConfig saved = captor.getValue();
        assertThat(saved.getAccountRef()).isEqualTo("dealer-1");
        assertThat(saved.getFacebookPageId()).isEqualTo("page-1");
        assertThat(saved.getIgBusinessAccountId()).isEqualTo("ig-biz-1");
        assertThat(tokenCipher.decrypt(saved.getPageAccessTokenEncrypted())).isEqualTo("page-token-1");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void handleCallback_multiplePages_marksSelectionRequiredAndRedirectsWithState() {
        SocialOauthConnection connection = pendingConnection();
        when(connectionRepository.findByStateToken("state-1")).thenReturn(Optional.of(connection));
        when(metaOAuthClient.exchangeCodeForUserToken("auth-code"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("short-lived", 3600));
        when(metaOAuthClient.exchangeForLongLivedToken("short-lived"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("long-lived", 5_184_000));
        when(metaOAuthClient.listPages("long-lived")).thenReturn(List.of(
                new MetaOAuthClient.PageOption("page-1", "Autobooks North", "token-1", "ig-1"),
                new MetaOAuthClient.PageOption("page-2", "Autobooks South", "token-2", null)));

        String redirectTo = service.handleCallback("auth-code", "state-1");

        assertThat(redirectTo).contains("needsSelection=1").contains("connectState=state-1");
        assertThat(connection.getStatus()).isEqualTo(SocialOauthStatus.PAGE_SELECTION_REQUIRED);
        assertThat(connection.getLongLivedUserTokenEncrypted()).isNotNull();
        assertThat(connection.getLongLivedUserTokenExpiresAt())
                .isCloseTo(Instant.now().plusSeconds(5_184_000), org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
        org.mockito.Mockito.verify(accountConfigRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void handleCallback_noPages_failsAndRedirects() {
        SocialOauthConnection connection = pendingConnection();
        when(connectionRepository.findByStateToken("state-1")).thenReturn(Optional.of(connection));
        when(metaOAuthClient.exchangeCodeForUserToken("auth-code"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("short-lived", 3600));
        when(metaOAuthClient.exchangeForLongLivedToken("short-lived"))
                .thenReturn(new MetaOAuthClient.TokenExchangeResult("long-lived", 5_184_000));
        when(metaOAuthClient.listPages("long-lived")).thenReturn(List.of());

        String redirectTo = service.handleCallback("auth-code", "state-1");

        assertThat(redirectTo).contains("connected=0").contains("error=no_pages");
        assertThat(connection.getStatus()).isEqualTo(SocialOauthStatus.FAILED);
    }

    @Test
    void handleCallback_unknownState_throws() {
        when(connectionRepository.findByStateToken("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handleCallback("auth-code", "ghost"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void select_choosesNamedPageAndCreatesAccountConfig() {
        SocialOauthConnection connection = pendingConnection();
        connection.setStatus(SocialOauthStatus.PAGE_SELECTION_REQUIRED);
        connection.setLongLivedUserTokenEncrypted(tokenCipher.encrypt("long-lived"));
        Instant userTokenExpiry = Instant.now().plusSeconds(5_000_000);
        connection.setLongLivedUserTokenExpiresAt(userTokenExpiry);
        when(connectionRepository.findByStateToken("state-1")).thenReturn(Optional.of(connection));
        when(metaOAuthClient.listPages("long-lived")).thenReturn(List.of(
                new MetaOAuthClient.PageOption("page-1", "Autobooks North", "token-1", "ig-1"),
                new MetaOAuthClient.PageOption("page-2", "Autobooks South", "token-2", "ig-2")));
        when(accountConfigRepository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.empty());

        service.select(client, "state-1", "page-2");

        ArgumentCaptor<SocialAccountConfig> captor = ArgumentCaptor.forClass(SocialAccountConfig.class);
        org.mockito.Mockito.verify(accountConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getFacebookPageId()).isEqualTo("page-2");
        assertThat(tokenCipher.decrypt(captor.getValue().getPageAccessTokenEncrypted())).isEqualTo("token-2");
        // Must inherit the long-lived USER token's own ~58-day remaining expiry, not this pending
        // connection's few-minute expiresAt (the bug this test guards against).
        assertThat(captor.getValue().getTokenExpiresAt())
                .isCloseTo(userTokenExpiry, org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(connection.getStatus()).isEqualTo(SocialOauthStatus.COMPLETED);
    }

    @Test
    void listPendingPages_decryptsStoredUserTokenAndReturnsOptions() {
        SocialOauthConnection connection = pendingConnection();
        connection.setStatus(SocialOauthStatus.PAGE_SELECTION_REQUIRED);
        connection.setLongLivedUserTokenEncrypted(tokenCipher.encrypt("long-lived"));
        when(connectionRepository.findByStateToken("state-1")).thenReturn(Optional.of(connection));
        when(metaOAuthClient.listPages("long-lived")).thenReturn(List.of(
                new MetaOAuthClient.PageOption("page-1", "Autobooks North", "token-1", "ig-1")));

        OAuthPendingSelectionRs result = service.listPendingPages(client, "state-1");

        assertThat(result.getPages()).hasSize(1);
        assertThat(result.getPages().get(0).getPageId()).isEqualTo("page-1");
        assertThat(result.getPages().get(0).getIgBusinessAccountId()).isEqualTo("ig-1");
    }
}
