package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies <b>access</b> tokens only.
 *
 * <p>Two invariants this class exists to enforce:
 * <ol>
 *   <li>The subject is the numeric user id, never the email. The email is mutable
 *       (it can be changed from the profile screen), so using it as the
 *       authorization key meant a profile update silently re-keyed the identity.
 *       The email is still carried as a non-authoritative claim for logging.</li>
 *   <li>{@code type} is verified on every read. A refresh token presented as a
 *       bearer token previously authenticated successfully, which promoted a
 *       seven-day credential into a full session credential.</li>
 * </ol>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long accessExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessExpirationMs) {
        this.signingKey = buildSigningKey(secret);
        this.accessExpirationMs = accessExpirationMs;
    }

    /**
     * Refuses to start without a strong secret rather than silently falling back to
     * a checked-in development key. A 32-byte random value is the minimum that
     * yields an HS256 key; anything shorter would already have been rejected by
     * {@link Keys}, so the check here exists to produce a comprehensible message
     * and to reject the well-known placeholder values explicitly.
     */
    private static SecretKey buildSigningKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret no configurado. Defina la variable de entorno JWT_SECRET "
                            + "(generar con: openssl rand -base64 32).");
        }

        String trimmed = secret.trim();
        byte[] keyBytes = decode(trimmed);

        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret demasiado corto: " + keyBytes.length + " bytes, se requieren al menos "
                            + MIN_SECRET_BYTES + ". Regenere con: openssl rand -base64 32");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static byte[] decode(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException notBase64) {
            // Allow an operator to supply a raw passphrase instead of base64.
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EMAIL, email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature, expiry and token type, then returns the caller's id.
     *
     * @throws UnauthorizedException for every failure mode, with one message, so the
     *                              caller cannot distinguish them.
     */
    public Long extractUserId(String token) {
        Claims claims = parse(token);

        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            log.warn("token_type_rejected type={}", claims.get(CLAIM_TYPE));
            throw new UnauthorizedException();
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException();
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException notANumericSubject) {
            log.warn("token_subject_not_numeric subject_hash={}", Integer.toHexString(subject.hashCode()));
            throw new UnauthorizedException();
        }
    }

    public String extractEmailClaim(String token) {
        String email = parse(token).get(CLAIM_EMAIL, String.class);
        return email == null ? "" : email;
    }

    private Claims parse(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException();
        }
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getBody();
        } catch (ExpiredJwtException expired) {
            log.info("token_expired");
            throw new UnauthorizedException();
        } catch (JwtException | IllegalArgumentException invalid) {
            log.info("token_invalid");
            throw new UnauthorizedException();
        }
    }

    /**
     * Hashes an opaque refresh token for storage. The raw value never touches the
     * database, so a dump of {@code refresh_tokens} cannot be replayed.
     */
    public static String hashOpaqueToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 no disponible", impossible);
        }
    }
}