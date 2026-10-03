package com.rumi.body_track_backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Profile update. Deliberately carries no id or email-of-target field: the subject
 * comes from the verified token, so there is no field a client could use to
 * retarget the update at someone else's account.
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 120, message = "El nombre es demasiado largo")
    private String name;

    @Email(message = "Formato de email inválido")
    @Size(max = 254, message = "El email es demasiado largo")
    private String email;

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