package com.example.demo.events;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic event for product communications
 * Can be used for various communication types: EMAIL, SMS, PUSH_NOTIFICATION
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommunicationEvent {
    
    private String eventId;
    private String accountNumber;
    private String customerId;
    private String communicationType; // STATEMENT, ALERT, PROMOTIONAL, REGULATORY
    private String channel; // EMAIL, SMS, PUSH_NOTIFICATION, IN_APP
    private String eventType; // COMM_OPENING, COMM_MONTHLY_STATEMENT, etc.
    private String template;
    private Map<String, String> templateVariables; // ${CUSTOMER_NAME}, ${ACCOUNT_NUMBER}, etc.
    private LocalDateTime timestamp;
}
