package com.ayush.bank.transaction;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class TransferController {
    private final TransferService transferService;
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }
    @PostMapping("/transfers")
    public ResponseEntity<String> transfer(@Valid @RequestBody TransferRequest request) {
        transferService.transfer(request.fromId(), request.toId(), request.amount());
        return ResponseEntity.ok("Transfer successful");
    }
}