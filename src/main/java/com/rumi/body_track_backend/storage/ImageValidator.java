package com.rumi.body_track_backend.storage;

import com.rumi.body_track_backend.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Decides whether a byte stream really is an image, and of what kind.
 *
 * <p>Kept separate from {@link ImageStorage} on purpose: every backend, local disk
 * or S3, must apply exactly the same rules, and a future Supabase implementation
 * should reuse this component rather than re-deriving trust from the request.
 *
 * <p>What is checked, in order of trustworthiness:
 * <ol>
 *   <li><b>Magic bytes.</b> The declared {@code Content-Type} and the extension are
 *       client-controlled and are deliberately ignored.</li>
 *   <li><b>Full decode.</b> The bytes must decode as an image end to end. This is
 *       what catches a polyglot file that begins with valid JPEG magic bytes and
 *       carries a payload after them.</li>
 *   <li><b>Dimensions.</b> Decoding before enforcing a pixel ceiling is what stops
 *       a decompression bomb; the size of the compressed bytes says nothing.</li>
 * </ol>
 */
@Component
public class ImageValidator {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final String REJECT_NOT_IMAGE = "El archivo no es una imagen válida";
    private static final String REJECT_TOO_LARGE = "El archivo supera el tamaño permitido";
    private static final String REJECT_DIMENSIONS = "La imagen supera las dimensiones permitidas";
    private static final String REJECT_EMPTY = "El archivo está vacío";

    private final long maxBytes;
    private final int maxWidthPx;
    private final int maxHeightPx;

    public ImageValidator(
            @Value("${app.upload.max-bytes}") long maxBytes,
            @Value("${app.upload.max-width-px}") int maxWidthPx,
            @Value("${app.upload.max-height-px}") int maxHeightPx) {
        this.maxBytes = maxBytes;
        this.maxWidthPx = maxWidthPx;
        this.maxHeightPx = maxHeightPx;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException(REJECT_EMPTY, "invalid_image");
        }

        if (file.getSize() > maxBytes) {
            throw new BadRequestException(REJECT_TOO_LARGE, "file_too_large");
        }

        byte[] bytes = readAll(file);

        if (bytes.length == 0) {
            throw new BadRequestException(REJECT_EMPTY, "invalid_image");
        }
        if (bytes.length > maxBytes) {
            // Catches a stream that under-reported its length.
            throw new BadRequestException(REJECT_TOO_LARGE, "file_too_large");
        }

        String detected = detectContentType(bytes);
        if (detected == null) {
            throw new BadRequestException(REJECT_NOT_IMAGE, "invalid_image");
        }

        BufferedImage decoded = decode(bytes);
        if (decoded == null) {
            // Right magic bytes, but the payload is not a complete image.
            throw new BadRequestException(REJECT_NOT_IMAGE, "invalid_image");
        }

        int width = decoded.getWidth();
        int height = decoded.getHeight();

        if (width <= 0 || height <= 0) {
            throw new BadRequestException(REJECT_NOT_IMAGE, "invalid_image");
        }
        if (width > maxWidthPx || height > maxHeightPx) {
            throw new BadRequestException(REJECT_DIMENSIONS, "invalid_image_dimensions");
        }

        return new ValidatedImage(bytes, detected);
    }

    /**
     * Returns the canonical content type for the detected format, or null when the
     * bytes are not a supported image. Only formats are re-encoded by the image
     * decoder, so the whitelist is exactly this method's branches.
     */
    private String detectContentType(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    /**
     * The extension is derived from the detected type, never from
     * {@code getOriginalFilename()}. The client-supplied name never influences the
     * path that gets written.
     */
    public static String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            default -> ".jpg";
        };
    }

    private BufferedImage decode(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException unreadable) {
            // ImageIO returns null for unrecognised data; a malformed stream can also
            // surface as an unchecked exception from a reader.
            return null;
        }
    }

    private byte[] readAll(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new BadRequestException("No se pudo leer el archivo", "invalid_image");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    /** Bytes proven to be a complete image, with its detected content type. */
    public record ValidatedImage(byte[] bytes, String contentType) {
    }
}