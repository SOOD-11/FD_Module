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
 * It maintains a time offset that continues to run like a real clock.
 * 
 * When you set a logical time, the clock continues running from that point,
 * maintaining the offset from system time. This allows realistic simulation
 * of time passage while still being able to jump to specific dates for testing.
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

    // The offset between logical time and system time.
    // Logical time = System time + offset
    private Duration timeOffset;
    
    // The instant when the offset was last set (for tracking)
    private Instant offsetSetAt;

    public LogicalClockService() {
        // Initialize with zero offset - logical time equals system time
        this.timeOffset = Duration.ZERO;
        this.offsetSetAt = Instant.now();
        log.info("LogicalClockService initialized - using RUNNING logical clock");
        log.info("System timezone: {}", systemZone);
        log.info("Initial time offset: {} (logical time = system time)", timeOffset);
        log.warn("Time control APIs are ENABLED. Use /api/admin/time endpoints to manipulate time.");
        log.warn("Note: Logical clock RUNS continuously from the set point, it does not freeze.");
    }

    @Override
    public LocalDate getLogicalDate() {
        return LocalDate.ofInstant(getCurrentLogicalInstant(), systemZone);
    }

    @Override
    public LocalDateTime getLogicalDateTime() {
        return LocalDateTime.ofInstant(getCurrentLogicalInstant(), systemZone);
    }

    @Override
    public Instant getLogicalInstant() {
        return getCurrentLogicalInstant();
    }
    
    /**
     * Calculate the current logical instant by applying the offset to system time.
     * This ensures the clock continues to run.
     */
    private Instant getCurrentLogicalInstant() {
        return Instant.now().plus(timeOffset);
    }

    // --- Control Methods ---

    /**
     * Manually sets the application's logical clock to a specific instant.
     * The clock will continue to run from this point.
     * 
     * @param newTime The new Instant to set as "now".
     */
    public void setLogicalTime(Instant newTime) {
        Instant systemNow = Instant.now();
        Instant oldLogicalTime = getCurrentLogicalInstant();
        
        // Calculate the new offset: newTime - systemNow
        this.timeOffset = Duration.between(systemNow, newTime);
        this.offsetSetAt = systemNow;
        
        log.info("Logical time set from {} to {} (offset: {})", 
                oldLogicalTime, newTime, timeOffset);
        log.info("Clock will continue running from this point");
    }

    /**
     * Manually sets the application's logical clock to a specific date at noon.
     * The clock will continue to run from this point.
     * 
     * @param newDate The new LocalDate to set as "today".
     */
    public void setLogicalDate(LocalDate newDate) {
        // Set to noon to avoid timezone edge cases
        Instant newTime = newDate.atTime(12, 0).atZone(systemZone).toInstant();
        setLogicalTime(newTime);
    }

    /**
     * Advances the logical clock by a specified duration.
     * This increases the offset, moving logical time further ahead.
     * 
     * @param duration The amount of time to move forward (e.g., Duration.ofDays(1)).
     */
    public void advanceTime(Duration duration) {
        Instant oldLogicalTime = getCurrentLogicalInstant();
        this.timeOffset = this.timeOffset.plus(duration);
        Instant newLogicalTime = getCurrentLogicalInstant();
        
        log.info("Logical time advanced by {} from {} to {} (new offset: {})", 
                duration, oldLogicalTime, newLogicalTime, timeOffset);
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
     * This sets the offset to zero, so logical time = system time.
     */
    public void resetToSystemTime() {
        Instant oldLogicalTime = getCurrentLogicalInstant();
        this.timeOffset = Duration.ZERO;
        this.offsetSetAt = Instant.now();
        Instant newLogicalTime = getCurrentLogicalInstant();
        
        log.info("Logical clock reset to system time. Changed from {} to {} (offset: ZERO)", 
                oldLogicalTime, newLogicalTime);
    }
    
    /**
     * Get the current time offset (for debugging/monitoring).
     * 
     * @return The duration offset between logical and system time
     */
    public Duration getTimeOffset() {
        return this.timeOffset;
    }
    
    /**
     * Get the system time when the offset was last set (for debugging/monitoring).
     * 
     * @return The instant when the offset was last modified, or null if never set
     */
    public Instant getOffsetSetAt() {
        return this.offsetSetAt;
    }
}
