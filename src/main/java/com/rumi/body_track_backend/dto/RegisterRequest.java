package com.rumi.body_track_backend.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private LocalDate birthDate;
    private String gender;
    private String phone;
}
