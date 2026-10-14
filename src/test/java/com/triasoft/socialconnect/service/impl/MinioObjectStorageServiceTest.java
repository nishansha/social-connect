package com.triasoft.socialconnect.service.impl;

import com.triasoft.socialconnect.config.MinioProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No network involved — intercepts the outgoing request the same way ReplicateVideoGenClientTest
 * does, to prove what URI actually goes out without needing a reachable MinIO/ngrok endpoint.
 */
class MinioObjectStorageServiceTest {

    @Test
    void downloadFromUrl_sendsAnAlreadyPercentEncodedPresignedUrlVerbatim_notDoubleEncoded() {
        // Realistic shape of a MinIO presigned URL: AWS4-HMAC-SHA256 credential scope always
        // contains a literal "/" percent-encoded as "%2F".
        String presignedUrl = "https://minio.example.com/garage/INVENTORY/62/photo.jpeg"
                + "?X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=minioadmin%2F20261014%2Fus-east-1%2Fs3%2Faws4_request"
                + "&X-Amz-Date=20261014T102357Z&X-Amz-Expires=900&X-Amz-SignedHeaders=host"
                + "&X-Amz-Signature=deadbeef";

        AtomicReference<URI> capturedUri = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            capturedUri.set(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .body("abc")
                    .build());
        });

        MinioObjectStorageService service = new MinioObjectStorageService(
                org.mockito.Mockito.mock(MinioClient.class),
                org.mockito.Mockito.mock(MinioClient.class),
                new MinioProperties("http://localhost:9000", "https://minio.example.com", "key", "secret", "garage"),
                builder);

        byte[] result = service.downloadFromUrl(presignedUrl);

        assertThat(new String(result, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("abc");
        // The bug this guards against: WebClient's .uri(String) overload re-encodes an
        // already-percent-encoded URL string — "%2F" becomes "%252F" — corrupting the AWS SigV4
        // signature and making MinIO reject every single presigned-URL fetch with AccessDenied.
        assertThat(capturedUri.get().toString()).isEqualTo(presignedUrl);
        assertThat(capturedUri.get().toString()).doesNotContain("%252F");
    }
}
