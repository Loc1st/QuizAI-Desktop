package com.quizai.server.catalog;

import com.quizai.server.security.SessionPrincipal;
import com.quizai.server.web.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Profile({"dev", "test"})
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogRepository repository;

    public CatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    public List<SubjectView> subjects(SessionPrincipal principal) {
        authorize(principal);
        return repository.findActiveSubjects();
    }

    public List<TopicView> topics(SessionPrincipal principal, UUID subjectId) {
        authorize(principal);
        if (!repository.activeSubjectExists(subjectId)) {
            throw new ApiException(404, "Subject not found");
        }
        return repository.findActiveTopics(subjectId);
    }

    private static void authorize(SessionPrincipal principal) {
        if (principal == null) throw new ApiException(401, "Authentication required");
        if (!"STUDENT".equals(principal.role()) && !"TEACHER".equals(principal.role())) {
            throw new ApiException(403, "Access denied");
        }
    }
}
