package com.triasoft.socialconnect.service;

import com.triasoft.socialconnect.client.MetaOAuthClient;
import com.triasoft.socialconnect.dto.OAuthPageOptionRs;
import com.triasoft.socialconnect.dto.OAuthPendingSelectionRs;
import com.triasoft.socialconnect.dto.OAuthStartRq;
import com.triasoft.socialconnect.dto.OAuthStartRs;
import com.triasoft.socialconnect.entity.SocialAccountConfig;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.entity.SocialOauthConnection;
import com.triasoft.socialconnect.enums.SocialOauthStatus;
import com.triasoft.socialconnect.exception.ApiException;
import com.triasoft.socialconnect.repository.SocialAccountConfigRepository;
import com.triasoft.socialconnect.repository.SocialOauthConnectionRepository;
import com.triasoft.socialconnect.security.TokenCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the "Connect Instagram/Facebook" handshake — the real replacement for manually
 * pasting a Page access token into {@code PUT /v1/accounts/{id}}. A Page access token can only
 * come out of this exchange (the Page admin consenting on Meta's own dialog); there is no API
 * that derives one from an IG Business Account ID or Page ID alone.
 *
 * <p>Flow: {@link #start} creates a pending {@link SocialOauthConnection} and hands garage-api a
 * Facebook-hosted URL to redirect the admin's browser to. Meta redirects back to {@link
 * #handleCallback} on this service (not garage-api) with a {@code code}, which is exchanged
 * server-to-server for a long-lived Page token. If the admin manages exactly one Page, the
 * handshake finishes immediately; with more than one, {@link #listPendingPages}/{@link #select}
 * let garage-api render a picker before finishing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetaOAuthService {

    private static final long PENDING_CONNECTION_TTL_SECONDS = 10 * 60;

    private final SocialOauthConnectionRepository connectionRepository;
    private final SocialAccountConfigRepository accountConfigRepository;
    private final MetaOAuthClient metaOAuthClient;
    private final TokenCipher tokenCipher;

    @Transactional
    public OAuthStartRs start(SocialClient client, OAuthStartRq request) {
        SocialOauthConnection connection = new SocialOauthConnection();
        connection.setStateToken(UUID.randomUUID().toString());
        connection.setSocialClientId(client.getId());
        connection.setAccountRef(request.getAccountRef());
        connection.setPlatform(request.getPlatform());
        connection.setReturnUrl(request.getReturnUrl());
        connection.setStatus(SocialOauthStatus.STARTED);
        connection.setExpiresAt(Instant.now().plusSeconds(PENDING_CONNECTION_TTL_SECONDS));
        connectionRepository.save(connection);

        return OAuthStartRs.builder()
                .authorizationUrl(metaOAuthClient.authorizationUrl(connection.getStateToken()))
                .build();
    }

    /**
     * Called by Meta redirecting the admin's own browser here — never by garage-api directly, so
     * there's no caller Bearer token to check; {@code state} is the only correlation we have, and
     * it's single-use + short-lived by construction. Returns the URL to redirect the browser to
     * next (back into garage-api's UI, one way or another).
     */
    @Transactional
    public String handleCallback(String code, String state) {
        SocialOauthConnection connection = connectionRepository.findByStateToken(state)
                .orElseThrow(() -> ApiException.notFound("Unknown or already-used OAuth state"));
        if (connection.getExpiresAt().isBefore(Instant.now())) {
            connection.setStatus(SocialOauthStatus.FAILED);
            connectionRepository.save(connection);
            return redirect(connection.getReturnUrl(), "connected=0&error=expired");
        }

        try {
            MetaOAuthClient.TokenExchangeResult shortLived = metaOAuthClient.exchangeCodeForUserToken(code);
            MetaOAuthClient.TokenExchangeResult longLived = metaOAuthClient.exchangeForLongLivedToken(shortLived.accessToken());
            List<MetaOAuthClient.PageOption> pages = metaOAuthClient.listPages(longLived.accessToken());

            if (pages.isEmpty()) {
                connection.setStatus(SocialOauthStatus.FAILED);
                connectionRepository.save(connection);
                return redirect(connection.getReturnUrl(), "connected=0&error=no_pages");
            }
            if (pages.size() == 1) {
                finish(connection, pages.get(0), longLived.expiresInSeconds());
                return redirect(connection.getReturnUrl(), "connected=1");
            }

            // More than one Page — stash the long-lived USER token (not any Page token yet) so
            // listPendingPages/select can re-list and finish without redoing the code exchange.
            // Its own expiry is stashed alongside it: the Page token select() ultimately saves
            // inherits this lifespan, not this pending row's own few-minute expiresAt.
            connection.setLongLivedUserTokenEncrypted(tokenCipher.encrypt(longLived.accessToken()));
            connection.setLongLivedUserTokenExpiresAt(Instant.now().plusSeconds(longLived.expiresInSeconds()));
            connection.setStatus(SocialOauthStatus.PAGE_SELECTION_REQUIRED);
            connection.setExpiresAt(Instant.now().plusSeconds(PENDING_CONNECTION_TTL_SECONDS));
            connectionRepository.save(connection);
            return redirect(connection.getReturnUrl(), "needsSelection=1&connectState=" + connection.getStateToken());
        } catch (Exception e) {
            // Deliberately broad: ApiException (Meta rejected something), a network blip, a
            // TokenCipher failure — any of these must still redirect the admin's browser back
            // instead of rendering a raw 500 mid-handshake and leaving this row stuck in STARTED
            // (its state token is single-use by lookup, so a stuck row can't be retried).
            log.warn(":: MetaOAuthService - OAuth callback failed for state={}: {} ::", state, e.getMessage());
            connection.setStatus(SocialOauthStatus.FAILED);
            connectionRepository.save(connection);
            return redirect(connection.getReturnUrl(), "connected=0&error=exchange_failed");
        }
    }

    /** Re-lists the admin's Pages for the picker UI — never exposes the Page access tokens themselves. */
    @Transactional(readOnly = true)
    public OAuthPendingSelectionRs listPendingPages(SocialClient client, String stateToken) {
        SocialOauthConnection connection = loadSelectable(client, stateToken);
        String longLivedUserToken = tokenCipher.decrypt(connection.getLongLivedUserTokenEncrypted());
        List<OAuthPageOptionRs> options = metaOAuthClient.listPages(longLivedUserToken).stream()
                .map(page -> OAuthPageOptionRs.builder()
                        .pageId(page.pageId())
                        .pageName(page.pageName())
                        .igBusinessAccountId(page.igBusinessAccountId())
                        .build())
                .toList();
        return OAuthPendingSelectionRs.builder().stateToken(stateToken).pages(options).build();
    }

    @Transactional
    public void select(SocialClient client, String stateToken, String pageId) {
        SocialOauthConnection connection = loadSelectable(client, stateToken);
        String longLivedUserToken = tokenCipher.decrypt(connection.getLongLivedUserTokenEncrypted());
        MetaOAuthClient.PageOption page = metaOAuthClient.listPages(longLivedUserToken).stream()
                .filter(candidate -> candidate.pageId().equals(pageId))
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Page " + pageId + " is not one of this user's Pages"));
        // The Page token derived from the long-lived USER token inherits that token's ~60-day
        // lifespan, not this pending connection's own few-minute expiresAt — use the stashed
        // user-token expiry, not remainingTtlSeconds(connection).
        long tokenTtlSeconds = connection.getLongLivedUserTokenExpiresAt() == null
                ? 0
                : Math.max(0, connection.getLongLivedUserTokenExpiresAt().getEpochSecond() - Instant.now().getEpochSecond());
        finish(connection, page, tokenTtlSeconds);
    }

    private void finish(SocialOauthConnection connection, MetaOAuthClient.PageOption page, long tokenTtlSeconds) {
        SocialAccountConfig config = accountConfigRepository
                .findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(connection.getSocialClientId(), connection.getAccountRef(), connection.getPlatform())
                .orElseGet(() -> {
                    SocialAccountConfig fresh = new SocialAccountConfig();
                    fresh.setSocialClientId(connection.getSocialClientId());
                    fresh.setAccountRef(connection.getAccountRef());
                    fresh.setPlatform(connection.getPlatform());
                    return fresh;
                });
        config.setFacebookPageId(page.pageId());
        config.setIgBusinessAccountId(page.igBusinessAccountId());
        config.setPageAccessTokenEncrypted(tokenCipher.encrypt(page.pageAccessToken()));
        config.setTokenExpiresAt(Instant.now().plusSeconds(tokenTtlSeconds > 0 ? tokenTtlSeconds : 60L * 24 * 60 * 60));
        config.setTokenLastRefreshedAt(Instant.now());
        config.setActive(true);
        accountConfigRepository.save(config);

        connection.setStatus(SocialOauthStatus.COMPLETED);
        connection.setLongLivedUserTokenEncrypted(null);
        connection.setLongLivedUserTokenExpiresAt(null);
        connectionRepository.save(connection);
    }

    private SocialOauthConnection loadSelectable(SocialClient client, String stateToken) {
        SocialOauthConnection connection = connectionRepository.findByStateToken(stateToken)
                .orElseThrow(() -> ApiException.notFound("Unknown OAuth state"));
        if (!connection.getSocialClientId().equals(client.getId())) {
            throw ApiException.notFound("Unknown OAuth state");
        }
        if (connection.getStatus() != SocialOauthStatus.PAGE_SELECTION_REQUIRED) {
            throw ApiException.conflict("This connection is not awaiting Page selection (status=" + connection.getStatus() + ")");
        }
        if (connection.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("This connection attempt has expired — start again");
        }
        return connection;
    }

    /**
     * Deletes PAGE_SELECTION_REQUIRED connections past their {@code expiresAt} — covers abandoned
     * handshakes (admin closed the Page-picker without choosing) that would otherwise sit forever
     * holding an encrypted long-lived user token with nothing else cleaning them up. Deliberately
     * scoped to this one status: a STARTED row holds no token yet, and sweeping those too would
     * delete a row still mid-handshake if the admin simply takes longer than expiresAt on
     * Facebook's own consent screens — handleCallback's own lookup-by-state already rejects a
     * truly expired STARTED row cleanly. Called by the poller, not a request thread.
     */
    @Transactional
    public void sweepExpiredConnections() {
        int deleted = connectionRepository.deleteByStatusAndExpiresAtBefore(SocialOauthStatus.PAGE_SELECTION_REQUIRED, Instant.now());
        if (deleted > 0) {
            log.info(":: MetaOAuthService - swept {} expired OAuth connection(s) ::", deleted);
        }
    }

    private String redirect(String returnUrl, String query) {
        // Not build(true): returnUrl is plain config (no pre-encoded components to preserve), and
        // asserting it's already-encoded would throw on the admin's browser mid-redirect if it
        // ever contains a character that needs escaping.
        return UriComponentsBuilder.fromUriString(returnUrl)
                .query(query)
                .build()
                .toUriString();
    }
}
