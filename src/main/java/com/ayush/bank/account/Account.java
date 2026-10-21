package com.ayush.bank.account;

import java.math.BigDecimal;   // Boot 3+/4 uses jakarta, NOT javax

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity                                   // 1. "this class maps to a table"
public class Account {

    @Id                                    // 2. primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // 3. database picks the id
    private Long id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    protected Account() {}                 // 4. JPA needs ivate?

    public Account(String ownerName, BigDecimal balance) {
        this.ownerName = ownerName;
        this.balance = balance;
    }

    // 6. three getters (no setters)
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