package com.triasoft.socialpublisher.dto;

import com.triasoft.socialpublisher.enums.SocialPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OAuthStartRq {
    @NotBlank
    private String accountRef;
    @NotNull
    private SocialPlatform platform;
    /** Where the admin's browser lands after the handshake — garage-api's own settings page. */
    @NotBlank
    private String returnUrl;
}
