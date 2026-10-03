package com.rumi.body_track_backend.security;

import com.rumi.body_track_backend.repository.SkinAnomalyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Upload validation.
 *
 * <p>The upload path previously trusted the client's declared content type and
 * filename extension, wrote the bytes straight to disk, and derived the stored name
 * from a predictable {@code anomaly-<id>-<millis>} pattern. Nothing checked magic
 * bytes, dimensions or size.
 */
class UploadValidationIT extends SecurityTestBase {

    @Autowired
    private SkinAnomalyRepository skinAnomalyRepository;

    private Long anomalyId;

    @BeforeEach
    void setUp() throws Exception {
        provisionTwoUsers();
        anomalyId = createAnomaly(userAToken, "Lunar");
    }

    private org.springframework.test.web.servlet.ResultActions upload(
            String filename, String declaredContentType, byte[] content) throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", filename, declaredContentType, content);
        return mockMvc.perform(asUser(multipart("/anomalies/" + anomalyId + "/image"), userAToken)
                .file(file));
    }

    @Test
    @DisplayName("A real JPEG is accepted and stored under a server-generated name")
    void acceptsRealImage() throws Exception {
        upload("whatever.jpg", "image/jpeg", jpegBytes(32, 32)).andExpect(status().isOk());

        String key = skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath();

        assertThat(key)
                .as("stored name must not come from the client")
                .doesNotContain("whatever")
                .matches("[0-9a-fA-F-]{36}\\.jpg");
    }

    @Test
    @DisplayName("A text file renamed to .jpg is rejected")
    void rejectsTextFileWithImageExtension() throws Exception {
        byte[] text = "esto no es una imagen, es texto plano".getBytes(StandardCharsets.UTF_8);

        upload("photo.jpg", "image/jpeg", text)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_image"));

        assertThat(skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath()).isNull();
    }

    @Test
    @DisplayName("An HTML payload masquerading as an image is rejected")
    void rejectsHtmlDisguisedAsImage() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        upload("xss.jpg", "image/jpeg", html).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A truncated JPEG is rejected even though it starts with valid magic bytes")
    void rejectsTruncatedJpegWithValidHeader() throws Exception {
        // A real image cut in half: the magic bytes are intact, so a header-only
        // check passes it. Only attempting the full decode rejects it.
        byte[] full = jpegBytes(64, 64);
        byte[] truncated = java.util.Arrays.copyOf(full, full.length / 2);

        upload("truncated.jpg", "image/jpeg", truncated)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_image"));
    }

    @Test
    @DisplayName("A mislabelled PNG is stored under its true type, not the declared one")
    void mismatchedDeclaredTypeIsCorrected() throws Exception {
        // The declared content type is not trusted, but neither is a mismatch fatal:
        // the bytes are a valid image, so the stored type and extension follow the
        // actual format. Rejecting outright would break ordinary clients that get
        // the MIME type wrong on a perfectly good photograph.
        upload("actually-png.jpg", "image/jpeg", pngBytes(32, 32))
                .andExpect(status().isOk());

        assertThat(skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath())
                .endsWith(".png");

        mockMvc.perform(asUser(get("/anomalies/" + anomalyId + "/image"), userAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    @DisplayName("A ZIP archive is rejected regardless of its name")
    void rejectsZipArchive() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("payload.txt"));
            zip.write("contenido".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        upload("archive.jpg", "image/jpeg", out.toByteArray())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An empty upload is rejected")
    void rejectsEmptyFile() throws Exception {
        upload("empty.jpg", "image/jpeg", new byte[0])
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A file above the size ceiling is rejected")
    void rejectsOversizedFile() throws Exception {
        byte[] tooLarge = new byte[9 * 1024 * 1024];
        java.util.Arrays.fill(tooLarge, (byte) 'x');

        upload("big.jpg", "image/jpeg", tooLarge)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("file_too_large"));
    }

    @Test
    @DisplayName("An image beyond the dimension ceiling is rejected")
    void rejectsOversizedDimensions() throws Exception {
        // Small on the wire, huge once decoded: the classic decompression bomb. A
        // byte-size limit alone would wave this through.
        upload("bomb.jpg", "image/jpeg", jpegBytes(7000, 7000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid_image_dimensions"));
    }

    @Test
    @DisplayName("A traversal attempt in the filename changes nothing about the stored path")
    void rejectsTraversalFilename() throws Exception {
        upload("../../../../etc/passwd.jpg", "image/jpeg", jpegBytes(16, 16))
                .andExpect(status().isOk());

        String key = skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath();

        assertThat(key).matches("[0-9a-fA-F-]{36}\\.jpg");
        assertThat(Path.of("./target/test-uploads").resolve(key).normalize())
                .startsWith(Path.of("./target/test-uploads").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Served images carry nosniff, a real content type and no caching")
    void servedImageHasHardenedHeaders() throws Exception {
        upload("photo.jpg", "image/jpeg", jpegBytes(32, 32)).andExpect(status().isOk());

        mockMvc.perform(asUser(get("/anomalies/" + anomalyId + "/image"), userAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("A PNG is stored and served with its own content type")
    void pngRoundTripsWithCorrectType() throws Exception {
        byte[] png = pngBytes(16, 16);

        upload("photo.png", "image/png", png).andExpect(status().isOk());

        assertThat(skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath())
                .endsWith(".png");

        mockMvc.perform(asUser(get("/anomalies/" + anomalyId + "/image"), userAToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    @DisplayName("Replacing an image removes the previous file")
    void replacingAnImageCleansUpTheOldFile() throws Exception {
        upload("first.jpg", "image/jpeg", jpegBytes(32, 32)).andExpect(status().isOk());
        String firstKey = skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath();
        assertThat(Files.exists(Path.of("./target/test-uploads").resolve(firstKey))).isTrue();

        upload("second.jpg", "image/jpeg", jpegBytes(48, 48)).andExpect(status().isOk());
        String secondKey = skinAnomalyRepository.findById(anomalyId).orElseThrow().getImagePath();

        assertThat(secondKey).isNotEqualTo(firstKey);
        assertThat(Files.exists(Path.of("./target/test-uploads").resolve(firstKey)))
                .as("previous image should not be orphaned")
                .isFalse();
    }

    @Test
    @DisplayName("The response advertises image presence without exposing the internal path")
    void responseHidesInternalPath() throws Exception {
        upload("photo.jpg", "image/jpeg", jpegBytes(32, 32)).andExpect(status().isOk());

        String body = mockMvc.perform(asUser(get("/anomalies"), userAToken))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("imagePath")
                .doesNotContain("target/test-uploads")
                .contains("\"hasImage\":true");
    }

    private static byte[] jpegBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private static byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}