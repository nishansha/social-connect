package com.triasoft.socialconnect.client;

/**
 * Adapter over a hosted image-to-video inference API (Replicate first — see
 * {@code ReplicateVideoGenClient}). One call per source photo; there is no multi-photo-in,
 * single-clip-out primitive on any real provider.
 */
public interface VideoGenClient {

    /**
     * Submits one image + prompt, returns the provider's job id. {@code orientation} is decided
     * once per post (from the first source photo) by the caller and applied to every clip in that
     * post — see {@code SocialPostService.submitVideo} — so all clips share one resolution, which
     * {@code FfmpegVideoStitchService}'s stream-copy concat requires.
     */
    String submit(String imageUrl, String prompt, VideoOrientation orientation);

    VideoGenStatus poll(String jobId);

    /** Only valid once {@link #poll} returns {@link VideoGenStatus#SUCCEEDED}. */
    String resultVideoUrl(String jobId);

    enum VideoGenStatus {
        RUNNING,
        SUCCEEDED,
        FAILED
    }

    /** Provider-agnostic — {@code ReplicateVideoGenClient} maps this to Replicate's own aspect_ratio string. */
    enum VideoOrientation {
        LANDSCAPE,
        PORTRAIT
    }
}
