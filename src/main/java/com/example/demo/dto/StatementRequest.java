package com.example.demo.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request for generating FD account statement")
public record StatementRequest(
    
    @Schema(description = "Statement period start date", example = "2025-10-01")
    @NotNull(message = "Start date is required")
    LocalDate startDate,
    
    @Schema(description = "Statement period end date", example = "2025-10-27")
    @NotNull(message = "End date is required")
    LocalDate endDate
) {
}
