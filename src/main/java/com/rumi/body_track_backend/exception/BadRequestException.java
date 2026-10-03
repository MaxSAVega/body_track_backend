package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is syntactically valid but semantically rejected, e.g. an upload
 * whose bytes are not a supported image.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String safeMessage, String code) {
        super(HttpStatus.BAD_REQUEST, code, safeMessage);
    }
}