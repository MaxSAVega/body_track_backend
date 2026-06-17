package com.rumi.body_track_backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secretBase64,
            @Value("${jwt.access-token-expiration}") long accessExpirationMs,
            @Value("${jwt.refresh-token-expiration}") long refreshExpirationMs
    ) {
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64.trim());
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateToken(String email) {
        return buildToken(email, "access", accessExpirationMs);
    }

    public String generateRefreshToken(String email) {
        return buildToken(email, "refresh", refreshExpirationMs);
    }

    private String buildToken(String email, String type, long expirationMs) {
        return Jwts.builder()
                .subject(email)
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            return "access".equals(claims.get("type")) && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = getClaims(token);
            return "refresh".equals(claims.get("type")) && !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getBody();
    }
}
