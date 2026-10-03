package com.rumi.body_track_backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumi.body_track_backend.TestBeans;
import com.rumi.body_track_backend.dto.AuthResponse;
import com.rumi.body_track_backend.dto.LoginRequest;
import com.rumi.body_track_backend.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for the security suite.
 *
 * <p>Each test provisions two independent users and authenticates as the second
 * one, so "can user A reach user B's data" is expressible without a fixture that
 * has to be reasoned about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
public abstract class SecurityTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected com.rumi.body_track_backend.repository.SkinAnomalyRepository skinAnomalyRepository;

    @Autowired
    protected com.rumi.body_track_backend.repository.UserRepository userRepository;

    @Autowired
    protected com.rumi.body_track_backend.repository.RefreshTokenRepository refreshTokenRepository;

    /**
     * Wipes the database and the upload directory before every test method.
     *
     * <p>Without this, re-registering the same two fixture accounts on each method
     * collides with the unique-email constraint and every test after the first
     * fails on setup rather than on the behaviour it is meant to check.
     *
     * <p>Runs before the subclass {@code setUp}, since superclass callbacks are
     * invoked first.
     */
    @BeforeEach
    void resetState() throws Exception {
        skinAnomalyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        Path uploadDir = Path.of("./target/test-uploads");
        if (Files.exists(uploadDir)) {
            try (var entries = Files.walk(uploadDir)) {
                entries.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        Files.deleteIfExists(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        }
    }

    protected static final String USER_A_EMAIL = "owner.a@example.test";
    protected static final String USER_A_PASSWORD = "PasswordA1!";
    protected static final String USER_B_EMAIL = "owner.b@example.test";
    protected static final String USER_B_PASSWORD = "PasswordB1!";

    protected Long userAId;
    protected Long userBId;
    protected String userAToken;

    protected void provisionTwoUsers() throws Exception {
        AuthResponse a = register(USER_A_EMAIL, USER_A_PASSWORD);
        AuthResponse b = register(USER_B_EMAIL, USER_B_PASSWORD);

        this.userAId = a.getUserId();
        this.userBId = b.getUserId();
        this.userAToken = a.getToken();
    }

    protected AuthResponse register(String email, String password) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail(email);
        request.setPassword(password);

        String body = objectMapper.writeValueAsString(request);
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
    }

    /** Full round trip through the real login endpoint, so tokens are genuine. */
    protected String loginAndGetToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class).getToken();
    }

    protected String loginAndGetRefreshToken(String email, String password) throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class).getRefreshToken();
    }

    /**
     * Generic so that a {@code multipart()} builder stays a multipart builder:
     * widening it to the base type would hide {@code file(...)}.
     */
    protected <T extends MockHttpServletRequestBuilder> T asUser(T builder, String token) {
        builder.header("Authorization", "Bearer " + token);
        return builder;
    }

    /** Creates an anomaly owned by {@code token}'s user and returns its id. */
    protected Long createAnomaly(String token, String type) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "type", type,
                "description", "created by the security suite",
                "bodyPart", "Back",
                "diameter1", 4.5));

        MvcResult result = mockMvc.perform(asUser(post("/anomalies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body), token))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }
}