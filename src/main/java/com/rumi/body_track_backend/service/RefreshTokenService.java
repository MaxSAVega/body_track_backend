package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.exception.UnauthorizedException;
import com.rumi.body_track_backend.model.RefreshToken;
import com.rumi.body_track_backend.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>The token is 256 bits of {@link SecureRandom}, Base64url-encoded, and only
 * its SHA-256 is persisted. Rotation happens on every single use: the presented
 * row is revoked and a fresh one is issued. That makes a refresh token strictly
 * single-use, which is what turns reuse into a detectable signal rather than an
 * undetectable compromise.
 *
 * <p>Reuse detection: if a token that has already been rotated is presented, it
 * is either a replay by an attacker or the legitimate client racing itself. Both
 * are indistinguishable, so the safe response is the same — revoke the whole
 * family and force a fresh login.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    private static final String REASON_ROTATED = "rotated";

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenRevoker revoker;
    private final long refreshExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenRevoker revoker,
            @Value("${jwt.refresh-token-expiration}") long refreshExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.revoker = revoker;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public record IssuedToken(String rawToken, Long userId, String familyId) {
    }

    /** Starts a new family for a login or registration. */
    @Transactional
    public IssuedToken issueNewFamily(Long userId) {
        return insert(userId, UUID.randomUUID().toString());
    }

    /** Continues an existing family, revoking the token that was just consumed. */
    @Transactional
    public IssuedToken rotate(RefreshToken consumed) {
        consumed.setRevokedAt(LocalDateTime.now());
        consumed.setRevokedReason(REASON_ROTATED);
        refreshTokenRepository.save(consumed);

        return insert(consumed.getUserId(), consumed.getFamilyId());
    }

    /**
     * Resolves a raw token to its row, enforcing rotation and reuse rules.
     *
     * @throws UnauthorizedException with a single generic message on every failure.
     */
    @Transactional
    public RefreshToken consume(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException();
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(JwtService.hashOpaqueToken(rawToken))
                .orElseThrow(UnauthorizedException::new);

        if (stored.getRevokedAt() != null) {
            // The token was already rotated or explicitly revoked. Treat as theft.
            // The revocation is delegated so it commits independently: this method
            // is about to throw, and a rollback of the caller's transaction would
            // otherwise undo the family revocation and leave the stolen token live.
            revoker.revokeFamilyForReuse(stored.getFamilyId(), stored.getUserId());
            throw new UnauthorizedException();
        }

        if (stored.isLive(LocalDateTime.now()) == false) {
            throw new UnauthorizedException();
        }

        return stored;
    }

    /** Real logout: no server-side session survives it. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        revoker.revokeAllForLogout(userId);
    }

    /**
     * Drops rows that expired long ago. Without this the table grows without
     * bound, since expiry alone does not delete anything.
     */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now().minusDays(30));
        if (removed > 0) {
            log.info("purged_expired_refresh_tokens count={}", removed);
        }
    }

    private IssuedToken insert(Long userId, String familyId) {
        String raw = generateRawToken();
        LocalDateTime now = LocalDateTime.now();

        RefreshToken row = new RefreshToken();
        row.setTokenHash(JwtService.hashOpaqueToken(raw));
        row.setUserId(userId);
        row.setFamilyId(familyId);
        row.setExpiresAt(now.plusNanos(refreshExpirationMs * 1_000_000L));
        row.setCreatedAt(now);

        refreshTokenRepository.save(row);
        return new IssuedToken(raw, userId, familyId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}