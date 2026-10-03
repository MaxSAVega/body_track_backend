package com.rumi.body_track_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumi.body_track_backend.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.rumi.body_track_backend.repository.UserRepository;
import com.rumi.body_track_backend.service.JwtService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Authorization rules and the response shape for unauthenticated / forbidden calls.
 *
 * <p>Three changes matter here:
 * <ul>
 *   <li>{@code /anomalies/{id}/image} is no longer public. It was {@code permitAll}
 *       while the controller performed no ownership check, which made every
 *       clinical photograph in the system retrievable by iterating an id.</li>
 *   <li>Only the three genuinely pre-authentication routes are permitted; the rest
 *       of {@code /auth/**} is not blanket-open, so a new endpoint added under that
 *       prefix is authenticated by default.</li>
 *   <li>Failures return JSON with the standard error shape instead of the
 *       framework's HTML or empty 403 body.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RateLimitFilter rateLimitFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtService jwtService,
                          UserRepository userRepository,
                          RateLimitFilter rateLimitFilter,
                          ObjectMapper objectMapper,
                          @Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.rateLimitFilter = rateLimitFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = allowedOrigins == null || allowedOrigins.isBlank()
                ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .filter(origin -> !origin.isEmpty()).toList();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Correct for a stateless Bearer-token API: there is no cookie for a
                // cross-site request to ride on.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Everything else, including every /anomalies route and the
                        // image endpoint, requires a valid access token.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, request.getRequestURI(),
                                        HttpStatus.UNAUTHORIZED, "unauthorized",
                                        "Credenciales inválidas o sesión expirada"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, request.getRequestURI(),
                                        HttpStatus.FORBIDDEN, "forbidden",
                                        "No tienes permiso para esta operación")))
                .headers(headers -> headers
                        // Stops a stored image from being re-interpreted as script.
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.NO_REFERRER))
                        .cacheControl(cache -> cache.disable()))

                .addFilterBefore(new JwtAuthFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class)
                // Rate limiting runs before authentication so that unauthenticated
                // flooding is bounded too.
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class);

        return http.build();
    }

    /**
     * Credentials are disabled entirely unless origins are configured explicitly.
     *
     * <p>A native Flutter client is not subject to CORS, so the previous
     * {@code allowedOriginPatterns("*") + allowCredentials(true)} combination only
     * widened browser reach to the API without enabling anything the app needed.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (!allowedOrigins.isEmpty()) {
            configuration.setAllowedOrigins(allowedOrigins);
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
            configuration.setAllowCredentials(false);
            configuration.setMaxAge(3600L);
        } else {
            configuration.setAllowedOrigins(List.of());
        }

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, String path,
                            HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        // Without an explicit charset the container defaults to ISO-8859-1 and the
        // accented characters in the Spanish messages arrive as "inv?lidas".
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .code(code)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}