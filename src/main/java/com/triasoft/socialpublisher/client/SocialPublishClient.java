package com.triasoft.socialpublisher.client;

import com.triasoft.socialpublisher.entity.SocialAccountConfig;

/**
 * Meta-generic publish contract — {@code InstagramGraphClient} is the only implementation now;
 * a phase-2 {@code FacebookPageClient} implements the same interface, so {@code SocialPostService}
 * needs no changes when Facebook support is added, just a platform-keyed bean lookup.
 */
public interface SocialPublishClient {

    String createPhotoContainer(SocialAccountConfig account, String imageUrl, String caption);

    String createVideoContainer(SocialAccountConfig account, String videoUrl, String caption);

    ContainerStatus pollContainer(SocialAccountConfig account, String containerId);

    String publish(SocialAccountConfig account, String containerId);

    enum ContainerStatus {
        IN_PROGRESS,
        FINISHED,
        ERROR,
        EXPIRED
    }
}
