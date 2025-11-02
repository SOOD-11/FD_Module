package com.example.demo.events;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event published to Kafka alert topic when:
 * - A new FD account is created
 * - An existing FD account is modified
 */
public record AccountAlertEvent(
    @JsonProperty("account_number")
    String accountNumber,
    
    @JsonProperty("alert_type")
    AlertType alertType,
    
    @JsonProperty("alert_message")
    String alertMessage,
    
    @JsonProperty("customer_id")
    String customerId,
    
    @JsonProperty("timestamp")
    LocalDateTime timestamp,
    
    @JsonProperty("event_id")
    String eventId,
    
    @JsonProperty("details")
    String details
) {
    
    public enum AlertType {
        ACCOUNT_CREATED,
        ACCOUNT_MODIFIED,
        ACCOUNT_HOLDER_ADDED,
        ACCOUNT_STATUS_CHANGED
    }
}
