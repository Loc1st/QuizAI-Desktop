package com.quizai.server.v2.ai;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;

final class StudyAccess {
    static void authenticated(SessionPrincipal actor) {
        if (actor == null) throw new ApiException(401, "Authentication is required");
        if (!"STUDENT".equals(actor.role()) && !"TEACHER".equals(actor.role())) throw new ApiException(403, "Access denied");
    }
    static void student(SessionPrincipal actor) {
        authenticated(actor);
        if (!"STUDENT".equals(actor.role())) throw new ApiException(403, "Student access required");
    }
    static String text(String value, int limit, String label) {
        if (value == null || value.isBlank() || value.length() > limit || value.indexOf('\0') >= 0) {
            throw new ApiException(400, label + " is required and must be at most " + limit + " characters");
        }
        return value.strip();
    }
    static ApiException missing() { return new ApiException(404, "Resource not found"); }
}
