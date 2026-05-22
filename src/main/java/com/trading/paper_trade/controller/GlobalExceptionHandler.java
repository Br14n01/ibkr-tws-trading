package com.trading.paper_trade.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates framework-level request errors into a JSON error body for API clients.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Wrong HTTP verb on an endpoint (e.g. GET on the POST-only /trade/buy). */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String supported = ex.getSupportedHttpMethods() == null ? "none" : ex.getSupportedHttpMethods().toString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 405);
        body.put("error", "Method Not Allowed");
        body.put("message", "HTTP method '" + ex.getMethod()
                + "' is not supported for this endpoint. Supported method(s): " + supported + ".");

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }
}
