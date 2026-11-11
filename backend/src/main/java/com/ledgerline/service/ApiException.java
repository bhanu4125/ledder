package com.ledgerline.service;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public ApiException(HttpStatus status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    public static ApiException badRequest(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message); }
    public static ApiException unauthorized() { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication failed"); }
    public static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action"); }
    public static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"); }
    public static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT, "CONFLICT", message); }
}
