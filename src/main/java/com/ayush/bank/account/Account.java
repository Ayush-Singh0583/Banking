package com.ayush.bank.account;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity                                   // "this class maps to a table"
public class Account {

    @Id                                    // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // database picks the id
    private Long id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    protected Account() {}                 // JPA needs a no-arg constructor; protected stops accidental use

    public Account(String ownerName, BigDecimal balance) {
        this.ownerName = ownerName;
        this.balance = balance;
    }
    public void debit(BigDecimal amount) {
    this.balance = balance.subtract(amount);   // balance minus amount
}

    public void credit(BigDecimal amount) {
        this.balance = balance.add(amount);   // balance plus amount
    }
    // getters only, no setters
    public Long getId() {
    return id;
}
    public String getOwnerName(){
        return ownerName;
    }
    public BigDecimal getBalance(){
        return balance;
}
}