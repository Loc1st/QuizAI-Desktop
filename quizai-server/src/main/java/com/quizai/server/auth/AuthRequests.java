package com.quizai.server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public final class AuthRequests {
    private AuthRequests() { }

    public record Register(@NotBlank @Email @Size(max = 254) String email,
                           @NotBlank @Size(min = 8, max = 72) String password,
                           @NotBlank @Pattern(regexp = "STUDENT|TEACHER") String role) {
        public Register {
            email = normalizeEmail(email);
        }

        @Override
        public String toString() {
            return "Register[password=[REDACTED], role=" + role + "]";
        }
    }

    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(min = 8, max = 72) String password,
                        @NotBlank @Size(max = 200) String deviceSessionId) {
        public Login {
            email = normalizeEmail(email);
            deviceSessionId = deviceSessionId == null ? null : deviceSessionId.strip();
        }

        @Override
        public String toString() {
            return "Login[password=[REDACTED]]";
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
