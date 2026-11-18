package com.quizai.server.v2.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Explicit REST projections. Database snapshots and their hidden keys are never serialized. */
public final class ExamDtos {
    private ExamDtos() {}

    public record BankRequest(String name) {}
    public record Bank(UUID id, String name, Instant archivedAt, Instant createdAt) {}
    public record OptionInput(String text, boolean correct) {}
    public record QuestionRequest(UUID bankId, String stem, UUID subjectId, UUID topicId,
                                  String difficulty, List<OptionInput> options) {}
    public record QuestionOption(UUID id, int position, String text, boolean correct) {}
    public record Question(UUID questionId, UUID versionId, UUID bankId, int versionNo, String stem,
                           UUID subjectId, UUID topicId, String difficulty, String approvalStatus,
                           String origin, List<QuestionOption> options) {}
    public record QuizRequest(String title, List<UUID> questionVersionIds) {}
    public record Quiz(UUID id, String title, String status, List<UUID> questionVersionIds) {}
    public record AssignmentRequest(UUID classId, UUID quizId, String title, Instant opensAt,
                                    Instant closesAt, Integer durationMinutes, Integer maxAttempts,
                                    Instant scoreReleaseAt, Instant answerReleaseAt, String answerReleaseMode,
                                    List<UUID> studentIds) {}
    public record ReleasesRequest(Instant scoreReleaseAt, Instant answerReleaseAt, String answerReleaseMode) {}
    public record Assignment(UUID id, UUID classId, UUID quizId, String title, Instant opensAt,
                             Instant closesAt, int durationMinutes, int maxAttempts, String status,
                             Instant scoreReleaseAt, Instant answerReleaseAt, String answerReleaseMode) {}
    public record StudentAssignment(UUID id, UUID recipientId, UUID classId, String title,
                                    Instant opensAt, Instant closesAt, int durationMinutes, int maxAttempts,
                                    int attemptsUsed, boolean canStart, UUID activeAttemptId,
                                    BigDecimal bestScore, boolean scoreAvailable, boolean answerAvailable) {}
    public record StartRequest(UUID startRequestId) {}
    public record SubmitRequest(UUID submitRequestId) {}
    public record AnswerRequest(String selectedOptionKey, Long answerRevision) {}
    public record SavedAnswer(UUID assignmentQuestionId, String selectedOptionKey, long answerRevision,
                              Instant serverSavedAt) {}
    public record StudentOption(String key, String text) {}
    public record AttemptQuestion(UUID id, int position, String stem, List<StudentOption> options,
                                  String selectedOptionKey, long answerRevision, Instant serverSavedAt,
                                  String correctOptionKey) {}
    public record Attempt(UUID id, UUID assignmentId, int attemptNo, String status,
                          Instant startedAt, Instant deadlineAt, Instant submittedAt, Instant serverTime,
                          boolean scoreAvailable, boolean answerAvailable, Integer earnedPoints,
                          Integer possiblePoints, BigDecimal normalizedScore, List<AttemptQuestion> questions) {}
    public record AttemptResult(UUID id, int attemptNo, String status, Instant startedAt,
                                Instant deadlineAt, Instant submittedAt, Integer earnedPoints,
                                Integer possiblePoints, BigDecimal normalizedScore) {}
    public record StudentResult(UUID studentId, String email, int attemptsUsed, BigDecimal bestScore,
                                List<AttemptResult> attempts) {}
}
