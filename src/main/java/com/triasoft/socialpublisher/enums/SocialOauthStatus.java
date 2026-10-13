package com.triasoft.socialpublisher.enums;

/** Lifecycle of one "Connect Instagram/Facebook" handshake, see {@link com.triasoft.socialpublisher.entity.SocialOauthConnection}. */
public enum SocialOauthStatus {
    STARTED,
    PAGE_SELECTION_REQUIRED,
    COMPLETED,
    FAILED
}
