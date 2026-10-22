package com.ayush.bank.transaction;

public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException(String message) {
        super(message);
    }
}

