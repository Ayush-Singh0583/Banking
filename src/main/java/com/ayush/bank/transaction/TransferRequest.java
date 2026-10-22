package com.ayush.bank.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull Long fromId,
        @NotNull Long toId,
        @NotNull @Positive BigDecimal amount
) {}
