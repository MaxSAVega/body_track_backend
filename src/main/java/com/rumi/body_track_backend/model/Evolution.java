package com.rumi.body_track_backend.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "evolutions")
public class Evolution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn( name = "skin_anomaly_id")
    private SkinAnomaly skinAnomaly;

    private String note;
    private String type;
    private String shape;
    private Double diameter1;
    private Double diameter2;
    private Integer colorValue;
    private Boolean hurts;
    private Boolean hasChanged;
    private String imagePath;

    private LocalDateTime date;
}
