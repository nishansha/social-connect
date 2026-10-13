package com.triasoft.socialpublisher.dto;

import com.triasoft.socialpublisher.enums.SocialPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class SocialAccountConfigRq {
    /** Opaque caller-supplied tenant reference — garage-api sends its tenant id. */
    @NotBlank
    private String accountRef;
    @NotNull
    private SocialPlatform platform;
    private String igBusinessAccountId;
    private String facebookPageId;
    /** Plaintext — encrypted before storage, never echoed back in any response. */
    private String pageAccessToken;
    private Instant tokenExpiresAt;
    private boolean active = true;
}
