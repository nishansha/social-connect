package com.triasoft.socialpublisher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code key} must be a base64-encoded 32-byte AES-256 key — used to encrypt Page access tokens at rest. */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String key) {
}
