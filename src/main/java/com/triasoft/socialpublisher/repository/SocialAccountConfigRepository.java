package com.triasoft.socialpublisher.repository;

import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialAccountConfigRepository extends JpaRepository<SocialAccountConfig, Long> {
    List<SocialAccountConfig> findBySocialClientId(Long socialClientId);

    List<SocialAccountConfig> findBySocialClientIdAndAccountRef(Long socialClientId, String accountRef);

    Optional<SocialAccountConfig> findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(
            Long socialClientId, String accountRef, SocialPlatform platform);
}
