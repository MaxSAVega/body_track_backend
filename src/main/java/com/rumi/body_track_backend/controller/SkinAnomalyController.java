package com.rumi.body_track_backend.controller;

import com.rumi.body_track_backend.model.SkinAnomaly;
import com.rumi.body_track_backend.service.JwtService;
import com.rumi.body_track_backend.service.SkinAnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/anomalies")
@RequiredArgsConstructor
public class SkinAnomalyController {

    private final SkinAnomalyService skinAnomalyService;
    private final JwtService jwtService;

    // Extrae el email del token JWT que viene en el header
    private String getEmail(String authHeader) {
        String token = authHeader.substring(7);
        return jwtService.extractEmail(token);
    }

    @PostMapping
    public ResponseEntity<SkinAnomaly> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SkinAnomaly anomaly) {
        String email = getEmail(authHeader);
        return ResponseEntity.ok(skinAnomalyService.create(anomaly, email));
    }

    @GetMapping
    public ResponseEntity<List<SkinAnomaly>> getAll(
            @RequestHeader("Authorization") String authHeader) {
        String email = getEmail(authHeader);
        return ResponseEntity.ok(skinAnomalyService.getByUser(email));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        skinAnomalyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
