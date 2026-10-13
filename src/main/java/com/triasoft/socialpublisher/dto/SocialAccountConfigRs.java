package com.triasoft.socialpublisher.dto;

import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** Never carries the Page access token itself — write-only via {@link SocialAccountConfigRq}. */
@Data
@Builder
public class SocialAccountConfigRs {
    private Long id;
    private String accountRef;
    private SocialPlatform platform;
    private String igBusinessAccountId;
    private String facebookPageId;
    private Instant tokenExpiresAt;
    private Instant tokenLastRefreshedAt;
    private boolean active;

    public static SocialAccountConfigRs from(SocialAccountConfig config) {
        return SocialAccountConfigRs.builder()
                .id(config.getId())
                .accountRef(config.getAccountRef())
                .platform(config.getPlatform())
                .igBusinessAccountId(config.getIgBusinessAccountId())
                .facebookPageId(config.getFacebookPageId())
                .tokenExpiresAt(config.getTokenExpiresAt())
                .tokenLastRefreshedAt(config.getTokenLastRefreshedAt())
                .active(config.isActive())
                .build();
    }
}
