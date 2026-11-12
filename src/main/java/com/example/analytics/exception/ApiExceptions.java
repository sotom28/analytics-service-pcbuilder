package com.example.analytics.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

public final class ApiExceptions {

    private ApiExceptions() {
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String m) { super(m); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String m) { super(m); }
    }

    @RestControllerAdvice
    public static class Handler {

        @ExceptionHandler(UnauthorizedException.class)
        ResponseEntity<Map<String, String>> unauthorized(UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }

        @ExceptionHandler(ForbiddenException.class)
        ResponseEntity<Map<String, String>> forbidden(ForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        }
    }
}
