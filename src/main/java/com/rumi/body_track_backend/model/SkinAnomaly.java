package com.rumi.body_track_backend.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class SkinAnomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String type;           // Lunar, Verruga, Cicatriz...
    private String description;
    private String bodyPart;       // "Cabeza", "Pie izquierdo"...
    private String shape;          // Redondo, Ovalado, Irregular
    private Double diameter1;      // en mm
    private Double diameter2;      // null si es Redondo
    private Integer colorValue;    // valor ARGB
    private Boolean hurts;
    private Boolean hasChanged;
    private Boolean isCongenital;  // congénito o no
    private String status;         // Activo, Resuelto, Requiere atención

    private Double x;   //coordenadas 3D
    private Double y;
    private Double z;

    private String imagePath; // ruta o URL de la dirección
    private LocalDate appearanceDate;
    private LocalDateTime createdAt;
}
