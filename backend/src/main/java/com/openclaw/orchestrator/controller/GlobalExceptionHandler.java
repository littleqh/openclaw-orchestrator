package com.openclaw.orchestrator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        Map<String, String> error = new HashMap<>();
        String message = e.getMessage();
        if (message != null && message.startsWith("NOT_FOUND:")) {
            error.put("message", message.substring(10));
            return ResponseEntity.status(404).body(error);
        }
        if (message != null && message.startsWith("CONFLICT:")) {
            error.put("message", message.substring(9));
            return ResponseEntity.status(409).body(error);
        }
        if (message != null && message.startsWith("401:")) {
            error.put("message", message.substring(4));
            return ResponseEntity.status(401).body(error);
        }
        error.put("message", message != null ? message : "Unknown error");
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> error = new HashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            error.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAll(Exception e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        error.put("type", e.getClass().getSimpleName());
        return ResponseEntity.status(500).body(error);
    }
}
