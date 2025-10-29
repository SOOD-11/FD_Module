package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CurrentBalancesDto(
    LocalDateTime asOfDate,
    BigDecimal principal,
    BigDecimal interest,
    BigDecimal penalty
) {
}
