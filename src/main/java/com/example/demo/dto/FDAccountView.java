package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.example.demo.enums.AccountStatus;

import lombok.Data;


public record FDAccountView (
		
		
		String accountNumber,
	    String accountName,
	    String productCode,
	    AccountStatus status,
	    BigDecimal principalAmount,
	    BigDecimal maturityAmount,
	    LocalDate effectiveDate,
	    LocalDate maturityDate,
	    List<AccountHolderView> accountHolders
		
		
		
		
		
		)


{

}
