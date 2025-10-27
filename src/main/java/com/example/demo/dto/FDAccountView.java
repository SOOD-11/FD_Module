package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.example.demo.enums.AccountStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Fixed Deposit account details view")
public record FDAccountView (
		
		@Schema(description = "Unique account number", example = "FD2025001234")
		String accountNumber,
		
		@Schema(description = "Name of the FD account", example = "My Savings FD")
	    String accountName,
	    
	    @Schema(description = "Product code", example = "FD-REGULAR-01")
	    String productCode,
	    
	    @Schema(description = "Current status of the account", example = "ACTIVE")
	    AccountStatus status,
	    
	    @Schema(description = "Principal amount deposited", example = "100000.00")
	    BigDecimal principalAmount,
	    
	    @Schema(description = "Expected maturity amount", example = "107500.00")
	    BigDecimal maturityAmount,
	    
	    @Schema(description = "Account effective date", example = "2025-01-15")
	    LocalDate effectiveDate,
	    
	    @Schema(description = "Expected maturity date", example = "2026-01-15")
	    LocalDate maturityDate,
	    
	    @Schema(description = "Interest rate percentage", example = "7.50")
	    BigDecimal interestRate,
	    
	    @Schema(description = "Annual Percentage Yield", example = "7.76")
	    BigDecimal apy,
	    
	    @Schema(description = "Customer category 1 (e.g., SENIOR, JUNIOR)", example = "JR")
	    String category1Id,
	    
	    @Schema(description = "Customer category 2 (e.g., GOLD, PLATINUM)", example = "DY")
	    String category2Id,
	    
	    @Schema(description = "Interest type (SIMPLE or COMPOUND)", example = "COMPOUND")
	    String interestType,
	    
	    @Schema(description = "Compounding frequency (MONTHLY, QUARTERLY, YEARLY)", example = "QUARTERLY")
	    String compoundingFrequency,
	    
	    @Schema(description = "Tenure value", example = "5")
	    Integer tenureValue,
	    
	    @Schema(description = "Tenure unit (YEARS, MONTHS, DAYS)", example = "YEARS")
	    String tenureUnit,
	    
	    @Schema(description = "Currency code", example = "INR")
	    String currency,
	    
	    @Schema(description = "List of account holders and their roles")
	    List<AccountHolderView> accountHolders
		
		
		
		
		
		)


{

}
