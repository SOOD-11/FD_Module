package com.example.demo.controller;

import com.example.demo.time.LogicalClockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for managing system time (logical clock).
 * 
 * CRITICAL: This controller is ONLY enabled in non-production profiles.
 * It allows testers and developers to control the application's perception of time
 * for testing date-sensitive business logic.
 * 
 * Usage:
 * - Set spring.profiles.active=dev or test (NOT prod)
 * - Use these endpoints to manipulate the logical clock
 * - All business services will see the manipulated time
 */
@RestController
@RequestMapping("/api/admin/time")
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "System Time Management (TEST ONLY)", 
    description = """
        Administrative API for controlling the application's logical clock.
        
        **⚠️ WARNING: This API is DISABLED in production environments.**
        
        These endpoints allow you to manipulate the application's perception of time
        for testing purposes. All date-sensitive operations (interest calculations,
        maturity processing, EOD jobs) will use the logical time set here.
        
        ## Common Testing Scenarios
        
        1. **Fast-forward to maturity date**: Set logical date to maturity date, then trigger batch job
        2. **Test end-of-month**: Set date to last day of month at 23:00, run EOD process
        3. **Test interest accrual**: Advance time day-by-day and verify interest calculations
        """
)
public class SystemTimeAdminController {

    private final LogicalClockService logicalClock;

    /**
     * Get the current logical time of the system
     */
    @Operation(
        summary = "Get current logical time",
        description = "Returns the current logical date and time that the application is operating on"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Current logical time retrieved successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "logicalDate": "2026-01-31",
                        "logicalDateTime": "2026-01-31T23:55:00",
                        "logicalInstant": "2026-01-31T18:55:00Z",
                        "message": "Current logical time"
                    }
                    """
                )
            )
        )
    })
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentLogicalTime() {
        Instant logicalInstant = logicalClock.getLogicalInstant();
        
        Map<String, Object> response = new HashMap<>();
        // Return both UTC and local timezone info
        response.put("logicalInstant", logicalInstant.toString());
        response.put("logicalDateTimeUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime().toString());
        response.put("logicalDateUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString());
        
        response.put("logicalDateTimeLocal", logicalClock.getLogicalDateTime().toString());
        response.put("logicalDateLocal", logicalClock.getLogicalDate().toString());
        response.put("systemTimezone", java.time.ZoneId.systemDefault().toString());
        
        response.put("message", "Current logical time");
        
        log.info("Time query - Current logical time: {} (UTC)", logicalInstant);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Set the logical time to an absolute instant
     */
    @Operation(
        summary = "Set logical time to specific instant",
        description = "Sets the application's logical clock to an absolute instant in time (ISO-8601 format)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logical time set successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "newLogicalDate": "2026-01-31",
                        "newLogicalDateTime": "2026-01-31T23:00:00",
                        "newLogicalInstant": "2026-01-31T18:00:00Z",
                        "message": "Logical time set successfully"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid date format"
        )
    })
    @PostMapping("/set-instant")
    public ResponseEntity<Map<String, Object>> setLogicalInstant(
            @Parameter(description = "Request containing the new instant in ISO-8601 format", required = true)
            @RequestBody SetInstantRequest request) {
        try {
            Instant newTime = Instant.parse(request.newLogicalInstant);
            logicalClock.setLogicalTime(newTime);
            
            Instant logicalInstant = logicalClock.getLogicalInstant();
            
            Map<String, Object> response = new HashMap<>();
            // Return date in UTC to avoid timezone confusion
            response.put("newLogicalDateUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString());
            response.put("newLogicalDateTimeUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime().toString());
            response.put("newLogicalInstant", logicalInstant.toString());
            
            // Also include local timezone info for reference
            response.put("newLogicalDateLocal", logicalClock.getLogicalDate().toString());
            response.put("newLogicalDateTimeLocal", logicalClock.getLogicalDateTime().toString());
            response.put("systemTimezone", java.time.ZoneId.systemDefault().toString());
            
            response.put("message", "Logical time set successfully");
            
            log.warn("LOGICAL TIME CHANGED to {} (UTC)", newTime);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid date format. Use ISO-8601 format (e.g., '2026-01-31T23:00:00Z')");
            error.put("details", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Set the logical date (date only, time set to start of day)
     */
    @Operation(
        summary = "Set logical date",
        description = "Sets the application's logical clock to a specific date at start of day (00:00:00)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logical date set successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "newLogicalDate": "2026-01-31",
                        "newLogicalDateTime": "2026-01-31T00:00:00",
                        "message": "Logical date set successfully"
                    }
                    """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid date format"
        )
    })
    @PostMapping("/set-date")
    public ResponseEntity<Map<String, Object>> setLogicalDate(
            @Parameter(description = "Request containing the new date in YYYY-MM-DD format", required = true)
            @RequestBody SetDateRequest request) {
        try {
            LocalDate newDate = LocalDate.parse(request.newLogicalDate);
            logicalClock.setLogicalDate(newDate);
            
            Instant logicalInstant = logicalClock.getLogicalInstant();
            
            Map<String, Object> response = new HashMap<>();
            // Return both UTC and local timezone info
            response.put("newLogicalInstant", logicalInstant.toString());
            response.put("newLogicalDateTimeUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime().toString());
            response.put("newLogicalDateUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString());
            
            response.put("newLogicalDateTimeLocal", logicalClock.getLogicalDateTime().toString());
            response.put("newLogicalDateLocal", logicalClock.getLogicalDate().toString());
            response.put("systemTimezone", java.time.ZoneId.systemDefault().toString());
            
            response.put("message", "Logical date set successfully (set to noon UTC to avoid timezone issues)");
            
            log.warn("LOGICAL DATE CHANGED to {} (noon UTC)", newDate);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid date format. Use YYYY-MM-DD format (e.g., '2026-01-31')");
            error.put("details", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Advance the logical time by a relative duration
     */
    @Operation(
        summary = "Advance logical time",
        description = "Advances the logical clock forward by a specified duration (days and/or hours)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logical time advanced successfully",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "advancedBy": "P1DT2H",
                        "newLogicalDate": "2026-02-01",
                        "newLogicalDateTime": "2026-02-01T02:00:00",
                        "message": "Logical time advanced successfully"
                    }
                    """
                )
            )
        )
    })
    @PostMapping("/advance")
    public ResponseEntity<Map<String, Object>> advanceLogicalTime(
            @Parameter(description = "Request containing days and hours to advance", required = true)
            @RequestBody AdvanceTimeRequest request) {
        try {
            Duration duration = Duration.ofDays(request.days).plusHours(request.hours);
            logicalClock.advanceTime(duration);
            
            Instant logicalInstant = logicalClock.getLogicalInstant();
            
            Map<String, Object> response = new HashMap<>();
            response.put("advancedBy", duration.toString());
            
            // Return both UTC and local timezone info
            response.put("newLogicalInstant", logicalInstant.toString());
            response.put("newLogicalDateTimeUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime().toString());
            response.put("newLogicalDateUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString());
            
            response.put("newLogicalDateTimeLocal", logicalClock.getLogicalDateTime().toString());
            response.put("newLogicalDateLocal", logicalClock.getLogicalDate().toString());
            response.put("systemTimezone", java.time.ZoneId.systemDefault().toString());
            
            response.put("message", "Logical time advanced successfully");
            
            log.warn("LOGICAL TIME ADVANCED by {} to {} (UTC)", duration, logicalInstant);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to advance time");
            error.put("details", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Reset the logical clock to current system time
     */
    @Operation(
        summary = "Reset to system time",
        description = "Resets the logical clock to the current actual system time"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Logical time reset to system time",
            content = @Content(
                mediaType = "application/json",
                examples = @ExampleObject(
                    value = """
                    {
                        "newLogicalDate": "2025-10-29",
                        "newLogicalDateTime": "2025-10-29T14:30:00",
                        "message": "Logical time reset to system time"
                    }
                    """
                )
            )
        )
    })
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetToSystemTime() {
        logicalClock.resetToSystemTime();
        
        Instant logicalInstant = logicalClock.getLogicalInstant();
        
        Map<String, Object> response = new HashMap<>();
        // Return both UTC and local timezone info
        response.put("newLogicalInstant", logicalInstant.toString());
        response.put("newLogicalDateTimeUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDateTime().toString());
        response.put("newLogicalDateUTC", logicalInstant.atZone(java.time.ZoneId.of("UTC")).toLocalDate().toString());
        
        response.put("newLogicalDateTimeLocal", logicalClock.getLogicalDateTime().toString());
        response.put("newLogicalDateLocal", logicalClock.getLogicalDate().toString());
        response.put("systemTimezone", java.time.ZoneId.systemDefault().toString());
        
        response.put("message", "Logical time reset to system time");
        
        log.warn("LOGICAL TIME RESET to system time: {} (UTC)", logicalInstant);
        
        return ResponseEntity.ok(response);
    }

    // --- DTOs ---

    public static class SetInstantRequest {
        public String newLogicalInstant; // e.g., "2026-01-31T23:00:00Z"
    }

    public static class SetDateRequest {
        public String newLogicalDate; // e.g., "2026-01-31"
    }

    public static class AdvanceTimeRequest {
        public long days;
        public long hours;
    }
}
