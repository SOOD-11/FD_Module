package com.example.demo.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.support.CompositeItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.demo.entities.Accountholder;
import com.example.demo.entities.FdAccount;
import com.example.demo.enums.AccountStatus;
import com.example.demo.events.AccountAlertEvent;
import com.example.demo.events.AccountMaturedEvent;
import com.example.demo.service.CustomerService;
import com.example.demo.service.KafkaProducerService;
import com.example.demo.time.IClockService;


import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@Configuration

public class MaturityProcessingJobConfiguration {
	
	
	
	   private final EntityManagerFactory entityManagerFactory;
	    private final KafkaProducerService kafkaProducerService;
	    private final IClockService clockService;
	    private final CustomerService customerService;

	    
	    public MaturityProcessingJobConfiguration(EntityManagerFactory entityManagerFactory, 
	    		KafkaProducerService kafkaProducerService,
	    		IClockService clockService,
	    		CustomerService customerService) {
	        this.entityManagerFactory = entityManagerFactory;
	        this.kafkaProducerService = kafkaProducerService;
	        this.clockService = clockService;
	        this.customerService = customerService;
	    }
	    @Bean
	    public JpaPagingItemReader<FdAccount> maturityReader() {
	        return new JpaPagingItemReaderBuilder<FdAccount>()
	                .name("maturityReader")
	                .entityManagerFactory(entityManagerFactory)
	                .queryString("SELECT a FROM FdAccount a WHERE a.status = :status AND a.maturityDate <= :today")
	                .parameterValues(Map.of(
	                        "status", AccountStatus.ACTIVE,
	                        "today", clockService.getLogicalDate()
	                ))
	                .pageSize(100)
	                .build();
	    }

	    @Bean
	    public ItemProcessor<FdAccount, MaturityProcessingResult> maturityProcessor() {
	        return (account) -> {
	            log.info("Processing maturity for account: {}", account.getAccountNumber());
	            FdAccount newRenewedAccount = null;

	            switch (account.getMaturityInstruction()) {
	                case RENEW_PRINCIPAL_AND_INTEREST:
	                    log.info("Renewing account {} for a similar term.", account.getAccountNumber());
	                    newRenewedAccount = createRenewedAccount(account);
	                    // Fall-through is intentional to also mark the original as matured
	                case PAYOUT_TO_LINKED_ACCOUNT:
	                case CLOSE:
	                default:
	                    log.info("Marking account {} as MATURED.", account.getAccountNumber());
	                    account.setStatus(AccountStatus.MATURED);
	                    
	                    List<String> customerIdsToNotify = account.getAccountHolders().stream()
	                            .map(Accountholder::getCustomerId)
	                            .collect(Collectors.toList());

	                    AccountMaturedEvent event = new AccountMaturedEvent(
	                            account.getAccountNumber(),
	                            account.getMaturityAmount(),
	                            account.getMaturityDate(),
	                            UUID.randomUUID().toString(),
	                            customerIdsToNotify
	                    );
	                    kafkaProducerService.sendAccountMaturedEvent(event);
	                    
	                    // Fetch customer contact details for alert
	                    String primaryCustomerId = customerIdsToNotify.isEmpty() ? "SYSTEM" : customerIdsToNotify.get(0);
	                    String customerEmail = null;
	                    String customerPhone = null;
	                    try {
	                        customerEmail = customerService.getEmailByCustomerNumber(primaryCustomerId);
	                        customerPhone = customerService.getPhoneByCustomerNumber(primaryCustomerId);
	                    } catch (Exception e) {
	                        log.error("❌ Failed to fetch contact details for maturity alert: {}", primaryCustomerId, e);
	                    }
	                    
	                    // Send alert for account status change to MATURED
	                    AccountAlertEvent alertEvent = new AccountAlertEvent(
	                            account.getAccountNumber(),
	                            AccountAlertEvent.AlertType.ACCOUNT_STATUS_CHANGED,
	                            String.format("Account %s has matured", account.getAccountNumber()),
	                            primaryCustomerId,
	                            customerEmail,
	                            customerPhone,
	                            clockService.getLogicalDateTime(),
	                            UUID.randomUUID().toString(),
	                            String.format("Maturity Amount: %s, Maturity Date: %s, Instruction: %s", 
	                                    account.getMaturityAmount(), 
	                                    account.getMaturityDate(),
	                                    account.getMaturityInstruction())
	                    );
	                    kafkaProducerService.sendAlertEvent(alertEvent);
	                    break;
	            }
	            return new MaturityProcessingResult(account, newRenewedAccount);
	        };
	    }

	    @Bean
	    public ItemWriter<MaturityProcessingResult> maturityWriter() {
	        JpaItemWriter<FdAccount> updateWriter = new JpaItemWriter<>();
	        updateWriter.setEntityManagerFactory(entityManagerFactory);

	        JpaItemWriter<FdAccount> insertWriter = new JpaItemWriter<>();
	        insertWriter.setEntityManagerFactory(entityManagerFactory);

	        CompositeItemWriter<MaturityProcessingResult> compositeWriter = new CompositeItemWriter<>();
	        compositeWriter.setDelegates(Arrays.asList(
	                (ItemWriter<MaturityProcessingResult>) items -> {
	                    List<FdAccount> originalAccounts = items.getItems().stream().map(MaturityProcessingResult::originalAccount).toList();
	                    updateWriter.write(new org.springframework.batch.item.Chunk<>(originalAccounts));
	                },
	                (ItemWriter<MaturityProcessingResult>) items -> {
	                    List<FdAccount> newAccounts = items.getItems().stream()
	                            .map(MaturityProcessingResult::newRenewedAccount)
	                            .filter(acc -> acc != null)
	                            .toList();
	                    if (!newAccounts.isEmpty()) {
	                        insertWriter.write(new org.springframework.batch.item.Chunk<>(newAccounts));
	                    }
	                }
	        ));
	        return compositeWriter;
	    }

	    @SuppressWarnings("deprecation")
		private FdAccount createRenewedAccount(FdAccount originalAccount) {
	        FdAccount newAccount = new FdAccount();
	        // NOTE: This account number logic is a placeholder and not safe for production
	        newAccount.setAccountNumber(originalAccount.getAccountNumber() + "-R");
	        newAccount.setAccountName(originalAccount.getAccountName());
	        newAccount.setProductCode(originalAccount.getProductCode());
	        newAccount.setStatus(AccountStatus.ACTIVE);
	        newAccount.setTermInMonths(originalAccount.getTermInMonths());
	        newAccount.setInterestRate(new BigDecimal("6.50")); // Placeholder for prevailing rate
	        newAccount.setPrincipalAmount(originalAccount.getMaturityAmount());
	        newAccount.setEffectiveDate(clockService.getLogicalDate());
	        newAccount.setMaturityDate(clockService.getLogicalDate().plusMonths(newAccount.getTermInMonths()));
	        
	      
	        BigDecimal rateAsDecimal = newAccount.getInterestRate().divide(new BigDecimal("100"), 10, BigDecimal.ROUND_HALF_UP);
	      
	        BigDecimal termInYears = new BigDecimal(newAccount.getTermInMonths()).divide(new BigDecimal("12"), 10, BigDecimal.ROUND_HALF_UP);
	        BigDecimal interest = newAccount.getPrincipalAmount().multiply(rateAsDecimal).multiply(termInYears);
	        newAccount.setMaturityAmount(newAccount.getPrincipalAmount().add(interest).setScale(2, BigDecimal.ROUND_HALF_UP));
	        
	        newAccount.setMaturityInstruction(originalAccount.getMaturityInstruction());
	        // Copy account holders to the new account
	        List<Accountholder> newHolders = new ArrayList<>();
	        for(Accountholder oldHolder : originalAccount.getAccountHolders()){
	            Accountholder newHolder = new Accountholder();
	            newHolder.setCustomerId(oldHolder.getCustomerId());
	            newHolder.setRoleType(oldHolder.getRoleType());
	            newHolder.setOwnershipPercentage(oldHolder.getOwnershipPercentage());
	            newHolder.setFdAccount(newAccount); // Link to the new account
	            newHolders.add(newHolder);
	        }
	        newAccount.setAccountHolders(newHolders);
	        
	        // Send alert for renewed account creation
	        List<String> customerIds = newHolders.stream()
	                .map(Accountholder::getCustomerId)
	                .collect(Collectors.toList());
	        
	        // Fetch customer contact details for alert
	        String renewalCustomerId = customerIds.isEmpty() ? "SYSTEM" : customerIds.get(0);
	        String renewalEmail = null;
	        String renewalPhone = null;
	        try {
	            renewalEmail = customerService.getEmailByCustomerNumber(renewalCustomerId);
	            renewalPhone = customerService.getPhoneByCustomerNumber(renewalCustomerId);
	        } catch (Exception e) {
	            log.error("❌ Failed to fetch contact details for renewal alert: {}", renewalCustomerId, e);
	        }
	        
	        AccountAlertEvent alertEvent = new AccountAlertEvent(
	                newAccount.getAccountNumber(),
	                AccountAlertEvent.AlertType.ACCOUNT_CREATED,
	                String.format("FD account %s renewed from matured account %s", 
	                        newAccount.getAccountNumber(), originalAccount.getAccountNumber()),
	                renewalCustomerId,
	                renewalEmail,
	                renewalPhone,
	                clockService.getLogicalDateTime(),
	                UUID.randomUUID().toString(),
	                String.format("Original Account: %s, New Principal: %s, New Maturity Date: %s", 
	                        originalAccount.getAccountNumber(),
	                        newAccount.getPrincipalAmount(), 
	                        newAccount.getMaturityDate())
	        );
	        kafkaProducerService.sendAlertEvent(alertEvent);
	        log.info("Alert sent for renewed account creation: {} (Email: {}, Phone: {})", 
	                newAccount.getAccountNumber(), renewalEmail, renewalPhone);
	        
	        return newAccount;
	    }

	    public record MaturityProcessingResult(FdAccount originalAccount, FdAccount newRenewedAccount) {}

	    @Bean
	    public Step maturityProcessingStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
	    		 @Qualifier("maturityReader") JpaPagingItemReader<FdAccount> maturityReader,
                 @Qualifier("maturityProcessor") ItemProcessor<FdAccount, MaturityProcessingResult> maturityProcessor,
                 @Qualifier("maturityWriter") ItemWriter<MaturityProcessingResult> maturityWriter) {
	        return new StepBuilder("maturityProcessingStep", jobRepository)
	                .<FdAccount, MaturityProcessingResult>chunk(100, transactionManager)
	                .reader(maturityReader)
	                .processor(maturityProcessor)
	                .writer(maturityWriter)
	                .build();
	    }

	    @Bean
	    public Job maturityProcessingJob(JobRepository jobRepository,  @Qualifier("maturityProcessingStep") Step maturityProcessingStep) {
	        return new JobBuilder("maturityProcessingJob", jobRepository)
	                .start(maturityProcessingStep)
	                .build();
	    }

}
