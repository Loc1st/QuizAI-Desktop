package com.quizai.common.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quizai.common.api.ApiModels.ClassView;
import com.quizai.common.api.ApiModels.Member;
import com.quizai.common.api.ApiModels.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** Asynchronous desktop transport. Credentials and session tokens remain in memory only. */
public final class QuizApiClient {
    private final URI baseUri;
    private final String expectedRole;
    private final String deviceSessionId = UUID.randomUUID().toString();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicLong generation = new AtomicLong();
    private volatile Session session;

    private static final class Session {
        private final String token;
        private final Instant expiresAt;
        private final User user;
        private Session(String token, Instant expiresAt, User user) {
            this.token = token;
            this.expiresAt = expiresAt;
            this.user = user;
        }
    }

    public QuizApiClient(URI baseUri, String expectedRole) {
        this.baseUri = validateBaseUri(baseUri);
        if (!List.of("STUDENT", "TEACHER").contains(expectedRole)) throw new IllegalArgumentException("Unsupported role");
        this.expectedRole = expectedRole;
    }

    public static QuizApiClient configured(String role) {
        String configured=System.getProperty("quizai.api.base-url");
        if(configured==null||configured.isBlank())configured=System.getenv("QUIZAI_API_BASE_URL");
        if(configured==null||configured.isBlank())configured="http://127.0.0.1:8080";
        return new QuizApiClient(URI.create(configured), role);
    }

    private static URI validateBaseUri(URI uri) {
        Objects.requireNonNull(uri, "API base URI");
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("API URL must be an origin without credentials, query, or path");
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        boolean loopback = host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1")
                || host.equals("[::1]") || host.equals("::1");
        if (!scheme.equals("https") && !(scheme.equals("http") && loopback)) {
            throw new IllegalArgumentException("HTTPS is required outside localhost");
        }
        return uri.resolve("/");
    }

    public User currentUser() {
        Session value = session;
        if (value != null && !value.expiresAt.isAfter(Instant.now())) {
            clearIfCurrent(value.token);
            return null;
        }
        return value == null ? null : value.user;
    }

    /** Authenticated V2 business transport sharing this in-memory session. */
    public V2ApiClient v2() { return new V2ApiClient(this); }
    public StudyApiClient study() { return new StudyApiClient(this); }

    public CompletableFuture<User> register(String email, String password) {
        return request("POST", "/api/auth/register", Map.of("email", email, "password", password, "role", expectedRole), null)
                .thenApply(this::user);
    }

    public CompletableFuture<User> login(String email, String password) {
        long attempt = generation.incrementAndGet();
        session = null;
        return request("POST", "/api/auth/login", Map.of("email", email, "password", password,
                "deviceSessionId", deviceSessionId), null).thenCompose(node -> {
            String token = requiredText(node, "token");
            User user = user(node.path("user"));
            Instant expiry = Instant.parse(requiredText(node, "expiresAt"));
            if (!expectedRole.equals(user.role()) || generation.get() != attempt) {
                return revoke(token).handle((ignored, error) -> {
                    throw new ApiException(403, "Tài khoản không thuộc ứng dụng này. Hãy dùng QuizAI "
                            + (expectedRole.equals("STUDENT") ? "Client cho sinh viên." : "Admin cho giảng viên."));
                });
            }
            session = new Session(token, expiry, user);
            return CompletableFuture.completedFuture(user);
        });
    }

    public CompletableFuture<User> verifySession() {
        return authenticated("GET", "/api/auth/session", null).thenApply(node -> user(node.path("user")));
    }

    public CompletableFuture<Void> logout() {
        generation.incrementAndGet();
        Session previous = session;
        session = null;
        return previous == null ? CompletableFuture.completedFuture(null) : revoke(previous.token);
    }

    private CompletableFuture<Void> revoke(String token) {
        return request("POST", "/api/auth/logout", null, token).thenApply(ignored -> null);
    }

    public CompletableFuture<List<ClassView>> listClasses() {
        return authenticated("GET", expectedRole.equals("TEACHER") ? "/api/classes" : "/api/classes/joined", null)
                .thenApply(node -> {
                    if (!node.isArray()) throw invalidResponse();
                    List<ClassView> values = new ArrayList<>();
                    node.forEach(item -> values.add(classView(item)));
                    return List.copyOf(values);
                });
    }

    public CompletableFuture<ClassView> createClass(String name) {
        return authenticated("POST", "/api/classes", Map.of("name", name)).thenApply(this::classView);
    }

    public CompletableFuture<ClassView> updateClass(UUID id, String name, String status) {
        return authenticated("PATCH", "/api/classes/" + id, Map.of("name", name, "status", status)).thenApply(this::classView);
    }

    public CompletableFuture<ClassView> setJoinEnabled(UUID id, boolean enabled) {
        return authenticated("PUT", "/api/classes/" + id + "/join-code", Map.of("enabled", enabled)).thenApply(this::classView);
    }

    public CompletableFuture<ClassView> rotateJoinCode(UUID id) {
        return authenticated("POST", "/api/classes/" + id + "/join-code/rotate", null).thenApply(this::classView);
    }

    public CompletableFuture<List<Member>> listMembers(UUID id) {
        return authenticated("GET", "/api/classes/" + id + "/members", null).thenApply(node -> {
            if (!node.isArray()) throw invalidResponse();
            List<Member> values = new ArrayList<>();
            node.forEach(item -> values.add(new Member(uuid(item, "membershipId"), uuid(item, "studentId"),
                    requiredText(item, "email"), optionalInstant(item, "joinedAt"))));
            return List.copyOf(values);
        });
    }

    public CompletableFuture<ClassView> joinClass(String code) {
        return authenticated("POST", "/api/classes/join", Map.of("code", code)).thenApply(this::classView);
    }

    public CompletableFuture<Void> leaveClass(UUID id) {
        return authenticated("POST", "/api/classes/" + id + "/leave", null).thenApply(ignored -> null);
    }

    CompletableFuture<JsonNode> authenticated(String method, String path, Object body) {
        Session value = session;
        if (value == null || currentUser() == null) return CompletableFuture.failedFuture(new ApiException(401, "Phiên đã hết hạn. Vui lòng đăng nhập lại."));
        return request(method, path, body, value.token);
    }

    private CompletableFuture<JsonNode> request(String method, String path, Object body, String token) {
        try {
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                publisher = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8);
            }
            return exchange(method,path,publisher,body==null?null:"application/json",token);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(invalidResponse());
        }
    }

    CompletableFuture<JsonNode> upload(String path, byte[] bytes, String contentType) {
        Session value=session;
        if(value==null||currentUser()==null)return CompletableFuture.failedFuture(new ApiException(401,"Phiên đã hết hạn. Vui lòng đăng nhập lại."));
        return exchange("POST",path,HttpRequest.BodyPublishers.ofByteArray(bytes),contentType,value.token);
    }

    private CompletableFuture<JsonNode> exchange(String method,String path,HttpRequest.BodyPublisher publisher,String contentType,String token) {
        try {
            HttpRequest.Builder builder=HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(requestTimeout(method,path)).header("Accept","application/json");
            if(token!=null)builder.header("Authorization","Bearer "+token);
            if(contentType!=null)builder.header("Content-Type",contentType);
            return http.sendAsync(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((response, failure) -> {
                        if (failure != null) throw transportFailure(method,path,failure);
                        int status = response.statusCode();
                        if (status < 200 || status >= 300) {
                            if (status == 401 && token != null) clearIfCurrent(token);
                            throw new ApiException(status, message(status));
                        }
                        if (status == 204) return json.nullNode();
                        try { return json.readTree(response.body()); }
                        catch (JsonProcessingException exception) { throw invalidResponse(); }
                    });
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.failedFuture(invalidResponse());
        }
    }

    /** Gemini writes can take 25 seconds inside a bounded server transaction; reads stay short. */
    static Duration requestTimeout(String method,String path) {
        return Duration.ofSeconds(isAiWrite(method,path)?45:20);
    }

    private static boolean isAiWrite(String method,String path) {
        return "POST".equals(method) && ("/api/v2/ai/generate".equals(path)
                || path.matches("/api/v2/ai/conversations/[^/]+/messages"));
    }

    static ApiException transportFailure(String method,String path,Throwable failure) {
        if(isAiWrite(method,path)) {
            Throwable cause=failure;
            while(cause.getCause()!=null&&!(cause instanceof HttpTimeoutException))cause=cause.getCause();
            String message=cause instanceof HttpTimeoutException?"Hết thời gian chờ phản hồi AI. ":"Chưa nhận được xác nhận từ máy chủ AI. ";
            return new ApiException(0,message+(path.equals("/api/v2/ai/generate")
                    ?"Hãy làm mới danh sách bộ đề để kiểm tra dữ liệu đã lưu trước khi chủ động tạo lại."
                    :"Hãy tải lại lịch sử hội thoại để kiểm tra tin nhắn đã lưu trước khi chủ động gửi lại."));
        }
        return new ApiException(0,"Không thể kết nối máy chủ. Kiểm tra kết nối và thử lại.");
    }

    private void clearIfCurrent(String token) {
        Session value = session;
        if (value != null && value.token.equals(token)) session = null;
    }

    private User user(JsonNode node) {
        return new User(uuid(node, "id"), requiredText(node, "email"), requiredText(node, "role"));
    }

    private ClassView classView(JsonNode node) {
        return new ClassView(uuid(node, "id"), uuid(node, "teacherId"), requiredText(node, "name"),
                requiredText(node, "status"), node.path("joinEnabled").asBoolean(), optionalText(node, "joinCode"),
                optionalUuid(node, "membershipId"), optionalInstant(node, "joinedAt"));
    }

    private static UUID uuid(JsonNode node, String field) {
        try { return UUID.fromString(requiredText(node, field)); }
        catch (IllegalArgumentException exception) { throw invalidResponse(); }
    }
    private static UUID optionalUuid(JsonNode node, String field) {
        return optionalText(node, field) == null ? null : uuid(node, field);
    }
    private static Instant optionalInstant(JsonNode node, String field) {
        String value = optionalText(node, field);
        try { return value == null ? null : Instant.parse(value); }
        catch (RuntimeException exception) { throw invalidResponse(); }
    }
    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) throw invalidResponse();
        return value;
    }
    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }
    private static ApiException invalidResponse() { return new ApiException(0, "Phản hồi máy chủ không hợp lệ. Vui lòng thử lại."); }
    private static String message(int status) {
        return switch (status) {
            case 400, 422 -> "Thông tin chưa hợp lệ. Kiểm tra email, mật khẩu hoặc dữ liệu vừa nhập.";
            case 401 -> "Thông tin đăng nhập không đúng hoặc phiên đã hết hạn. Vui lòng đăng nhập lại.";
            case 403 -> "Bạn không có quyền thực hiện thao tác này.";
            case 404 -> "Không tìm thấy tài nguyên hoặc mã tham gia không hợp lệ.";
            case 409 -> "Dữ liệu đã tồn tại hoặc đã thay đổi. Làm mới và kiểm tra lại.";
            case 429 -> "Có quá nhiều yêu cầu. Vui lòng đợi một lúc rồi thử lại.";
            default -> status >= 500 ? "Máy chủ chưa sẵn sàng. Vui lòng thử lại sau." : "Yêu cầu chưa hoàn tất. Vui lòng thử lại.";
        };
    }
}
