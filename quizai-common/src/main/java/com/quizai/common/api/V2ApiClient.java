package com.quizai.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizai.common.api.ApiModels.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** REST-only V2 transport. No persistence, scoring or authorization decisions live here. */
public final class V2ApiClient {
    private final QuizApiClient client;
    V2ApiClient(QuizApiClient client) { this.client = client; }

    public CompletableFuture<List<CatalogItem>> subjects() {
        return client.authenticated("GET", "/api/catalog/subjects", null).thenApply(n -> array(n,this::catalog));
    }
    public CompletableFuture<List<CatalogItem>> topics(UUID subjectId) {
        return client.authenticated("GET", "/api/catalog/subjects/" + subjectId + "/topics", null).thenApply(n -> array(n,this::catalog));
    }
    public CompletableFuture<CatalogItem> createSubject(String code,String name) {
        return client.authenticated("POST", "/api/catalog/subjects", Map.of("code",code,"name",name)).thenApply(this::catalog);
    }
    public CompletableFuture<CatalogItem> createTopic(UUID subjectId,String code,String name) {
        return client.authenticated("POST", "/api/catalog/subjects/" + subjectId + "/topics", Map.of("code",code,"name",name)).thenApply(this::catalog);
    }

    public CompletableFuture<List<Bank>> banks() { return list("/teacher/banks", this::bank); }
    public CompletableFuture<Bank> createBank(String name) { return post("/teacher/banks", Map.of("name", name), this::bank); }
    public CompletableFuture<List<Question>> questions(UUID bankId) {
        return list("/teacher/questions" + (bankId == null ? "" : "?bankId=" + bankId), this::question);
    }
    public CompletableFuture<List<Question>> versions(UUID questionId) {
        return list("/teacher/questions/" + questionId + "/versions", this::question);
    }
    public CompletableFuture<List<StudentStudySummary>> studySummary(UUID classId) {
        return client.authenticated("GET", "/api/classes/" + classId + "/study-summary", null)
                .thenApply(n -> array(n, value -> new StudentStudySummary(id(value,"studentId"), str(value,"email"),
                        value.path("completedSessions").asLong(), value.path("totalQuestions").asLong(),
                        value.path("correctAnswers").asLong(), decimal(value,"accuracy"))));
    }
    public CompletableFuture<Question> createQuestion(Map<String, Object> body) { return post("/teacher/questions", body, this::question); }
    public CompletableFuture<Question> createVersion(UUID questionId, Map<String, Object> body) {
        return post("/teacher/questions/" + questionId + "/versions", body, this::question);
    }
    public CompletableFuture<Question> approve(Question question) {
        return post("/teacher/questions/" + question.questionId() + "/versions/" + question.versionId() + "/approve", null, this::question);
    }
    public CompletableFuture<List<Quiz>> quizzes() { return list("/teacher/quizzes", this::quiz); }
    public CompletableFuture<Quiz> createQuiz(String title, List<UUID> versions) {
        return post("/teacher/quizzes", Map.of("title", title, "questionVersionIds", versions), this::quiz);
    }
    public CompletableFuture<Quiz> updateQuiz(UUID id,String title,List<UUID> versions) {
        return client.authenticated("PUT","/api/v2/teacher/quizzes/"+id,Map.of("title",title,"questionVersionIds",versions)).thenApply(this::quiz);
    }
    public CompletableFuture<List<Assignment>> assignments() { return list("/teacher/assignments", this::assignment); }
    public CompletableFuture<Assignment> createAssignment(Map<String, Object> body) { return post("/teacher/assignments", body, this::assignment); }
    public CompletableFuture<Assignment> publish(UUID id) { return post("/teacher/assignments/" + id + "/publish", null, this::assignment); }
    public CompletableFuture<Assignment> releases(UUID id, Map<String, Object> body) {
        return client.authenticated("PUT", "/api/v2/teacher/assignments/" + id + "/releases", body).thenApply(this::assignment);
    }
    public CompletableFuture<List<StudentResult>> results(UUID id) {
        return list("/teacher/assignments/" + id + "/results", n -> new StudentResult(id(n,"studentId"), str(n,"email"),
                integer(n,"attemptsUsed"), decimal(n,"bestScore"), array(n.path("attempts"), this::resultAttempt)));
    }
    public CompletableFuture<List<StudentAssignment>> studentAssignments() {
        return list("/student/assignments", n -> new StudentAssignment(id(n,"id"), id(n,"recipientId"), id(n,"classId"),
                str(n,"title"), time(n,"opensAt"), time(n,"closesAt"), integer(n,"durationMinutes"), integer(n,"maxAttempts"),
                integer(n,"attemptsUsed"), n.path("canStart").asBoolean(), optionalId(n,"activeAttemptId"), decimal(n,"bestScore"),
                n.path("scoreAvailable").asBoolean(), n.path("answerAvailable").asBoolean()));
    }
    public CompletableFuture<Attempt> start(UUID assignmentId, UUID requestId) {
        return post("/student/assignments/" + assignmentId + "/attempts", Map.of("startRequestId", requestId), this::parseAttempt);
    }
    public CompletableFuture<List<Attempt>> studentAttempts(UUID assignmentId) {
        return list("/student/assignments/" + assignmentId + "/attempts", this::parseAttempt);
    }
    public CompletableFuture<List<ExamQuestion>> review(UUID assignmentId) {
        return list("/student/assignments/" + assignmentId + "/review", this::examQuestion);
    }
    public CompletableFuture<Attempt> attempt(UUID id) {
        return client.authenticated("GET", "/api/v2/student/attempts/" + id, null).thenApply(this::parseAttempt);
    }
    public CompletableFuture<SavedAnswer> save(UUID attemptId, UUID questionId, String key, long revision) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("selectedOptionKey", key); body.put("answerRevision", revision);
        return client.authenticated("PUT", "/api/v2/student/attempts/" + attemptId + "/answers/" + questionId, body)
                .thenApply(n -> {
                    if(!n.hasNonNull("answerRevision")||!n.get("answerRevision").isIntegralNumber()||!n.hasNonNull("serverSavedAt"))throw invalid();
                    SavedAnswer ack=new SavedAnswer(id(n,"assignmentQuestionId"),nullable(n,"selectedOptionKey"),n.get("answerRevision").asLong(),time(n,"serverSavedAt"));
                    if(!questionId.equals(ack.assignmentQuestionId())||ack.answerRevision()<revision||ack.serverSavedAt()==null)throw invalid();
                    return ack;
                });
    }
    public CompletableFuture<Attempt> submit(UUID attemptId, UUID requestId) {
        return post("/student/attempts/" + attemptId + "/submit", Map.of("submitRequestId", requestId), this::parseAttempt);
    }

    private <T> CompletableFuture<List<T>> list(String path, Function<JsonNode,T> mapper) {
        return client.authenticated("GET", "/api/v2" + path, null).thenApply(n -> array(n, mapper));
    }
    private <T> CompletableFuture<T> post(String path, Object body, Function<JsonNode,T> mapper) {
        return client.authenticated("POST", "/api/v2" + path, body).thenApply(mapper);
    }
    private Bank bank(JsonNode n) { return new Bank(id(n,"id"),str(n,"name"),time(n,"archivedAt"),time(n,"createdAt")); }
    private CatalogItem catalog(JsonNode n) { return new CatalogItem(id(n,"id"),str(n,"code"),str(n,"name"),optionalId(n,"subjectId")); }
    private Question question(JsonNode n) {
        return new Question(id(n,"questionId"),id(n,"versionId"),optionalId(n,"bankId"),integer(n,"versionNo"),str(n,"stem"),
                optionalId(n,"subjectId"),optionalId(n,"topicId"),str(n,"difficulty"),str(n,"approvalStatus"),str(n,"origin"),
                array(n.path("options"), o -> new QuestionOption(id(o,"id"),integer(o,"position"),str(o,"text"),o.path("correct").asBoolean())));
    }
    private Quiz quiz(JsonNode n) { return new Quiz(id(n,"id"),str(n,"title"),str(n,"status"),array(n.path("questionVersionIds"),v -> UUID.fromString(v.asText()))); }
    private Assignment assignment(JsonNode n) {
        return new Assignment(id(n,"id"),id(n,"classId"),id(n,"quizId"),str(n,"title"),time(n,"opensAt"),time(n,"closesAt"),
                integer(n,"durationMinutes"),integer(n,"maxAttempts"),str(n,"status"),time(n,"scoreReleaseAt"),time(n,"answerReleaseAt"),str(n,"answerReleaseMode"));
    }
    private Attempt parseAttempt(JsonNode n) {
        return new Attempt(id(n,"id"),id(n,"assignmentId"),integer(n,"attemptNo"),str(n,"status"),time(n,"startedAt"),time(n,"deadlineAt"),
                time(n,"submittedAt"),time(n,"serverTime"),n.path("scoreAvailable").asBoolean(),n.path("answerAvailable").asBoolean(),
                optionalInt(n,"earnedPoints"),optionalInt(n,"possiblePoints"),decimal(n,"normalizedScore"),
                array(n.path("questions"),this::examQuestion));
    }
    private ExamQuestion examQuestion(JsonNode q) { return new ExamQuestion(id(q,"id"),integer(q,"position"),str(q,"stem"),
            array(q.path("options"),o -> new SafeOption(str(o,"key"),str(o,"text"))),nullable(q,"selectedOptionKey"),
            q.path("answerRevision").asLong(),time(q,"serverSavedAt"),nullable(q,"correctOptionKey")); }
    private ResultAttempt resultAttempt(JsonNode n) {
        return new ResultAttempt(id(n,"id"),integer(n,"attemptNo"),str(n,"status"),time(n,"startedAt"),time(n,"deadlineAt"),
                time(n,"submittedAt"),optionalInt(n,"earnedPoints"),optionalInt(n,"possiblePoints"),decimal(n,"normalizedScore"));
    }
    private static <T> List<T> array(JsonNode n, Function<JsonNode,T> mapper) {
        if (!n.isArray()) throw invalid();
        List<T> result = new ArrayList<>(); n.forEach(v -> result.add(mapper.apply(v))); return List.copyOf(result);
    }
    private static String str(JsonNode n, String key) { String value=nullable(n,key); if(value==null) throw invalid(); return value; }
    private static String nullable(JsonNode n, String key) { JsonNode v=n.get(key); return v==null||v.isNull()?null:v.asText(); }
    private static UUID id(JsonNode n,String key) { return UUID.fromString(str(n,key)); }
    private static UUID optionalId(JsonNode n,String key) { String v=nullable(n,key); return v==null?null:UUID.fromString(v); }
    private static Instant time(JsonNode n,String key) { String v=nullable(n,key); return v==null?null:Instant.parse(v); }
    private static int integer(JsonNode n,String key) { return n.path(key).asInt(); }
    private static Integer optionalInt(JsonNode n,String key) { return n.hasNonNull(key)?n.get(key).asInt():null; }
    private static BigDecimal decimal(JsonNode n,String key) { return n.hasNonNull(key)?n.get(key).decimalValue():null; }
    private static ApiException invalid() { return new ApiException(0,"Phản hồi máy chủ không hợp lệ. Vui lòng thử lại."); }
}
