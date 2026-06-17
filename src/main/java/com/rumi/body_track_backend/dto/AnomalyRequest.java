package com.rumi.body_track_backend.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AnomalyRequest {
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
    private LocalDate appearanceDate;
}
