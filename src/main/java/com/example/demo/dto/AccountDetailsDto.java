package com.example.demo.dto;

import java.math.BigDecimal;

public record AccountDetailsDto(
    String accountNumber,
    String productName,
    String status,
    String currency,
    BigDecimal principalAmount,
    BigDecimal maturityAmount,
    String issueDate,
    String maturityDate,
    String tenure,
    BigDecimal interestRate,
    BigDecimal apy,
    String interestType,
    String compoundingFrequency
) {
}
