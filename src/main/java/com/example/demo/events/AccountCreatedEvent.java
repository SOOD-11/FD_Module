package com.example.demo.events;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountCreatedEvent(
		 String accountNumber,
		    String customerId,
		    BigDecimal principalAmount,
		    LocalDate maturityDate,
		    String eventId
		
		
		
		)



{

}
