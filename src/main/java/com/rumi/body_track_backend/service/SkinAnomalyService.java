package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.dto.AnomalyRequest;
import com.rumi.body_track_backend.dto.AnomalyResponse;
import com.rumi.body_track_backend.exception.NotFoundException;
import com.rumi.body_track_backend.model.SkinAnomaly;
import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.SkinAnomalyRepository;
import com.rumi.body_track_backend.repository.UserRepository;
import com.rumi.body_track_backend.storage.ImageStorage;
import com.rumi.body_track_backend.storage.StoredImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Anomaly records and their clinical images.
 *
 * <p>Every method that takes an id also takes the authenticated user's id, and both
 * are applied in the query. Nothing here trusts an identifier that arrived from the
 * client as sufficient to reach a record.
 */
@Service
public class SkinAnomalyService {

    private final SkinAnomalyRepository skinAnomalyRepository;
    private final UserRepository userRepository;
    private final ImageStorage imageStorage;

    public SkinAnomalyService(SkinAnomalyRepository skinAnomalyRepository,
                              UserRepository userRepository,
                              ImageStorage imageStorage) {
        this.skinAnomalyRepository = skinAnomalyRepository;
        this.userRepository = userRepository;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public AnomalyResponse create(Long userId, AnomalyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario"));

        SkinAnomaly anomaly = new SkinAnomaly();
        anomaly.setUser(user);
        applyRequest(anomaly, request);

        return toResponse(skinAnomalyRepository.save(anomaly));
    }

    @Transactional
    public AnomalyResponse update(Long id, Long userId, AnomalyRequest request) {
        SkinAnomaly anomaly = requireOwned(id, userId);
        applyRequest(anomaly, request);
        return toResponse(skinAnomalyRepository.save(anomaly));
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> getByUser(Long userId, Pageable pageable) {
        return skinAnomalyRepository.findByUserId(userId, pageable).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> getByUserUpdatedAfter(Long userId, LocalDateTime since, Pageable pageable) {
        return skinAnomalyRepository.findByUserIdAndUpdatedAtAfter(userId, since, pageable).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Deletes a record the caller owns, together with its image.
     *
     * <p>Previously this was {@code deleteById} with no owner predicate, so any
     * authenticated account could delete any record. The row count from the scoped
     * delete is what decides success, and the file removal happens outside the
     * database transaction so a storage fault cannot silently leave the row behind.
     */
    @Transactional
    public void delete(Long id, Long userId) {
        SkinAnomaly anomaly = skinAnomalyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Anomalía"));

        String imageKey = anomaly.getImagePath();

        int removed = skinAnomalyRepository.deleteByIdAndUserId(id, userId);
        if (removed == 0) {
            throw new NotFoundException("Anomalía");
        }

        if (imageKey != null && !imageKey.isBlank()) {
            imageStorage.delete(imageKey);
        }
    }

    @Transactional(readOnly = true)
    public AnomalyResponse getById(Long id, Long userId) {
        return toResponse(requireOwned(id, userId));
    }

    @Transactional
    public AnomalyResponse uploadImage(Long id, Long userId, MultipartFile file) {
        SkinAnomaly anomaly = requireOwned(id, userId);

        StoredImage stored = imageStorage.store(userId, file);

        // Replacing an image must not leave the previous file behind.
        String previousKey = anomaly.getImagePath();

        anomaly.setImagePath(stored.key());
        AnomalyResponse response = toResponse(skinAnomalyRepository.save(anomaly));

        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(stored.key())) {
            imageStorage.delete(previousKey);
        }

        return response;
    }

    /**
     * Returns the stored bytes for an anomaly the caller owns.
     *
     * <p>This method previously took only an id and was mapped to {@code permitAll},
     * which combined into an unauthenticated read of every clinical photograph in
     * the system. The ownership check is now mandatory and unavoidable: there is no
     * overload that omits the user.
     */
    @Transactional(readOnly = true)
    public ImagePayload loadImage(Long id, Long userId) {
        SkinAnomaly anomaly = requireOwned(id, userId);

        String key = anomaly.getImagePath();
        if (key == null || key.isBlank()) {
            throw new NotFoundException("Imagen");
        }

        byte[] bytes = imageStorage.read(key)
                .orElseThrow(() -> new NotFoundException("Imagen"));

        return new ImagePayload(bytes, imageStorage.contentTypeOf(key));
    }

    /**
     * Single point where ownership is enforced, so a future endpoint cannot
     * accidentally introduce the unscoped variant.
     */
    private SkinAnomaly requireOwned(Long id, Long userId) {
        return skinAnomalyRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Anomalía"));
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

    /**
     * {@code imagePath} is no longer serialised. It used to expose the internal
     * naming scheme, the anomaly id and the upload timestamp, which is both a
     * de-anonymising metadata channel and a hint for enumeration. The client only
     * needs to know whether an image exists.
     */
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
                .hasImage(a.getImagePath() != null && !a.getImagePath().isBlank())
                .appearanceDate(a.getAppearanceDate())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    /** Verified image bytes plus the content type detected when they were stored. */
    public record ImagePayload(byte[] bytes, String contentType) {
    }
}