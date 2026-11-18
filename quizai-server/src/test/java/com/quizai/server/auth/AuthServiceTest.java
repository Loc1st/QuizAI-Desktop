package com.quizai.server.auth;

import com.quizai.server.identity.AuthSessionRecord;
import com.quizai.server.identity.AuthSessionRepository;
import com.quizai.server.identity.UserRecord;
import com.quizai.server.identity.UserRepository;
import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-08T00:00:00Z");
    private final UserRepository users = mock(UserRepository.class);
    private final AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    private final PasswordEncoder passwords = new BCryptPasswordEncoder(4);
    private final SessionTokens tokens = new SessionTokens();
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, sessions, passwords, tokens,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(12));
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndGrantsTeacherImmediately() {
        var response = service.register(new AuthRequests.Register("  TEACHER@Example.COM  ", "password123", "TEACHER"));
        var captor = ArgumentCaptor.forClass(UserRecord.class);
        verify(users).insert(captor.capture());
        UserRecord stored = captor.getValue();
        assertEquals("teacher@example.com", response.email());
        assertEquals("TEACHER", stored.role());
        assertEquals("ACTIVE", stored.status());
        assertTrue(passwords.matches("password123", stored.passwordHash()));
        assertNotEquals("password123", stored.passwordHash());
        assertEquals(NOW, stored.createdAt());
        verifyNoInteractions(sessions);
    }

    @Test
    void duplicateRegistrationAndUtf8PasswordLimitAreRejected() {
        doThrow(new DuplicateKeyException("duplicate email")).when(users).insert(any());
        assertThrows(ApiException.class, () -> service.register(new AuthRequests.Register("a@example.com", "password123", "STUDENT")));
        reset(users);
        assertThrows(ApiException.class, () -> service.register(new AuthRequests.Register("a@example.com", "é".repeat(37), "STUDENT")));
        assertThrows(ApiException.class, () -> service.register(new AuthRequests.Register("a@example.com", "password123", "SUPERADMIN")));
        verifyNoInteractions(users, sessions);
    }

    @Test
    void loginStoresOnlyTokenDigestAndExpiryAndLogoutRevokesCurrentSession() {
        UserRecord user = activeUser();
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(user));
        var response = service.login(new AuthRequests.Login(" STUDENT@EXAMPLE.COM ", "password123", " device-1 "));
        var captor = ArgumentCaptor.forClass(AuthSessionRecord.class);
        verify(sessions).insert(captor.capture());
        AuthSessionRecord stored = captor.getValue();
        assertEquals(NOW.plus(Duration.ofHours(12)), response.expiresAt());
        assertEquals("device-1", stored.deviceSessionId());
        assertEquals(tokens.hash(response.token()), stored.sessionTokenHash());
        assertNotEquals(response.token(), stored.sessionTokenHash());
        assertNull(stored.revokedAt());
        SessionPrincipal principal = new SessionPrincipal(user.id(), user.email(), user.role(), stored.id(), stored.expiresAt());
        when(sessions.findActivePrincipal(stored.sessionTokenHash(), NOW)).thenReturn(Optional.of(principal));
        assertEquals(principal, service.authenticate(response.token()));
        service.logout(principal);
        verify(sessions).revoke(stored.id(), user.id(), NOW);
    }

    @Test
    void invalidCredentialsInactiveUsersAndMissingSessionsAreRejected() {
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(activeUser()));
        assertThrows(ApiException.class, () -> service.login(new AuthRequests.Login("student@example.com", "wrongpass", "device")));
        UserRecord active = activeUser();
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(new UserRecord(active.id(), active.email(), active.passwordHash(), active.role(), "DISABLED", NOW, NOW)));
        assertThrows(ApiException.class, () -> service.login(new AuthRequests.Login("student@example.com", "password123", "device")));
        when(sessions.findActivePrincipal(anyString(), eq(NOW))).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> service.authenticate(tokens.issue()));
        verify(sessions, never()).insert(any());
    }

    private UserRecord activeUser() {
        return new UserRecord(UUID.randomUUID(), "student@example.com", passwords.encode("password123"), "STUDENT", "ACTIVE", NOW, NOW);
    }
}
