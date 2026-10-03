package com.rumi.body_track_backend.storage;

import com.rumi.body_track_backend.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Local-filesystem implementation of {@link ImageStorage}.
 *
 * <p>This is the current backend and is deliberately provider-neutral: it is
 * selected by the presence of {@code app.storage.type=local} and can be replaced by
 * an S3 implementation without touching callers.
 *
 * <p>Containment is enforced on both the write and the read path, and the read path
 * resolves symlinks. Normalisation alone is not enough because
 * {@code normalize()} does not follow links, so a link planted inside the upload
 * directory would otherwise satisfy a prefix check while pointing elsewhere.
 */
@Component
public class LocalFileSystemImageStorage implements ImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileSystemImageStorage.class);

    private final ImageValidator imageValidator;
    private final Path root;

    public LocalFileSystemImageStorage(
            ImageValidator imageValidator,
            @Value("${app.upload.dir}") String uploadDirPath) {
        this.imageValidator = imageValidator;
        this.root = Paths.get(uploadDirPath).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            // Do not include the absolute path: this message used to be echoed to clients.
            throw new IllegalStateException("No se pudo inicializar el almacenamiento de imágenes");
        }
    }

    @Override
    public StoredImage store(Long ownerId, MultipartFile file) {
        ImageValidator.ValidatedImage validated = imageValidator.validate(file);

        // Name is server-generated and random: the client's filename, its extension
        // and the entity id are all absent by construction, which removes both path
        // traversal and filename predictability.
        String key = UUID.randomUUID().toString()
                + ImageValidator.extensionFor(validated.contentType());

        Path target = resolveWithinRoot(key);
        try {
            Files.copy(new java.io.ByteArrayInputStream(validated.bytes()), target,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("image_store_failed owner_id={} key={}", ownerId, key, e);
            throw new BadRequestException("No se pudo guardar la imagen", "storage_error");
        }

        return new StoredImage(key, validated.contentType(), validated.bytes().length);
    }

    @Override
    public Optional<byte[]> read(String key) {
        Optional<Path> resolved = resolveWithinRootSafely(key);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }

        Path path = resolved.get();
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            log.warn("image_read_failed key={}", key, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        resolveWithinRootSafely(key).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // Orphaned file: the database row is already gone, so log and move on.
                log.warn("image_delete_failed key={}", key, e);
            }
        });
    }

    @Override
    public String contentTypeOf(String key) {
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    /**
     * Rejects any key that is not a bare generated filename. The pattern check is
     * belt-and-braces: {@code normalize()} plus the prefix comparison already
     * contain traversal, but a key is also a database value that a future code path
     * could populate from elsewhere.
     */
    private Optional<Path> resolveWithinRootSafely(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            return Optional.empty();
        }
        if (!key.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            return Optional.empty();
        }

        Path candidate = root.resolve(key).normalize();
        if (!candidate.startsWith(root) || candidate.getParent() == null
                || !candidate.getParent().equals(root)) {
            log.warn("image_key_rejected key={}", key);
            return Optional.empty();
        }

        try {
            // toRealPath resolves symlinks, so a link is compared against its target.
            Path real = candidate.toRealPath();
            if (!real.startsWith(root.toRealPath())) {
                log.warn("image_key_escaped_root key={}", key);
                return Optional.empty();
            }
            return Optional.of(real);
        } catch (IOException doesNotExistYet) {
            return Optional.of(candidate);
        }
    }

    private Path resolveWithinRoot(String key) {
        return resolveWithinRootSafely(key)
                .orElseThrow(() -> new BadRequestException("Archivo inválido", "invalid_image"));
    }
}