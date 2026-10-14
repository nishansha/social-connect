package com.triasoft.socialconnect.controller;

import com.triasoft.socialconnect.dto.SocialPostRs;
import com.triasoft.socialconnect.dto.SocialPostSubmitRq;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.enums.SocialPostType;
import com.triasoft.socialconnect.service.SocialPostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The pinned v1 contract — see the plan / garage-api's {@code SocialPublisherClient} for the exact shapes this must keep matching. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/posts")
public class SocialPostController {

    private final SocialPostService socialPostService;

    @PostMapping
    public ResponseEntity<SocialPostRs> submit(@AuthenticationPrincipal SocialClient client, @Valid @RequestBody SocialPostSubmitRq request) {
        SocialPostRs response = request.getPostType() == SocialPostType.PHOTO
                ? socialPostService.submitPhoto(client, request)
                : socialPostService.submitVideo(client, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SocialPostRs> getStatus(@AuthenticationPrincipal SocialClient client, @PathVariable Long id) {
        return ResponseEntity.ok(socialPostService.getStatus(client, id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<SocialPostRs> approve(@AuthenticationPrincipal SocialClient client, @PathVariable Long id) {
        return ResponseEntity.ok(socialPostService.approve(client, id));
    }
}
