package com.rumi.body_track_backend.security;

import com.rumi.body_track_backend.dto.LoginRequest;
import com.rumi.body_track_backend.dto.RefreshRequest;
import com.rumi.body_track_backend.dto.RegisterRequest;
import com.rumi.body_track_backend.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authentication: who may reach the API at all, and how tokens behave.
 */
class AuthFlowIT extends SecurityTestBase {

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.secret}")
    private String secret;

    private String userBToken;

    @BeforeEach
    void setUp() throws Exception {
        provisionTwoUsers();
        userBToken = loginAndGetToken(USER_B_EMAIL, USER_B_PASSWORD);
    }

    @Test
    @DisplayName("No token at all is refused with 401 and a JSON body")
    void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/anomalies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas o sesión expirada"));
    }

    @Test
    @DisplayName("A garbage token is refused with 401")
    void rejectsMalformedToken() throws Exception {
        mockMvc.perform(asUser(get("/anomalies"), "not-even-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token signed with a different key is refused")
    void rejectsTokenSignedWithAnotherKey() throws Exception {
        SecretKey attackerKey = Keys.hmacShaKeyFor(
                "attacker-key-that-is-definitely-32-bytes!".getBytes(StandardCharsets.UTF_8));

        String forged = Jwts.builder()
                .subject(String.valueOf(userAId))
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(attackerKey)
                .compact();

        mockMvc.perform(asUser(get("/anomalies"), forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("An expired token is refused with 401")
    void rejectsExpiredToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret.trim()));

        String expired = Jwts.builder()
                .subject(String.valueOf(userAId))
                .claim("type", "access")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis() - 7_200_000))
                .expiration(new Date(System.currentTimeMillis() - 3_600_000))
                .signWith(key)
                .compact();

        mockMvc.perform(asUser(get("/anomalies"), expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A token with no type claim is refused")
    void rejectsTokenWithoutTypeClaim() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret.trim()));

        String untyped = Jwts.builder()
                .subject(String.valueOf(userAId))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(key)
                .compact();

        mockMvc.perform(asUser(get("/anomalies"), untyped))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A refresh token cannot be used as an access token")
    void refreshTokenIsNotAnAccessToken() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret.trim()));

        String refreshShaped = Jwts.builder()
                .subject(String.valueOf(userAId))
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(key)
                .compact();

        // This is the exact escalation that was possible before: the controllers
        // re-parsed the header without checking the type, so a long-lived refresh
        // token functioned as a session credential on every write endpoint.
        mockMvc.perform(asUser(post("/anomalies")
                        .contentType("application/json")
                        .content("{\"type\":\"Verruga\"}"), refreshShaped))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(asUser(put("/auth/profile")
                        .contentType("application/json")
                        .content("{\"name\":\"hijacked\"}"), refreshShaped))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("The access token subject is the user id, not the email")
    void accessTokenSubjectIsUserId() {
        String token = jwtService.generateAccessToken(userAId, USER_A_EMAIL);
        Long subject = jwtService.extractUserId(token);

        assertThat(subject).isEqualTo(userAId);
        assertThat(subject).isNotEqualTo(USER_A_EMAIL);
    }

    @Test
    @DisplayName("Profile updates can only target the caller's own account")
    void profileUpdateIsScopedToTheCaller() throws Exception {
        // The request carries a userId aimed at A, but the row that changes must be
        // B's, because the subject is taken from the verified token.
        mockMvc.perform(asUser(put("/auth/profile")
                        .contentType("application/json")
                        .content("{\"name\":\"Renamed By B\",\"userId\":" + userAId + "}"),
                        userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userBId))
                .andExpect(jsonPath("$.name").value("Renamed By B"))
                .andExpect(jsonPath("$.email").value(USER_B_EMAIL));
    }

    @Test
    @DisplayName("Login rejects unknown accounts and wrong passwords identically")
    void loginDoesNotDistinguishFailureModes() throws Exception {
        LoginRequest unknown = new LoginRequest();
        unknown.setEmail("nobody@example.test");
        unknown.setPassword("WrongPassword1!");

        LoginRequest wrongPassword = new LoginRequest();
        wrongPassword.setEmail(USER_A_EMAIL);
        wrongPassword.setPassword("WrongPassword1!");

        String unknownBody = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(unknown)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPasswordBody = mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(unknownBody).get("message"))
                .isEqualTo(objectMapper.readTree(wrongPasswordBody).get("message"));
        assertThat(objectMapper.readTree(unknownBody).get("message").asText())
                .doesNotContain("encontrado")
                .doesNotContain("incorrecta");
    }

    @Test
    @DisplayName("Registration does not confirm that an email is taken")
    void registrationDoesNotEnumerateAccounts() throws Exception {
        RegisterRequest duplicate = new RegisterRequest();
        duplicate.setName("Impostor");
        duplicate.setEmail(USER_A_EMAIL);
        duplicate.setPassword("PasswordC1!");

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("registrado"))));
    }

    @Test
    @DisplayName("Logout revokes the caller's refresh tokens")
    void logoutRevokesRefreshTokens() throws Exception {
        RefreshRequest refresh = new RefreshRequest();
        refresh.setRefreshToken(loginAndGetRefreshToken(USER_A_EMAIL, USER_A_PASSWORD));

        mockMvc.perform(asUser(post("/auth/logout"), userAToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(refresh)))
                .andExpect(status().isUnauthorized());
    }
}