package com.rumi.body_track_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder
public class AnomalyResponse {
    private Long id;
    private String type;
    private String description;
    private String bodyPart;
    private String shape;
    private Double diameter1;
    private Double diameter2;
    private Long colorValue;
    private Boolean hurts;
    private Boolean hasChanged;
    private String status;
    private Double x;
    private Double y;
    private Double z;

    /**
     * Replaces the former {@code imagePath} field, which disclosed the internal file
     * naming scheme, the anomaly id and the upload timestamp. Clients should treat
     * this as a boolean and fetch bytes from {@code /anomalies/{id}/image}.
     */
    private Boolean hasImage;

    private LocalDate appearanceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}