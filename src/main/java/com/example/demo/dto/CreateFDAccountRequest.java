package com.example.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request object for creating a new Fixed Deposit account")
public record CreateFDAccountRequest(
		
		    @Schema(description = "Name of the FD account", example = "My Savings FD", required = true)
		    @NotBlank(message = "Account name is mandatory")
		    String accountName,

		    @Schema(description = "Calculation ID from FD calculation service", example = "123", required = true)
		    @NotNull(message = "Calculation ID is mandatory")
		    @Positive(message = "Calculation ID must be positive")
		    Long calcId
		) {}	
		
		
		
		
		
		
		
		
		
		
		
		
		





	
	
	
	


