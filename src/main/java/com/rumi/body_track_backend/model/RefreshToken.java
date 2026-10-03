package com.rumi.body_track_backend.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A refresh token, stored as an opaque random value.
 *
 * <p>Only the SHA-256 hash is persisted. Each row also carries the family it
 * belongs to, so that presenting an already-rotated token can be recognised as
 * theft and invalidate every sibling session rather than just the one row.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_token_user", columnList = "user_id")
})
@Data
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 of the raw token. The raw token is never stored. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Groups every token descended from one login, for theft-response mass revocation. */
    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /** Why the row was revoked; {@code null} while the token is live. */
    @Column(name = "revoked_reason", length = 32)
    private String revokedReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isLive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}