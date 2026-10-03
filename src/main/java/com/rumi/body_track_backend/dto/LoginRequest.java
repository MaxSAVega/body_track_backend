package com.rumi.body_track_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 254, message = "El email es demasiado largo")
    private String email;

    /**
     * No minimum length: rejecting a short password here would confirm that the
     * account exists and is old, which is an enumeration signal.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 128, message = "La contraseña es demasiado larga")
    private String password;
}