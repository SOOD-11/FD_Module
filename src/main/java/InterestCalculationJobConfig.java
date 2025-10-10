import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.demo.entities.FdAccount;
import com.example.demo.entities.FdTransaction;
import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.TransactionType;
import com.example.demo.repository.FdTransactionRepository;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class InterestCalculationJobConfig {
	@Autowired
    private FdTransactionRepository transactionRepository;

    // 1. READER: Reads FdAccount entities from the database page by page.
    @Bean
    public JpaPagingItemReader<FdAccount> interestCalculationReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<FdAccount>()
                .name("interestCalculationReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT a FROM FdAccount a WHERE a.status = :status")
                .parameterValues(Map.of("status", AccountStatus.ACTIVE))
                .pageSize(100) // Process 100 accounts at a time
                .build();
    }

    // 2. PROCESSOR: Calculates interest for a single account and returns a new transaction.
    @Bean
    public ItemProcessor<FdAccount, FdTransaction> interestCalculationProcessor() {
        return (account) -> {
            // Simple daily interest calculation for demonstration
            BigDecimal dailyInterestRate = account.getInterestRate()
                    .divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP) // Annual rate as decimal
                    .divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP); // Daily rate

            BigDecimal interestAmount = account.getPrincipalAmount()
                    .multiply(dailyInterestRate)
                    .setScale(4, RoundingMode.HALF_UP);

            if (interestAmount.compareTo(BigDecimal.ZERO) > 0) {
                FdTransaction transaction = new FdTransaction();
                transaction.setFdAccount(account);
                transaction.setTransactionType(TransactionType.INTEREST_ACCRUAL);
                transaction.setAmount(interestAmount);
                transaction.setTransactionDate(LocalDateTime.now());
                transaction.setDescription("Daily interest accrual.");
                transaction.setTransactionReference(UUID.randomUUID().toString());
                log.info("Processed account: {}, Calculated interest: {}", account.getAccountNumber(), interestAmount);
                return transaction;
            }
            return null; // Skip accounts that earn no interest
        };
    }

    // 3. WRITER: Saves the created transactions to the database in a batch.
    @Bean
    public ItemWriter<FdTransaction> interestCalculationWriter() {
        return transactions -> {
            log.info("Writing {} new interest transactions to the database.", transactions.size());
            transactionRepository.saveAll(transactions);
        };
    }

    // 4. STEP: Combines the reader, processor, and writer into a single step.
    @Bean
    public Step interestCalculationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                        JpaPagingItemReader<FdAccount> reader,
                                        ItemProcessor<FdAccount, FdTransaction> processor,
                                        ItemWriter<FdTransaction> writer) {
        return new StepBuilder("interestCalculationStep", jobRepository)
                .<FdAccount, FdTransaction>chunk(100, transactionManager) // Process in chunks of 100
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    // 5. JOB: The complete job that contains our step.
    @Bean
    public Job interestCalculationJob(JobRepository jobRepository, Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", jobRepository)
                .start(interestCalculationStep)
                .build();
    }
	
	

}
