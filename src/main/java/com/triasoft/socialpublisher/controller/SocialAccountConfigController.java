package com.triasoft.socialpublisher.controller;

import com.triasoft.socialpublisher.dto.SocialAccountConfigRq;
import com.triasoft.socialpublisher.dto.SocialAccountConfigRs;
import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.service.SocialAccountConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** A caller only ever sees/edits its own account configuration — scoped by the authenticated {@link SocialClient}. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/accounts")
public class SocialAccountConfigController {

    private final SocialAccountConfigService service;

    @GetMapping
    public ResponseEntity<List<SocialAccountConfigRs>> list(
            @AuthenticationPrincipal SocialClient client,
            @RequestParam(required = false) String accountRef) {
        return ResponseEntity.ok(service.list(client, accountRef));
    }

    @PostMapping
    public ResponseEntity<SocialAccountConfigRs> create(@AuthenticationPrincipal SocialClient client, @Valid @RequestBody SocialAccountConfigRq request) {
        return ResponseEntity.ok(service.create(client, request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SocialAccountConfigRs> update(@AuthenticationPrincipal SocialClient client, @PathVariable Long id, @Valid @RequestBody SocialAccountConfigRq request) {
        return ResponseEntity.ok(service.update(client, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal SocialClient client, @PathVariable Long id) {
        service.deactivate(client, id);
        return ResponseEntity.noContent().build();
    }
}
