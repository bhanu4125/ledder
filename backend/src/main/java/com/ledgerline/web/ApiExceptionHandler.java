package com.ledgerline.web;

import com.ledgerline.service.ApiException;
import com.ledgerline.web.ApiModels.ErrorBody;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> apiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorBody(exception.getCode(), exception.getMessage(), Instant.now(), correlationId()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, TypeMismatchException.class})
    ResponseEntity<ErrorBody> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorBody(
                "BAD_REQUEST", "Request validation failed", Instant.now(), correlationId()));
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorBody("FORBIDDEN", "You are not allowed to perform this action",
                        Instant.now(), correlationId()));
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> missingResource() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody("NOT_FOUND", "Resource not found", Instant.now(), correlationId()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception exception) {
        logger.error("Unhandled API error", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("INTERNAL_ERROR", "The request could not be completed",
                        Instant.now(), correlationId()));
    }
    private String correlationId() { return MDC.get("correlationId"); }
}
