package com.rumi.body_track_backend.controller;

import com.rumi.body_track_backend.dto.AnomalyRequest;
import com.rumi.body_track_backend.dto.AnomalyResponse;
import com.rumi.body_track_backend.security.AuthPrincipal;
import com.rumi.body_track_backend.service.SkinAnomalyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

/**
 * The listing routes answer with a bare JSON array, capped rather than paginated.
 *
 * <p>Serialising a Spring {@code Page} directly would change the response shape to
 * an object with {@code content}/{@code totalElements}, and the Flutter client
 * parses this endpoint as a list, so a page envelope would break it silently.
 * The page size is clamped instead: unbounded growth was the actual problem, and
 * a hard ceiling closes it without altering the contract.
 */
@RestController
@RequestMapping("/anomalies")
@RequiredArgsConstructor
@Validated
public class SkinAnomalyController {

    private static final int MAX_PAGE_SIZE = 200;

    private final SkinAnomalyService skinAnomalyService;

    @PostMapping
    public ResponseEntity<AnomalyResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AnomalyRequest request) {
        return ResponseEntity.ok(skinAnomalyService.create(principal.userId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnomalyResponse> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody AnomalyRequest request) {
        return ResponseEntity.ok(skinAnomalyService.update(id, principal.userId(), request));
    }

    @PostMapping("/{id}/image")
    public ResponseEntity<AnomalyResponse> uploadImage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(skinAnomalyService.uploadImage(id, principal.userId(), file));
    }

    /**
     * Serves the clinical image for an anomaly the caller owns.
     *
     * <p>This route used to be mapped to {@code permitAll} while the method it called
     * had no user parameter at all, so any unauthenticated caller could walk the id
     * range and download every patient's photographs. It is now authenticated, the
     * ownership check is not optional, and a miss is reported identically whether the
     * anomaly is absent, owned by someone else, or has no image.
     */
    @GetMapping("/{id}/image")
    public ResponseEntity<ByteArrayResource> getImage(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id) {

        SkinAnomalyService.ImagePayload payload = skinAnomalyService.loadImage(id, principal.userId());

        return ResponseEntity.ok()
                // Detected from the bytes at upload time, not assumed to be JPEG.
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .contentLength(payload.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                // Without this a browser may re-run an image through its HTML parser.
                .header("X-Content-Type-Options", "nosniff")
                // Clinical images must not sit in a shared or on-disk cache.
                .cacheControl(CacheControl.maxAge(0, TimeUnit.SECONDS).cachePrivate().noStore())
                .body(new ByteArrayResource(payload.bytes()));
    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String updatedSince,
            @RequestParam(required = false, defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false, defaultValue = "100")
            @Min(1) @Max(MAX_PAGE_SIZE) int size) {

        Long userId = principal.userId();
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE));

        if (updatedSince != null) {
            return ResponseEntity.ok(skinAnomalyService.getByUserUpdatedAfter(
                    userId, parseSince(updatedSince), pageable));
        }

        return ResponseEntity.ok(skinAnomalyService.getByUser(userId, pageable));
    }

    /**
     * A malformed timestamp previously surfaced as a raw
     * {@code DateTimeParseException} message through the generic handler. It is
     * reported as a plain validation failure now.
     */
    private static LocalDateTime parseSince(String updatedSince) {
        try {
            return LocalDateTime.ofInstant(Instant.parse(updatedSince), ZoneOffset.UTC);
        } catch (DateTimeParseException malformed) {
            throw new com.rumi.body_track_backend.exception.BadRequestException(
                    "Formato de updatedSince inválido", "invalid_updated_since");
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable Long id) {
        skinAnomalyService.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}