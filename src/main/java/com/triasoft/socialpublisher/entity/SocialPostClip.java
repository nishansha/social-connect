package com.triasoft.socialpublisher.entity;

import com.triasoft.socialpublisher.enums.SocialClipStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One row per source photo on a VIDEO post — each is an independent image-to-video call. */
@Getter
@Setter
@Entity
@Table(name = "social_post_clip")
public class SocialPostClip extends BaseEntity {

    @Column(name = "social_post_id", nullable = false)
    private Long socialPostId;

    @Column(name = "source_photo_url", nullable = false, length = 2000)
    private String sourcePhotoUrl;

    @Column(name = "sequence_order", nullable = false)
    private int sequenceOrder;

    @Column(name = "video_gen_provider", length = 50)
    private String videoGenProvider;

    @Column(name = "video_gen_job_id", length = 100)
    private String videoGenJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SocialClipStatus status = SocialClipStatus.PENDING;

    @Column(name = "clip_object_key", length = 500)
    private String clipObjectKey;

    @Column(name = "poll_attempts", nullable = false)
    private int pollAttempts = 0;
}
