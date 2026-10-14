package com.triasoft.socialconnect.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a real production bug: {@code webClientBuilder()} was singleton-scoped, so
 * one consumer's {@code .baseUrl(...)}/{@code .defaultHeader(...)} call (mutating in place —
 * {@link WebClient.Builder} doesn't copy) permanently contaminated every other consumer built from
 * the same shared instance. Concretely: Replicate's API key ended up as a default Authorization
 * header on MinioObjectStorageService's plain downloader, and MinIO rejected the resulting request
 * ("multiple authentication types") since presigned URLs carry their own query-string signature.
 */
class WebClientConfigTest {

    @Test
    void webClientBuilder_isPrototypeScoped_soOneConsumersMutationsNeverLeakIntoAnother() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(WebClientConfig.class)) {
            WebClient.Builder forReplicate = ctx.getBean(WebClient.Builder.class);
            WebClient.Builder forPlainDownloads = ctx.getBean(WebClient.Builder.class);

            assertThat(forReplicate).isNotSameAs(forPlainDownloads);

            // Exactly what ReplicateVideoGenClient's constructor does.
            forReplicate.baseUrl("https://api.replicate.com/v1")
                    .defaultHeader("Authorization", "Bearer r8_some-secret-key");

            // Exactly what MinioObjectStorageService's constructor does: build a plain client from
            // a separately-injected builder, with no base URL or default headers of its own.
            StringBuilder capturedAuthHeader = new StringBuilder("none");
            WebClient plainClient = forPlainDownloads.exchangeFunction(request -> {
                String auth = request.headers().getFirst("Authorization");
                capturedAuthHeader.setLength(0);
                capturedAuthHeader.append(auth == null ? "none" : auth);
                return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
            }).build();

            plainClient.get()
                    .uri(URI.create("https://minio.example.com/garage/object.jpg?X-Amz-Signature=abc"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            assertThat(capturedAuthHeader.toString()).isEqualTo("none");
        }
    }
}
