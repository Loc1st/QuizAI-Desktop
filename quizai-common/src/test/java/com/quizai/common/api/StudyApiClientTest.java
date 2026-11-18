package com.quizai.common.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Local HTTP transport fixtures; these tests do not claim a live Gemini or database check. */
class StudyApiClientTest {
    @TempDir Path temporary;
    private HttpServer server;
    private QuizApiClient client;
    private StudyApiClient study;
    private final AtomicReference<String> body=new AtomicReference<>();
    private final AtomicReference<String> contentType=new AtomicReference<>();
    private final AtomicReference<String> authorization=new AtomicReference<>();
    private static final String ID="11111111-1111-1111-1111-111111111111";
    private static final String DOCUMENT="{\"id\":\""+ID+"\",\"inputType\":\"TXT\",\"fileName\":\"lesson.txt\",\"mimeType\":\"text/plain\",\"sizeBytes\":5,\"extractionStatus\":\"READY\",\"createdAt\":\"2026-11-13T01:00:00Z\"}";
    @BeforeEach void start()throws IOException{
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/auth/login",e->reply(e,"{\"token\":\"memory-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\""+ID+"\",\"email\":\"student@example.test\",\"role\":\"STUDENT\"}}"));
        server.createContext("/api/v2/documents/upload",e->{capture(e);reply(e,DOCUMENT);});
        server.createContext("/api/v2/documents/paste",e->{capture(e);reply(e,DOCUMENT);});
        server.start();client=new QuizApiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"STUDENT");study=client.study();client.login("student@example.test","secret").join();
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void uploadsSupportedFileWithBoundedMultipartMimeAndSession()throws IOException{
        Path file=temporary.resolve("lesson.txt");Files.writeString(file,"Học Java",StandardCharsets.UTF_8);
        var document=study.upload(file).join();assertEquals("READY",document.extractionStatus());
        assertTrue(contentType.get().startsWith("multipart/form-data; boundary=QuizAI"));assertEquals("Bearer memory-token",authorization.get());
        assertTrue(body.get().contains("name=\"file\"; filename=\"lesson.txt\""));assertTrue(body.get().contains("Content-Type: text/plain"));assertTrue(body.get().contains("Học Java"));
    }
    @Test void rejectsUnsupportedFilesBeforeHttp()throws IOException{
        Path file=temporary.resolve("lesson.exe");Files.writeString(file,"not a document");
        assertThrows(CompletionException.class,()->study.upload(file).join());assertNull(body.get());
    }
    @Test void rejectsOversizedFilesBeforeReadingOrSending()throws IOException{
        Path file=temporary.resolve("large.txt");try(var random=new java.io.RandomAccessFile(file.toFile(),"rw")){random.setLength(5L*1024*1024+1);}
        assertThrows(CompletionException.class,()->study.upload(file).join());assertNull(body.get());
    }
    @Test void pastedTextUsesJsonEscapingAndOwnSession(){
        study.paste("Bài \"Java\"","line one\nline two").join();assertEquals("application/json",contentType.get());assertTrue(body.get().contains("Bài \\\"Java\\\""));assertTrue(body.get().contains("line one\\nline two"));
    }
    private void capture(HttpExchange exchange)throws IOException{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));}
    private void reply(HttpExchange exchange,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
}
