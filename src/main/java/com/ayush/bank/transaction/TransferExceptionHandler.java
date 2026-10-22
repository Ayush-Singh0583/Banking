package com.ayush.bank.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TransferExceptionHandler {

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<String> handleInsufficient(InsufficientBalanceException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());   
    }
    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<String> handleSameAccount(SameAccountTransferException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());   
    }
}
