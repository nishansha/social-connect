package com.triasoft.socialpublisher.client.impl;

import com.triasoft.socialpublisher.client.SocialPublishClient;
import com.triasoft.socialpublisher.config.MetaProperties;
import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.security.TokenCipher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Instagram Content Publishing API (Graph API): container create -> poll -> {@code media_publish}.
 * The only {@link SocialPublishClient} implementation today — a phase-2 {@code FacebookPageClient}
 * implements the same interface for Facebook Page posting, additively.
 */
@Slf4j
@Component
public class InstagramGraphClient implements SocialPublishClient {

    private final WebClient webClient;
    private final MetaProperties metaProperties;
    private final TokenCipher tokenCipher;

    public InstagramGraphClient(WebClient.Builder webClientBuilder, MetaProperties metaProperties, TokenCipher tokenCipher) {
        this.metaProperties = metaProperties;
        this.tokenCipher = tokenCipher;
        this.webClient = webClientBuilder.baseUrl("https://graph.facebook.com/" + metaProperties.graphApiVersion()).build();
    }

    @Override
    public String createPhotoContainer(SocialAccountConfig account, List<String> imageUrls, String caption) {
        if (imageUrls.size() == 1) {
            MultiValueMap<String, String> form = baseForm(account);
            form.add("image_url", imageUrls.get(0));
            if (caption != null) {
                form.add("caption", caption);
            }
            return post("/" + account.getIgBusinessAccountId() + "/media", form, "Failed to create photo media container");
        }
        return createCarouselContainer(account, imageUrls, caption);
    }

    /**
     * Instagram's carousel shape: each image becomes its own "carousel item" container (no
     * caption — the caption lives on the parent only), then a {@code media_type=CAROUSEL} parent
     * container references them by id. The parent is what gets polled/published afterward —
     * {@link #pollContainer}/{@link #publish} are unchanged, they already just take a container id
     * with no awareness of whether it's a single photo, a carousel, or (eventually) a video.
     */
    private String createCarouselContainer(SocialAccountConfig account, List<String> imageUrls, String caption) {
        List<String> childIds = imageUrls.stream()
                .map(imageUrl -> {
                    MultiValueMap<String, String> itemForm = baseForm(account);
                    itemForm.add("image_url", imageUrl);
                    itemForm.add("is_carousel_item", "true");
                    return post("/" + account.getIgBusinessAccountId() + "/media", itemForm, "Failed to create carousel item container");
                })
                .toList();

        MultiValueMap<String, String> carouselForm = baseForm(account);
        carouselForm.add("media_type", "CAROUSEL");
        carouselForm.add("children", String.join(",", childIds));
        if (caption != null) {
            carouselForm.add("caption", caption);
        }
        return post("/" + account.getIgBusinessAccountId() + "/media", carouselForm, "Failed to create carousel media container");
    }

    @Override
    public String createVideoContainer(SocialAccountConfig account, String videoUrl, String caption) {
        MultiValueMap<String, String> form = baseForm(account);
        form.add("video_url", videoUrl);
        form.add("media_type", "REELS");
        if (caption != null) {
            form.add("caption", caption);
        }
        return post("/" + account.getIgBusinessAccountId() + "/media", form, "Failed to create video media container");
    }

    @Override
    public ContainerPollResult pollContainer(SocialAccountConfig account, String containerId) {
        // "status" (not just "status_code") is Meta's own human-readable rejection reason —
        // e.g. why a generated video failed Reels' asset requirements. Without it, every
        // container-processing failure looks identical ("entered ERROR") regardless of cause.
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/" + containerId)
                        .queryParam("fields", "status_code,status")
                        .queryParam("access_token", decryptedToken(account))
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        String statusCode = response == null ? null : String.valueOf(response.get("status_code"));
        String detail = response == null ? null : (String) response.get("status");
        ContainerStatus status = switch (statusCode == null ? "" : statusCode) {
            case "FINISHED" -> ContainerStatus.FINISHED;
            case "ERROR" -> ContainerStatus.ERROR;
            case "EXPIRED" -> ContainerStatus.EXPIRED;
            default -> ContainerStatus.IN_PROGRESS;
        };
        return new ContainerPollResult(status, detail);
    }

    @Override
    public String publish(SocialAccountConfig account, String containerId) {
        MultiValueMap<String, String> form = baseForm(account);
        form.add("creation_id", containerId);
        return post("/" + account.getIgBusinessAccountId() + "/media_publish", form, "Failed to publish media container");
    }

    private MultiValueMap<String, String> baseForm(SocialAccountConfig account) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", decryptedToken(account));
        return form;
    }

    private String decryptedToken(SocialAccountConfig account) {
        return tokenCipher.decrypt(account.getPageAccessTokenEncrypted());
    }

    @SuppressWarnings("unchecked")
    private String post(String path, MultiValueMap<String, String> form, String errorMessage) {
        Map<String, Object> response = webClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse -> clientResponse.bodyToMono(String.class)
                        .flatMap(body -> {
                            log.warn(":: InstagramGraphClient - {} :: {}", errorMessage, body);
                            return reactor.core.publisher.Mono.error(ApiException.badRequest(errorMessage + ": " + body));
                        }))
                .bodyToMono(Map.class)
                .block();
        if (response == null || response.get("id") == null) {
            throw ApiException.badRequest(errorMessage + ": empty response");
        }
        return String.valueOf(response.get("id"));
    }
}
