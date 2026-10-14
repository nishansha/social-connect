package com.triasoft.socialconnect.controller;

import com.triasoft.socialconnect.dto.OAuthPendingSelectionRs;
import com.triasoft.socialconnect.dto.OAuthSelectRq;
import com.triasoft.socialconnect.dto.OAuthStartRq;
import com.triasoft.socialconnect.dto.OAuthStartRs;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.service.MetaOAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** The "Connect Instagram/Facebook" OAuth handshake — see {@link MetaOAuthService} for the full flow. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/accounts/oauth")
public class MetaOAuthController {

    private final MetaOAuthService service;

    @PostMapping("/start")
    public ResponseEntity<OAuthStartRs> start(@AuthenticationPrincipal SocialClient client, @Valid @RequestBody OAuthStartRq request) {
        return ResponseEntity.ok(service.start(client, request));
    }

    /**
     * Public (no Bearer auth) — this is Meta redirecting the admin's own browser here, which can't
     * carry our service-to-service token. Correlated purely by {@code state}; see {@link
     * MetaOAuthService#handleCallback}.
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String redirectTo = service.handleCallback(code, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectTo))
                .build();
    }

    @GetMapping("/pending/{state}")
    public ResponseEntity<OAuthPendingSelectionRs> listPendingPages(@AuthenticationPrincipal SocialClient client, @PathVariable String state) {
        return ResponseEntity.ok(service.listPendingPages(client, state));
    }

    @PostMapping("/select")
    public ResponseEntity<Void> select(@AuthenticationPrincipal SocialClient client, @Valid @RequestBody OAuthSelectRq request) {
        service.select(client, request.getStateToken(), request.getPageId());
        return ResponseEntity.noContent().build();
    }
}
