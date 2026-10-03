package com.rumi.body_track_backend.security;

import com.rumi.body_track_backend.model.SkinAnomaly;
import com.rumi.body_track_backend.repository.SkinAnomalyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cross-tenant access control.
 *
 * <p>Each case asserts both halves of the requirement: that the caller is refused,
 * and that the target row survives. A refusal alone would still be satisfied by an
 * endpoint that refused <em>and</em> had already deleted something.
 *
 * <p>Refusals are asserted as 404 rather than 403 on purpose. Responding 403 for
 * "exists but not yours" and 404 for "does not exist" is itself an enumeration
 * channel over the whole anomaly table.
 */
class OwnershipIT extends SecurityTestBase {

    @Autowired
    private SkinAnomalyRepository skinAnomalyRepository;

    private String userBToken;
    private Long userAAnomalyId;

    @BeforeEach
    void setUp() throws Exception {
        provisionTwoUsers();
        userBToken = loginAndGetToken(USER_B_EMAIL, USER_B_PASSWORD);
        userAAnomalyId = createAnomaly(userAToken, "Lunar");
    }

    @Test
    @DisplayName("B's list never contains A's anomaly")
    void listIsScopedToTheCaller() throws Exception {
        mockMvc.perform(asUser(get("/anomalies"), userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Sanity check that the empty list is a consequence of scoping and not of
        // the fixture failing to create anything.
        mockMvc.perform(asUser(get("/anomalies"), userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(userAAnomalyId));
    }

    @Test
    @DisplayName("A's anomaly is not modifiable by B, and the row is unchanged")
    void cannotUpdateAnomalyOwnedByAnother() throws Exception {
        String original = skinAnomalyRepository.findById(userAAnomalyId).orElseThrow().getDescription();

        mockMvc.perform(asUser(put("/anomalies/" + userAAnomalyId)
                        .contentType("application/json")
                        .content("{\"type\":\"Verruga\",\"description\":\"hacked by B\"}"), userBToken))
                .andExpect(status().isNotFound());

        SkinAnomaly after = skinAnomalyRepository.findById(userAAnomalyId).orElseThrow();
        assertThat(after.getDescription()).isEqualTo(original);
        assertThat(after.getType()).isEqualTo("Lunar");
    }

    @Test
    @DisplayName("B cannot delete A's anomaly, and the row survives")
    void cannotDeleteAnomalyOwnedByAnother() throws Exception {
        mockMvc.perform(asUser(delete("/anomalies/" + userAAnomalyId), userBToken))
                .andExpect(status().isNotFound());

        assertThat(skinAnomalyRepository.findById(userAAnomalyId)).isPresent();
    }

    @Test
    @DisplayName("B cannot upload an image onto A's anomaly")
    void cannotUploadImageToAnomalyOwnedByAnother() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", minimalJpeg());

        mockMvc.perform(asUser(multipart("/anomalies/" + userAAnomalyId + "/image"), userBToken)
                        .file(file))
                .andExpect(status().isNotFound());

        assertThat(skinAnomalyRepository.findById(userAAnomalyId).orElseThrow().getImagePath())
                .isNull();
    }

    @Test
    @DisplayName("B cannot download A's clinical image")
    void cannotDownloadImageOwnedByAnother() throws Exception {
        // A uploads a real image first, so the refusal cannot be mistaken for
        // "there was no image to begin with".
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", minimalJpeg());
        mockMvc.perform(asUser(multipart("/anomalies/" + userAAnomalyId + "/image"), userAToken)
                        .file(file))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/anomalies/" + userAAnomalyId + "/image"), userBToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("The owner retains full access to their own anomaly and image")
    void ownerKeepsFullAccess() throws Exception {
        mockMvc.perform(asUser(get("/anomalies"), userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(userAAnomalyId));

        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", minimalJpeg());
        mockMvc.perform(asUser(multipart("/anomalies/" + userAAnomalyId + "/image"), userAToken)
                        .file(file))
                .andExpect(status().isOk());

        mockMvc.perform(asUser(get("/anomalies/" + userAAnomalyId + "/image"), userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist());

        mockMvc.perform(asUser(delete("/anomalies/" + userAAnomalyId), userAToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Deleting an anomaly also removes its image from storage")
    void deleteRemovesTheStoredFile() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", minimalJpeg());
        mockMvc.perform(asUser(multipart("/anomalies/" + userAAnomalyId + "/image"), userAToken)
                        .file(file))
                .andExpect(status().isOk());

        String key = skinAnomalyRepository.findById(userAAnomalyId).orElseThrow().getImagePath();
        Path expected = Path.of("./target/test-uploads").resolve(key);
        assertThat(Files.exists(expected)).as("image written").isTrue();

        mockMvc.perform(asUser(delete("/anomalies/" + userAAnomalyId), userAToken))
                .andExpect(status().isNoContent());

        assertThat(Files.exists(expected)).as("orphaned image left behind").isFalse();
    }

    @Test
    @DisplayName("A record can never be reached by submitting an owner id in the body")
    void bodySuppliedOwnershipIsIgnored() throws Exception {
        // The DTO has no owner field, so an attempt to inject one is either rejected
        // as unknown or silently ignored. Either way the row belongs to the caller.
        mockMvc.perform(asUser(put("/anomalies/" + userAAnomalyId)
                        .contentType("application/json")
                        .content("{\"type\":\"Verruga\",\"userId\":" + userBId
                                + ",\"user\":{\"id\":" + userBId + "}}"), userAToken))
                .andExpect(status().isOk());

        assertThat(skinAnomalyRepository.findById(userAAnomalyId).orElseThrow().getUser().getId())
                .isEqualTo(userAId);
    }

    /** A 1x1 JPEG, written as literals so the test carries no binary fixture. */
    static byte[] minimalJpeg() {
        return java.util.Base64.getDecoder().decode(
                "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a"
                        + "HBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAA"
                        + "AAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==");
    }
}