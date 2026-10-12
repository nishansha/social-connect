package com.triasoft.socialpublisher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A registered trusted caller application (garage-api today, any future app later). No
 * self-service registration in v1 — rows are inserted by whoever operates this service, via a
 * Flyway migration or a one-off script, with a bcrypt-hashed secret.
 */
@Getter
@Setter
@Entity
@Table(name = "social_client")
public class SocialClient extends BaseEntity {

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false, length = 100)
    private String clientSecretHash;

    // Deliberately separate from clientSecretHash: that's bcrypt (one-way, for OAuth2 auth) — HMAC
    // webhook signing needs the actual plaintext value to compute a signature with, so it can't be
    // derived from a hash. Stored in plaintext, same as how webhook-signing secrets are commonly
    // handled (e.g. Stripe) — this is not a login credential.
    @Column(name = "webhook_secret", nullable = false, length = 100)
    private String webhookSecret;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
