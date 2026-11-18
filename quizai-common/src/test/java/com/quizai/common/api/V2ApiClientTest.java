package com.quizai.common.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class V2ApiClientTest {
    private HttpServer server;
    private QuizApiClient client;
    private V2ApiClient api;
    private final AtomicReference<String> body=new AtomicReference<>();
    private final AtomicReference<String> authorization=new AtomicReference<>();
    private static final UUID ID=UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID QUESTION=UUID.fromString("22222222-2222-2222-2222-222222222222");
    private String answerResponse="{\"assignmentQuestionId\":\""+QUESTION+"\",\"selectedOptionKey\":null,\"answerRevision\":7,\"serverSavedAt\":\"2026-11-13T01:00:00Z\"}";
    private int saveStatus=200;
    private static final String ATTEMPT="{\"id\":\""+ID+"\",\"assignmentId\":\""+ID+"\",\"attemptNo\":1,\"status\":\"IN_PROGRESS\",\"startedAt\":\"2026-11-13T01:00:00Z\",\"deadlineAt\":\"2026-11-13T01:30:00Z\",\"submittedAt\":null,\"serverTime\":\"2026-11-13T01:01:00Z\",\"scoreAvailable\":false,\"answerAvailable\":false,\"earnedPoints\":null,\"possiblePoints\":null,\"normalizedScore\":null,\"questions\":[{\"id\":\""+QUESTION+"\",\"position\":1,\"stem\":\"Which?\",\"options\":[{\"key\":\"option-A\",\"text\":\"One\"},{\"key\":\"option-B\",\"text\":\"Two\"}],\"selectedOptionKey\":null,\"answerRevision\":-1,\"serverSavedAt\":null,\"correctOptionKey\":null}]}";

    @BeforeEach void start() throws IOException {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/auth/login",e->reply(e,200,"{\"token\":\"memory-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\""+ID+"\",\"email\":\"student@example.test\",\"role\":\"STUDENT\"}}"));
        server.createContext("/api/v2/student/assignments/"+ID+"/attempts",e->{capture(e);reply(e,200,e.getRequestMethod().equals("GET")?"["+ATTEMPT+"]":ATTEMPT);});
        server.createContext("/api/v2/student/attempts/"+ID+"/answers/"+QUESTION,e->{capture(e);reply(e,saveStatus,answerResponse);});
        server.createContext("/api/v2/student/attempts/"+ID+"/submit",e->{capture(e);reply(e,200,ATTEMPT.replace("IN_PROGRESS","SUBMITTED"));});
        server.start();client=new QuizApiClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"STUDENT");api=client.v2();
    }
    @AfterEach void stop(){server.stop(0);}
    private void login(){client.login("student@example.test","secret").join();}
    @Test void startRetryRetainsRequestIdentityAndHiddenResultFields(){
        login();UUID request=UUID.randomUUID();var first=api.start(ID,request).join();String initial=body.get();api.start(ID,request).join();
        assertEquals(initial,body.get());assertTrue(initial.contains(request.toString()));assertEquals("Bearer memory-token",authorization.get());
        assertFalse(first.scoreAvailable());assertNull(first.normalizedScore());assertNull(first.earnedPoints());assertNull(first.questions().getFirst().correctOptionKey());
        assertEquals(-1,first.questions().getFirst().answerRevision());assertEquals("option-A",first.questions().getFirst().options().getFirst().key());
    }
    @Test void saveSendsNullSelectionAndExactRevisionAndReturnsOnlyServerAck(){
        login();var ack=api.save(ID,QUESTION,null,7).join();assertTrue(body.get().contains("\"selectedOptionKey\":null"));assertTrue(body.get().contains("\"answerRevision\":7"));
        assertEquals(QUESTION,ack.assignmentQuestionId());assertEquals(7,ack.answerRevision());assertNotNull(ack.serverSavedAt());
    }
    @Test void failedSaveNeverReturnsAnAcknowledgement(){
        login();saveStatus=409;var failure=assertThrows(CompletionException.class,()->api.save(ID,QUESTION,"option-A",7).join());
        assertEquals(409,((ApiException)failure.getCause()).status());
    }
    @Test void malformedOrWrongQuestionAckIsNeverAcceptedAsSaved(){
        login();answerResponse=answerResponse.replace(QUESTION.toString(),ID.toString());
        assertThrows(CompletionException.class,()->api.save(ID,QUESTION,null,7).join());
        answerResponse="{\"assignmentQuestionId\":\""+QUESTION+"\",\"selectedOptionKey\":null,\"answerRevision\":7}";
        assertThrows(CompletionException.class,()->api.save(ID,QUESTION,null,7).join());
    }
    @Test void submitUsesOneRequestIdentityAndNoUnacknowledgedClientPayload(){
        login();UUID request=UUID.randomUUID();assertEquals("SUBMITTED",api.submit(ID,request).join().status());String value=body.get();api.submit(ID,request).join();
        assertEquals(value,body.get());assertTrue(value.contains("submitRequestId"));assertFalse(value.contains("answers"));
    }
    @Test void ownHistoryParsesReleaseSafeAttempts(){login();var values=api.studentAttempts(ID).join();assertEquals(1,values.size());assertNull(values.getFirst().normalizedScore());}
    @Test void unauthenticatedRequestsFailBeforeTransport(){var error=assertThrows(CompletionException.class,()->api.start(ID,UUID.randomUUID()).join());assertEquals(401,((ApiException)error.getCause()).status());assertNull(body.get());}
    private void capture(HttpExchange exchange)throws IOException{body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));}
    private void reply(HttpExchange exchange,int status,String text)throws IOException{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();}
}
