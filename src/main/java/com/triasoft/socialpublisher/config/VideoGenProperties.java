package com.triasoft.socialpublisher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code model} is the provider-specific model identifier (e.g. a Replicate "owner/name" slug) — confirm the exact value and its input schema against the provider's live catalog before relying on it. */
@ConfigurationProperties(prefix = "app.video-gen")
public record VideoGenProperties(String provider, String apiKey, String model) {
}
