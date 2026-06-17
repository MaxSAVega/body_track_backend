package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.dto.AnomalyRequest;
import com.rumi.body_track_backend.dto.AnomalyResponse;
import com.rumi.body_track_backend.model.SkinAnomaly;
import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.SkinAnomalyRepository;
import com.rumi.body_track_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkinAnomalyService {

    private final SkinAnomalyRepository skinAnomalyRepository;
    private final UserRepository userRepository;
    private final Path uploadDir;

    public SkinAnomalyService(
            SkinAnomalyRepository skinAnomalyRepository,
            UserRepository userRepository,
            @Value("${app.upload.dir}") String uploadDirPath) {
        this.skinAnomalyRepository = skinAnomalyRepository;
        this.userRepository = userRepository;
        this.uploadDir = Paths.get(uploadDirPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads: " + this.uploadDir, e);
        }
    }

    @Transactional
    public AnomalyResponse create(AnomalyRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        SkinAnomaly anomaly = new SkinAnomaly();
        anomaly.setUser(user);
        applyRequest(anomaly, request);

        SkinAnomaly saved = skinAnomalyRepository.save(anomaly);
        return toResponse(saved);
    }

    @Transactional
    public AnomalyResponse update(Long id, AnomalyRequest request, String email) {
        SkinAnomaly anomaly = skinAnomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));

        if (!anomaly.getUser().getEmail().equals(email)) {
            throw new RuntimeException("No autorizado");
        }

        applyRequest(anomaly, request);

        SkinAnomaly saved = skinAnomalyRepository.save(anomaly);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> getByUser(String email) {
        return skinAnomalyRepository.findByUserEmail(email)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AnomalyResponse> getByUserPaged(String email, Pageable pageable) {
        return skinAnomalyRepository.findByUserEmail(email, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> getByUserUpdatedAfter(String email, LocalDateTime since) {
        return skinAnomalyRepository.findByUserEmailAndUpdatedAtAfter(email, since)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AnomalyResponse> getByUserUpdatedAfterPaged(String email, LocalDateTime since, Pageable pageable) {
        return skinAnomalyRepository.findByUserEmailAndUpdatedAtAfter(email, since, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void delete(Long id) {
        skinAnomalyRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public AnomalyResponse getById(Long id) {
        SkinAnomaly anomaly = skinAnomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));
        return toResponse(anomaly);
    }

    @Transactional
    public void uploadImage(Long id, MultipartFile file, String email) {
        SkinAnomaly anomaly = skinAnomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));

        if (!anomaly.getUser().getEmail().equals(email)) {
            throw new RuntimeException("No autorizado");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String filename = "anomaly-" + id + "-" + System.currentTimeMillis() + ext;
        Path targetPath = this.uploadDir.resolve(filename);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            anomaly.setImagePath(filename);
            skinAnomalyRepository.save(anomaly);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar la imagen", e);
        }
    }

    @Transactional(readOnly = true)
    public Resource getImageFile(Long id) {
        SkinAnomaly anomaly = skinAnomalyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Anomalía no encontrada"));

        String imagePath = anomaly.getImagePath();
        if (imagePath == null || imagePath.isBlank()) {
            throw new RuntimeException("La anomalía no tiene imagen");
        }

        Path filePath = this.uploadDir.resolve(imagePath).normalize();
        if (!filePath.startsWith(this.uploadDir)) {
            throw new RuntimeException("Acceso no permitido");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new RuntimeException("No se pudo leer la imagen");
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error al acceder a la imagen", e);
        }
    }

    private void applyRequest(SkinAnomaly anomaly, AnomalyRequest request) {
        anomaly.setType(request.getType());
        anomaly.setDescription(request.getDescription());
        anomaly.setBodyPart(request.getBodyPart());
        anomaly.setShape(request.getShape());
        anomaly.setDiameter1(request.getDiameter1());
        anomaly.setDiameter2(request.getDiameter2());
        anomaly.setColorValue(request.getColorValue());
        anomaly.setHurts(request.getHurts());
        anomaly.setHasChanged(request.getHasChanged());
        anomaly.setStatus(request.getStatus());
        anomaly.setX(request.getX());
        anomaly.setY(request.getY());
        anomaly.setZ(request.getZ());
        anomaly.setAppearanceDate(request.getAppearanceDate());
    }

    private AnomalyResponse toResponse(SkinAnomaly a) {
        return AnomalyResponse.builder()
                .id(a.getId())
                .type(a.getType())
                .description(a.getDescription())
                .bodyPart(a.getBodyPart())
                .shape(a.getShape())
                .diameter1(a.getDiameter1())
                .diameter2(a.getDiameter2())
                .colorValue(a.getColorValue())
                .hurts(a.getHurts())
                .hasChanged(a.getHasChanged())
                .status(a.getStatus())
                .x(a.getX())
                .y(a.getY())
                .z(a.getZ())
                .imagePath(a.getImagePath())
                .appearanceDate(a.getAppearanceDate())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
