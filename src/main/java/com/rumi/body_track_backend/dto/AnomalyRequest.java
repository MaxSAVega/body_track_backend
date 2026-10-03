package com.rumi.body_track_backend.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Body of a create or update anomaly.
 *
 * <p>{@code description} is free-text clinical notes and previously had no length
 * bound, so a single request could write an arbitrarily large row. The measurement
 * fields are ranged because unbounded doubles accepted {@code NaN}, negatives and
 * near-maximum values straight into the database.
 *
 * <p>There is no image or owner field: the owner is the authenticated caller and the
 * image arrives on its own endpoint.
 */
@Data
public class AnomalyRequest {

    @Size(max = 60, message = "Valor demasiado largo")
    private String type;

    @Size(max = 4000, message = "La descripción es demasiado larga")
    private String description;

    @Size(max = 120, message = "Valor demasiado largo")
    private String bodyPart;

    @Size(max = 60, message = "Valor demasiado largo")
    private String shape;

    @DecimalMin(value = "0.0", message = "Diámetro fuera de rango")
    @DecimalMax(value = "500.0", message = "Diámetro fuera de rango")
    private Double diameter1;

    @DecimalMin(value = "0.0", message = "Diámetro fuera de rango")
    @DecimalMax(value = "500.0", message = "Diámetro fuera de rango")
    private Double diameter2;

    @Min(value = 0, message = "Color fuera de rango")
    @Max(value = 4294967295L, message = "Color fuera de rango")
    private Long colorValue;

    private Boolean hurts;

    private Boolean hasChanged;

    @Size(max = 40, message = "Valor demasiado largo")
    private String status;

    @DecimalMin(value = "-1000.0", message = "Coordenada fuera de rango")
    @DecimalMax(value = "1000.0", message = "Coordenada fuera de rango")
    private Double x;

    @DecimalMin(value = "-1000.0", message = "Coordenada fuera de rango")
    @DecimalMax(value = "1000.0", message = "Coordenada fuera de rango")
    private Double y;

    @DecimalMin(value = "-1000.0", message = "Coordenada fuera de rango")
    @DecimalMax(value = "1000.0", message = "Coordenada fuera de rango")
    private Double z;

    @PastOrPresent(message = "La fecha de aparición no puede ser futura")
    private LocalDate appearanceDate;
}