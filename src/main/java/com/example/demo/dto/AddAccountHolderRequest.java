package com.example.demo.dto;
import java.math.BigDecimal;

import com.example.demo.enums.RoleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddAccountHolderRequest(	
				
@NotBlank(message = "Customer ID is mandatory")
   String customerId,

@NotNull(message = "Role type is mandatory")
    RoleType roleType,

// This can be null, for example, for a nominee
     BigDecimal ownershipPercentage
) 

{
	
	

}
