package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Local HTTP fixture only: this test is not live Gemini acceptance. */
class GeminiClientTest {
    @Test void sendsKeyOnlyInHeaderAndParsesCompletedCandidate() throws Exception {
        var received = new AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            received.set(exchange.getRequestURI() + "|" + exchange.getRequestHeaders().getFirst("x-goog-api-key") + "|" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] payload = "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"answer\"}]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();
        try {
            var client = new GeminiClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "unit-test-key", "test-model", Duration.ofSeconds(2));
            assertEquals("answer", client.generate("context", false));
            assertTrue(received.get().startsWith("/test-model:generateContent|unit-test-key|"));
            assertFalse(received.get().substring(received.get().lastIndexOf('|') + 1).contains("unit-test-key"));
        } finally { server.stop(0); }
    }

    @Test void disabledConfigurationAndBlockedOutputAreClearSanitizedFailures() {
        var client = new GeminiClient(HttpClient.newHttpClient(), URI.create("https://generativelanguage.googleapis.com/v1beta/models/"), "", "test-model", Duration.ofSeconds(1));
        assertEquals(503, assertThrows(ApiException.class, () -> client.generate("private content", false)).status());
        assertThrows(ApiException.class, () -> GeminiClient.parseResponse("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}"));
        assertThrows(ApiException.class, () -> GeminiClient.parseResponse("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"truncated\"}]}}]}"));
    }

    @Test void stalledProviderIsBoundedAndErrorNeverEchoesPrivateContext() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var client = new GeminiClient(HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "test-secret", "test-model", Duration.ofMillis(50));
            ApiException failure = assertThrows(ApiException.class, () -> client.generate("private-content", false));
            assertEquals(504, failure.status());
            assertFalse(failure.getMessage().contains("test-secret"));
            assertFalse(failure.getMessage().contains("private-content"));
        } finally { server.stop(0); }
    }

    @Test void httpClientTimeoutCompletionAlsoMapsToGatewayTimeout() {
        HttpClient http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("secret-provider-detail")));
        var client = new GeminiClient(http, URI.create("https://generativelanguage.googleapis.com/v1beta/models/"), "unit-secret", "test-model", Duration.ofSeconds(1));
        ApiException failure = assertThrows(ApiException.class, () -> client.generate("private-context", false));
        assertEquals(504, failure.status());
        assertFalse(failure.getMessage().contains("secret-provider-detail"));
    }
}
