package com.quizai.server.v2.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HTTP records for private study. Internal storage keys are never transport fields. */
public final class StudyDtos {
    private StudyDtos() { }
    public record Paste(String title, String text) { }
    public record Document(UUID id, String inputType, String fileName, String mimeType,
                           long sizeBytes, String extractionStatus, Instant createdAt) { }
    public record Generate(UUID documentId, String title, int count, UUID bankId,
                           UUID subjectId, UUID topicId, String difficulty) { }
    public record Generated(UUID quizId, List<UUID> questionVersionIds) { }
    public record PrivateQuiz(UUID id, String title, String status, int questionCount) { }
    public record Option(UUID id, int position, String text, Boolean correct) { }
    public record PracticeQuestion(UUID id, int position, String stem, List<Option> options, UUID selectedOptionId) { }
    public record Practice(UUID id, UUID quizId, String status, Instant startedAt, Instant finishedAt,
                           Integer correctCount, Integer totalQuestions, List<PracticeQuestion> questions) { }
    public record Answer(UUID selectedOptionId) { }
    public record ConversationRequest(String type, UUID documentId, UUID practiceSessionId) { }
    public record Conversation(UUID id, String type, UUID documentId, UUID practiceSessionId, Instant createdAt) { }
    public record MessageRequest(String message, UUID practiceQuestionId) { }
    public record Message(UUID id, String role, String content, Instant createdAt, UUID practiceQuestionId) { }
}
