-- Decided once per post from the first source photo (see SocialPostService.detectOrientation)
-- and needed again later by the poller (advanceClip), which runs off the request thread — must
-- be persisted, not just a local variable in submitVideo.
ALTER TABLE social.social_post ADD COLUMN orientation VARCHAR(20);
