package com.rumi.body_track_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Índices declarados porque todas las consultas de lectura filtran por
 * {@code user_id}: la de listado, la de sincronización incremental y la de
 * ownership. Sin ellos cada petición recorre la tabla clínica completa, que es
 * justo lo que se quiere evitar en cuanto haya volumen.
 */
@Entity
@Table(name = "skin_anomalies", indexes = {
        @Index(name = "idx_anomaly_user", columnList = "user_id"),
        @Index(name = "idx_anomaly_user_updated", columnList = "user_id, updated_at")
})
@Data
@NoArgsConstructor
public class SkinAnomaly {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String type;           // Lunar, Verruga, Cicatriz...
    private String description;
    private String bodyPart;       // "Cabeza", "Pie izquierdo"...
    private String shape;          // Redondo, Ovalado, Irregular
    private Double diameter1;      // en mm
    private Double diameter2;      // null si es Redondo
    private Long colorValue;    // valor ARGB
    private Boolean hurts;
    private Boolean hasChanged;
    private Boolean isCongenital;  // congénito o no
    private String status;         // Activo, Resuelto, Requiere atención

    private Double x;   //coordenadas 3D
    private Double y;
    private Double z;

    /**
     * Clave opaca del archivo almacenado, no una ruta del sistema de archivos.
     *
     * Nunca se serializa en las respuestas de la API: el cliente recibe
     * {@code hasImage} y descarga la imagen por su id. Se mantiene como columna
     * porque el endpoint de imagen necesita localizar el bytes.
     */
    private String imagePath;
    private LocalDate appearanceDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
