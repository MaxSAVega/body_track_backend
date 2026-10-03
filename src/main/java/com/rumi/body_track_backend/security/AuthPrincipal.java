package com.rumi.body_track_backend.security;

/**
 * The authenticated caller, resolved once by {@code JwtAuthFilter} and read by
 * controllers through {@code @AuthenticationPrincipal}.
 *
 * <p>Holding the numeric id here is the point of the change: every ownership check
 * downstream is a query predicate on {@code userId}, so no endpoint can be
 * tricked into trusting an id that arrived in the request body.
 */
public record AuthPrincipal(Long userId, String email) {
}