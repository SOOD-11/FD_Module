package com.example.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PrematureWithdrawalInquiryResponse(
		String accountNumber,
	    BigDecimal originalPrincipal,
	    BigDecimal interestAccruedToDate,
	    BigDecimal penaltyAmount,
	    BigDecimal finalPayoutAmount,
	    LocalDate inquiryDate
		
		
		) {

}
