package com.rumi.body_track_backend.config;

import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.UserRepository;
import com.rumi.body_track_backend.security.AuthPrincipal;
import com.rumi.body_track_backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a bearer token into an {@link AuthPrincipal} on the {@code SecurityContext}.
 *
 * <p>Controllers read the principal from here via {@code @AuthenticationPrincipal} and
 * never re-parse the header. That consolidation is the actual fix for the previous
 * design: two independent authentication paths existed, and the one used by the
 * controllers did not check the token type, so a refresh token authenticated
 * successfully.
 *
 * <p>The user row is reloaded on every request rather than trusted from the token
 * body. A token therefore stops working the moment its owner is deleted, and the
 * id inside the token is confirmed against the database before it is used for
 * authorization.
 *
 * <p>Deliberately not a {@code @Component}: this filter is placed in the chain
 * explicitly by {@link SecurityConfig} so its position is declared rather than
 * left to container ordering. Registering it as both a bean and an ordered chain
 * member makes Spring Security refuse to start the context.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // No credentials presented. The authorization rules decide what happens
            // next; this filter never rejects on its own.
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        AuthPrincipal principal = authenticate(token);
        if (principal == null) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    @Transactional(readOnly = true)
    AuthPrincipal authenticate(String token) {
        Long userId;
        try {
            userId = jwtService.extractUserId(token);
        } catch (RuntimeException rejected) {
            // Already logged by JwtService. Leave the context empty so the entry
            // point produces a 401 rather than the request proceeding unverified.
            log.info("bearer_token_rejected path={}", "auth");
            return null;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.info("token_for_missing_user user_id={}", userId);
            return null;
        }

        return new AuthPrincipal(user.getId(), user.getEmail());
    }
}