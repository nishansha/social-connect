package com.triasoft.socialconnect.dto;

import com.triasoft.socialconnect.entity.SocialPost;
import com.triasoft.socialconnect.enums.SocialPlatform;
import com.triasoft.socialconnect.enums.SocialPostType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response body for {@code POST /v1/posts}, {@code GET /v1/posts/{id}}, {@code POST
 * /v1/posts/{id}/approve}, and the webhook callback payload — same shape in all four places, per
 * the contract pinned with garage-api. {@code id} is a string on purpose: callers must treat it
 * as opaque, never parse it.
 */
@Data
@Builder(toBuilder = true)
public class SocialPostRs {
    private String id;
    private SocialPlatform platform;
    private SocialPostType postType;
    private String inventoryRef;
    private String status;
    private String failureStage;
    private String providerErrorCode;
    private String providerErrorMessage;
    private String instagramMediaId;
    private String videoPreviewUrl;
    private Instant approvedAt;
    private Instant publishedAt;

    public static SocialPostRs from(SocialPost post) {
        return SocialPostRs.builder()
                .id(String.valueOf(post.getId()))
                .platform(post.getPlatform())
                .postType(post.getPostType())
                .inventoryRef(post.getInventoryRef())
                .status(post.getStatus().name())
                .failureStage(post.getFailureStage() != null ? post.getFailureStage().name() : null)
                .providerErrorCode(post.getProviderErrorCode())
                .providerErrorMessage(post.getProviderErrorMessage())
                .instagramMediaId(post.getInstagramMediaId())
                .approvedAt(post.getApprovedAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }
}
