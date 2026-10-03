package com.rumi.body_track_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class RegisterRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 120, message = "El nombre es demasiado largo")
    private String name;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Size(max = 254, message = "El email es demasiado largo")
    private String email;

    /**
     * Minimum length is enforced here; a short password reaching BCrypt is both a
     * weak-credential problem and, on the login path, an amplification vector.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
    private String password;

    @Past(message = "La fecha de nacimiento debe ser pasada")
    private LocalDate birthDate;

    @Size(max = 40, message = "Valor demasiado largo")
    private String gender;

    @Pattern(regexp = "^$|^[0-9 +()-]{6,24}$", message = "Formato de teléfono inválido")
    @Size(max = 24, message = "El teléfono es demasiado largo")
    private String phone;

    @Pattern(regexp = "^$|^[0-9]{8,15}$", message = "Formato de documento inválido")
    @Size(max = 15, message = "El documento es demasiado largo")
    private String dni;

    @DecimalMin(value = "0.5", message = "Altura fuera de rango")
    @DecimalMax(value = "3.0", message = "Altura fuera de rango")
    private Double height;

    @DecimalMin(value = "1.0", message = "Peso fuera de rango")
    @DecimalMax(value = "400.0", message = "Peso fuera de rango")
    private Double weight;
}