package com.quizai.server.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return message(413, "Document exceeds the upload size limit");
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> application(ApiException failure) {
        return message(failure.status(), failure.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            ConstraintViolationException.class})
    ResponseEntity<Map<String, String>> invalidRequest(Exception ignored) {
        // Never echo rejected passwords, tokens, join codes, or database error details.
        return message(400, "Dữ liệu yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ignored) {
        return message(409, "Yêu cầu xung đột với dữ liệu hiện có.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, String>> forbidden(AccessDeniedException ignored) {
        return message(403, "Bạn không có quyền thực hiện thao tác này.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> unauthorized(AuthenticationException ignored) {
        return message(401, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NoResourceFoundException ignored) {
        return message(404, "Không tìm thấy tài nguyên.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception failure) {
        Throwable root = failure;
        for (int depth=0; depth<20 && root.getCause()!=null && root.getCause()!=root; depth++) root=root.getCause();
        // No exception messages, SQL parameters, headers, or provider payloads are logged.
        String state = root instanceof java.sql.SQLException sql ? sql.getSQLState() : "none";
        String frame = java.util.Arrays.stream(root.getStackTrace())
                .filter(f -> f.getClassName().startsWith("com.quizai.")).findFirst().map(Object::toString).orElse("external");
        LOG.error("API_UNEXPECTED type={} root={} sqlstate={} source={}", failure.getClass().getName(), root.getClass().getName(), state, frame);
        return message(500, "Không thể xử lý yêu cầu lúc này.");
    }

    private ResponseEntity<Map<String, String>> message(int status, String text) {
        return ResponseEntity.status(status).body(Map.of("message", text));
    }
}
