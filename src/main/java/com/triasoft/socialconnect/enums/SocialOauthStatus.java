package com.triasoft.socialconnect.enums;

/** Lifecycle of one "Connect Instagram/Facebook" handshake, see {@link com.triasoft.socialconnect.entity.SocialOauthConnection}. */
public enum SocialOauthStatus {
    STARTED,
    PAGE_SELECTION_REQUIRED,
    COMPLETED,
    FAILED
}
