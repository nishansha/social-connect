package com.triasoft.socialconnect.service;

import com.triasoft.socialconnect.dto.SocialAccountConfigRq;
import com.triasoft.socialconnect.dto.SocialAccountConfigRs;
import com.triasoft.socialconnect.entity.SocialAccountConfig;
import com.triasoft.socialconnect.entity.SocialClient;
import com.triasoft.socialconnect.exception.ApiException;
import com.triasoft.socialconnect.repository.SocialAccountConfigRepository;
import com.triasoft.socialconnect.security.TokenCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SocialAccountConfigService {

    private final SocialAccountConfigRepository repository;
    private final TokenCipher tokenCipher;

    public List<SocialAccountConfigRs> list(SocialClient client, String accountRef) {
        List<SocialAccountConfig> configs = accountRef != null
                ? repository.findBySocialClientIdAndAccountRef(client.getId(), accountRef)
                : repository.findBySocialClientId(client.getId());
        return configs.stream().map(SocialAccountConfigRs::from).toList();
    }

    @Transactional
    public SocialAccountConfigRs create(SocialClient client, SocialAccountConfigRq request) {
        repository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(
                        client.getId(), request.getAccountRef(), request.getPlatform())
                .ifPresent(existing -> {
                    throw ApiException.conflict("An active " + request.getPlatform() + " account is already configured for this tenant — update it instead");
                });

        SocialAccountConfig config = new SocialAccountConfig();
        config.setSocialClientId(client.getId());
        config.setAccountRef(request.getAccountRef());
        applyRequest(config, request);
        return SocialAccountConfigRs.from(repository.save(config));
    }

    @Transactional
    public SocialAccountConfigRs update(SocialClient client, Long id, SocialAccountConfigRq request) {
        SocialAccountConfig config = loadOwned(client, id);
        applyRequest(config, request);
        return SocialAccountConfigRs.from(repository.save(config));
    }

    @Transactional
    public void deactivate(SocialClient client, Long id) {
        SocialAccountConfig config = loadOwned(client, id);
        config.setActive(false);
        repository.save(config);
    }

    private void applyRequest(SocialAccountConfig config, SocialAccountConfigRq request) {
        config.setPlatform(request.getPlatform());
        config.setIgBusinessAccountId(request.getIgBusinessAccountId());
        config.setFacebookPageId(request.getFacebookPageId());
        config.setTokenExpiresAt(request.getTokenExpiresAt());
        config.setActive(request.isActive());
        if (request.getPageAccessToken() != null && !request.getPageAccessToken().isBlank()) {
            config.setPageAccessTokenEncrypted(tokenCipher.encrypt(request.getPageAccessToken()));
            config.setTokenLastRefreshedAt(Instant.now());
        }
    }

    private SocialAccountConfig loadOwned(SocialClient client, Long id) {
        SocialAccountConfig config = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Account config not found"));
        if (!config.getSocialClientId().equals(client.getId())) {
            throw ApiException.notFound("Account config not found");
        }
        return config;
    }
}
