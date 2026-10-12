package com.triasoft.socialpublisher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.meta")
public record MetaProperties(String appId, String appSecret, String graphApiVersion) {
}
