package com.triasoft.socialpublisher.client;

/**
 * Adapter over a hosted image-to-video inference API (Replicate first — see
 * {@code ReplicateVideoGenClient}). One call per source photo; there is no multi-photo-in,
 * single-clip-out primitive on any real provider.
 */
public interface VideoGenClient {

    /** Submits one image + prompt, returns the provider's job id. */
    String submit(String imageUrl, String prompt);

    VideoGenStatus poll(String jobId);

    /** Only valid once {@link #poll} returns {@link VideoGenStatus#SUCCEEDED}. */
    String resultVideoUrl(String jobId);

    enum VideoGenStatus {
        RUNNING,
        SUCCEEDED,
        FAILED
    }
}
