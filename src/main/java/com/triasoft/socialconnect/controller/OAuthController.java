package com.triasoft.socialconnect.controller;

import com.triasoft.socialconnect.dto.TokenRq;
import com.triasoft.socialconnect.dto.TokenRs;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.exception.ApiException;
import com.triasoft.socialconnect.repository.SocialClientRepository;
import com.triasoft.socialconnect.security.ClientTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/oauth")
public class OAuthController {

    private static final String GRANT_TYPE = "client_credentials";

    private final SocialClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientTokenService clientTokenService;

    @PostMapping("/token")
    public ResponseEntity<TokenRs> token(@RequestBody TokenRq request) {
        if (!GRANT_TYPE.equals(request.getGrantType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported grant_type, expected client_credentials");
        }
        SocialClient client = clientRepository.findByClientIdAndActiveTrue(request.getClientId())
                .filter(c -> passwordEncoder.matches(request.getClientSecret(), c.getClientSecretHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid client credentials"));

        String token = clientTokenService.issueToken(client);
        return ResponseEntity.ok(TokenRs.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(clientTokenService.expirationSeconds())
                .build());
    }
}
