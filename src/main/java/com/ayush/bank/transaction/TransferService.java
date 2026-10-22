package com.ayush.bank.transaction;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;   // the SPRING one, not jakarta

import com.ayush.bank.account.Account;
import com.ayush.bank.account.AccountService;

@Service
public class TransferService {

    private final AccountService accountService;

    public TransferService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        // Step 1: lock both accounts, SMALLER id first (prevents deadlock)
        Long smallerId = Math.min(fromId, toId);
        Long biggerId  = Math.max(fromId, toId);
        Account first  = accountService.getForUpdate(smallerId);
        Account second = accountService.getForUpdate(biggerId);

        // Step 2: which locked account is the sender?
        Account from = fromId.equals(smallerId) ? first : second;
        Account to   = fromId.equals(smallerId) ? second : first;

        // Step 3: not enough money? → throw → Spring undoes everything
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance in account " + fromId);
        }

        // Step 4: move the money
        from.debit(amount);
        to.credit(amount);
    }   // method ends → commit → saved automatically, locks released
}