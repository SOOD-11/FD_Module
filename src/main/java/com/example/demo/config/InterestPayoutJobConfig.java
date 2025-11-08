package com.example.demo.config;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.demo.entities.FdAccount;
import com.example.demo.entities.FdAccountBalance;
import com.example.demo.entities.FdTransaction;
import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.TransactionType;
import com.example.demo.events.AccountAlertEvent;
import com.example.demo.repository.FdAccountBalanceRepository;
import com.example.demo.repository.FdTransactionRepository;
import com.example.demo.service.CustomerService;
import com.example.demo.service.KafkaProducerService;
import com.example.demo.time.IClockService;

import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class InterestPayoutJobConfig {
    
    @Autowired
    private FdTransactionRepository transactionRepository;
    
    @Autowired
    private FdAccountBalanceRepository balanceRepository;
    
    @Autowired
    private IClockService clockService;
    
    @Autowired
    private KafkaProducerService kafkaProducerService;
    
    @Autowired
    private CustomerService customerService;

    // 1. READER: Reads FdAccount entities that have payout_freq configured
    @Bean
    public JpaPagingItemReader<FdAccount> interestPayoutReader(EntityManagerFactory entityManagerFactory) {
        return new JpaPagingItemReaderBuilder<FdAccount>()
                .name("interestPayoutReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT a FROM FdAccount a WHERE a.status = :status AND a.payoutFreq IS NOT NULL")
                .parameterValues(Map.of("status", AccountStatus.ACTIVE))
                .pageSize(100)
                .build();
    }

    // 2. PROCESSOR: Pays out interest at payout frequency dates and resets FD_INTEREST to 0
    @Bean
    public ItemProcessor<FdAccount, FdTransaction> interestPayoutProcessor() {
        return (account) -> {
            // Check if today is a payout date
            if (!isPayoutDate(account)) {
                return null; // Skip if not payout date
            }
            
            // Get current FD_INTEREST balance
            FdAccountBalance interestBalance = balanceRepository
                    .findByFdAccount_AccountNumberAndBalanceType(account.getAccountNumber(), "FD_INTEREST");
            
            if (interestBalance == null || interestBalance.getBalanceAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.info("No interest to payout for account: {}", account.getAccountNumber());
                return null; // No interest to payout
            }
            
            BigDecimal interestToPayout = interestBalance.getBalanceAmount();
            
            // Reset FD_INTEREST balance to 0 (interest has been paid out)
            interestBalance.setBalanceAmount(BigDecimal.ZERO);
            interestBalance.setUpdatedAt(clockService.getLogicalDateTime());
            balanceRepository.save(interestBalance);
            
            // Create interest payout transaction
            FdTransaction transaction = new FdTransaction();
            transaction.setFdAccount(account);
            transaction.setTransactionType(TransactionType.INTEREST_PAYOUT);
            transaction.setAmount(interestToPayout);
            transaction.setTransactionDate(clockService.getLogicalDateTime());
            transaction.setDescription(String.format("%s interest payout - paid to customer account.", account.getPayoutFreq()));
            transaction.setTransactionReference(UUID.randomUUID().toString());
            
            log.info("Processing interest payout for account: {}, Payout frequency: {}, Amount: {}", 
                    account.getAccountNumber(), account.getPayoutFreq(), interestToPayout);
            
            return transaction;
        };
    }
    
    /**
     * Checks if today is a payout date for the given account.
     * Uses fixed dates: 1st of month for MONTHLY, 1st of quarter for QUARTERLY, 1st of year for YEARLY.
     */
    private boolean isPayoutDate(FdAccount account) {
        LocalDate today = clockService.getLogicalDateTime().toLocalDate();
        String payoutFreq = account.getPayoutFreq();
        
        if (payoutFreq == null) {
            return false; // No payout frequency set
        }
        
        // Check if account has started (today must be after effective date)
        if (today.isBefore(account.getEffectiveDate()) || today.equals(account.getEffectiveDate())) {
            return false;
        }
        
        int dayOfMonth = today.getDayOfMonth();
        int month = today.getMonthValue();
        
        switch (payoutFreq) {
            case "MONTHLY":
                // 1st of every month
                return dayOfMonth == 1;
                
            case "QUARTERLY":
                // 1st of every quarter (Jan, Apr, Jul, Oct)
                return dayOfMonth == 1 && (month == 1 || month == 4 || month == 7 || month == 10);
                
            case "YEARLY":
                // 1st of every year (Jan 1st)
                return dayOfMonth == 1 && month == 1;
                
            default:
                log.warn("Unknown payout frequency: {} for account: {}", 
                        payoutFreq, account.getAccountNumber());
                return false;
        }
    }

    // 3. WRITER: Saves the payout transactions to the database and sends alerts
    @Bean
    public ItemWriter<FdTransaction> interestPayoutWriter() {
        return transactions -> {
            log.info("Writing {} interest payout transactions to the database.", transactions.size());
            transactionRepository.saveAll(transactions);
            
            // Send alert for each payout transaction
            for (FdTransaction transaction : transactions) {
                FdAccount account = transaction.getFdAccount();
                
                // Get primary account holder (owner)
                String customerId = account.getAccountHolders().isEmpty() 
                        ? "SYSTEM" 
                        : account.getAccountHolders().get(0).getCustomerId();
                
                // Fetch customer contact details
                String email = null;
                String phoneNumber = null;
                try {
                    email = customerService.getEmailByCustomerNumber(customerId);
                    phoneNumber = customerService.getPhoneByCustomerNumber(customerId);
                } catch (Exception e) {
                    log.warn("Failed to fetch customer contact details for customer: {}", customerId, e);
                }
                
                // Create alert message
                String alertMessage = String.format("Interest payout of ₹%s has been credited to your account.", 
                        transaction.getAmount());
                
                String details = String.format("Transaction Type: %s, Amount: %s, Date: %s, Reference: %s, Payout Frequency: %s, Description: %s",
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getTransactionDate(),
                        transaction.getTransactionReference(),
                        account.getPayoutFreq(),
                        transaction.getDescription());
                
                // Create and publish alert event
                AccountAlertEvent alertEvent = new AccountAlertEvent(
                    account.getAccountNumber(),
                    AccountAlertEvent.AlertType.ACCOUNT_MODIFIED,
                    alertMessage,
                    customerId,
                    email,
                    phoneNumber,
                    clockService.getLogicalDateTime(),
                    UUID.randomUUID().toString(),
                    details
                );
                
                kafkaProducerService.sendAlertEvent(alertEvent);
                log.info("Published interest payout alert for account: {}", account.getAccountNumber());
            }
        };
    }

    // 4. STEP: Combines reader, processor, and writer
    @Bean
    public Step interestPayoutStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                    JpaPagingItemReader<FdAccount> interestPayoutReader,
                                    ItemProcessor<FdAccount, FdTransaction> interestPayoutProcessor,
                                    ItemWriter<FdTransaction> interestPayoutWriter) {
        return new StepBuilder("interestPayoutStep", jobRepository)
                .<FdAccount, FdTransaction>chunk(100, transactionManager)
                .reader(interestPayoutReader)
                .processor(interestPayoutProcessor)
                .writer(interestPayoutWriter)
                .build();
    }

    // 5. JOB: Defines the interest payout job
    @Bean
    @Qualifier("interestPayoutJob")
    public Job interestPayoutJob(JobRepository jobRepository, Step interestPayoutStep) {
        return new JobBuilder("interestPayoutJob", jobRepository)
                .start(interestPayoutStep)
                .build();
    }
}
