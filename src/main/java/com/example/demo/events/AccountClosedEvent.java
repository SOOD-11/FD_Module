package com.example.demo.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AccountClosedEvent(
		
		  String accountNumber,
		    String reasonForClosure,
		   BigDecimal finalPayoutAmount,
		    LocalDate closureDate,
		    String eventId,
		    List<String> customerIdsToNotify
		
		
		) {

}
