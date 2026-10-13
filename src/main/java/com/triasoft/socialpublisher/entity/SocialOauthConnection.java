package com.triasoft.socialpublisher.entity;

import com.triasoft.socialpublisher.enums.SocialOauthStatus;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per "Connect Instagram/Facebook" OAuth handshake. Correlated purely by
 * {@code stateToken} — the opaque value Meta echoes back on redirect — since the browser never
 * carries our Bearer token through the Facebook consent dialog. No FK to
 * {@link SocialAccountConfig}: that row doesn't exist yet when the handshake starts, and may never
 * exist if the admin cancels or the exchange fails.
 *
 * <p>When the Facebook user manages more than one Page, {@code longLivedUserTokenEncrypted} is
 * kept just long enough (row TTL via {@code expiresAt}) to let {@link
 * com.triasoft.socialpublisher.service.MetaOAuthService#listPendingPages} re-list the Pages and
 * {@code select} finish the handshake, without a separate Pages table.
 */
@Getter
@Setter
@Entity
@Table(name = "social_oauth_connection")
public class SocialOauthConnection extends BaseEntity {

    @Column(name = "state_token", nullable = false, unique = true, length = 100)
    private String stateToken;

    @Column(name = "social_client_id", nullable = false)
    private Long socialClientId;

    @Column(name = "account_ref", nullable = false, length = 200)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SocialPlatform platform;

    @Column(name = "return_url", nullable = false, length = 1000)
    private String returnUrl;

    @Column(name = "long_lived_user_token_encrypted", length = 2000)
    private String longLivedUserTokenEncrypted;

    /** The user token's own ~60-day Meta expiry — distinct from {@link #expiresAt}, this row's short pending-handshake TTL. */
    @Column(name = "long_lived_user_token_expires_at")
    private Instant longLivedUserTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SocialOauthStatus status = SocialOauthStatus.STARTED;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
