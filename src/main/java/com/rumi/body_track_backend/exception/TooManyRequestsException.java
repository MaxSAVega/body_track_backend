package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The caller exceeded a rate limit. The message never states the limit itself,
 * which would let an attacker calibrate the threshold.
 */
public class TooManyRequestsException extends ApiException {

    public TooManyRequestsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate_limited",
                "Demasiados intentos. Inténtalo de nuevo más tarde.");
    }
}