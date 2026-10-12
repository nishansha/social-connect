package com.triasoft.socialpublisher.repository;

import com.triasoft.socialpublisher.entity.SocialClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialClientRepository extends JpaRepository<SocialClient, Long> {
    Optional<SocialClient> findByClientIdAndActiveTrue(String clientId);
}
