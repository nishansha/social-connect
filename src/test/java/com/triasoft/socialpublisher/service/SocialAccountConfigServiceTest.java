package com.triasoft.socialpublisher.service;

import com.triasoft.socialpublisher.config.EncryptionProperties;
import com.triasoft.socialpublisher.dto.SocialAccountConfigRq;
import com.triasoft.socialpublisher.dto.SocialAccountConfigRs;
import com.triasoft.socialpublisher.entity.SocialAccountConfig;
import com.triasoft.socialpublisher.entity.SocialClient;
import com.triasoft.socialpublisher.enums.SocialPlatform;
import com.triasoft.socialpublisher.exception.ApiException;
import com.triasoft.socialpublisher.repository.SocialAccountConfigRepository;
import com.triasoft.socialpublisher.security.TokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocialAccountConfigServiceTest {

    @Mock
    private SocialAccountConfigRepository repository;

    private TokenCipher tokenCipher;
    private SocialAccountConfigService service;
    private SocialClient client;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        tokenCipher = new TokenCipher(new EncryptionProperties(Base64.getEncoder().encodeToString(key)));
        service = new SocialAccountConfigService(repository, tokenCipher);

        client = new SocialClient();
        client.setId(1L);
    }

    @Test
    void create_encryptsTokenAndNeverExposesItInResponse() {
        when(repository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SocialAccountConfigRq rq = new SocialAccountConfigRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);
        rq.setIgBusinessAccountId("ig-biz-1");
        rq.setPageAccessToken("EAAG-plaintext-page-token");

        SocialAccountConfigRs result = service.create(client, rq);

        assertThat(result.getIgBusinessAccountId()).isEqualTo("ig-biz-1");
        // The response DTO has no token field at all — compile-time guarantee it can't leak.
        ArgumentCaptor<SocialAccountConfig> captor = ArgumentCaptor.forClass(SocialAccountConfig.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        String stored = captor.getValue().getPageAccessTokenEncrypted();
        assertThat(stored).isNotEqualTo("EAAG-plaintext-page-token");
        assertThat(tokenCipher.decrypt(stored)).isEqualTo("EAAG-plaintext-page-token");
    }

    @Test
    void create_rejectsDuplicateActivePlatformConfigForSameTenant() {
        when(repository.findBySocialClientIdAndAccountRefAndPlatformAndActiveTrue(1L, "dealer-1", SocialPlatform.INSTAGRAM))
                .thenReturn(Optional.of(new SocialAccountConfig()));

        SocialAccountConfigRq rq = new SocialAccountConfigRq();
        rq.setAccountRef("dealer-1");
        rq.setPlatform(SocialPlatform.INSTAGRAM);

        assertThatThrownBy(() -> service.create(client, rq)).isInstanceOf(ApiException.class);
    }

    @Test
    void update_rejectsConfigBelongingToAnotherClient() {
        SocialAccountConfig other = new SocialAccountConfig();
        other.setId(5L);
        other.setSocialClientId(999L);
        when(repository.findById(5L)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(client, 5L, new SocialAccountConfigRq()))
                .isInstanceOf(ApiException.class);
    }
}
