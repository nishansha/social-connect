package com.triasoft.socialpublisher.entity;

import com.triasoft.socialpublisher.enums.SocialPlatform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One row per (client, platform) — a caller's own Meta account configuration. */
@Getter
@Setter
@Entity
@Table(name = "social_account_config", uniqueConstraints = @UniqueConstraint(columnNames = {"social_client_id", "platform"}))
public class SocialAccountConfig extends BaseEntity {

    @Column(name = "social_client_id", nullable = false)
    private Long socialClientId;

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
