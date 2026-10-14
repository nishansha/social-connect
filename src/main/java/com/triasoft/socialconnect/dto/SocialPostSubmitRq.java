package com.triasoft.socialconnect.dto;

import com.triasoft.socialconnect.enums.SocialPlatform;
import com.triasoft.socialconnect.enums.SocialPostType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Request body for {@code POST /v1/posts} — the pinned contract garage-api (and any future caller) sends. */
@Data
public class SocialPostSubmitRq {
    /** Opaque caller-supplied tenant reference — which of the caller's accounts to post as. */
    @NotBlank
    private String accountRef;
    @NotNull
    private SocialPlatform platform;
    @NotNull
    private SocialPostType postType;
    @NotNull
    private String inventoryRef;
    @NotEmpty
    private List<String> photoUrls;
    private String prompt;
    private String caption;
    @NotNull
    private String callbackUrl;
}
