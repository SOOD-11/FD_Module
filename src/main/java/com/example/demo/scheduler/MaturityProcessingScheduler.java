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
@RequiredArgsConstructor
@Slf4j
public class MaturityProcessingScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job maturityProcessingJob;
    private final IClockService clockService;
    
    /**
     * Scheduled to run daily at 1:00 AM (01:00)
     * Cron expression: "0 0 1 * * ?" 
     * - Second: 0
     * - Minute: 0
     * - Hour: 1 (1 AM)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: ? (don't care)
     * 
     * Note: Runs after interest calculation (at midnight) to process any accounts
     * that reached maturity. Uses logical clock for maturity date comparison.
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "UTC")
    public void processMaturedAccounts() {
        log.info("Starting scheduled maturity processing job at logical time: {}", 
                clockService.getLogicalDateTime());
        
        try {
            // Use logical time for job parameters to ensure consistency with maturity date checks
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .toJobParameters();
            
            jobLauncher.run(maturityProcessingJob, jobParameters);
            
            log.info("Maturity processing job completed successfully at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("Failed to execute maturity processing job at logical time: {}", 
                    clockService.getLogicalDateTime(), e);
        }
    }
}
