Here is a document outlining the process for implementing a logical (business) date system.

-----

## Implementing a Logical (Business) Date Abstraction

### 1\. Objective

To decouple the application's business logic from the physical system clock. This allows testers and developers to control the "current date" of the entire system, enabling robust testing of date-sensitive logic (e.g., end-of-day, end-of-month, interest calculation, loan maturity) without changing the server's clock.

### 2\. The Problem

Hard-coding calls to `LocalDate.now()`, `new Date()`, or `System.currentTimeMillis()` makes your application untestable from a time-based perspective. Business logic becomes permanently tied to the server's physical clock.

### 3\. The Solution: The `ClockService`

The solution is to abstract the concept of "time." We will never ask "What time is it?" directly. Instead, we will ask a dedicated service, `IClockService`, for the time.

We will use **Dependency Injection (DI)** to provide this service to all other business services. For production, we will inject a "real" clock. For testing, we will inject a "mutable" clock that we can control.

### 4\. Implementation Steps

This guide uses Java and the Spring Boot framework as an example, but the principles (Interfaces, Dependency Injection, Profiles) apply to any modern framework.

#### Step 1: Define the Clock Interface

Create a contract that all your services will use to get the date and time. This ensures your business logic doesn't care *which* clock implementation is being used.

```java
// IClockService.java
package com.yourbank.common.time;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Provides an abstraction for accessing the current date and time.
 * Business logic should INJECT this service instead of calling
 * LocalDate.now() or Instant.now() directly.
 */
public interface IClockService {

    /**
     * Gets the current *logical* calendar date for business operations.
     * Use this for daily interest, EOD checks, etc.
     */
    LocalDate getLogicalDate();

    /**
     * Gets the current *logical* instant in time for transactions.
     * Use this for timestamping records (e.g., created_at).
     */
    Instant getLogicalInstant();
}
```

#### Step 2: Create the Production Clock Implementation

This is the "real" clock. It simply forwards all calls to the actual system clock. It should only be active in the `prod` environment.

```java
// ProductionClockService.java
package com.yourbank.common.time;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Production implementation of the IClockService.
 * This service is ONLY active in the "prod" profile.
 * It returns the actual system time.
 */
@Service
@Profile("prod")
public class ProductionClockService implements IClockService {

    // Using a system default time zone. Consider making this configurable.
    private final ZoneId systemZone = ZoneId.systemDefault();

    @Override
    public LocalDate getLogicalDate() {
        return LocalDate.now(systemZone);
    }

    @Override
    public Instant getLogicalInstant() {
        return Instant.now();
    }
}
```

#### Step 3: Create the Logical (Test) Clock Implementation

This is the "mutable" clock. It holds a date/time variable in memory and returns that value. It includes methods to *change* this value. It should be active in all environments *except* `prod`.

```java
// LogicalClockService.java
package com.yourbank.common.time;

import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.ZoneId;

/**
 * Test/Development implementation of the IClockService.
 * This service is active in ANY profile *except* "prod".
 * It holds a mutable, logical time that can be changed for testing.
 */
@Service
@Profile("!prod")
public class LogicalClockService implements IClockService {

    // Using a system default time zone. Consider making this configurable.
    private final ZoneId systemZone = ZoneId.systemDefault();

    // The current "logical time" of the application.
    private Instant currentLogicalTime;

    public LogicalClockService() {
        // Initialize to the real system time on startup.
        this.currentLogicalTime = Instant.now();
    }

    @Override
    public LocalDate getLogicalDate() {
        return LocalDate.ofInstant(this.currentLogicalTime, systemZone);
    }

    @Override
    public Instant getLogicalInstant() {
        return this.currentLogicalTime;
    }

    // --- Control Methods ---

    /**
     * Manually sets the application's logical clock to a specific instant.
     * @param newTime The new Instant to set as "now".
     */
    public void setLogicalTime(Instant newTime) {
        this.currentLogicalTime = newTime;
    }

    /**
     * Advances the logical clock by a specified duration.
     * @param duration The amount of time to move forward (e.g., Duration.ofDays(1)).
     */
    public void advanceTime(Duration duration) {
        this.currentLogicalTime = this.currentLogicalTime.plus(duration);
    }
}
```

#### Step 4: Inject and Use the Clock in Business Services

Refactor your existing services (e.g., `InterestCalculationService`) to use the `IClockService` via constructor injection.

```java
// InterestCalculationService.java
package com.yourbank.accounts.services;

import com.yourbank.common.time.IClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class InterestCalculationService {

    private final IClockService clockService;
    // ... other dependencies like repositories
    
    /**
     * Inject the IClockService via the constructor.
     * Spring will automatically provide the correct implementation
     * (LogicalClockService or ProductionClockService) based on the active profile.
     */
    @Autowired
    public InterestCalculationService(IClockService clockService /*, ... other repos */) {
        this.clockService = clockService;
    }

    public void calculateAndApplyDailyInterest() {
        
        // --- THIS IS THE CORRECT WAY ---
        LocalDate today = clockService.getLogicalDate();
        
        // --- DO NOT DO THIS ---
        // LocalDate today = LocalDate.now(); 

        System.out.println("Running EOD interest calculations for logical date: " + today);
        
        // ...
        // Your logic to find accounts and apply interest based on 'today'
        // ...
    }
}
```

#### Step 5: Create an Admin Controller to Set the Date

To allow testers (or automated test scripts) to change the logical date, expose a secured admin-only API endpoint. **This endpoint must be disabled in production.**

```java
// SystemAdminController.java
package com.yourbank.admin;

import com.yourbank.common.time.LogicalClockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.time.Duration;

/**
 * Admin controller for managing system state.
 * CRITICAL: This controller is ONLY enabled in non-production profiles.
 */
@RestController
@RequestMapping("/api/admin/time")
@Profile("!prod")
public class SystemAdminController {

    private final LogicalClockService logicalClock;

    /**
     * Note: We inject the *specific* LogicalClockService implementation,
     * not the interface, because we need access to the `setLogicalTime` method.
     */
    @Autowired
    public SystemAdminController(LogicalClockService logicalClock) {
        this.logicalClock = logicalClock;
    }

    // DTO for the request
    static class SetDateRequest {
        public String newLogicalDate; // e.g., "2026-01-31T23:00:00Z"
    }
    
    static class AdvanceDateRequest {
        public long days;
        public long hours;
    }

    /**
     * Sets the logical date to an absolute value.
     */
    @PostMapping("/set-logical-date")
    public ResponseEntity<String> setLogicalDate(@RequestBody SetDateRequest request) {
        try {
            Instant newTime = Instant.parse(request.newLogicalDate);
            logicalClock.setLogicalTime(newTime);
            return ResponseEntity.ok("Logical date set to: " + newTime);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid date format. Use ISO-8601 (e.g., '2026-01-31T23:00:00Z')");
        }
    }

    /**
     * Advances the logical date by a relative amount.
     */
    @PostMapping("/advance-logical-date")
    public ResponseEntity<String> advanceLogicalDate(@RequestBody AdvanceDateRequest request) {
        try {
            Duration duration = Duration.ofDays(request.days).plusHours(request.hours);
            logicalClock.advanceTime(duration);
            return ResponseEntity.ok("Logical date advanced by " + duration.toString() + ". New date: " + logicalClock.getLogicalInstant());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

### 5\. Testing Flow Example

An automated test for "End of Month Interest" would look like this:

1.  **Start Test:** Ensure the application is running in a `test` profile.
2.  **Set Time:** Send an API request:
    `POST /api/admin/time/set-logical-date`
    Body: `{ "newLogicalDate": "2026-01-31T23:55:00Z" }`
3.  **Run Batch Job:** Trigger the end-of-day process:
    `POST /api/batch/run-eod-process`
4.  **Verify:** The `InterestCalculationService` will run, see the date is "2026-01-31", and correctly apply end-of-month interest.
5.  **Advance Time:** Send another API request:
    `POST /api/admin/time/advance-logical-date`
    Body: `{ "days": 0, "hours": 1 }`
6.  **Verify State:** The system's logical date is now "2026-02-01". Check that new balances are correct.

-----