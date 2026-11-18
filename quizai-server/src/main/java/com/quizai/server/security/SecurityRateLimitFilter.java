package com.quizai.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded process-local protection for password and class-code guessing in DEV/TEST. */
final class SecurityRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_BUCKETS = 10_000;
    private static final long WINDOW_MILLIS = 60_000;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    SecurityRateLimitFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        int limit = limit(request);
        if (limit != 0 && !allow(request.getRemoteAddr() + ':' + request.getServletPath(), limit)) {
            response.setHeader("Retry-After", "60");
            SecurityJson.write(response, 429, "Too many requests; try again shortly");
            return;
        }
        chain.doFilter(request, response);
    }

    private int limit(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return 0;
        }
        return switch (request.getServletPath()) {
            case "/api/auth/register" -> 5;
            case "/api/auth/login" -> 20;
            case "/api/classes/join" -> 30;
            default -> 0;
        };
    }

    private synchronized boolean allow(String key, int limit) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || now - window.startedAt >= WINDOW_MILLIS) {
            if (windows.size() >= MAX_BUCKETS) {
                windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= WINDOW_MILLIS);
                // Refuse new buckets at capacity; never evict a live guessing limit.
                if (windows.size() >= MAX_BUCKETS && window == null) {
                    return false;
                }
            }
            window = new Window(now);
            windows.put(key, window);
        }
        return ++window.count <= limit;
    }

    private static final class Window {
        private final long startedAt;
        private int count;

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
