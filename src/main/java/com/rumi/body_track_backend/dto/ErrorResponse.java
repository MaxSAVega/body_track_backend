package com.rumi.body_track_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape returned by every failing request, so clients never have
 * to special-case a different body from a different code path.
 */
@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** Stable, machine-readable identifier for the client to branch on. */
    private String code;

    /** Human-readable, already-safe text. Never contains SQL or internal detail. */
    private String message;

    /** Field-level validation failures, present only for 400 responses. */
    private Map<String, String> fieldErrors;

    private Instant timestamp;

    /** Path of the failing request, echoed so logs and client reports line up. */
    private String path;
}