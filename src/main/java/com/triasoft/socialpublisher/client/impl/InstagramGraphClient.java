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
    public String createPhotoContainer(SocialAccountConfig account, String imageUrl, String caption) {
        MultiValueMap<String, String> form = baseForm(account);
        form.add("image_url", imageUrl);
        if (caption != null) {
            form.add("caption", caption);
        }
        return post("/" + account.getIgBusinessAccountId() + "/media", form, "Failed to create photo media container");
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
    public ContainerStatus pollContainer(SocialAccountConfig account, String containerId) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/" + containerId)
                        .queryParam("fields", "status_code")
                        .queryParam("access_token", decryptedToken(account))
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();
        String statusCode = response == null ? null : String.valueOf(response.get("status_code"));
        return switch (statusCode == null ? "" : statusCode) {
            case "FINISHED" -> ContainerStatus.FINISHED;
            case "ERROR" -> ContainerStatus.ERROR;
            case "EXPIRED" -> ContainerStatus.EXPIRED;
            default -> ContainerStatus.IN_PROGRESS;
        };
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
