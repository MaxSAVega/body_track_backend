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
    private String imagePath;
    private LocalDate appearanceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
