package com.ayush.bank.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<String> handleInsufficient(InsufficientBalanceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());   // 409 + your message
    }
}