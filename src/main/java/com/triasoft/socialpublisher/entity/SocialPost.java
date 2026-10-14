package com.triasoft.socialpublisher.entity;

import com.triasoft.socialpublisher.client.VideoGenClient;
import com.triasoft.socialpublisher.enums.SocialFailureStage;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.enums.SocialPostStatus;
import com.triasoft.socialpublisher.enums.SocialPostType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One row per post attempt. {@code inventoryRef} is an opaque string supplied by the caller —
 * this service never interprets it (doesn't know what "inventory" means to any given caller),
 * only echoes it back on status reads and the webhook callback so the caller can correlate.
 */
@Getter
@Setter
@Entity
@Table(name = "social_post")
public class SocialPost extends BaseEntity {

    @Column(name = "social_client_id", nullable = false)
    private Long socialClientId;

    @Column(name = "account_ref", nullable = false, length = 200)
    private String accountRef;

    @Column(name = "inventory_ref", nullable = false, length = 200)
    private String inventoryRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SocialPlatform platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "post_type", nullable = false, length = 10)
    private SocialPostType postType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SocialPostStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "social_post_photo_url", joinColumns = @JoinColumn(name = "social_post_id"))
    @OrderColumn(name = "sequence_order")
    @Column(name = "photo_url", length = 2000)
    private List<String> sourcePhotoUrls = new ArrayList<>();

    @Column(name = "prompt", columnDefinition = "text")
    private String prompt;

    /** Decided once from the first source photo (see SocialPostService.detectOrientation); persisted because advanceClip needs it again later, off the request thread. */
    @Enumerated(EnumType.STRING)
    @Column(name = "orientation", length = 20)
    private VideoGenClient.VideoOrientation orientation;

    @Column(name = "caption", length = 2000)
    private String caption;

    @Column(name = "callback_url", length = 1000)
    private String callbackUrl;

    @Column(name = "media_container_id", length = 100)
    private String mediaContainerId;

    @Column(name = "instagram_media_id", length = 100)
    private String instagramMediaId;

    @Column(name = "generated_video_object_key", length = 500)
    private String generatedVideoObjectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_stage", length = 30)
    private SocialFailureStage failureStage;

    @Column(name = "provider_error_code", length = 100)
    private String providerErrorCode;

    @Column(name = "provider_error_message", length = 2000)
    private String providerErrorMessage;

    @Column(name = "container_poll_attempts", nullable = false)
    private int containerPollAttempts = 0;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
