package com.rumi.body_track_backend.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String name;
    private String email;
    private String dni;
    private Double height;
    private Double weight;
}
