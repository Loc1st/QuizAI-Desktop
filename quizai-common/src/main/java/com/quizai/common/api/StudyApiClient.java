package com.quizai.common.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.quizai.common.api.StudyModels.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Asynchronous private documents, Gemini and practice transport. */
public final class StudyApiClient {
    private final QuizApiClient client;
    StudyApiClient(QuizApiClient client){this.client=client;}
    public CompletableFuture<List<Document>> documents(){return get("/documents",n->list(n,this::document));}
    public CompletableFuture<Document> paste(String title,String text){return post("/documents/paste",Map.of("title",title,"text",text),this::document);}
    public CompletableFuture<Document> upload(Path file){
        return CompletableFuture.supplyAsync(()->{
            try{
                long size=Files.size(file);if(size<=0||size>5L*1024*1024)throw new ApiException(400,"Chọn tệp không rỗng, tối đa 5 MB.");
                byte[] data;try(var input=Files.newInputStream(file)){data=input.readNBytes(5*1024*1024+1);}
                if(data.length>5*1024*1024)throw new ApiException(400,"Tệp quá lớn.");
                String name=file.getFileName().toString().replaceAll("[\\r\\n\\\"\\\\]","_");
                String lower=name.toLowerCase(Locale.ROOT);
                String media=lower.endsWith(".pdf")?"application/pdf":lower.endsWith(".docx")?"application/vnd.openxmlformats-officedocument.wordprocessingml.document":lower.endsWith(".txt")?"text/plain":null;
                if(media==null)throw new ApiException(400,"Chỉ hỗ trợ PDF, DOCX và TXT.");
                String boundary="QuizAI"+UUID.randomUUID().toString().replace("-","");
                ByteArrayOutputStream body=new ByteArrayOutputStream();
                body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+media+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                body.write(data);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
                return new Multipart(body.toByteArray(),"multipart/form-data; boundary="+boundary);
            }catch(IOException error){throw new ApiException(0,"Không đọc được tệp đã chọn.");}
        }).thenCompose(part->client.upload("/api/v2/documents/upload",part.bytes(),part.contentType())).thenApply(this::document);
    }
    private record Multipart(byte[] bytes,String contentType) { }
    public CompletableFuture<Generated> generate(Map<String,Object> body){return post("/ai/generate",body,n->new Generated(id(n,"quizId"),list(n.path("questionVersionIds"),v->UUID.fromString(v.asText()))));}
    public CompletableFuture<List<PrivateQuiz>> quizzes(){return get("/practice/quizzes",n->list(n,q->new PrivateQuiz(id(q,"id"),text(q,"title"),text(q,"status"),q.path("questionCount").asInt())));}
    public CompletableFuture<List<Practice>> history(){return get("/practice",n->list(n,this::parsePractice));}
    public CompletableFuture<Practice> start(UUID quizId){return post("/practice/"+quizId+"/start",null,this::parsePractice);}
    public CompletableFuture<Practice> practice(UUID id){return get("/practice/"+id,this::parsePractice);}
    public CompletableFuture<Practice> save(UUID sessionId,UUID questionId,UUID optionId){
        Map<String,Object> body=new LinkedHashMap<>();body.put("selectedOptionId",optionId);
        return client.authenticated("PUT","/api/v2/practice/"+sessionId+"/answers/"+questionId,body).thenApply(this::parsePractice);
    }
    public CompletableFuture<Practice> complete(UUID id){return post("/practice/"+id+"/complete",null,this::parsePractice);}
    public CompletableFuture<Conversation> conversation(String type,UUID documentId,UUID practiceId){
        Map<String,Object> body=new LinkedHashMap<>();body.put("type",type);body.put("documentId",documentId);body.put("practiceSessionId",practiceId);
        return post("/ai/conversations",body,this::parseConversation);
    }
    public CompletableFuture<List<Conversation>> conversations(){return get("/ai/conversations",n->list(n,this::parseConversation));}
    public CompletableFuture<List<Message>> messages(UUID id){return get("/ai/conversations/"+id+"/messages",n->list(n,this::message));}
    public CompletableFuture<Message> send(UUID id,String message,UUID practiceQuestionId){
        Map<String,Object> body=new LinkedHashMap<>();body.put("message",message);body.put("practiceQuestionId",practiceQuestionId);
        return post("/ai/conversations/"+id+"/messages",body,this::message);
    }
    private <T> CompletableFuture<T> get(String path,Function<JsonNode,T> convert){return client.authenticated("GET","/api/v2"+path,null).thenApply(convert);}
    private <T> CompletableFuture<T> post(String path,Object body,Function<JsonNode,T> convert){return client.authenticated("POST","/api/v2"+path,body).thenApply(convert);}
    private Document document(JsonNode n){return new Document(id(n,"id"),text(n,"inputType"),nullable(n,"fileName"),nullable(n,"mimeType"),n.path("sizeBytes").asLong(),text(n,"extractionStatus"),time(n,"createdAt"));}
    private Practice parsePractice(JsonNode n){return new Practice(id(n,"id"),id(n,"quizId"),text(n,"status"),time(n,"startedAt"),time(n,"finishedAt"),integer(n,"correctCount"),integer(n,"totalQuestions"),
            list(n.path("questions"),q->new PracticeQuestion(id(q,"id"),q.path("position").asInt(),text(q,"stem"),
                    list(q.path("options"),o->new PracticeOption(id(o,"id"),o.path("position").asInt(),text(o,"text"),o.hasNonNull("correct")?o.path("correct").asBoolean():null)),optionalId(q,"selectedOptionId"))));}
    private Message message(JsonNode n){return new Message(id(n,"id"),text(n,"role"),text(n,"content"),time(n,"createdAt"),optionalId(n,"practiceQuestionId"));}
    private Conversation parseConversation(JsonNode n){return new Conversation(id(n,"id"),text(n,"type"),optionalId(n,"documentId"),optionalId(n,"practiceSessionId"),time(n,"createdAt"));}
    private static <T> List<T> list(JsonNode n,Function<JsonNode,T> convert){if(!n.isArray())throw new ApiException(0,"Phản hồi máy chủ không hợp lệ.");List<T> result=new ArrayList<>();n.forEach(v->result.add(convert.apply(v)));return List.copyOf(result);}
    private static String nullable(JsonNode n,String field){return n.hasNonNull(field)?n.get(field).asText():null;}
    private static String text(JsonNode n,String field){String value=nullable(n,field);if(value==null)throw new ApiException(0,"Phản hồi máy chủ không hợp lệ.");return value;}
    private static UUID id(JsonNode n,String field){return UUID.fromString(text(n,field));}
    private static UUID optionalId(JsonNode n,String field){String value=nullable(n,field);return value==null?null:UUID.fromString(value);}
    private static Instant time(JsonNode n,String field){String value=nullable(n,field);return value==null?null:Instant.parse(value);}
    private static Integer integer(JsonNode n,String field){return n.hasNonNull(field)?n.get(field).asInt():null;}
}
