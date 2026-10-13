package com.triasoft.socialpublisher.client.impl;

import com.triasoft.socialpublisher.client.VideoGenClient;
import com.triasoft.socialpublisher.config.VideoGenProperties;
import com.triasoft.socialpublisher.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Replicate's generic prediction lifecycle (confirmed against their docs): {@code POST
 * /v1/models/{model}/predictions} with {@code {"input": {...}}}, poll {@code GET
 * /v1/predictions/{id}}, status one of starting|processing|succeeded|failed|canceled.
 *
 * <p>What is NOT yet confirmed — isolated to {@link #buildInput} and {@link #extractOutputUrl} on
 * purpose, per the plan's note that the exact model schema needs a live Replicate account to
 * verify: the input parameter names for the chosen model (image/prompt/aspect_ratio/etc.) and the
 * exact shape of the {@code output} field (a bare URL string vs. an array). Confirm both against
 * {@code app.video-gen.model}'s real schema before relying on this in production — the rest of
 * this class (the prediction lifecycle itself) is provider-level and will not need to change
 * regardless of which model is chosen.
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
    public String submit(String imageUrl, String prompt) {
        if (properties.model() == null || properties.model().isBlank()) {
            // Fails loudly instead of silently building a malformed "/models//predictions" URL,
            // which Replicate 301-redirects — WebClient doesn't follow redirects by default, so
            // that surfaces as a confusing "no id" error instead of this actionable one.
            throw ApiException.badRequest("app.video-gen.model is not configured");
        }
        Map<String, Object> response = webClient.post()
                .uri("/models/{model}/predictions", properties.model())
                .bodyValue(Map.of("input", buildInput(imageUrl, prompt)))
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null || response.get("id") == null) {
            throw ApiException.badRequest("Replicate prediction creation returned no id");
        }
        return String.valueOf(response.get("id"));
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
        Map<String, Object> response = webClient.get()
                .uri("/predictions/{id}", jobId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        if (response == null) {
            throw ApiException.badRequest("Replicate prediction " + jobId + " returned no body");
        }
        return response;
    }

    // TODO confirm against the live schema for app.video-gen.model: the real parameter names
    // (this assumes "image" + "prompt", common across Wan/LTX-Video/SVD-style i2v models, but
    // verify — and whether an aspect_ratio/resolution param is needed to target Reels' 9:16).
    private Map<String, Object> buildInput(String imageUrl, String prompt) {
        return Map.of(
                "image", imageUrl,
                "prompt", prompt
        );
    }

    // TODO confirm against the live schema: Replicate's "output" field shape varies by model —
    // some return a bare URL string, others a list of URLs (frame-by-frame or multi-output).
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
