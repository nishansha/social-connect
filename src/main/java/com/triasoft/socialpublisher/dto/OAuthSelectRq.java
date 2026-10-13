package com.triasoft.socialpublisher.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthSelectRq {
    @NotBlank
    private String stateToken;
    @NotBlank
    private String pageId;
}
