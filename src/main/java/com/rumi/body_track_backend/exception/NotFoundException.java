package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The requested resource does not exist <em>or</em> is not visible to the caller.
 *
 * <p>Both cases deliberately collapse into 404 so that ownership cannot be probed
 * by comparing a 403 against a 404. Callers must never pass a user-controlled id
 * straight into the message.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource) {
        super(HttpStatus.NOT_FOUND, "not_found", resource + " no encontrada");
    }
}