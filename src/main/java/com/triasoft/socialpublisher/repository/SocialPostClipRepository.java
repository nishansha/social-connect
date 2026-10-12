package com.triasoft.socialpublisher.repository;

import com.triasoft.socialpublisher.entity.SocialPostClip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SocialPostClipRepository extends JpaRepository<SocialPostClip, Long> {
    List<SocialPostClip> findBySocialPostIdOrderBySequenceOrder(Long socialPostId);
}
