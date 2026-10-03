package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Commits security revocations in their own transaction.
 *
 * <p>This exists as a separate bean for one specific reason. Reuse detection works
 * by revoking a token family and <em>then</em> rejecting the request, and that
 * rejection propagates as an exception. If the revocation shared the transaction
 * of the failing request, the rollback would discard it: the family would stay
 * live, the warning would be logged, and the attacker's stolen token would keep
 * working. The failure has to undo the request while the response to the theft
 * survives.
 *
 * <p>A separate class is required because Spring's {@code @Transactional} works
 * through a proxy: a call from one method of a bean to another method of the same
 * bean bypasses it, and the propagation setting would be silently ignored.
 */
@Component
public class RefreshTokenRevoker {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenRevoker.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * Revokes every live token in a family, committing regardless of whether the
     * calling request is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamilyForReuse(String familyId, Long userId) {
        int revoked = refreshTokenRepository.revokeFamily(
                familyId, LocalDateTime.now(), "reuse");
        log.warn("refresh_token_reuse_detected user_id={} family={} revoked_siblings={}",
                userId, familyId, revoked);
        return revoked;
    }

    /** Revokes every live token for a user, used on logout. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForLogout(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(
                userId, LocalDateTime.now(), "logout");
        log.info("logout_revoked_tokens user_id={} count={}", userId, revoked);
        return revoked;
    }
}