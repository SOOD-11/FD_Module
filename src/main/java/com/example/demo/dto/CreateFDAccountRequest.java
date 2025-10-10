package com.example.demo.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;




public record CreateFDAccountRequest(
		
		
		
			    @NotBlank(message = "Account name is mandatory")
			    String accountName,

			    @NotBlank(message = "Product code is mandatory")
			    String productCode,

			    @NotBlank(message = "Primary customer ID is mandatory")
			    String customerId,

			    @Positive(message = "Term must be a positive number")
			    Integer termInMonths, // Optional

			    @DecimalMin(value = "0.0", inclusive = false, message = "Interest rate must be positive")
			    @Digits(integer=3, fraction=2, message = "Invalid interest rate format")
			    BigDecimal interestRate, // Optional

			    @NotNull(message = "Principal amount is mandatory")
			    @Positive(message = "Principal amount must be positive")
			    BigDecimal principalAmount
			) {}	
		
		
		
		
		
		
		
		
		
		
		
		
		





	
	
	
	


