# social-publisher

Standalone service for posting photos (and AI-generated videos built from photos + a prompt) to
Instagram via Meta's Graph API — Facebook Page support is phase 2. Built so any trusted
application (garage-api today, others later) can call it the same way: REST + OAuth2
client-credentials, no coupling to any caller's domain model.

## Running locally

Reuses garage-api's existing local Postgres instance (new schema `social`) and a separate MinIO
bucket. With those running:

```bash
mvn spring-boot:run
```

Flyway creates the `social` schema and its tables on first boot. Default dev config
(`application-dev.yml`) has `meta.*` and `video-gen.*` left blank — those endpoints will fail
until real credentials are set (see Prerequisites below), but everything else (OAuth2 token
issuance, account config CRUD, the post state machine) works without them.

## Registering a caller (e.g. garage-api)

No self-service registration in v1 — insert a row by hand:

```sql
INSERT INTO social.social_client (client_id, client_secret_hash, webhook_secret, name, active, created_at, updated_at, version)
VALUES (
  'garage-api',
  '$2a$10$...',          -- bcrypt hash of the real secret; never store the plaintext
  'generate-a-separate-random-value-for-this',
  'garage-api',
  true,
  now(), now(), 0
);
```

Generate the bcrypt hash with any bcrypt tool (e.g. Spring's `BCryptPasswordEncoder`, or
`htpasswd -bnBC 10 "" <secret> | tr -d ':\n'`). The caller is then configured with:
- `SOCIAL_PUBLISHER_CLIENT_ID=garage-api`
- `SOCIAL_PUBLISHER_CLIENT_SECRET=<the plaintext secret you hashed above>`
- `SOCIAL_WEBHOOK_SECRET=<the same value as webhook_secret above>` — deliberately a *different*
  secret from the client secret; see `SocialClient.webhookSecret`'s doc comment for why.

## API (v1) — the pinned contract

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/oauth/token` | `{client_id, client_secret, grant_type: "client_credentials"}` → `{access_token, token_type, expires_in}` |
| `GET`/`POST`/`PUT`/`DELETE` | `/v1/accounts` | manage the caller's own Meta account config |
| `POST` | `/v1/posts` | `{platform, postType, inventoryRef, photoUrls[], prompt?, caption, callbackUrl}` |
| `GET` | `/v1/posts/{id}` | status/detail |
| `POST` | `/v1/posts/{id}/approve` | approve a reviewed video, trigger publish |

Webhook callback (same body shape as the above): `POST {callbackUrl}`, headers
`X-Social-Timestamp` (epoch seconds) + `X-Social-Signature` (hex), signed
`HMAC-SHA256(webhook_secret, timestamp + "." + rawBody)`.

## Known open items (see plan / memory for detail)

1. **Video-gen model schema unconfirmed.** `ReplicateVideoGenClient.buildInput`/`extractOutputUrl`
   are isolated and TODO-marked — the generic Replicate prediction lifecycle is confirmed and
   correct, but the specific model's input param names and output shape need a live Replicate
   account to verify before this can actually generate a video.
2. **Instagram Reels format compatibility unverified** — whether the chosen model's raw output
   (aspect/duration/codec) matches Reels' requirements, or needs a transcode step added to
   `FfmpegVideoStitchService`.
3. **No real Meta Developer App / Instagram Business Account exists yet** — `InstagramGraphClient`
   is built against the documented Content Publishing API contract but has never made a real call.
4. **ffmpeg untested on the development machine this was built on** — the Dockerfile installs it
   for the real runtime; `FfmpegVideoStitchService`'s `ProcessBuilder` invocation itself hasn't
   been run end-to-end locally.
