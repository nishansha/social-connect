package com.triasoft.socialconnect.repository;

import com.triasoft.socialconnect.entity.SocialPost;
import com.triasoft.socialconnect.enums.SocialPostStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
    Optional<SocialPost> findByIdAndSocialClientId(Long id, Long socialClientId);

    List<SocialPost> findByStatusIn(List<SocialPostStatus> statuses);
}
