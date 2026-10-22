package com.ayush.bank.transaction;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull Long fromId,
        @NotNull Long toId,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {}
