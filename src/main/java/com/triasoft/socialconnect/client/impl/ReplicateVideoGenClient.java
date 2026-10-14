package com.triasoft.socialconnect.client.impl;

import com.triasoft.socialconnect.client.VideoGenClient;
import com.triasoft.socialconnect.config.VideoGenProperties;
import com.triasoft.socialconnect.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Replicate's generic prediction lifecycle (confirmed against their docs): {@code POST
 * /v1/models/{owner}/{name}/predictions} with {@code {"input": {...}}}, poll {@code GET
 * /v1/predictions/{id}}, status one of starting|processing|succeeded|failed|canceled.
 *
 * <p>The model id ({@code app.video-gen.model}, e.g. {@code "wan-video/wan-2.2-i2v-fast"}) must be
 * split into its two path segments before building the request: {@link WebClient}'s path-variable
 * templating percent-encodes a {@code /} inside a single template value, so passing the whole id as
 * one {@code {model}} variable silently produces {@code /models/wan-video%2Fwan-2.2-i2v-fast/...} —
 * not a valid Replicate endpoint. {@link #splitModel} guards against this.
 *
 * <p>{@code app.video-gen.model}'s real schema (confirmed against Replicate's published API
 * schema for {@code wan-video/wan-2.2-i2v-fast} — not guessed): input takes {@code image} (URL
 * string), {@code prompt} (string), and {@code aspect_ratio} (enum, exactly {@code "16:9"} or
 * {@code "9:16"}, default {@code "16:9"} — no other values accepted). Output is a bare URI string.
 * {@link #buildInput} and {@link #extractOutputUrl} reflect this confirmed shape.
 */
@Slf4j
@Component
public class ReplicateVideoGenClient implements VideoGenClient {

    private final WebClient webClient;
    private final VideoGenProperties properties;

    public ReplicateVideoGenClient(WebClient.Builder webClientBuilder, VideoGenProperties properties) {
        this.properties = properties;
        this.webClient = webClientBuilder.baseUrl("https://api.replicate.com/v1")
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public String submit(String imageUrl, String prompt, VideoOrientation orientation) {
        String[] model = splitModel();
        Map<String, Object> response = callReplicate(() -> webClient.post()
                .uri("/models/{owner}/{name}/predictions", model[0], model[1])
                .bodyValue(Map.of("input", buildInput(imageUrl, prompt, orientation)))
                .retrieve()
                .bodyToMono(Map.class)
                .block());
        if (response == null || response.get("id") == null) {
            throw ApiException.badRequest("Replicate prediction creation returned no id");
        }
        return String.valueOf(response.get("id"));
    }

    /** @return {@code {owner, name}}. Fails loudly on a missing/malformed {@code app.video-gen.model} instead of silently building a broken request URL. */
    private String[] splitModel() {
        String model = properties.model();
        if (model == null || model.isBlank()) {
            throw ApiException.badRequest("app.video-gen.model is not configured");
        }
        String[] parts = model.split("/", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw ApiException.badRequest("app.video-gen.model must be in \"owner/name\" form, got: " + model);
        }
        return parts;
    }

    @Override
    public VideoGenStatus poll(String jobId) {
        Map<String, Object> response = fetch(jobId);
        String status = String.valueOf(response.get("status"));
        return switch (status) {
            case "succeeded" -> VideoGenStatus.SUCCEEDED;
            case "failed", "canceled" -> VideoGenStatus.FAILED;
            default -> VideoGenStatus.RUNNING; // starting | processing
        };
    }

    @Override
    public String resultVideoUrl(String jobId) {
        return extractOutputUrl(fetch(jobId));
    }

    private Map<String, Object> fetch(String jobId) {
        Map<String, Object> response = callReplicate(() -> webClient.get()
                .uri("/predictions/{id}", jobId)
                .retrieve()
                .bodyToMono(Map.class)
                .block());
        if (response == null) {
            throw ApiException.badRequest("Replicate prediction " + jobId + " returned no body");
        }
        return response;
    }

    /**
     * A failed call to Replicate (an error response, a connection/timeout failure, anything under
     * {@link WebClientException}) otherwise reaches {@code GlobalExceptionHandler}'s generic
     * handler, which logs the real cause server-side only and returns a sanitized "Internal server
     * error" to the caller. Translating it into an {@link ApiException} here puts the real reason
     * back on the wire so it's visible outside this process, not just in this service's own console.
     * {@link WebClientResponseException} (an actual HTTP error response) is caught separately
     * because it alone carries Replicate's response body — the specific rejection reason — which
     * {@code WebClientException}'s own {@code getMessage()} does not include.
     */
    private Map<String, Object> callReplicate(java.util.function.Supplier<Map<String, Object>> call) {
        try {
            return call.get();
        } catch (WebClientResponseException e) {
            log.warn(":: ReplicateVideoGenClient - Replicate rejected a request: {} {} ::", e.getStatusCode(), e.getResponseBodyAsString());
            throw ApiException.badRequest("Replicate rejected the video-gen request (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (WebClientException e) {
            log.warn(":: ReplicateVideoGenClient - call to Replicate failed: {} ::", e.getMessage());
            throw ApiException.badRequest("Call to Replicate failed: " + e.getMessage());
        }
    }

    private Map<String, Object> buildInput(String imageUrl, String prompt, VideoOrientation orientation) {
        return Map.of(
                "image", imageUrl,
                "prompt", prompt,
                "aspect_ratio", orientation == VideoOrientation.PORTRAIT ? "9:16" : "16:9"
        );
    }

    @SuppressWarnings("unchecked")
    private String extractOutputUrl(Map<String, Object> prediction) {
        Object output = prediction.get("output");
        if (output instanceof String url) {
            return url;
        }
        if (output instanceof java.util.List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
        }
        throw ApiException.badRequest("Could not extract a video URL from Replicate output: " + output);
    }
}
