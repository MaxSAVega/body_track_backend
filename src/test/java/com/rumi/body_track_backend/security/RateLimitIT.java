package com.rumi.body_track_backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumi.body_track_backend.TestBeans;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Brute-force resistance on the credential endpoints.
 *
 * <p>Runs in its own context and its own class so the counters start from zero:
 * the limiter is per-JVM state, so sharing a context with the other tests would
 * make these assertions depend on how many requests ran before them.
 *
 * <p>Ordered because a single IP's budget is shared across cases, and each case
 * is meant to contribute part of the exhaustion rather than race the others.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestBeans.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
// A separate context, and therefore a separate RateLimitFilter instance, with a
// budget small enough to exhaust in a handful of requests.
@TestPropertySource(properties = {
        "app.rate-limit.login.max=5",
        "app.rate-limit.register.max=5",
        "app.rate-limit.refresh.max=5",
        "app.rate-limit.upload.max=5"
})
class RateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EMAIL = "ratelimit@example.test";

    private String attemptLogin() throws Exception {
        String body = """
                {"email":"%s","password":"WrongPassword1!"}
                """.formatted(EMAIL);

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        return result.getResponse().getStatus() + ":"
                + result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    @DisplayName("Failed logins are eventually throttled with 429")
    void failedLoginsAreThrottled() throws Exception {
        boolean throttled = false;

        for (int attempt = 0; attempt < 40 && !throttled; attempt++) {
            if (attemptLogin().startsWith("429:")) {
                throttled = true;
            }
        }

        assertThat(throttled)
                .as("repeated wrong passwords should trigger the limiter")
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("A 429 response is JSON and tells the client to retry later")
    void throttledResponseIsActionable() throws Exception {
        String response = attemptLogin();
        assertThat(response).startsWith("429:");

        JsonNode body = objectMapper.readTree(response.substring(response.indexOf(':') + 1));
        assertThat(body.get("code").asText()).isEqualTo("rate_limited");
        assertThat(body.has("message")).isTrue();
        assertThat(body.has("timestamp")).isTrue();
        assertThat(body.get("path").asText()).isEqualTo("/auth/login");
        assertThat(response)
                .doesNotContain("com.rumi")
                .doesNotContain("Exception");
    }

    @Test
    @Order(3)
    @DisplayName("The throttle applies to registration too")
    void registrationIsThrottled() throws Exception {
        boolean throttled = false;

        for (int attempt = 0; attempt < 40 && !throttled; attempt++) {
            String body = """
                    {"name":"Flood","email":"flood-%d@example.test","password":"PasswordD1!"}
                    """.formatted(attempt);

            MvcResult result = mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            if (result.getResponse().getStatus() == 429) {
                throttled = true;
            }
        }

        assertThat(throttled).as("registration flood should be throttled").isTrue();
    }
}