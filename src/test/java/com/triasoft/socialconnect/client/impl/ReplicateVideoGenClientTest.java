package com.triasoft.socialconnect.client.impl;

import com.triasoft.socialconnect.client.VideoGenClient;
import com.triasoft.socialconnect.config.VideoGenProperties;
import com.triasoft.socialconnect.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No network involved — {@link WebClient.Builder#exchangeFunction} intercepts the outgoing
 * {@code ClientRequest} so these tests see exactly what URI/body would have been sent, without
 * ever reaching Replicate. That's deliberate: this project's directive during video-gen work is to
 * verify via automated tests only, never real Replicate calls (credits are limited).
 */
class ReplicateVideoGenClientTest {

    private static VideoGenProperties properties(String model) {
        return new VideoGenProperties("replicate", "r8_test-key", model);
    }

    /**
     * Proves the bug this class guards against is real on THIS project's actual Spring version
     * (6.2.11, not whatever happens to be newest in the local Maven cache) — a single {@code {model}}
     * template variable containing a "/" comes back percent-encoded, not as a path separator.
     */
    @Test
    void webClientPathVariableTemplating_percentEncodesASlashInsideASingleValue() {
        String uri = new DefaultUriBuilderFactory("https://api.replicate.com/v1")
                .uriString("/models/{model}/predictions")
                .build("wan-video/wan-2.2-i2v-fast")
                .toString();

        assertThat(uri).contains("%2F");
    }

    @Test
    void submit_requestsTheTwoSegmentModelPath_notAPercentEncodedSingleSegment() {
        AtomicReference<URI> capturedUri = new AtomicReference<>();
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            capturedUri.set(request.url());
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"id\":\"abc123\"}")
                    .build());
        });
        ReplicateVideoGenClient client = new ReplicateVideoGenClient(builder, properties("wan-video/wan-2.2-i2v-fast"));

        String jobId = client.submit("https://example.com/a.jpg", "pan", VideoGenClient.VideoOrientation.LANDSCAPE);

        assertThat(jobId).isEqualTo("abc123");
        // The bug this guards against: WebClient's path-variable templating percent-encodes a "/"
        // inside a single template value, so passing the whole "owner/name" id as one {model}
        // variable silently produced ".../models/wan-video%2Fwan-2.2-i2v-fast/predictions" instead
        // of this real two-segment endpoint — every video submission 404'd against Replicate.
        assertThat(capturedUri.get().toString())
                .isEqualTo("https://api.replicate.com/v1/models/wan-video/wan-2.2-i2v-fast/predictions");
    }

    @Test
    void submit_rejectsAModelIdWithoutAnOwnerSlash() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            throw new AssertionError("should not have made a request");
        });
        ReplicateVideoGenClient client = new ReplicateVideoGenClient(builder, properties("no-owner-segment"));

        assertThatThrownBy(() -> client.submit("https://example.com/a.jpg", "pan", VideoGenClient.VideoOrientation.LANDSCAPE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("owner/name");
    }

    @Test
    void submit_wrapsAReplicateErrorResponseIntoAnApiExceptionCarryingTheRealBody() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header("Content-Type", "application/json")
                        .body("{\"detail\":\"Invalid token\"}")
                        .build()));
        ReplicateVideoGenClient client = new ReplicateVideoGenClient(builder, properties("wan-video/wan-2.2-i2v-fast"));

        assertThatThrownBy(() -> client.submit("https://example.com/a.jpg", "pan", VideoGenClient.VideoOrientation.LANDSCAPE))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid token");
    }
}
