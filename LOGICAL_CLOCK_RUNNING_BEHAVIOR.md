# Logical Clock - Running Behavior

## Overview

The `LogicalClockService` has been refactored to implement a **running clock** instead of a frozen clock. This means that when you set the logical time to a specific instant, the clock continues to run from that point forward, just like a real clock.

## Key Concept: Offset-Based Time

Instead of storing a fixed instant, the logical clock now maintains a **time offset**:

```
Logical Time = System Time + Offset
```

### Example

If you set the logical time to **2026-01-31 12:00:00** on **2025-11-02 10:00:00**:

1. **Offset calculated**: 2026-01-31 12:00:00 minus 2025-11-02 10:00:00 = ~14 months
2. **Clock continues running**: Every call to `getLogicalInstant()` returns `Instant.now() + 14 months`
3. **Time advances naturally**: If you wait 5 seconds, the logical time advances by 5 seconds

## API Changes

### Setting Logical Time

```java
// Set logical time to a specific instant
clockService.setLogicalTime(Instant.parse("2026-01-31T12:00:00Z"));

// The clock now runs from this point
Instant time1 = clockService.getLogicalInstant(); // e.g., 2026-01-31T12:00:00.123Z
Thread.sleep(1000); // Wait 1 second
Instant time2 = clockService.getLogicalInstant(); // e.g., 2026-01-31T12:00:01.123Z

// time2 is 1 second after time1!
```

### Setting Logical Date

```java
// Set logical date to a specific date
clockService.setLogicalDate(LocalDate.of(2026, 1, 31));

// Clock continues running from noon of that day
LocalDate date1 = clockService.getLogicalDate(); // 2026-01-31
Thread.sleep(1000);
LocalDate date2 = clockService.getLogicalDate(); // Still 2026-01-31 (unless past midnight)
```

### Advancing Time

```java
// Advance the clock by adding to the offset
clockService.advanceDays(30);

// This increases the offset by 30 days
// If offset was +14 months, it's now +14 months + 30 days
```

### Resetting to System Time

```java
// Reset offset to zero
clockService.resetToSystemTime();

// Now logical time = system time (offset = 0)
Duration offset = clockService.getTimeOffset(); // Returns Duration.ZERO
```

## New Methods

### Get Time Offset

```java
// Get the current offset between logical and system time
Duration offset = clockService.getTimeOffset();

// Positive offset = logical time is ahead of system time
// Negative offset = logical time is behind system time
// Zero offset = logical time equals system time
```

### Get Offset Set At

```java
// Get when the offset was last modified
Instant offsetSetAt = clockService.getOffsetSetAt();

// Useful for debugging and tracking when time was last manipulated
```

## Benefits of Running Clock

### 1. More Realistic Testing

```java
// Test interest accrual that happens continuously
clockService.setLogicalDate(LocalDate.of(2026, 1, 1));

// Interest calculation service runs every second
// Each calculation sees time advancing naturally
// No need to manually advance time between each calculation
```

### 2. Testing Long-Running Processes

```java
// Set time to day before maturity
clockService.setLogicalDate(maturityDate.minusDays(1));

// Start batch job that processes maturities
// Job runs for several minutes in real time
// During execution, logical time continues advancing
// Job sees realistic time progression
```

### 3. Testing Time-Sensitive Race Conditions

```java
// Set time near a critical boundary (e.g., end of month)
clockService.setLogicalTime(
    LocalDate.of(2026, 1, 31)
        .atTime(23, 59, 50)
        .atZone(ZoneId.systemDefault())
        .toInstant()
);

// Start two concurrent processes
// Both see time advancing naturally
// Can test race conditions around month boundaries
```

### 4. Testing Event Ordering

```java
// Set time to specific point
clockService.setLogicalTime(startTime);

// Create event 1
Event event1 = new Event(clockService.getLogicalInstant()); // T0

// Wait a bit
Thread.sleep(100);

// Create event 2
Event event2 = new Event(clockService.getLogicalInstant()); // T0 + 100ms

// Events have different timestamps naturally
assertTrue(event2.timestamp.isAfter(event1.timestamp));
```

## Migration from Old Behavior

### Old Behavior (Frozen Clock)

```java
// Set time to 2026-01-31
clockService.setLogicalTime(Instant.parse("2026-01-31T12:00:00Z"));

// Time is frozen
Instant time1 = clockService.getLogicalInstant(); // 2026-01-31T12:00:00.000Z
Thread.sleep(5000);
Instant time2 = clockService.getLogicalInstant(); // 2026-01-31T12:00:00.000Z (SAME!)

// To advance, must explicitly call advanceTime()
clockService.advanceDays(1);
Instant time3 = clockService.getLogicalInstant(); // 2026-02-01T12:00:00.000Z
```

### New Behavior (Running Clock)

```java
// Set time to 2026-01-31
clockService.setLogicalTime(Instant.parse("2026-01-31T12:00:00Z"));

// Time continues running
Instant time1 = clockService.getLogicalInstant(); // 2026-01-31T12:00:00.123Z
Thread.sleep(5000);
Instant time2 = clockService.getLogicalInstant(); // 2026-01-31T12:00:05.123Z (ADVANCED!)

// Can still explicitly advance
clockService.advanceDays(1);
Instant time3 = clockService.getLogicalInstant(); // ~2026-02-01T12:00:05.123Z
```

## Implementation Details

### Time Offset Storage

```java
public class LogicalClockService implements IClockService {
    // System time zone
    private final ZoneId systemZone;
    
    // Offset between logical and system time
    private Duration timeOffset;
    
    // When was the offset last set? (for tracking)
    private Instant offsetSetAt;
    
    // Calculate current logical time on demand
    private Instant getCurrentLogicalInstant() {
        return Instant.now().plus(timeOffset);
    }
}
```

### Control Methods

```java
// Set logical time by calculating required offset
public void setLogicalTime(Instant newTime) {
    Instant systemNow = Instant.now();
    this.timeOffset = Duration.between(systemNow, newTime);
    this.offsetSetAt = systemNow;
}

// Advance by adding to offset
public void advanceTime(Duration duration) {
    this.timeOffset = this.timeOffset.plus(duration);
}

// Reset by zeroing offset
public void resetToSystemTime() {
    this.timeOffset = Duration.ZERO;
    this.offsetSetAt = Instant.now();
}
```

## Testing Recommendations

### Test That Clock Runs

```java
@Test
void testClockContinuesRunning() throws InterruptedException {
    // Set logical time
    clockService.setLogicalTime(Instant.parse("2026-01-31T12:00:00Z"));
    
    // Get time twice with delay
    Instant time1 = clockService.getLogicalInstant();
    Thread.sleep(100);
    Instant time2 = clockService.getLogicalInstant();
    
    // Verify time advanced
    assertTrue(time2.isAfter(time1));
    
    // Verify advancement is reasonable (~100ms)
    long elapsedMillis = Duration.between(time1, time2).toMillis();
    assertTrue(elapsedMillis >= 90 && elapsedMillis <= 200);
}
```

### Test Offset Calculations

```java
@Test
void testOffsetCalculation() {
    // Set time to future
    Instant futureTime = Instant.now().plus(Duration.ofDays(365));
    clockService.setLogicalTime(futureTime);
    
    // Get offset
    Duration offset = clockService.getTimeOffset();
    
    // Verify offset is approximately 1 year
    long offsetDays = offset.toDays();
    assertTrue(offsetDays >= 364 && offsetDays <= 366);
}
```

### Test Past Time

```java
@Test
void testPastTime() {
    // Set time to past
    clockService.setLogicalTime(Instant.parse("2020-01-01T00:00:00Z"));
    
    // Verify logical time is before system time
    Instant logicalTime = clockService.getLogicalInstant();
    Instant systemTime = Instant.now();
    assertTrue(logicalTime.isBefore(systemTime));
    
    // Verify offset is negative
    Duration offset = clockService.getTimeOffset();
    assertTrue(offset.isNegative());
}
```

## Compatibility Notes

### Existing Tests

Most existing tests should work without changes:

```java
// This still works as expected
clockService.setLogicalDate(LocalDate.of(2026, 1, 31));
LocalDate date = clockService.getLogicalDate();
assertEquals(LocalDate.of(2026, 1, 31), date);
```

### Timing-Sensitive Tests

Tests that assume time is frozen may need updates:

```java
// OLD: Assumes time doesn't change
clockService.setLogicalTime(someTime);
Instant t1 = clockService.getLogicalInstant();
doSomeLongOperation(); // Takes 5 seconds
Instant t2 = clockService.getLogicalInstant();
assertEquals(t1, t2); // FAILS! Time advanced during operation

// NEW: Account for time passage
clockService.setLogicalTime(someTime);
Instant t1 = clockService.getLogicalInstant();
doSomeLongOperation();
Instant t2 = clockService.getLogicalInstant();
assertTrue(Duration.between(t1, t2).getSeconds() >= 5); // Time advanced naturally
```

## Use Cases

### 1. Maturity Processing

```java
// Set time to day before maturity
clockService.setLogicalDate(maturityDate.minusDays(1));

// Run maturity batch job
// Job queries for accounts maturing today or in the past
// As job runs, time continues advancing
// Near end of job, some accounts might cross maturity threshold
// This tests realistic timing scenarios
```

### 2. Interest Calculation

```java
// Set time to start of month
clockService.setLogicalDate(LocalDate.of(2026, 1, 1));

// Run interest calculation job
// Job calculates interest for multiple accounts
// Each calculation uses current logical time
// Times naturally progress through the job
// Accounts processed later have slightly later calculation times
```

### 3. Communication Scheduling

```java
// Set time near scheduled communication time
Instant scheduledTime = Instant.parse("2026-01-31T12:00:00Z");
clockService.setLogicalTime(scheduledTime.minusSeconds(10));

// Start communication service
// Service checks if it's time to send
// First check: Not yet (10 seconds early)
Thread.sleep(15000); // Wait 15 seconds
// Second check: Yes, send now (5 seconds past scheduled time)
```

## Troubleshooting

### Time Advances Too Fast

```java
// If time seems to advance unexpectedly, check offset
Duration offset = clockService.getTimeOffset();
log.info("Current offset: {}", offset);

// Large positive offset = logical time is far ahead
// Large negative offset = logical time is far behind
```

### Time Doesn't Match Expected Value

```java
// Remember: Time continues running!
Instant expectedTime = Instant.parse("2026-01-31T12:00:00Z");
clockService.setLogicalTime(expectedTime);

// Don't do this:
Thread.sleep(1000);
Instant actualTime = clockService.getLogicalInstant();
assertEquals(expectedTime, actualTime); // FAILS! Time advanced by 1 second

// Do this instead:
Instant time1 = clockService.getLogicalInstant();
Thread.sleep(1000);
Instant time2 = clockService.getLogicalInstant();
assertEquals(1000, Duration.between(time1, time2).toMillis(), 100); // Allow ±100ms
```

### Tests Flaky Due to Time Advancement

```java
// If test assumes time is frozen, update it:

// FLAKY:
clockService.setLogicalDate(targetDate);
assertEquals(targetDate, clockService.getLogicalDate()); // Might cross midnight!

// STABLE:
clockService.setLogicalDate(targetDate);
LocalDate actualDate = clockService.getLogicalDate();
// Allow for day boundary (if test runs exactly at midnight)
assertTrue(actualDate.equals(targetDate) || actualDate.equals(targetDate.plusDays(1)));
```

## Summary

The running clock provides more realistic time behavior for testing:

- ✅ **More realistic**: Clock behaves like real time
- ✅ **Better testing**: Can test time-sensitive scenarios naturally
- ✅ **Simpler code**: No need to manually advance time in many cases
- ✅ **Race condition testing**: Can test timing-dependent bugs
- ⚠️ **Breaking change**: Tests assuming frozen time need updates
- ⚠️ **Timing sensitivity**: Tests must account for natural time advancement

The clock still supports explicit time advancement for scenarios where you need precise control over time progression.
