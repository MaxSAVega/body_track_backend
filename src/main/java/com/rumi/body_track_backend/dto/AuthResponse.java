package com.rumi.body_track_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String refreshToken;

    /**
     * Additive. The client does not need to read it, but sending it means a future
     * release can move off email-keyed lookups without another round of changes.
     */
    private Long userId;

    private String gender;
    private String name;
    private String email;
    private String dni;
    private Double height;
    private Double weight;
}