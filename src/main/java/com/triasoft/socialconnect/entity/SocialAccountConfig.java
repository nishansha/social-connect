package com.triasoft.socialconnect.entity;

import com.triasoft.socialconnect.enums.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per (client, accountRef, platform) — a caller's own Meta account configuration for one
 * of its tenants. {@code accountRef} is an opaque string the caller supplies (garage-api sends
 * its tenant id) — same convention as {@link SocialPost#getInventoryRef()}.
 */
@Getter
@Setter
@Entity
@Table(name = "social_account_config", uniqueConstraints = @UniqueConstraint(columnNames = {"social_client_id", "account_ref", "platform"}))
public class SocialAccountConfig extends BaseEntity {

    @Column(name = "social_client_id", nullable = false)
    private Long socialClientId;

    @Column(name = "account_ref", nullable = false, length = 200)
    private String accountRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SocialPlatform platform;

    @Column(name = "ig_business_account_id", length = 64)
    private String igBusinessAccountId;

    @Column(name = "facebook_page_id", length = 64)
    private String facebookPageId;

    @Column(name = "page_access_token_encrypted", length = 2000)
    private String pageAccessTokenEncrypted;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "token_last_refreshed_at")
    private Instant tokenLastRefreshedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
