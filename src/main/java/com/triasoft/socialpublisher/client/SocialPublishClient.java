package com.triasoft.socialpublisher.client;

import com.triasoft.socialpublisher.entity.SocialAccountConfig;

import java.util.List;

/**
 * Meta-generic publish contract — {@code InstagramGraphClient} is the only implementation now;
 * a phase-2 {@code FacebookPageClient} implements the same interface, so {@code SocialPostService}
 * needs no changes when Facebook support is added, just a platform-keyed bean lookup.
 */
public interface SocialPublishClient {

    /**
     * One image publishes directly; two or more publish as an Instagram carousel (the
     * implementation decides which, based on {@code imageUrls.size()}) — callers never need to
     * know which Graph API shape backs either case.
     */
    String createPhotoContainer(SocialAccountConfig account, List<String> imageUrls, String caption);

    String createVideoContainer(SocialAccountConfig account, String videoUrl, String caption);

    ContainerPollResult pollContainer(SocialAccountConfig account, String containerId);

    String publish(SocialAccountConfig account, String containerId);

    enum ContainerStatus {
        IN_PROGRESS,
        FINISHED,
        ERROR,
        EXPIRED
    }

    /** {@code detail} is Meta's own human-readable reason — only meaningful (non-null) once {@code status} is ERROR or EXPIRED; a generic "entered ERROR" otherwise tells staff nothing actionable. */
    record ContainerPollResult(ContainerStatus status, String detail) {
    }
}
