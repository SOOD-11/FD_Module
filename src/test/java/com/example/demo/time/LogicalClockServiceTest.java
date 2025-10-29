package com.example.demo.time;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Example tests demonstrating how to use LogicalClockService for testing
 * time-dependent business logic.
 */
class LogicalClockServiceTest {

    private LogicalClockService clockService;
    
    @BeforeEach
    void setUp() {
        clockService = new LogicalClockService();
    }
    
    @Test
    void testSetLogicalDate() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2025, 12, 31);
        
        // Act
        clockService.setLogicalDate(targetDate);
        LocalDate actualDate = clockService.getLogicalDate();
        
        // Assert
        assertEquals(targetDate, actualDate);
    }
    
    @Test
    void testAdvanceDays() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        clockService.setLogicalDate(startDate);
        
        // Act
        clockService.advanceDays(30);
        LocalDate actualDate = clockService.getLogicalDate();
        
        // Assert
        assertEquals(LocalDate.of(2025, 1, 31), actualDate);
    }
    
    @Test
    void testAdvanceTime() {
        // Arrange
        Instant startTime = Instant.parse("2025-01-01T00:00:00Z");
        clockService.setLogicalTime(startTime);
        
        // Act
        clockService.advanceTime(Duration.ofHours(24));
        Instant actualTime = clockService.getLogicalInstant();
        
        // Assert
        assertEquals(Instant.parse("2025-01-02T00:00:00Z"), actualTime);
    }
    
    @Test
    void testResetToSystemTime() {
        // Arrange
        clockService.setLogicalDate(LocalDate.of(2020, 1, 1));
        Instant beforeReset = clockService.getLogicalInstant();
        
        // Act
        clockService.resetToSystemTime();
        Instant afterReset = clockService.getLogicalInstant();
        
        // Assert
        assertNotEquals(beforeReset, afterReset);
        // After reset should be close to current system time (within 1 second)
        assertTrue(Duration.between(afterReset, Instant.now()).abs().getSeconds() < 1);
    }
    
    @Test
    void testGetLogicalDateTime() {
        // Arrange
        LocalDate targetDate = LocalDate.of(2025, 6, 15);
        clockService.setLogicalDate(targetDate);
        
        // Act
        LocalDateTime dateTime = clockService.getLogicalDateTime();
        
        // Assert
        assertEquals(targetDate, dateTime.toLocalDate());
    }
    
    /**
     * Example: Testing FD maturity logic
     * Demonstrates how to fast-forward time to test maturity date
     */
    @Test
    void exampleTestFDMaturity() {
        // Arrange: Create FD on 2025-01-01 with 6-month maturity
        LocalDate creationDate = LocalDate.of(2025, 1, 1);
        LocalDate maturityDate = creationDate.plusMonths(6); // 2025-07-01
        clockService.setLogicalDate(creationDate);
        
        // Simulate FD creation
        LocalDate fdCreatedOn = clockService.getLogicalDate();
        assertEquals(creationDate, fdCreatedOn);
        
        // Act: Fast-forward to maturity date
        clockService.setLogicalDate(maturityDate);
        
        // Assert: Check if FD is mature
        LocalDate currentDate = clockService.getLogicalDate();
        assertTrue(currentDate.equals(maturityDate) || currentDate.isAfter(maturityDate),
                "FD should be mature when current date >= maturity date");
    }
    
    /**
     * Example: Testing interest accrual over 30 days
     * Demonstrates day-by-day time advancement
     */
    @Test
    void exampleTestInterestAccrual() {
        // Arrange
        LocalDate startDate = LocalDate.of(2025, 1, 1);
        clockService.setLogicalDate(startDate);
        int totalDays = 30;
        int interestAccruals = 0;
        
        // Act: Simulate 30 days of interest accrual
        for (int day = 1; day <= totalDays; day++) {
            clockService.advanceDays(1);
            
            // Simulate interest calculation
            LocalDate currentDate = clockService.getLogicalDate();
            if (currentDate.isAfter(startDate)) {
                interestAccruals++;
            }
        }
        
        // Assert
        assertEquals(30, interestAccruals, "Interest should accrue for 30 days");
        assertEquals(LocalDate.of(2025, 1, 31), clockService.getLogicalDate());
    }
    
    /**
     * Example: Testing premature withdrawal penalty based on term completion
     * Demonstrates testing time-based penalty calculations
     */
    @Test
    void exampleTestPrematureWithdrawalPenalty() {
        // Arrange: FD with 12-month term
        LocalDate creationDate = LocalDate.of(2025, 1, 1);
        LocalDate maturityDate = creationDate.plusMonths(12);
        clockService.setLogicalDate(creationDate);
        
        // Act: Withdraw after 6 months (50% completion)
        LocalDate withdrawalDate = creationDate.plusMonths(6);
        clockService.setLogicalDate(withdrawalDate);
        
        // Calculate term completion percentage
        long totalDays = Duration.between(
            creationDate.atStartOfDay(ZoneId.systemDefault()),
            maturityDate.atStartOfDay(ZoneId.systemDefault())
        ).toDays();
        
        long elapsedDays = Duration.between(
            creationDate.atStartOfDay(ZoneId.systemDefault()),
            withdrawalDate.atStartOfDay(ZoneId.systemDefault())
        ).toDays();
        
        double completionPercentage = (elapsedDays * 100.0) / totalDays;
        
        // Assert: Should be around 50% completion
        assertTrue(completionPercentage >= 49 && completionPercentage <= 51,
                "Withdrawal at 6 months should be around 50% term completion");
    }
    
    /**
     * Example: Testing end-of-month statement generation
     * Demonstrates testing monthly batch jobs
     */
    @Test
    void exampleTestMonthlyStatement() {
        // Arrange: Set to last day of month
        LocalDate endOfMonth = LocalDate.of(2025, 1, 31);
        clockService.setLogicalDate(endOfMonth);
        
        // Act: Calculate statement period (previous month)
        LocalDate currentDate = clockService.getLogicalDate();
        LocalDate firstDayOfLastMonth = currentDate.minusMonths(1).withDayOfMonth(1);
        LocalDate lastDayOfLastMonth = currentDate.withDayOfMonth(1).minusDays(1);
        
        // Assert: Verify statement period
        assertEquals(LocalDate.of(2024, 12, 1), firstDayOfLastMonth);
        assertEquals(LocalDate.of(2024, 12, 31), lastDayOfLastMonth);
    }
}
