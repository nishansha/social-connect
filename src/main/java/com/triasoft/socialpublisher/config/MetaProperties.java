package com.triasoft.socialpublisher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.meta")
public record MetaProperties(
        String appId,
        String appSecret,
        String graphApiVersion,
        /** This service's own public base URL — must exactly match a "Valid OAuth Redirect URI" registered on the Meta App. */
        String oauthRedirectBaseUrl) {
}
