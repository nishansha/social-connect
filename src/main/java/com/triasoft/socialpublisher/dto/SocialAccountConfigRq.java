package com.triasoft.socialpublisher.dto;

import com.triasoft.socialpublisher.enums.SocialPlatform;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class SocialAccountConfigRq {
    @NotNull
    private SocialPlatform platform;
    private String igBusinessAccountId;
    private String facebookPageId;
    /** Plaintext — encrypted before storage, never echoed back in any response. */
    private String pageAccessToken;
    private Instant tokenExpiresAt;
    private boolean active = true;
}
