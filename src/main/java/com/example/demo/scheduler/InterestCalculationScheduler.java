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
public class InterestCalculationScheduler {
    
    private final JobLauncher jobLauncher;
    private final Job interestCalculationJob;
    private final IClockService clockService;
    
    /**
     * Scheduled to run daily at midnight (00:00)
     * Cron expression: "0 0 0 * * ?" 
     * - Second: 0
     * - Minute: 0
     * - Hour: 0 (midnight)
     * - Day of month: * (every day)
     * - Month: * (every month)
     * - Day of week: ? (don't care)
     * 
     * Note: Uses logical clock for interest calculation timing
     */
    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    public void calculateDailyInterest() {
        log.info("Starting scheduled daily interest calculation job at logical time: {}", 
                clockService.getLogicalDateTime());
        
        try {
            // Use logical time for job parameters to ensure time consistency
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .toJobParameters();
            
            jobLauncher.run(interestCalculationJob, jobParameters);
            
            log.info("Daily interest calculation job completed successfully at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("Failed to execute daily interest calculation job at logical time: {}", 
                    clockService.getLogicalDateTime(), e);
        }
    }
}
