package com.rumi.body_track_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank(message = "El token es obligatorio")
    @Size(max = 512, message = "Token inválido")
    private String refreshToken;
}