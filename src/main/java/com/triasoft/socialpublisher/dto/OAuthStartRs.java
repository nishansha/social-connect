package com.triasoft.socialpublisher.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OAuthStartRs {
    /** The Facebook OAuth consent dialog URL — garage-api redirects the admin's browser here. */
    private String authorizationUrl;
}
