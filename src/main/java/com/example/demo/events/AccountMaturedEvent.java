package com.example.demo.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AccountMaturedEvent(
		
		
		  String accountNumber,
		    BigDecimal maturityAmount,
		    LocalDate maturityDate,
		    String eventId,
		    // A list of all customer IDs (owner, co-owners, nominees) associated with the account
		    List<String> customerIdsToNotify
		
		
		) {

}
