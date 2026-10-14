package com.triasoft.socialconnect.client;

import com.triasoft.socialconnect.config.MetaProperties;
import com.triasoft.socialconnect.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Raw Meta OAuth + Graph API calls needed to turn a consent-dialog {@code code} into a usable,
 * long-lived Page access token — the only legitimate way to obtain one. There is no Graph API
 * endpoint that hands back a token given just an IG Business Account ID or Page ID; a token only
 * ever comes out of this exchange, which requires the Page admin's own explicit consent.
 */
@Slf4j
@Component
public class MetaOAuthClient {

    /**
     * {@code instagram_content_publish}/{@code pages_read_engagement} are what {@link
     * com.triasoft.socialconnect.client.impl.InstagramGraphClient} actually uses against a real
     * token (confirmed, not the newer {@code instagram_business_content_publish} naming).
     */
    private static final String SCOPES = "pages_show_list,pages_read_engagement,instagram_basic,instagram_content_publish";

    private final WebClient webClient;
    private final MetaProperties metaProperties;

    public MetaOAuthClient(WebClient.Builder webClientBuilder, MetaProperties metaProperties) {
        this.metaProperties = metaProperties;
        this.webClient = webClientBuilder.baseUrl("https://graph.facebook.com/" + metaProperties.graphApiVersion()).build();
    }

    public record TokenExchangeResult(String accessToken, long expiresInSeconds) {
    }

    public record PageOption(String pageId, String pageName, String pageAccessToken, String igBusinessAccountId) {
    }

    /** The URL garage-api redirects the admin's browser to — Meta's own hosted consent dialog, not ours. */
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString("https://www.facebook.com/" + metaProperties.graphApiVersion() + "/dialog/oauth")
                .queryParam("client_id", metaProperties.appId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("scope", URLEncoder.encode(SCOPES, StandardCharsets.UTF_8))
                .build(true)
                .toUriString();
    }

    public TokenExchangeResult exchangeCodeForUserToken(String code) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/oauth/access_token")
                        .queryParam("client_id", metaProperties.appId())
                        .queryParam("client_secret", metaProperties.appSecret())
                        .queryParam("redirect_uri", redirectUri())
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::logAndMapError)
                .bodyToMono(Map.class)
                .block();
        return toTokenResult(response, "code-for-token exchange");
    }

    public TokenExchangeResult exchangeForLongLivedToken(String shortLivedUserToken) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", metaProperties.appId())
                        .queryParam("client_secret", metaProperties.appSecret())
                        .queryParam("fb_exchange_token", shortLivedUserToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::logAndMapError)
                .bodyToMono(Map.class)
                .block();
        return toTokenResult(response, "long-lived token exchange");
    }

    @SuppressWarnings("unchecked")
    public List<PageOption> listPages(String longLivedUserToken) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/me/accounts")
                        .queryParam("fields", "id,name,access_token,instagram_business_account")
                        .queryParam("access_token", longLivedUserToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::logAndMapError)
                .bodyToMono(Map.class)
                .block();
        if (response == null || !(response.get("data") instanceof List<?> data)) {
            return List.of();
        }
        return data.stream()
                .map(row -> (Map<String, Object>) row)
                .map(row -> {
                    Object igAccount = row.get("instagram_business_account");
                    String igId = igAccount instanceof Map<?, ?> igMap ? String.valueOf(igMap.get("id")) : null;
                    return new PageOption(
                            String.valueOf(row.get("id")),
                            String.valueOf(row.get("name")),
                            String.valueOf(row.get("access_token")),
                            igId);
                })
                .toList();
    }

    private String redirectUri() {
        return metaProperties.oauthRedirectBaseUrl() + "/v1/accounts/oauth/callback";
    }

    @SuppressWarnings("unchecked")
    private TokenExchangeResult toTokenResult(Map<String, Object> response, String step) {
        if (response == null || response.get("access_token") == null) {
            throw ApiException.badRequest("Meta " + step + " returned no access_token");
        }
        Object expiresIn = response.get("expires_in");
        long expiresInSeconds = expiresIn instanceof Number number ? number.longValue() : 0L;
        return new TokenExchangeResult(String.valueOf(response.get("access_token")), expiresInSeconds);
    }

    private reactor.core.publisher.Mono<? extends Throwable> logAndMapError(org.springframework.web.reactive.function.client.ClientResponse clientResponse) {
        return clientResponse.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> {
                    log.warn(":: MetaOAuthClient - Meta OAuth/Graph call failed :: {}", body);
                    return reactor.core.publisher.Mono.error(ApiException.badRequest("Meta rejected the request: " + body));
                });
    }
}
