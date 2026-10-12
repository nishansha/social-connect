package com.triasoft.socialpublisher.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triasoft.socialpublisher.dto.SocialPostRs;
import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.service.WebhookDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcherImpl implements WebhookDispatcher {

    private static final String TIMESTAMP_HEADER = "X-Social-Timestamp";
    private static final String SIGNATURE_HEADER = "X-Social-Signature";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public void dispatch(SocialClient client, String callbackUrl, SocialPostRs payload) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(payload);
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String signature = sign(client.getWebhookSecret(), timestamp, body);

            webClientBuilder.build().post()
                    .uri(callbackUrl)
                    .header(TIMESTAMP_HEADER, timestamp)
                    .header(SIGNATURE_HEADER, signature)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .doOnError(e -> log.warn(":: WebhookDispatcher - callback to {} failed: {} ::", callbackUrl, e.getMessage()))
                    .onErrorResume(e -> reactor.core.publisher.Mono.empty())
                    .subscribe();
        } catch (Exception e) {
            // Never let a webhook delivery failure break the caller's state transition — the
            // caller's GET /v1/posts/{id} is the fallback if this never arrives.
            log.warn(":: WebhookDispatcher - failed to dispatch to {}: {} ::", callbackUrl, e.getMessage());
        }
    }

    private String sign(String secret, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        byte[] signatureBytes = mac.doFinal(body);
        return HexFormat.of().formatHex(signatureBytes);
    }
}
