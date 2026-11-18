package com.quizai.common.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TeacherV2ApiClientTest {
    private HttpServer server;
    private QuizApiClient client;
    private V2ApiClient api;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OLD_VERSION = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID NEW_VERSION = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private int historyStatus = 200;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", e -> reply(e, 200, "{\"token\":\"teacher-memory-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"" + ID + "\",\"email\":\"teacher@example.test\",\"role\":\"TEACHER\"}}"));
        server.createContext("/api/v2/teacher/questions/" + ID + "/versions", e -> {
            authorization.set(e.getRequestHeaders().getFirst("Authorization"));
            assertEquals("GET", e.getRequestMethod());
            reply(e, historyStatus, historyStatus == 200 ? "[" + question(NEW_VERSION, 2, "DRAFT") + "," + question(OLD_VERSION, 1, "APPROVED") + "]" : "{\"message\":\"Forbidden\"}");
        });
        server.createContext("/api/classes/" + ID + "/study-summary", e -> {
            authorization.set(e.getRequestHeaders().getFirst("Authorization"));
            assertEquals("GET", e.getRequestMethod());
            reply(e, 200, "[{\"studentId\":\"" + ID + "\",\"email\":\"active@example.test\",\"completedSessions\":2,\"totalQuestions\":10,\"correctAnswers\":7,\"accuracy\":0.7},{\"studentId\":\"" + OLD_VERSION + "\",\"email\":\"new@example.test\",\"completedSessions\":0,\"totalQuestions\":0,\"correctAnswers\":0,\"accuracy\":null}]");
        });
        server.start();
        client = new QuizApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "TEACHER");
        api = client.v2();
        client.login("teacher@example.test", "secret").join();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void historyRetainsApprovedOldVersionBesideNewDraft() {
        var versions = api.versions(ID).join();
        assertEquals(2, versions.size());
        assertEquals(NEW_VERSION, versions.getFirst().versionId());
        assertEquals("DRAFT", versions.getFirst().approvalStatus());
        assertEquals(OLD_VERSION, versions.get(1).versionId());
        assertEquals("APPROVED", versions.get(1).approvalStatus());
        assertTrue(versions.get(1).options().getFirst().correct());
        assertEquals("Bearer teacher-memory-token", authorization.get());
    }

    @Test void forbiddenHistoryPropagatesFailureInsteadOfAnEmptyHistory() {
        historyStatus = 403;
        var error = assertThrows(CompletionException.class, () -> api.versions(ID).join());
        assertEquals(403, ((ApiException) error.getCause()).status());
    }

    @Test void summaryPreservesServerRatiosAndNoActivityNullAccuracy() {
        var summaries = api.studySummary(ID).join();
        assertEquals(2, summaries.size());
        var active = summaries.getFirst();
        assertEquals(2L, active.completedSessions());
        assertEquals(10L, active.totalQuestions());
        assertEquals(7L, active.correctAnswers());
        assertEquals(new BigDecimal("0.7"), active.accuracy());
        assertEquals(0L, summaries.get(1).completedSessions());
        assertNull(summaries.get(1).accuracy());
        assertEquals("Bearer teacher-memory-token", authorization.get());
    }

    private static String question(UUID version, int number, String status) {
        return "{\"questionId\":\"" + ID + "\",\"versionId\":\"" + version + "\",\"bankId\":\"" + ID + "\",\"versionNo\":" + number + ",\"stem\":\"Which option?\",\"subjectId\":null,\"topicId\":null,\"difficulty\":\"MEDIUM\",\"approvalStatus\":\"" + status + "\",\"origin\":\"MANUAL\",\"options\":[{\"id\":\"" + ID + "\",\"position\":1,\"text\":\"One\",\"correct\":true},{\"id\":\"" + OLD_VERSION + "\",\"position\":2,\"text\":\"Two\",\"correct\":false}]}";
    }

    private static void reply(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
