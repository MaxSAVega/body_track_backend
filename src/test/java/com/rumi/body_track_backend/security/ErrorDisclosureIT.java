package com.rumi.body_track_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Error responses must be useful to a client and useless to an attacker.
 *
 * <p>The original handler caught {@code Exception} and returned
 * {@code e.getMessage()} verbatim, so any unmapped failure surfaced the Hibernate
 * query, the table name and the SQL fragment.
 */
class ErrorDisclosureIT extends SecurityTestBase {

    private String lowerCase(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("A validation failure names the offending field but not the internals")
    void validationErrorIsPrecise() throws Exception {
        String body = mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"123\"}"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("email");
        assertThat(body).doesNotContain("Exception");
        assertThat(body).doesNotContain("com.rumi");
    }

    @Test
    @DisplayName("A malformed JSON body yields a clean 400 with no stack trace")
    void malformedJsonIsHandled() throws Exception {
        MockMvc mvc = mockMvc;

        MvcResult result = mvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{ this is not json"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);

        String body = lowerCase(result);
        assertThat(body)
                .doesNotContain("com.fasterxml")
                .doesNotContain("com.rumi")
                .doesNotContain("at org.springframework")
                .doesNotContain("exception");
    }

    @Test
    @DisplayName("An unknown route answers exactly like a protected one, hiding what exists")
    void unknownRouteDoesNotRevealWhichPathsExist() throws Exception {
        MvcResult unknown = mockMvc.perform(get("/no-such-endpoint")).andReturn();
        MvcResult real = mockMvc.perform(get("/anomalies")).andReturn();

        // anyRequest().authenticated() means an anonymous caller cannot tell a
        // missing path from a protected one. Returning 404 here would hand out a
        // free inventory of the API.
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(real.getResponse().getStatus());
        assertThat(unknown.getResponse().getContentType()).contains("application/json");
        assertThat(lowerCase(unknown)).doesNotContain("whitelabel");
    }

    @Test
    @DisplayName("A wrong HTTP method on a real path returns JSON")
    void methodNotAllowedReturnsJson() throws Exception {
        MvcResult result = mockMvc.perform(post("/anomalies")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);

        String body = lowerCase(result);
        assertThat(body).doesNotContain("com.rumi").doesNotContain("exception");
    }

    @Test
    @DisplayName("No response ever carries a stack trace or an internal package name")
    void noResponseLeaksInternals() throws Exception {
        provisionTwoUsers();

        String[] bodies = {
                // Unauthenticated
                mockMvc.perform(get("/anomalies")).andReturn().getResponse().getContentAsString(),
                // Nonexistent id
                mockMvc.perform(asUser(get("/anomalies/999999"), userAToken))
                        .andReturn().getResponse().getContentAsString(),
                // Nonexistent image
                mockMvc.perform(asUser(get("/anomalies/999999/image"), userAToken))
                        .andReturn().getResponse().getContentAsString(),
                // Bad payload
                mockMvc.perform(asUser(put("/anomalies/1")
                        .contentType("application/json")
                        .content("{\"type\":\"\"}"), userAToken))
                        .andReturn().getResponse().getContentAsString(),
                // Malformed token
                mockMvc.perform(asUser(get("/anomalies"), "Bearer not.a.token"))
                        .andReturn().getResponse().getContentAsString(),
        };

        for (String body : bodies) {
            assertThat(body)
                    .as("response body: %s", body)
                    .doesNotContain("com.rumi")
                    .doesNotContain("org.springframework")
                    .doesNotContain("java.lang")
                    .doesNotContain("\tat ")
                    .doesNotContain("select ")
                    .doesNotContain("refresh_tokens");
        }
    }

    @Test
    @DisplayName("Security headers are present on API responses")
    void securityHeadersArePresent() throws Exception {
        MvcResult result = mockMvc.perform(get("/anomalies")).andReturn();

        assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                .isEqualTo("nosniff");
        assertThat(result.getResponse().getHeader("Cache-Control")).isNotNull();
        assertThat(result.getResponse().getHeader("X-Frame-Options"))
                .isIn("DENY", "SAMEORIGIN");
    }

    @Test
    @DisplayName("CORS is closed by default and never reflects arbitrary origins")
    void corsIsClosedByDefault() throws Exception {
        MvcResult result = mockMvc.perform(get("/anomalies")
                        .header("Origin", "https://evil.example"))
                .andReturn();

        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }
}