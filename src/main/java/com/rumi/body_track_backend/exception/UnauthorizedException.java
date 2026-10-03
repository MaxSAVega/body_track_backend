package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller could not be authenticated, or presented credentials that are no
 * longer acceptable (missing, malformed, expired, revoked).
 *
 * <p>The message is intentionally identical across every cause so that it cannot
 * be used as an oracle.
 */
public class UnauthorizedException extends ApiException {

    public static final String GENERIC_MESSAGE = "Credenciales inválidas o sesión expirada";

    public UnauthorizedException() {
        this(GENERIC_MESSAGE);
    }

    public UnauthorizedException(String safeMessage) {
        super(HttpStatus.UNAUTHORIZED, "unauthorized", safeMessage);
    }
}