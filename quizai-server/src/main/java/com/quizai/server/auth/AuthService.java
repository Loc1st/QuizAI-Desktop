package com.quizai.server.auth;

import com.quizai.server.identity.AuthSessionRecord;
import com.quizai.server.identity.AuthSessionRepository;
import com.quizai.server.identity.UserRecord;
import com.quizai.server.identity.UserRepository;
import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
@Profile({"dev", "test", "v2-dev", "v2-test"})
public class AuthService {
    private static final Set<String> ROLES = Set.of("STUDENT", "TEACHER");
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final SessionTokens tokens;
    private final Clock clock;
    private final Duration sessionTtl;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordEncoder passwords,
                       SessionTokens tokens, Clock clock,
                       @Value("${quizai.auth.session-ttl:PT12H}") Duration sessionTtl) {
        if (sessionTtl.isZero() || sessionTtl.isNegative()) {
            throw new IllegalArgumentException("Session TTL must be positive");
        }
        this.users = users;
        this.sessions = sessions;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponses.User register(AuthRequests.Register request) {
        validatePassword(request.password());
        if (request.role() == null || !ROLES.contains(request.role())) {
            throw new ApiException(400, "Choose STUDENT or TEACHER");
        }
        Instant now = clock.instant();
        UserRecord user = new UserRecord(UUID.randomUUID(), request.email(), passwords.encode(request.password()),
                request.role(), "ACTIVE", now, now);
        try {
            users.insert(user);
        } catch (DuplicateKeyException duplicate) {
            throw new ApiException(409, "An account with this email already exists");
        }
        return new AuthResponses.User(user.id(), user.email(), user.role());
    }

    @Transactional
    public AuthResponses.Login login(AuthRequests.Login request) {
        validatePassword(request.password());
        UserRecord user = users.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.passwordHash());
        if (user == null || !matches || !"ACTIVE".equals(user.status()) || !ROLES.contains(user.role())) {
            throw new ApiException(401, "Invalid email or password");
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(sessionTtl);
        String token = tokens.issue();
        sessions.insert(new AuthSessionRecord(UUID.randomUUID(), user.id(), tokens.hash(token),
                request.deviceSessionId(), now, expiresAt, null, null));
        return new AuthResponses.Login(token, expiresAt, new AuthResponses.User(user.id(), user.email(), user.role()));
    }

    @Transactional(readOnly = true)
    public SessionPrincipal authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new ApiException(401, "Invalid or expired session");
        }
        return sessions.findActivePrincipal(tokens.hash(token), clock.instant())
                .orElseThrow(() -> new ApiException(401, "Invalid or expired session"));
    }

    @Transactional
    public void logout(SessionPrincipal principal) {
        sessions.revoke(principal.sessionId(), principal.userId(), clock.instant());
    }

    private static void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "Password must contain at least 8 characters and at most 72 UTF-8 bytes");
        }
    }
}
