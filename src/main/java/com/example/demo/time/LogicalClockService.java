package com.example.demo.time;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Test/Development implementation of the IClockService.
 * This service is active in ANY profile *except* "prod".
 * It holds a mutable, logical time that can be changed for testing.
 * 
 * This allows complete control over the application's perception of time,
 * enabling testing of date-sensitive logic (maturity dates, interest calculations,
 * end-of-month processing) without waiting for real time to pass.
 * 
 * Usage: Set spring.profiles.active=dev or test (anything except 'prod')
 */
@Service
@Profile("!prod")
@Slf4j
public class LogicalClockService implements IClockService {

    // Using a system default time zone. Consider making this configurable.
    private final ZoneId systemZone = ZoneId.systemDefault();

    // The current "logical time" of the application.
    private Instant currentLogicalTime;

    public LogicalClockService() {
        // Initialize to the real system time on startup.
        this.currentLogicalTime = Instant.now();
        log.info("LogicalClockService initialized - using MUTABLE logical clock");
        log.info("System timezone: {}", systemZone);
        log.info("Initial logical time: {}", currentLogicalTime);
        log.warn("Time control APIs are ENABLED. Use /api/admin/time endpoints to manipulate time.");
    }

    @Override
    public LocalDate getLogicalDate() {
        return LocalDate.ofInstant(this.currentLogicalTime, systemZone);
    }

    @Override
    public LocalDateTime getLogicalDateTime() {
        return LocalDateTime.ofInstant(this.currentLogicalTime, systemZone);
    }

    @Override
    public Instant getLogicalInstant() {
        return this.currentLogicalTime;
    }

    // --- Control Methods ---

    /**
     * Manually sets the application's logical clock to a specific instant.
     * 
     * @param newTime The new Instant to set as "now".
     */
    public void setLogicalTime(Instant newTime) {
        log.info("Logical time changed from {} to {}", this.currentLogicalTime, newTime);
        this.currentLogicalTime = newTime;
    }

    /**
     * Manually sets the application's logical clock to a specific date at start of day.
     * 
     * @param newDate The new LocalDate to set as "today".
     */
    public void setLogicalDate(LocalDate newDate) {
        Instant newTime = newDate.atStartOfDay(systemZone).toInstant();
        setLogicalTime(newTime);
    }

    /**
     * Advances the logical clock by a specified duration.
     * 
     * @param duration The amount of time to move forward (e.g., Duration.ofDays(1)).
     */
    public void advanceTime(Duration duration) {
        Instant oldTime = this.currentLogicalTime;
        this.currentLogicalTime = this.currentLogicalTime.plus(duration);
        log.info("Logical time advanced by {} from {} to {}", duration, oldTime, this.currentLogicalTime);
    }

    /**
     * Advances the logical clock by a specified number of days.
     * 
     * @param days Number of days to advance
     */
    public void advanceDays(long days) {
        advanceTime(Duration.ofDays(days));
    }

    /**
     * Resets the logical clock to the current system time.
     */
    public void resetToSystemTime() {
        Instant systemTime = Instant.now();
        log.info("Resetting logical time to system time: {}", systemTime);
        this.currentLogicalTime = systemTime;
    }
}
