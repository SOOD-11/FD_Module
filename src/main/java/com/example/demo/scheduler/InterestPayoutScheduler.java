package com.example.demo.scheduler;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.time.IClockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class InterestPayoutScheduler {

    private final JobLauncher jobLauncher;
    
    @Qualifier("interestPayoutJob")
    private final Job interestPayoutJob;
    
    private final IClockService clockService;

    /**
     * Scheduled to run daily at 00:30 UTC (after interest calculation at 00:00)
     * This ensures interest is calculated first, then paid out if payout_freq matches
     * 
     * Cron expression: "0 30 0 * * ?" means:
     * - 0 seconds
     * - 30 minutes
     * - 0 hour (midnight)
     * - every day of month
     * - every month
     * - any day of week
     */
    @Scheduled(cron = "0 30 0 * * ?", zone = "UTC")
    public void runInterestPayoutJob() {
        try {
            log.info("🕐 PROD SCHEDULER: Triggering interest payout job at: {}", 
                    clockService.getLogicalDateTime());

            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("executionTime", clockService.getLogicalDateTime().toString())
                    .addString("triggeredBy", "ProductionScheduler")
                    .toJobParameters();

            jobLauncher.run(interestPayoutJob, jobParameters);
            
            log.info("✅ Interest payout job completed successfully at: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("❌ Failed to run interest payout job", e);
        }
    }
}
