package com.triasoft.socialpublisher.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** {@code POST /oauth/token} request — the pinned OAuth2 client-credentials contract. */
@Data
public class TokenRq {
    @JsonProperty("client_id")
    private String clientId;
    @JsonProperty("client_secret")
    private String clientSecret;
    @JsonProperty("grant_type")
    private String grantType;
}
