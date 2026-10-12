package com.triasoft.socialpublisher.service.impl;

import com.triasoft.socialpublisher.config.MinioProperties;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.service.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Slf4j
@Service
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final MinioProperties properties;
    private final WebClient plainWebClient;

    public MinioObjectStorageService(MinioClient minioClient,
                                      @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                                      MinioProperties properties,
                                      WebClient.Builder webClientBuilder) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.properties = properties;
        this.plainWebClient = webClientBuilder.build();
    }

    @PostConstruct
    void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info(":: MinioObjectStorageService - created bucket {} ::", properties.bucket());
            }
        } catch (Exception e) {
            log.warn(":: MinioObjectStorageService - could not verify/create bucket {} (ok if it already exists with restricted perms): {} ::", properties.bucket(), e.getMessage());
        }
    }

    @Override
    public String storeFromUrl(String sourceUrl, String objectKeyPrefix) {
        byte[] content = plainWebClient.get()
                .uri(sourceUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
        if (content == null) {
            throw ApiException.badRequest("Downloaded empty content from " + sourceUrl);
        }
        return store(content, objectKeyPrefix, ".mp4", "video/mp4");
    }

    @Override
    public String store(byte[] content, String objectKeyPrefix, String extension, String contentType) {
        String objectKey = objectKeyPrefix + "/" + UUID.randomUUID() + extension;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store object " + objectKey, e);
        }
        return objectKey;
    }

    @Override
    public byte[] download(String objectKey) {
        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download object " + objectKey, e);
        }
    }

    @Override
    public String presignedUrl(String objectKey, int expirySeconds) {
        try {
            return publicMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to presign " + objectKey, e);
        }
    }
}
