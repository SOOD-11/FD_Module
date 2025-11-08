package com.example.demo.scheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.example.demo.time.IClockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom scheduler that uses logical time instead of system time.
 * This scheduler checks logical time every minute and triggers jobs 
 * when the logical time matches the scheduled time.
 * 
 * Only active in non-production profiles.
 */
@Service
@Profile("!prod")
@RequiredArgsConstructor
@Slf4j
public class LogicalTimeSchedulerService {
    
    private final JobLauncher jobLauncher;
    private final Job interestCalculationJob;
    private final Job maturityProcessingJob;
    private final Job monthlyStatementJob;
    private final Job interestPayoutJob;
    private final IClockService clockService;
    
    // Track last execution to avoid duplicate runs
    private LocalDate lastInterestCalculationDate = null;
    private LocalDate lastMaturityProcessingDate = null;
    private LocalDate lastMonthlyStatementDate = null;
    private LocalDate lastInterestPayoutDate = null;
    
    /**
     * Checks every minute if any jobs should be triggered based on logical time.
     * Uses system time polling but triggers based on logical time thresholds.
     * All times are interpreted in UTC timezone.
     */
    @Scheduled(fixedRate = 60000) // Check every 60 seconds
    public void checkAndTriggerJobs() {
        try {
            // Get logical time in UTC to match the original scheduler behavior
            java.time.Instant logicalInstant = clockService.getLogicalInstant();
            LocalDateTime logicalDateTimeUTC = LocalDateTime.ofInstant(logicalInstant, java.time.ZoneId.of("UTC"));
            LocalDate logicalDateUTC = logicalDateTimeUTC.toLocalDate();
            LocalTime logicalTimeUTC = logicalDateTimeUTC.toLocalTime();
            
            log.debug("Logical time check - UTC: {}, Hour: {}, Minute: {}", 
                    logicalDateTimeUTC, logicalTimeUTC.getHour(), logicalTimeUTC.getMinute());
            
            // Check and trigger interest calculation (daily at 00:00 UTC)
            if (shouldTriggerInterestCalculation(logicalDateUTC, logicalTimeUTC)) {
                triggerInterestCalculation(logicalDateUTC);
            }
            
            // Check and trigger interest payout (daily at 00:30 UTC - after interest calculation)
            if (shouldTriggerInterestPayout(logicalDateUTC, logicalTimeUTC)) {
                triggerInterestPayout(logicalDateUTC);
            }
            
            // Check and trigger maturity processing (daily at 01:00 UTC)
            if (shouldTriggerMaturityProcessing(logicalDateUTC, logicalTimeUTC)) {
                triggerMaturityProcessing(logicalDateUTC);
            }
            
            // Check and trigger monthly statement (1st of month at 23:00 UTC)
            if (shouldTriggerMonthlyStatement(logicalDateUTC, logicalTimeUTC)) {
                triggerMonthlyStatement(logicalDateUTC);
            }
            
        } catch (Exception e) {
            log.error("Error in logical time scheduler", e);
        }
    }
    
    private boolean shouldTriggerInterestCalculation(LocalDate logicalDate, LocalTime logicalTime) {
        // Trigger once per day when logical time passes 00:00
        // Allow a window of 00:00 to 00:59
        boolean isScheduledTime = logicalTime.getHour() == 0;
        boolean notAlreadyRun = !logicalDate.equals(lastInterestCalculationDate);
        
        return isScheduledTime && notAlreadyRun;
    }
    
    private boolean shouldTriggerInterestPayout(LocalDate logicalDate, LocalTime logicalTime) {
        // Trigger once per day when logical time passes 00:30
        // Allow a window of 00:30 to 00:59 (runs after interest calculation)
        boolean isScheduledTime = logicalTime.getHour() == 0 && logicalTime.getMinute() >= 30;
        boolean notAlreadyRun = !logicalDate.equals(lastInterestPayoutDate);
        
        return isScheduledTime && notAlreadyRun;
    }
    
    private boolean shouldTriggerMaturityProcessing(LocalDate logicalDate, LocalTime logicalTime) {
        // Trigger once per day when logical time passes 01:00
        // Allow a window of 01:00 to 01:59
        boolean isScheduledTime = logicalTime.getHour() == 1;
        boolean notAlreadyRun = !logicalDate.equals(lastMaturityProcessingDate);
        
        return isScheduledTime && notAlreadyRun;
    }
    
    private boolean shouldTriggerMonthlyStatement(LocalDate logicalDate, LocalTime logicalTime) {
        // Trigger once per month on 1st day when logical time passes 23:00 UTC
        // Allow a window of 23:00 to 23:59 on the 1st
        boolean isFirstOfMonth = logicalDate.getDayOfMonth() == 1;
        boolean isScheduledTime = logicalTime.getHour() == 23;
        boolean notAlreadyRun = !logicalDate.equals(lastMonthlyStatementDate);
        
        if (isFirstOfMonth && isScheduledTime && notAlreadyRun) {
            log.info("Monthly statement trigger conditions met - Date: {}, Time: {}:00-59 UTC", 
                    logicalDate, logicalTime.getHour());
            return true;
        }
        
        return false;
    }
    
    private void triggerInterestCalculation(LocalDate logicalDate) {
        try {
            log.info("🕐 LOGICAL TIME SCHEDULER: Triggering interest calculation at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addString("triggeredBy", "LogicalTimeScheduler")
                    .toJobParameters();
            
            jobLauncher.run(interestCalculationJob, jobParameters);
            lastInterestCalculationDate = logicalDate;
            
            log.info("✅ Interest calculation job completed at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("❌ Failed to trigger interest calculation job", e);
        }
    }
    
    private void triggerInterestPayout(LocalDate logicalDate) {
        try {
            log.info("🕐 LOGICAL TIME SCHEDULER: Triggering interest payout at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addString("triggeredBy", "LogicalTimeScheduler")
                    .toJobParameters();
            
            jobLauncher.run(interestPayoutJob, jobParameters);
            lastInterestPayoutDate = logicalDate;
            
            log.info("✅ Interest payout job completed at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("❌ Failed to trigger interest payout job", e);
        }
    }
    
    private void triggerMaturityProcessing(LocalDate logicalDate) {
        try {
            log.info("🕐 LOGICAL TIME SCHEDULER: Triggering maturity processing at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addString("triggeredBy", "LogicalTimeScheduler")
                    .toJobParameters();
            
            jobLauncher.run(maturityProcessingJob, jobParameters);
            lastMaturityProcessingDate = logicalDate;
            
            log.info("✅ Maturity processing job completed at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("❌ Failed to trigger maturity processing job", e);
        }
    }
    
    private void triggerMonthlyStatement(LocalDate logicalDate) {
        try {
            log.info("🕐 LOGICAL TIME SCHEDULER: Triggering monthly statement at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addString("triggeredBy", "LogicalTimeScheduler")
                    .toJobParameters();
            
            jobLauncher.run(monthlyStatementJob, jobParameters);
            lastMonthlyStatementDate = logicalDate;
            
            log.info("✅ Monthly statement job completed at logical time: {}", 
                    clockService.getLogicalDateTime());
        } catch (Exception e) {
            log.error("❌ Failed to trigger monthly statement job", e);
        }
    }
    
    /**
     * Reset tracking to allow jobs to run again.
     * Useful when testing or when logical time is reset.
     */
    public void resetTracking() {
        log.info("Resetting logical time scheduler tracking");
        lastInterestCalculationDate = null;
        lastMaturityProcessingDate = null;
        lastMonthlyStatementDate = null;
        lastInterestPayoutDate = null;
    }
}
