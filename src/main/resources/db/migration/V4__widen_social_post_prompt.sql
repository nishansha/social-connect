-- The video-gen prompt is free text typed by staff describing what video they want, with no
-- natural length cap (unlike account/reference ids) — VARCHAR(1000) was hit in real use and
-- failed the whole post submission with a DataIntegrityViolationException. caption is already
-- wide (2000) for the same reason; prompt should be unbounded too.
ALTER TABLE social.social_post ALTER COLUMN prompt TYPE TEXT;
