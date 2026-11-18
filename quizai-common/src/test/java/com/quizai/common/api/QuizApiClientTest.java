package com.quizai.common.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QuizApiClientTest {
    private HttpServer server;
    private QuizApiClient api;
    private final AtomicReference<String> logoutHeader = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private String role = "STUDENT";
    private int classesStatus = 200;
    private static final String ID = "11111111-1111-1111-1111-111111111111";

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/register", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 201, "{\"id\":\"" + ID + "\",\"email\":\"learner@example.com\",\"role\":\"STUDENT\"}");
        });
        server.createContext("/api/auth/login", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(exchange, 200, "{\"token\":\"opaque-session-value\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\"" + ID + "\",\"email\":\"learner@example.com\",\"role\":\"" + role + "\"}}");
        });
        server.createContext("/api/auth/logout", exchange -> {
            logoutHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            reply(exchange, 204, "");
        });
        server.createContext("/api/classes/joined", exchange -> {
            if (classesStatus == 302) exchange.getResponseHeaders().set("Location", "/api/auth/logout");
            reply(exchange, classesStatus, classesStatus == 200 ? "[]" : "{\"message\":\"Session expired\"}");
        });
        server.start();
        api = new QuizApiClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "STUDENT");
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void registrationAndLoginUseJsonAndLogoutRevokesToken() {
        api.register("learner@example.com", "a\"secure\\password").join();
        assertTrue(requestBody.get().contains("\"role\":\"STUDENT\""));
        assertTrue(requestBody.get().contains("a\\\"secure\\\\password"));
        var user = api.login("learner@example.com", "password").join();
        assertEquals("STUDENT", user.role());
        assertNotNull(api.currentUser());
        assertTrue(requestBody.get().contains("deviceSessionId"));
        api.logout().join();
        assertEquals("Bearer opaque-session-value", logoutHeader.get());
        assertNull(api.currentUser());
    }

    @Test void wrongRoleRevokesTheIssuedSession() {
        role = "TEACHER";
        var error = assertThrows(CompletionException.class, () -> api.login("learner@example.com", "password").join());
        assertInstanceOf(ApiException.class, error.getCause());
        assertEquals("Bearer opaque-session-value", logoutHeader.get());
        assertNull(api.currentUser());
    }

    @Test void unauthorizedResponseClearsMemorySession() {
        api.login("learner@example.com", "password").join();
        classesStatus = 401;
        var error = assertThrows(CompletionException.class, () -> api.listClasses().join());
        assertEquals(401, ((ApiException) error.getCause()).status());
        assertNull(api.currentUser());
    }

    @Test void redirectsAreNeverFollowedWithBearerCredentials() {
        api.login("learner@example.com", "password").join();
        classesStatus = 302;
        assertThrows(CompletionException.class, () -> api.listClasses().join());
        assertNull(logoutHeader.get());
    }

    @Test void onlyLoopbackMayUsePlainHttp() {
        assertThrows(IllegalArgumentException.class, () -> new QuizApiClient(URI.create("http://example.com:8080"), "STUDENT"));
        assertThrows(IllegalArgumentException.class, () -> new QuizApiClient(URI.create("https://user:secret@example.com"), "STUDENT"));
        assertDoesNotThrow(() -> new QuizApiClient(URI.create("https://example.com"), "TEACHER"));
    }

    @Test void onlyBoundedGeminiWriteRoutesUseFortyFiveSeconds() {
        String messages="/api/v2/ai/conversations/"+ID+"/messages";
        assertEquals(Duration.ofSeconds(45),QuizApiClient.requestTimeout("POST","/api/v2/ai/generate"));
        assertEquals(Duration.ofSeconds(45),QuizApiClient.requestTimeout("POST",messages));
        assertEquals(Duration.ofSeconds(20),QuizApiClient.requestTimeout("GET",messages));
        assertEquals(Duration.ofSeconds(20),QuizApiClient.requestTimeout("POST","/api/v2/ai/conversations"));
        assertEquals(Duration.ofSeconds(20),QuizApiClient.requestTimeout("POST","/api/v2/student/attempts/"+ID+"/submit"));
        assertEquals(Duration.ofSeconds(20),QuizApiClient.requestTimeout("GET","/api/v2/ai/generate"));
        assertEquals(Duration.ofSeconds(20),QuizApiClient.requestTimeout("POST","/api/v2/ai/conversations/"+ID+"/messages/extra"));
    }

    @Test void uncertainAiWriteFailuresTellUsersToReadStoredStateBeforeExplicitRetry() {
        var timeout=new CompletionException(new HttpTimeoutException("test timeout"));
        String generation=QuizApiClient.transportFailure("POST","/api/v2/ai/generate",timeout).getMessage();
        assertTrue(generation.contains("Hết thời gian chờ"));assertTrue(generation.contains("dữ liệu đã lưu"));assertTrue(generation.contains("trước khi chủ động tạo lại"));
        String chat=QuizApiClient.transportFailure("POST","/api/v2/ai/conversations/"+ID+"/messages",timeout).getMessage();
        assertTrue(chat.contains("lịch sử hội thoại"));assertTrue(chat.contains("trước khi chủ động gửi lại"));
        String ordinary=QuizApiClient.transportFailure("GET","/api/v2/ai/conversations/"+ID+"/messages",timeout).getMessage();
        assertFalse(ordinary.contains("dữ liệu đã lưu"));assertFalse(ordinary.contains("tin nhắn đã lưu"));
    }

    private void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (status != 204) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
