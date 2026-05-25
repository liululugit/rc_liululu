package com.example.exception;

public class RequestNotFoundException extends RuntimeException {
    public RequestNotFoundException(String requestId) {
        super("Request not found: " + requestId + ", may have already succeeded");
    }
}
