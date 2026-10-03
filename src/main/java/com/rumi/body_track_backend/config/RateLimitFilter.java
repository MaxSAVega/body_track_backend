package com.rumi.body_track_backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rumi.body_track_backend.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiting for the endpoints that are cheap to brute-force and
 * expensive to guess.
 *
 * <p>Deliberately in-memory and without a new dependency. The trade-off is stated
 * plainly: this is per-JVM, so it becomes bypassable the moment Spring Boot runs on
 * more than one instance. That is acceptable for a single-instance deployment; the
 * moment the topology changes the bucket has to move to shared storage. The filter
 * is isolated behind one class so that swap touches nothing else.
 *
 * <p>Two independent keys are counted per request: the client address, and for
 * credential endpoints the submitted identifier. A single key alone would let one
 * attacker spray many accounts from one address, while the identifier alone would
 * let one account be sprayed from many addresses.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final int MAX_IDENTIFIER_BODY_BYTES = 4096;

    // Overridable so the thresholds can be tuned per environment without a
    // rebuild, and so the throttling suite can exercise a deliberately tiny
    // budget instead of competing with every other test for the same counters.
    private final int loginLimit;
    private final long loginWindowMs;
    private final int registerLimit;
    private final long registerWindowMs;
    private final int refreshLimit;
    private final long refreshWindowMs;
    private final int uploadLimit;
    private final long uploadWindowMs;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.login.max:10}") int loginLimit,
                           @Value("${app.rate-limit.login.window-ms:60000}") long loginWindowMs,
                           @Value("${app.rate-limit.register.max:5}") int registerLimit,
                           @Value("${app.rate-limit.register.window-ms:3600000}") long registerWindowMs,
                           @Value("${app.rate-limit.refresh.max:30}") int refreshLimit,
                           @Value("${app.rate-limit.refresh.window-ms:300000}") long refreshWindowMs,
                           @Value("${app.rate-limit.upload.max:20}") int uploadLimit,
                           @Value("${app.rate-limit.upload.window-ms:300000}") long uploadWindowMs) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.loginLimit = loginLimit;
        this.loginWindowMs = loginWindowMs;
        this.registerLimit = registerLimit;
        this.registerWindowMs = registerWindowMs;
        this.refreshLimit = refreshLimit;
        this.refreshWindowMs = refreshWindowMs;
        this.uploadLimit = uploadLimit;
        this.uploadWindowMs = uploadWindowMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        Rule rule = ruleFor(path);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // The body has to be buffered to read the identifier, so the request is
        // wrapped and re-readable; otherwise Jackson would see an empty stream.
        CachedBodyRequest wrapped = new CachedBodyRequest(request);

        String address = clientAddress(request);
        if (exceeded("addr:" + rule.name() + ':' + address, rule)) {
            reject(request, response, rule.name(), "address");
            return;
        }

        String identifier = submittedIdentifier(wrapped, objectMapper);
        if (identifier != null && exceeded("id:" + rule.name() + ':' + identifier, rule)) {
            reject(request, response, rule.name(), "identifier");
            return;
        }

        filterChain.doFilter(wrapped, response);
    }

    private Rule ruleFor(String path) {
        if (path.endsWith("/auth/login")) {
            return new Rule("login", loginLimit, loginWindowMs);
        }
        if (path.endsWith("/auth/register")) {
            return new Rule("register", registerLimit, registerWindowMs);
        }
        if (path.endsWith("/auth/refresh")) {
            return new Rule("refresh", refreshLimit, refreshWindowMs);
        }
        if (path.contains("/anomalies/") && path.endsWith("/image")) {
            return new Rule("upload", uploadLimit, uploadWindowMs);
        }
        return null;
    }

    /**
     * Extracts the submitted email without consuming the body from the rest of the
     * chain. Anything unreadable is simply not counted, because a malformed body is
     * the validation layer's concern and not a brute-force signal.
     */
    private String submittedIdentifier(CachedBodyRequest request, ObjectMapper mapper) {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            return null;
        }

        byte[] body;
        try {
            body = request.getCachedBody();
        } catch (IOException e) {
            return null;
        }
        if (body.length == 0 || body.length > MAX_IDENTIFIER_BODY_BYTES) {
            return null;
        }

        try {
            JsonNode root = mapper.readTree(body);
            if (root == null) {
                return null;
            }
            JsonNode email = root.get("email");
            if (email != null && email.isTextual()) {
                String value = email.asText().trim().toLowerCase(Locale.ROOT);
                return value.length() <= 254 ? value : null;
            }
        } catch (Exception unreadable) {
            return null;
        }
        return null;
    }

    private boolean exceeded(String key, Rule rule) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.startedAt >= rule.windowMs) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        return window.count.get() > rule.limit;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        String ruleName, String scope) throws IOException {
        log.warn("rate_limit_exceeded rule={} scope={} path={} ip={}",
                ruleName, scope, request.getRequestURI(), clientAddress(request));

        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.builder()
                .code("rate_limited")
                .message("Demasiados intentos. Inténtalo de nuevo más tarde.")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build());
    }

    /**
     * Reads the *last* entry of {@code X-Forwarded-For}. The convention is that the
     * left-most is the original client, but when several trusted proxies append, the
     * right-most is the one actually attached to this hop. Taking the right-most
     * means a client can only ever claim addresses it genuinely received, and it
     * keeps working behind one proxy as well as several.
     */
    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String last = comma >= 0 ? forwarded.substring(comma + 1) : forwarded;
            return last.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    /** Drops idle windows so the map cannot grow without bound. */
    @jakarta.annotation.PostConstruct
    void startJanitor() {
        Thread janitor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(600_000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long cutoff = System.currentTimeMillis() - 3_600_000L;
                windows.entrySet().removeIf(e -> e.getValue().startedAt < cutoff);
            }
        }, "rate-limit-janitor");
        janitor.setDaemon(true);
        janitor.start();
    }

    private record Rule(String name, int limit, long windowMs) {
    }

    private static final class Window {
        private final long startedAt;
        private final AtomicInteger count;

        private Window(long startedAt, AtomicInteger count) {
            this.startedAt = startedAt;
            this.count = count;
        }
    }

    /** Buffers the body once so several readers can consume it. */
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private byte[] cached;

        private CachedBodyRequest(HttpServletRequest request) {
            super(request);
        }

        private byte[] getCachedBody() throws IOException {
            if (cached == null) {
                cached = super.getInputStream().readAllBytes();
            }
            return cached;
        }

        @Override
        public ServletInputStream getInputStream() {
            byte[] body;
            try {
                body = getCachedBody();
            } catch (IOException e) {
                body = new byte[0];
            }
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            byte[] body;
            try {
                body = getCachedBody();
            } catch (IOException e) {
                body = new byte[0];
            }
            return new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }
}