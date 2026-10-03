package com.rumi.body_track_backend.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Where clinical images live.
 *
 * <p>Exists so the domain never learns about a provider. {@code SkinAnomalyService}
 * talks only to this interface, which means moving from local disk to the Supabase
 * S3-compatible API is a new implementation class and a bean selection — no change
 * to authorization, validation, or the response contract.
 *
 * <p>Implementations must be safe for concurrent callers and must treat the key as
 * untrusted on read.
 */
public interface ImageStorage {

    /**
     * Validates the upload, generates a server-side opaque name, and persists it.
     *
     * @param ownerId the authenticated user, so per-user quota checks are possible
     *                inside the implementation
     * @return the opaque key to persist, plus the content type actually detected
     * @throws com.rumi.body_track_backend.exception.BadRequestException if the bytes
     *         are not a supported image
     */
    StoredImage store(Long ownerId, MultipartFile file);

    /**
     * Reads a stored object.
     *
     * @return the bytes, or empty if the key is unknown to this backend
     */
    Optional<byte[]> read(String key);

    /** Best-effort removal. Must not throw if the object is already gone. */
    void delete(String key);

    /** Content type recorded at write time, used when serving the bytes back. */
    String contentTypeOf(String key);
}