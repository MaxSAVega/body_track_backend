package com.rumi.body_track_backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for every error that is safe to report to an API client.
 *
 * <p>The message carried here is the ONLY text ever serialised to the client.
 * Anything derived from an exception message, SQL, or an internal identifier must
 * never be placed in it; log it server-side instead.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String safeMessage) {
        super(safeMessage);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}