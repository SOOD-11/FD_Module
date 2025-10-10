package com.example.demo.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.enums.TransactionType;

 //Represents a single financial transaction for an account in API responses.


public record FDTransactionView(
		TransactionType transactionType,
	    BigDecimal amount,
	    LocalDateTime transactionDate,
	    String description,
	    String transactionReference
		
		) 


{

}
