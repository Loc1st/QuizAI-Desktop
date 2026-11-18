package com.quizai.server.catalog;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {
    private final CatalogRepository repository = mock(CatalogRepository.class);
    private final CatalogService service = new CatalogService(repository);
    private final SessionPrincipal principal = new SessionPrincipal(UUID.randomUUID(), "student@example.invalid",
            "STUDENT", UUID.randomUUID(), Instant.parse("2026-11-08T09:00:00Z"));

    @Test
    void missingOrInactiveSubjectReturns404InsteadOfAnEmptyTopicList() {
        UUID subjectId = UUID.randomUUID();
        when(repository.activeSubjectExists(subjectId)).thenReturn(false);

        assertEquals(404, assertThrows(ApiException.class, () -> service.topics(principal, subjectId)).status());
        verify(repository, never()).findActiveTopics(subjectId);
    }

    @Test
    void activeSubjectReturnsItsActiveTopics() {
        UUID subjectId = UUID.randomUUID();
        List<TopicView> topics = List.of(new TopicView(UUID.randomUUID(), subjectId, "TCP", "TCP transport"));
        when(repository.activeSubjectExists(subjectId)).thenReturn(true);
        when(repository.findActiveTopics(subjectId)).thenReturn(topics);

        assertEquals(topics, service.topics(principal, subjectId));
    }

    @Test
    void bothAcceptedRolesCanReadTheCatalogAndAnonymousCallsAreRejected() {
        List<SubjectView> subjects = List.of(new SubjectView(UUID.randomUUID(), "NET", "Networking", 1));
        when(repository.findActiveSubjects()).thenReturn(subjects);
        assertEquals(subjects, service.subjects(principal));
        SessionPrincipal teacher = new SessionPrincipal(UUID.randomUUID(), "teacher@example.invalid", "TEACHER",
                UUID.randomUUID(), principal.expiresAt());
        assertEquals(subjects, service.subjects(teacher));
        assertEquals(401, assertThrows(ApiException.class, () -> service.subjects(null)).status());
    }
}
