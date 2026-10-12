CREATE TABLE social_client (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id       VARCHAR(100) NOT NULL UNIQUE,
    client_secret_hash VARCHAR(100) NOT NULL,
    webhook_secret  VARCHAR(100) NOT NULL,
    name            VARCHAR(150) NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE social_account_config (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    social_client_id            BIGINT NOT NULL REFERENCES social_client(id),
    platform                    VARCHAR(20) NOT NULL,
    ig_business_account_id      VARCHAR(64),
    facebook_page_id            VARCHAR(64),
    page_access_token_encrypted VARCHAR(2000),
    token_expires_at            TIMESTAMPTZ,
    token_last_refreshed_at     TIMESTAMPTZ,
    active                      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_social_account_config_client_platform UNIQUE (social_client_id, platform)
);

CREATE TABLE social_post (
    id                          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    social_client_id            BIGINT NOT NULL REFERENCES social_client(id),
    inventory_ref               VARCHAR(200) NOT NULL,
    platform                    VARCHAR(20) NOT NULL,
    post_type                   VARCHAR(10) NOT NULL,
    status                      VARCHAR(30) NOT NULL,
    prompt                      VARCHAR(1000),
    caption                     VARCHAR(2000),
    callback_url                VARCHAR(1000),
    media_container_id          VARCHAR(100),
    instagram_media_id          VARCHAR(100),
    generated_video_object_key  VARCHAR(500),
    failure_stage               VARCHAR(30),
    provider_error_code          VARCHAR(100),
    provider_error_message       VARCHAR(2000),
    container_poll_attempts     INTEGER NOT NULL DEFAULT 0,
    approved_at                 TIMESTAMPTZ,
    published_at                TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_social_post_client ON social_post (social_client_id);
CREATE INDEX idx_social_post_status ON social_post (status);

CREATE TABLE social_post_photo_url (
    social_post_id  BIGINT NOT NULL REFERENCES social_post(id),
    sequence_order  INTEGER NOT NULL,
    photo_url       VARCHAR(2000) NOT NULL,
    PRIMARY KEY (social_post_id, sequence_order)
);

CREATE TABLE social_post_clip (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    social_post_id      BIGINT NOT NULL REFERENCES social_post(id),
    source_photo_url    VARCHAR(2000) NOT NULL,
    sequence_order      INTEGER NOT NULL,
    video_gen_provider  VARCHAR(50),
    video_gen_job_id    VARCHAR(100),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    clip_object_key     VARCHAR(500),
    poll_attempts       INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_social_post_clip_post ON social_post_clip (social_post_id);
