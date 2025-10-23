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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/jobs")
@Tag(name = "Batch Jobs", description = "APIs for manually triggering batch processing jobs")
public class JobLaunchController {

    private final JobLauncher jobLaunch;
    private final Job interestCalculationJob;
    private final Job maturityProcessingJob;

    /**
     * Using explicit constructor injection. This is a best practice that clearly
     * declares the dependencies required for this controller to be created. Spring will
     * find the beans with the matching @Qualifier names and provide them here.
     */
    public JobLaunchController(JobLauncher jobLauncher,
                               @Qualifier("interestCalculationJob") Job interestCalculationJob,
                               @Qualifier("maturityProcessingJob") Job maturityProcessingJob) {
        this.jobLaunch = jobLauncher;
        this.interestCalculationJob = interestCalculationJob;
        this.maturityProcessingJob = maturityProcessingJob;
    }

    @Operation(summary = "Run interest calculation job", description = "Manually trigger the batch job to calculate interest for all active FD accounts")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Interest calculation job started successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to start the job")
    })
    @PostMapping("/run/interest-calculation")
    public ResponseEntity<String> runInterestCalculationJob() {
        try {
            // A unique parameter (like the current time) is required to run the same job more than once.
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();

            jobLaunch.run(interestCalculationJob, jobParameters);
            return ResponseEntity.ok("Interest calculation job has been started.");

        } catch (Exception e) {
            // Log the exception for better debugging in a real application
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }

    @Operation(summary = "Run maturity processing job", description = "Manually trigger the batch job to process maturing FD accounts and execute maturity instructions")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Maturity processing job started successfully"),
        @ApiResponse(responseCode = "500", description = "Failed to start the job")
    })
    @PostMapping("/run/maturity-processing")
    public ResponseEntity<String> runMaturityProcessingJob() {
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addLong("time", System.currentTimeMillis())
                    .toJobParameters();
            jobLaunch.run(maturityProcessingJob, jobParameters);
            return ResponseEntity.ok("Maturity processing job has been started.");
        } catch (Exception e) {
            // Log the exception for better debugging in a real application
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Failed to start job: " + e.getMessage());
        }
    }
}

