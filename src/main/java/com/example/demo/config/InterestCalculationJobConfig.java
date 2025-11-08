package com.example.demo.config;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
public class InterestCalculationJobConfig {
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

    // 2. PROCESSOR: Calculates compound interest at period end based on compounding_frequency.
    @Bean
    public ItemProcessor<FdAccount, FdTransaction> interestCalculationProcessor() {
        return (account) -> {
            // Check if today is a compounding period end
            if (!isCompoundingPeriodEnd(account)) {
                return null; // Skip if not period end
            }
            
            // Get current FD_INTEREST balance
            FdAccountBalance interestBalance = balanceRepository
                    .findByFdAccount_AccountNumberAndBalanceType(account.getAccountNumber(), "FD_INTEREST");
            
            BigDecimal currentInterestAccrued = (interestBalance != null) 
                    ? interestBalance.getBalanceAmount() 
                    : BigDecimal.ZERO;
            
            // Apply compound interest formula based on compounding frequency
            // MONTHLY: (1 + effective_rate/(12*100)) * (interest_accrued + principal)
            // QUARTERLY: (1 + effective_rate/(4*100)) * (interest_accrued + principal)
            // YEARLY: (1 + effective_rate/100) * (interest_accrued + principal)
            BigDecimal effectiveRate = account.getEffectiveRate();
            BigDecimal principal = account.getPrincipalAmount();
            String compoundingFrequency = account.getCompoundingFrequency();
            
            // Determine the divisor based on compounding frequency
            BigDecimal periodDivisor;
            switch (compoundingFrequency) {
                case "MONTHLY":
                    periodDivisor = new BigDecimal("12");
                    break;
                case "QUARTERLY":
                    periodDivisor = new BigDecimal("4");
                    break;
                case "YEARLY":
                    periodDivisor = BigDecimal.ONE;
                    break;
                default:
                    periodDivisor = BigDecimal.ONE;
                    log.warn("Unknown compounding frequency: {}, using yearly formula", compoundingFrequency);
            }
            
            // Calculate compound factor: 1 + (effective_rate / (period_divisor * 100))
            BigDecimal rateComponent = effectiveRate.divide(
                    periodDivisor.multiply(new BigDecimal("100")), 
                    10, 
                    RoundingMode.HALF_UP
            );
            BigDecimal compoundFactor = BigDecimal.ONE.add(rateComponent);
            
            // Calculate new amount after compounding
            BigDecimal newTotalAmount = compoundFactor.multiply(
                    currentInterestAccrued.add(principal)
            ).setScale(4, RoundingMode.HALF_UP);
            
            // Interest earned this period = newTotal - (currentInterest + principal)
            BigDecimal interestEarned = newTotalAmount.subtract(currentInterestAccrued.add(principal))
                    .setScale(4, RoundingMode.HALF_UP);
            
            if (interestEarned.compareTo(BigDecimal.ZERO) > 0) {
                // Update FD_INTEREST balance
                if (interestBalance == null) {
                    interestBalance = new FdAccountBalance();
                    interestBalance.setFdAccount(account);
                    interestBalance.setBalanceType("FD_INTEREST");
                    interestBalance.setBalanceAmount(currentInterestAccrued.add(interestEarned));
                    interestBalance.setIsActive(true);
                    interestBalance.setCreatedAt(clockService.getLogicalDateTime());
                    interestBalance.setUpdatedAt(clockService.getLogicalDateTime());
                } else {
                    interestBalance.setBalanceAmount(currentInterestAccrued.add(interestEarned));
                    interestBalance.setUpdatedAt(clockService.getLogicalDateTime());
                }
                balanceRepository.save(interestBalance);
                
                // Create transaction record
                FdTransaction transaction = new FdTransaction();
                transaction.setFdAccount(account);
                transaction.setTransactionType(TransactionType.INTEREST_ACCRUAL);
                transaction.setAmount(interestEarned);
                transaction.setTransactionDate(clockService.getLogicalDateTime());
                transaction.setDescription(String.format("%s compound interest accrual.", account.getCompoundingFrequency()));
                transaction.setTransactionReference(UUID.randomUUID().toString());
                
                log.info("Processed account: {}, Compounding frequency: {}, Calculated interest: {}", 
                        account.getAccountNumber(), account.getCompoundingFrequency(), interestEarned);
                return transaction;
            }
            return null; // Skip if no interest earned
        };
    }
    
    /**
     * Checks if today is a compounding period end for the given account.
     * Uses fixed dates: 1st of month for MONTHLY, 1st of quarter for QUARTERLY, 1st of year for YEARLY.
     */
    private boolean isCompoundingPeriodEnd(FdAccount account) {
        LocalDate today = clockService.getLogicalDateTime().toLocalDate();
        String compoundingFrequency = account.getCompoundingFrequency();
        
        if (compoundingFrequency == null) {
            return false; // No compounding frequency set
        }
        
        // Check if account has started (today must be after effective date)
        if (today.isBefore(account.getEffectiveDate()) || today.equals(account.getEffectiveDate())) {
            return false;
        }
        
        int dayOfMonth = today.getDayOfMonth();
        int month = today.getMonthValue();
        
        switch (compoundingFrequency) {
            case "MONTHLY":
                // 1st of every month at 12:00 AM
                return dayOfMonth == 1;
                
            case "QUARTERLY":
                // 1st of every quarter (Jan, Apr, Jul, Oct)
                return dayOfMonth == 1 && (month == 1 || month == 4 || month == 7 || month == 10);
                
            case "YEARLY":
                // 1st of every year (Jan 1st)
                return dayOfMonth == 1 && month == 1;
                
            default:
                log.warn("Unknown compounding frequency: {} for account: {}", 
                        compoundingFrequency, account.getAccountNumber());
                return false;
        }
    }

    // 3. WRITER: Saves the created transactions to the database in a batch and sends alerts.
    @Bean
    public ItemWriter<FdTransaction> interestCalculationWriter() {
        return transactions -> {
            log.info("Writing {} new interest transactions to the database.", transactions.size());
            transactionRepository.saveAll(transactions);
            
            // Send alert for each transaction
            for (FdTransaction transaction : transactions) {
                sendTransactionAlert(transaction, transaction.getFdAccount());
            }
        };
    }
    
    /**
     * Helper method to send transaction alert
     */
    private void sendTransactionAlert(FdTransaction transaction, FdAccount account) {
        // Get primary account holder (owner)
        String customerId = account.getAccountHolders().isEmpty() 
                ? "SYSTEM" 
                : account.getAccountHolders().get(0).getCustomerId();
        
        // Fetch customer email and phone number
        String customerEmail = null;
        String customerPhoneNumber = null;
        
        try {
            customerEmail = customerService.getEmailByCustomerNumber(customerId);
            customerPhoneNumber = customerService.getPhoneByCustomerNumber(customerId);
            log.debug("✅ Fetched customer contact for interest alert - Email: {}, Phone: {}", customerEmail, customerPhoneNumber);
        } catch (Exception e) {
            log.error("❌ Failed to fetch customer contact details for interest alert: {}", customerId, e);
            // Continue with null values
        }
        
        // Create alert message based on transaction type
        String alertMessage = String.format("Transaction %s: +%s on account %s", 
                transaction.getTransactionType(),
                transaction.getAmount(),
                account.getAccountNumber());
        
        String details = String.format("Transaction Type: %s, Amount: %s, Date: %s, Reference: %s, Description: %s",
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getTransactionReference(),
                transaction.getDescription());
        
        AccountAlertEvent alertEvent = new AccountAlertEvent(
                account.getAccountNumber(),
                AccountAlertEvent.AlertType.ACCOUNT_MODIFIED,
                alertMessage,
                customerId,
                customerEmail,
                customerPhoneNumber,
                clockService.getLogicalDateTime(),
                UUID.randomUUID().toString(),
                details
        );
        
        kafkaProducerService.sendAlertEvent(alertEvent);
        log.info("✅ Transaction alert sent for account: {} - Type: {}, Amount: {} (Email: {}, Phone: {})", 
                account.getAccountNumber(), transaction.getTransactionType(), transaction.getAmount(),
                customerEmail, customerPhoneNumber);
    }

    // 4. STEP: Combines the reader, processor, and writer into a single step.
    @Bean
    public Step interestCalculationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
    		 @Qualifier("interestCalculationReader") JpaPagingItemReader<FdAccount> reader,
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
    public Job interestCalculationJob(JobRepository jobRepository, @Qualifier("interestCalculationStep") Step interestCalculationStep) {
        return new JobBuilder("interestCalculationJob", jobRepository)
                .start(interestCalculationStep)
                .build();
    }
	
	

}
