-- The long-lived USER token's own ~60-day expiry (from Meta's exchange response) — distinct from
-- social_oauth_connection.expires_at (that row's own short pending-handshake TTL). select() must
-- derive the chosen Page token's tokenExpiresAt from this, since a Page token derived from the
-- user token inherits its lifespan, not the handshake's few-minute window.

ALTER TABLE social_oauth_connection ADD COLUMN long_lived_user_token_expires_at TIMESTAMPTZ;
