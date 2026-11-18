package com.quizai.common.api;

/** Safe user-facing API failure. Response bodies and credentials are never included. */
public final class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
