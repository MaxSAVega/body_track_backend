package com.rumi.body_track_backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Keeps the suite fast.
 *
 * <p>A real BCrypt at default strength costs roughly 100ms per call, which is
 * what makes brute-force expensive in production and what would make an
 * enumeration-timing test unusable here. The production encoder is untouched;
 * only the test profile substitutes a cheaper one.
 */
@TestConfiguration
public class TestBeans {

    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder(4);
    }
}