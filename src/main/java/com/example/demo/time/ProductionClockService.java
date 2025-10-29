package com.example.demo.time;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Production implementation of the IClockService.
 * This service is ONLY active in the "prod" profile.
 * It returns the actual system time.
 * 
 * Usage: Set spring.profiles.active=prod to use this implementation
 */
@Service
@Profile("prod")
@Slf4j
public class ProductionClockService implements IClockService {

    // Using a system default time zone. Consider making this configurable.
    private final ZoneId systemZone = ZoneId.systemDefault();

    public ProductionClockService() {
        log.info("ProductionClockService initialized - using REAL system clock");
        log.info("System timezone: {}", systemZone);
    }

    @Override
    public LocalDate getLogicalDate() {
        return LocalDate.now(systemZone);
    }

    @Override
    public LocalDateTime getLogicalDateTime() {
        return LocalDateTime.now(systemZone);
    }

    @Override
    public Instant getLogicalInstant() {
        return Instant.now();
    }
}
