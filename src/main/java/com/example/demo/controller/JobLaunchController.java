package com.example.demo.controller;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.time.IClockService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Batch Jobs", description = "APIs for manually triggering batch processing jobs. Use these to test batch jobs with logical time.")
@Slf4j
public class JobLaunchController {

    private final JobLauncher jobLaunch;
    private final Job interestCalculationJob;
    private final Job maturityProcessingJob;
    private final Job monthlyStatementJob;
    private final IClockService clockService;

    /**
     * Using explicit constructor injection. This is a best practice that clearly
     * declares the dependencies required for this controller to be created. Spring will
     * find the beans with the matching @Qualifier names and provide them here.
     */
    public JobLaunchController(JobLauncher jobLauncher,
                               @Qualifier("interestCalculationJob") Job interestCalculationJob,
                               @Qualifier("maturityProcessingJob") Job maturityProcessingJob,
                               @Qualifier("monthlyStatementJob") Job monthlyStatementJob,
                               IClockService clockService) {
        this.jobLaunch = jobLauncher;
        this.interestCalculationJob = interestCalculationJob;
        this.maturityProcessingJob = maturityProcessingJob;
        this.monthlyStatementJob = monthlyStatementJob;
        this.clockService = clockService;
    }

    @Operation(
        summary = "Run interest calculation job", 
        description = "Manually trigger the batch job to calculate interest for all active FD accounts. Uses current logical time."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Interest calculation job started successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to start the job")
    })
    @PostMapping("/run/interest-calculation")
    public ResponseEntity<String> runInterestCalculationJob() {
        try {
            log.info("Manually triggering interest calculation job at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            // Use logical time for job parameters to ensure consistency
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addLong("systemTime", System.currentTimeMillis())
                    .toJobParameters();

            jobLaunch.run(interestCalculationJob, jobParameters);
            
            String message = String.format("Interest calculation job started at logical time: %s", 
                    clockService.getLogicalDateTime());
            log.info(message);
            return ResponseEntity.ok(message);

        } catch (Exception e) {
            log.error("Failed to start interest calculation job", e);
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }

    @Operation(
        summary = "Run maturity processing job", 
        description = "Manually trigger the batch job to process maturing FD accounts and execute maturity instructions. Uses current logical time to determine which accounts are mature."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Maturity processing job started successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to start the job")
    })
    @PostMapping("/run/maturity-processing")
    public ResponseEntity<String> runMaturityProcessingJob() {
        try {
            log.info("Manually triggering maturity processing job at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            // Use logical time for job parameters to ensure consistency
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addLong("systemTime", System.currentTimeMillis())
                    .toJobParameters();
            
            jobLaunch.run(maturityProcessingJob, jobParameters);
            
            String message = String.format("Maturity processing job started at logical time: %s", 
                    clockService.getLogicalDateTime());
            log.info(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Failed to start maturity processing job", e);
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }
    
    @Operation(
        summary = "Run monthly statement job", 
        description = "Manually trigger the batch job to generate monthly statements for all FD accounts. Uses current logical time to calculate the previous month."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Monthly statement job started successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to start the job")
    })
    @PostMapping("/run/monthly-statement")
    public ResponseEntity<String> runMonthlyStatementJob() {
        try {
            log.info("Manually triggering monthly statement job at logical time: {}", 
                    clockService.getLogicalDateTime());
            
            // Use logical time for job parameters to ensure consistency
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("logicalTimestamp", clockService.getLogicalInstant().toEpochMilli())
                    .addString("logicalExecutionTime", clockService.getLogicalDateTime().toString())
                    .addString("logicalDate", clockService.getLogicalDate().toString())
                    .addLong("systemTime", System.currentTimeMillis())
                    .toJobParameters();
            
            jobLaunch.run(monthlyStatementJob, jobParameters);
            
            String message = String.format("Monthly statement job started at logical time: %s", 
                    clockService.getLogicalDateTime());
            log.info(message);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            log.error("Failed to start monthly statement job", e);
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }
}

