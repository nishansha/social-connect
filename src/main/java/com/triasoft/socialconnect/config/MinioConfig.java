package com.triasoft.socialconnect.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    // Used only to mint presigned URLs an external caller (garage-api, or Meta/Replicate
    // themselves if we ever hand them a URL directly) must be able to fetch — SigV4 signatures
    // are bound to the endpoint they're signed against, so the internal client above can't be
    // reused for this. Falls back to the internal endpoint when unset, same as garage-api's
    // MinioConfig, so local dev without a tunnel still starts.
    @Bean
    @Qualifier("publicMinioClient")
    public MinioClient publicMinioClient(MinioProperties properties) {
        String publicEndpoint = StringUtils.hasText(properties.publicEndpoint()) ? properties.publicEndpoint() : properties.endpoint();
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}
