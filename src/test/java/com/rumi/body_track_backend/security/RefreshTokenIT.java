package com.rumi.body_track_backend.security;

import com.rumi.body_track_backend.dto.RefreshRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Refresh token rotation, reuse detection and revocation.
 *
 * <p>Before this change refresh tokens were stateless JWTs: the same value could be
 * exchanged repeatedly for seven days, there was nothing to revoke, and logout was
 * purely a client-side deletion. Each behaviour asserted below was therefore
 * impossible before.
 */
class RefreshTokenIT extends SecurityTestBase {

    private String firstRefresh;

    @BeforeEach
    void setUp() throws Exception {
        provisionTwoUsers();
        firstRefresh = loginAndGetRefreshToken(USER_A_EMAIL, USER_A_PASSWORD);
    }

    private String refresh(String rawToken) throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);

        return mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void refreshExpectingUnauthorized(String rawToken) throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken(rawToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A refresh token is opaque: it is not a JWT and carries no claims")
    void refreshTokenIsOpaque() {
        assertThat(firstRefresh.split("\\.")).hasSize(1);
        assertThat(firstRefresh).doesNotContain(USER_A_EMAIL);
    }

    @Test
    @DisplayName("A refresh returns a new access token and a new refresh token")
    void refreshRotatesBothCredentials() throws Exception {
        String response = refresh(firstRefresh);

        var json = objectMapper.readTree(response);
        assertThat(json.get("token").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isNotBlank();
        assertThat(json.get("refreshToken").asText()).isNotEqualTo(firstRefresh);
        assertThat(json.get("userId").asLong()).isEqualTo(userAId);

        // The new token must work, so the rotation is a real hand-off rather than
        // a revocation of everything.
        assertThat(refresh(json.get("refreshToken").asText())).isNotBlank();
    }

    @Test
    @DisplayName("A rotated refresh token cannot be used a second time")
    void rotatedTokenIsSingleUse() throws Exception {
        refresh(firstRefresh);
        refreshExpectingUnauthorized(firstRefresh);
    }

    @Test
    @DisplayName("Reusing a rotated token kills every token in the same family")
    void reuseRevokesTheWholeFamily() throws Exception {
        String second = objectMapper.readTree(refresh(firstRefresh)).get("refreshToken").asText();
        String third = objectMapper.readTree(refresh(second)).get("refreshToken").asText();

        // Replaying the very first token is the theft signal.
        refreshExpectingUnauthorized(firstRefresh);

        // The consequence is that the legitimate chain is invalidated too, forcing a
        // fresh login. Both outcomes require knowing whether a replay happened, so the
        // safe choice is to close everything.
        refreshExpectingUnauthorized(third);
    }

    @Test
    @DisplayName("The access token from a refresh works on protected endpoints")
    void refreshedAccessTokenIsUsable() throws Exception {
        String accessToken = objectMapper.readTree(refresh(firstRefresh)).get("token").asText();

        mockMvc.perform(asUser(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/anomalies"), accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("An unknown or fabricated refresh token is refused")
    void rejectsFabricatedRefreshToken() throws Exception {
        refreshExpectingUnauthorized("totally-made-up-token");
        refreshExpectingUnauthorized("a.b.c");
        refreshExpectingUnauthorized(UUID.randomUUID().toString());

        // A blank token is a malformed request rather than a failed lookup, so it is
        // a 400 from validation. Both paths refuse it; only the reason differs.
        RefreshRequest blank = new RefreshRequest();
        blank.setRefreshToken("");
        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(blank)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Logout invalidates refresh tokens immediately")
    void logoutKillsRefresh() throws Exception {
        String userBToken = loginAndGetToken(USER_B_EMAIL, USER_B_PASSWORD);
        String bRefresh = loginAndGetRefreshToken(USER_B_EMAIL, USER_B_PASSWORD);

        mockMvc.perform(asUser(post("/auth/logout"), userBToken))
                .andExpect(status().isNoContent());

        refreshExpectingUnauthorized(bRefresh);
    }

    @Test
    @DisplayName("Refresh errors are generic and leak nothing")
    void refreshErrorsAreGeneric() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("not-a-real-token");

        String body = mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("refresh_tokens")
                .doesNotContain("SQL")
                .doesNotContain("Exception")
                .doesNotContain("at com.rumi");
    }
}