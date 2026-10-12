package com.triasoft.socialpublisher.service;

import com.triasoft.socialpublisher.dto.SocialPostRs;
import com.triasoft.socialpublisher.entity.SocialClient;

public interface WebhookDispatcher {

    /**
     * POSTs {@code payload} to {@code callbackUrl}, HMAC-SHA256-signed with the caller's own
     * client secret — the exact scheme garage-api's {@code SocialWebhookSignatureFilter}
     * verifies: headers {@code X-Social-Timestamp} (epoch seconds) and {@code X-Social-Signature}
     * (hex), signed content {@code timestamp + "." + rawBody}. Failures are logged and swallowed
     * — the caller's {@code GET /v1/posts/{id}} is the fallback if a webhook never arrives.
     */
    void dispatch(SocialClient client, String callbackUrl, SocialPostRs payload);
}
