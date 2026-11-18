package com.quizai.common.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Private study HTTP views. No private file paths or server credentials are exposed. */
public final class StudyModels {
    private StudyModels() { }
    public record Document(UUID id,String inputType,String fileName,String mimeType,long sizeBytes,String extractionStatus,Instant createdAt) { }
    public record Generated(UUID quizId,List<UUID> questionVersionIds) { }
    public record PrivateQuiz(UUID id,String title,String status,int questionCount) { }
    public record PracticeOption(UUID id,int position,String text,Boolean correct) { }
    public record PracticeQuestion(UUID id,int position,String stem,List<PracticeOption> options,UUID selectedOptionId) { }
    public record Practice(UUID id,UUID quizId,String status,Instant startedAt,Instant finishedAt,Integer correctCount,Integer totalQuestions,List<PracticeQuestion> questions) { }
    public record Conversation(UUID id,String type,UUID documentId,UUID practiceSessionId,Instant createdAt) { }
    public record Message(UUID id,String role,String content,Instant createdAt,UUID practiceQuestionId) { }
}
