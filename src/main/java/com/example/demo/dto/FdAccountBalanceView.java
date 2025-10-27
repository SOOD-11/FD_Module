package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * View object for FD Account Balance
 */
@Schema(description = "FD Account balance details for different balance types")
public record FdAccountBalanceView(
    
    @Schema(description = "Balance type (FD_PRINCIPAL, FD_INTEREST, PENALTY)", example = "FD_PRINCIPAL")
    String balanceType,
    
    @Schema(description = "Current balance amount", example = "100000.00")
    BigDecimal balanceAmount,
    
    @Schema(description = "Whether this balance type is active", example = "true")
    Boolean isActive,
    
    @Schema(description = "When this balance entry was created", example = "2025-10-27T10:00:00")
    LocalDateTime createdAt,
    
    @Schema(description = "When this balance was last updated", example = "2025-10-27T14:30:00")
    LocalDateTime updatedAt
) {
}
