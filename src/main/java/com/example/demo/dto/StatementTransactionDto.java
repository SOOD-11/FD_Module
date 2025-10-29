package com.example.demo.dto;

import java.math.BigDecimal;

public record StatementTransactionDto(
    String date,
    String description,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance,
    String referenceId
) {
}
