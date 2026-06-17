package com.rumi.body_track_backend.controller;

import com.rumi.body_track_backend.dto.AnomalyRequest;
import com.rumi.body_track_backend.dto.AnomalyResponse;
import com.rumi.body_track_backend.service.JwtService;
import com.rumi.body_track_backend.service.SkinAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping("/anomalies")
@RequiredArgsConstructor
public class SkinAnomalyController {

    private final SkinAnomalyService skinAnomalyService;
    private final JwtService jwtService;

    private String getEmail(String authHeader) {
        String token = authHeader.substring(7);
        return jwtService.extractEmail(token);
    }

    @PostMapping
    public ResponseEntity<AnomalyResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AnomalyRequest request) {
        String email = getEmail(authHeader);
        return ResponseEntity.ok(skinAnomalyService.create(request, email));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnomalyResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody AnomalyRequest request) {
        String email = getEmail(authHeader);
        return ResponseEntity.ok(skinAnomalyService.update(id, request, email));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<AnomalyResponse> uploadImage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        String email = getEmail(authHeader);
        skinAnomalyService.uploadImage(id, file, email);
        return ResponseEntity.ok(skinAnomalyService.getById(id));
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long id) {
        Resource resource = skinAnomalyService.getImageFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        String email = getEmail(authHeader);
        boolean isPaged = page >= 0 && size > 0;

        if (updatedSince != null) {
            Instant instant = Instant.parse(updatedSince);
            LocalDateTime since = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);

            if (isPaged) {
                Pageable pageable = PageRequest.of(page, size);
                return ResponseEntity.ok(skinAnomalyService.getByUserUpdatedAfterPaged(email, since, pageable));
            }
            return ResponseEntity.ok(skinAnomalyService.getByUserUpdatedAfter(email, since));
        }

        if (isPaged) {
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(skinAnomalyService.getByUserPaged(email, pageable));
        }
        return ResponseEntity.ok(skinAnomalyService.getByUser(email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skinAnomalyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
