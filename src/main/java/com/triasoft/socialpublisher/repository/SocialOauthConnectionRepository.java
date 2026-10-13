package com.triasoft.socialpublisher.repository;

import com.triasoft.socialpublisher.entity.SocialOauthConnection;
import com.triasoft.socialpublisher.enums.SocialOauthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SocialOauthConnectionRepository extends JpaRepository<SocialOauthConnection, Long> {
    Optional<SocialOauthConnection> findByStateToken(String stateToken);

    // Scoped to PAGE_SELECTION_REQUIRED only — the one status holding a stashed encrypted
    // long-lived user token. A plain STARTED row past its few-minute expiresAt is harmless (no
    // token stored yet) and is left for handleCallback's own expiry check to reject by lookup
    // instead — sweeping every status here would delete a row mid-handshake if the admin simply
    // took longer than expiresAt on Facebook's own consent screens.
    //
    // Explicit @Query bulk delete, not a derived deleteByXxx — the derived form selects-then-
    // removes each row individually, and that path doesn't reliably populate a primitive `long`
    // return (observed: AopInvocationException, "Null return value from advice does not match
    // primitive return type"). A single DELETE...WHERE statement via executeUpdate() doesn't have
    // that problem and is one round-trip instead of N+1.
    @Modifying
    @Query("delete from SocialOauthConnection c where c.status = :status and c.expiresAt < :cutoff")
    int deleteByStatusAndExpiresAtBefore(@Param("status") SocialOauthStatus status, @Param("cutoff") Instant cutoff);
}
