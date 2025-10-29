package com.example.demo.scheduler;

import java.time.LocalDateTime;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyStatementScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job monthlyStatementJob;
    
    /**
     * Scheduled to run on the 1st of every month at 11:00 PM (23:00)
     * Cron expression: "0 0 23 1 * ?" 
     * - Second: 0
     * - Minute: 0
     * - Hour: 23 (11 PM)
     * - Day of month: 1 (first day)
     * - Month: * (every month)
     * - Day of week: ? (don't care)
     */
    @Scheduled(cron = "0 0 23 1 * ?", zone = "UTC")
    public void generateMonthlyStatements() {
        log.info("Starting scheduled monthly statement generation job");
        
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("executionTime", LocalDateTime.now().toString())
                    .toJobParameters();
            
            jobLauncher.run(monthlyStatementJob, jobParameters);
            
            log.info("Monthly statement generation job completed successfully");
        } catch (Exception e) {
            log.error("Failed to execute monthly statement generation job", e);
        }
    }
}
