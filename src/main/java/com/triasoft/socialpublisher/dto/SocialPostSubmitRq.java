package com.triasoft.socialpublisher.dto;

import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.enums.SocialPostType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Request body for {@code POST /v1/posts} — the pinned contract garage-api (and any future caller) sends. */
@Data
public class SocialPostSubmitRq {
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
