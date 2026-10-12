package com.triasoft.socialpublisher.enums;

/**
 * PHOTO posts skip straight to PUBLISHING (no video-gen leg). VIDEO posts walk the full chain.
 * Terminal states: PUBLISHED, FAILED.
 */
public enum SocialPostStatus {
    PENDING_VIDEO_GEN,
    STITCHING,
    VIDEO_READY_FOR_REVIEW,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
