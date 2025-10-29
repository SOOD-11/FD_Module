package com.example.demo.config;

import java.time.LocalDate;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.demo.service.StatementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class MonthlyStatementJobConfiguration {
    
    private final StatementService statementService;
    
    @Bean
    public Job monthlyStatementJob(JobRepository jobRepository, Step monthlyStatementStep) {
        return new JobBuilder("monthlyStatementJob", jobRepository)
                .start(monthlyStatementStep)
                .build();
    }
    
    @Bean
    public Step monthlyStatementStep(JobRepository jobRepository, 
                                     PlatformTransactionManager transactionManager,
                                     Tasklet monthlyStatementTasklet) {
        return new StepBuilder("monthlyStatementStep", jobRepository)
                .tasklet(monthlyStatementTasklet, transactionManager)
                .build();
    }
    
    @Bean
    @StepScope
    public Tasklet monthlyStatementTasklet() {
        return (contribution, chunkContext) -> {
            log.info("Starting monthly statement generation batch job");
            
            // Calculate previous month's date range
            LocalDate today = LocalDate.now();
            LocalDate firstDayOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
            LocalDate lastDayOfLastMonth = today.withDayOfMonth(1).minusDays(1);
            
            log.info("Generating statements for period: {} to {}", firstDayOfLastMonth, lastDayOfLastMonth);
            
            // Generate statements for all accounts
            statementService.generateStatementsForAllAccounts(firstDayOfLastMonth, lastDayOfLastMonth);
            
            log.info("Monthly statement generation completed");
            
            return RepeatStatus.FINISHED;
        };
    }
}
