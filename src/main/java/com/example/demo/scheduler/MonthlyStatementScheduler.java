package com.example.demo.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.time.IClockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@org.springframework.context.annotation.Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class MonthlyStatementScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job monthlyStatementJob;
    private final IClockService clockService;
    
    /**
     * Scheduled to run on the 1st of every month at 11:00 PM (23:00 UTC)
     * Cron expression: "0 0 23 1 * ?" 
     * - Second: 0
     * - Minute: 0
     * - Hour: 23 (11 PM)
     * - Day of month: 1 (first day)
     * - Month: * (every month)
     * - Day of week: ? (don't care)
     * 
     * Note: This scheduler uses SYSTEM TIME (not logical time).
     * Only active in PRODUCTION profile.
     * For development/testing, use LogicalTimeSchedulerService instead.
     */
    @Scheduled(cron = "0 0 23 1 * ?", zone = "UTC")
    public void generateMonthlyStatements() {
        log.info("Starting scheduled monthly statement generation job at logical time: {}", 
                clockService.getLogicalDateTime());
        
        try {
            // Use logical time for job parameters to ensure consistency with time-based operations
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .toJobParameters();
            
            jobLauncher.run(monthlyStatementJob, jobParameters);
            
            log.info("Monthly statement generation job completed successfully at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("Failed to execute monthly statement generation job at logical time: {}", 
                    clockService.getLogicalDateTime(), e);
        }
    }
}
