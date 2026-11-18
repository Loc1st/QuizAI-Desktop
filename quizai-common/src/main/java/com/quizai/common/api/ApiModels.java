package com.quizai.common.api;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Transport views only; these types contain no persistence or server implementation. */
public final class ApiModels {
    private ApiModels() { }

    public record User(UUID id, String email, String role) { }
    public record ClassView(UUID id, UUID teacherId, String name, String status,
                            boolean joinEnabled, String joinCode, UUID membershipId, Instant joinedAt) { }
    public record Member(UUID membershipId, UUID studentId, String email, Instant joinedAt) { }
    public record Bank(UUID id, String name, Instant archivedAt, Instant createdAt) { }
    public record QuestionOption(UUID id, int position, String text, boolean correct) { }
    public record Question(UUID questionId, UUID versionId, UUID bankId, int versionNo, String stem,
                           UUID subjectId, UUID topicId, String difficulty, String approvalStatus,
                           String origin, List<QuestionOption> options) { }
    public record Quiz(UUID id, String title, String status, List<UUID> questionVersionIds) { }
    public record Assignment(UUID id, UUID classId, UUID quizId, String title, Instant opensAt,
                             Instant closesAt, int durationMinutes, int maxAttempts, String status,
                             Instant scoreReleaseAt, Instant answerReleaseAt, String answerReleaseMode) { }
    public record StudentAssignment(UUID id, UUID recipientId, UUID classId, String title, Instant opensAt,
                                    Instant closesAt, int durationMinutes, int maxAttempts, int attemptsUsed,
                                    boolean canStart, UUID activeAttemptId, BigDecimal bestScore,
                                    boolean scoreAvailable, boolean answerAvailable) { }
    public record SafeOption(String key, String text) { }
    public record ExamQuestion(UUID id, int position, String stem, List<SafeOption> options,
                               String selectedOptionKey, long answerRevision, Instant serverSavedAt,
                               String correctOptionKey) { }
    public record Attempt(UUID id, UUID assignmentId, int attemptNo, String status, Instant startedAt,
                          Instant deadlineAt, Instant submittedAt, Instant serverTime,
                          boolean scoreAvailable, boolean answerAvailable, Integer earnedPoints,
                          Integer possiblePoints, BigDecimal normalizedScore, List<ExamQuestion> questions) { }
    public record SavedAnswer(UUID assignmentQuestionId, String selectedOptionKey,
                              long answerRevision, Instant serverSavedAt) { }
    public record ResultAttempt(UUID id, int attemptNo, String status, Instant startedAt,
                                Instant deadlineAt, Instant submittedAt, Integer earnedPoints,
                                Integer possiblePoints, BigDecimal normalizedScore) { }
    public record StudentResult(UUID studentId, String email, int attemptsUsed, BigDecimal bestScore,
                                List<ResultAttempt> attempts) { }
    public record StudentStudySummary(UUID studentId, String email, long completedSessions,
                                      long totalQuestions, long correctAnswers, BigDecimal accuracy) { }
    public record CatalogItem(UUID id, String code, String name, UUID subjectId) { }
}
