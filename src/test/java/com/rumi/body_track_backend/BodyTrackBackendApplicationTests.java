package com.rumi.body_track_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Without an explicit profile the application deliberately refuses to start:
// jwt.secret has no default, so a missing JWT_SECRET fails fast instead of
// silently signing tokens with a well-known key.
@SpringBootTest
@ActiveProfiles("test")
class BodyTrackBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
