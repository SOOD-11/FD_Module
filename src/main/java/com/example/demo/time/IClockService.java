package com.example.demo.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Provides an abstraction for accessing the current date and time.
 * Business logic should INJECT this service instead of calling
 * LocalDate.now(), LocalDateTime.now() or Instant.now() directly.
 * 
 * This allows the entire application to operate on a "logical" date
 * that can be controlled for testing purposes.
 */
public interface IClockService {

    /**
     * Gets the current *logical* calendar date for business operations.
     * Use this for daily interest, EOD checks, maturity dates, etc.
     * 
     * @return The current logical date
     */
    LocalDate getLogicalDate();

    /**
     * Gets the current *logical* date and time for business operations.
     * Use this for timestamp fields that need date+time precision.
     * 
     * @return The current logical date and time
     */
    LocalDateTime getLogicalDateTime();

    /**
     * Gets the current *logical* instant in time for transactions.
     * Use this for timestamping records (e.g., created_at).
     * 
     * @return The current logical instant
     */
    Instant getLogicalInstant();
}
