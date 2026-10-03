package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The request conflicts with existing state.
 *
 * <p>Used for registration and profile updates where the conflict would otherwise
 * reveal that a particular email address is already registered. Pair with a
 * generic client-facing message; the specific resource stays in the log.
 */
public class ConflictException extends ApiException {

    public ConflictException(String safeMessage, String code) {
        super(HttpStatus.CONFLICT, code, safeMessage);
    }
}