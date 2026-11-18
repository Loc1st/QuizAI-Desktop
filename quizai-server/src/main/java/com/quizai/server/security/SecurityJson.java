package com.quizai.server.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Fixed, non-sensitive filter errors; request details and credentials are never echoed. */
final class SecurityJson {
    private SecurityJson() { }

    static void unauthorized(HttpServletResponse response) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, 401, "Authentication required");
    }

    static void write(HttpServletResponse response, int status, String safeMessage) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"message\":\"" + safeMessage + "\"}");
    }
}
