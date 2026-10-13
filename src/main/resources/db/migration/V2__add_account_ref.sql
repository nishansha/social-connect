-- SocialAccountConfig and SocialPost were scoped only by (social_client_id, platform) — one
-- Instagram/Facebook account per CALLING APPLICATION. A calling application (garage-api) is
-- itself multi-tenant, so this must become per-tenant: (social_client_id, account_ref, platform).
-- account_ref is an opaque string the caller supplies (garage-api sends its tenant id), same
-- convention as social_post.inventory_ref — this service never interprets it.

ALTER TABLE social_account_config ADD COLUMN account_ref VARCHAR(200);
UPDATE social_account_config SET account_ref = 'default' WHERE account_ref IS NULL;
ALTER TABLE social_account_config ALTER COLUMN account_ref SET NOT NULL;

ALTER TABLE social_account_config DROP CONSTRAINT uq_social_account_config_client_platform;
ALTER TABLE social_account_config ADD CONSTRAINT uq_social_account_config_client_account_platform
    UNIQUE (social_client_id, account_ref, platform);

ALTER TABLE social_post ADD COLUMN account_ref VARCHAR(200);
UPDATE social_post SET account_ref = 'default' WHERE account_ref IS NULL;
ALTER TABLE social_post ALTER COLUMN account_ref SET NOT NULL;

CREATE INDEX idx_social_account_config_lookup ON social_account_config (social_client_id, account_ref, platform);

-- Pending OAuth "Connect Instagram/Facebook" handshakes — short-lived, correlated purely by the
-- opaque state token Meta echoes back on redirect. No FK to social_account_config: the row this
-- creates/updates doesn't exist yet when the handshake starts.
CREATE TABLE social_oauth_connection (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    state_token         VARCHAR(100) NOT NULL UNIQUE,
    social_client_id    BIGINT NOT NULL REFERENCES social_client(id),
    account_ref         VARCHAR(200) NOT NULL,
    platform            VARCHAR(20) NOT NULL,
    return_url          VARCHAR(1000) NOT NULL,
    long_lived_user_token_encrypted VARCHAR(2000),
    status              VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_social_oauth_connection_expires ON social_oauth_connection (expires_at);
