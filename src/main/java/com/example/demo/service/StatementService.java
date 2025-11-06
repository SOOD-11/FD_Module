package com.example.demo.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.demo.dto.AccountDetailsDto;
import com.example.demo.dto.CurrentBalancesDto;
import com.example.demo.dto.CustomerDetailsDto;
import com.example.demo.dto.CustomerProfileResponse;
import com.example.demo.dto.StatementNotificationRequest;
import com.example.demo.dto.StatementPeriodDto;
import com.example.demo.dto.StatementTransactionDto;
import com.example.demo.entities.FdAccount;
import com.example.demo.entities.FdAccountBalance;
import com.example.demo.entities.FdTransaction;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.FdAccountBalanceRepository;
import com.example.demo.repository.FdAccountRepository;
import com.example.demo.repository.FdTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatementService {
    
    private final FdAccountRepository fdAccountRepository;
    private final FdTransactionRepository transactionRepository;
    private final FdAccountBalanceRepository balanceRepository;
    private final CustomerService customerService;
    private final ProductService productService;
    private final KafkaProducerService kafkaProducerService;
    private final BatchAuthService batchAuthService;
    
    /**
     * Generate and send statement for a specific account
     * 
     * @param accountNumber Account number
     * @param startDate Statement period start date
     * @param endDate Statement period end date
     * @param userEmail User's email (from JWT or account holder)
     * @param jwtToken JWT token for authentication (optional for batch processing)
     */
    public void generateStatement(String accountNumber, LocalDate startDate, LocalDate endDate, String userEmail, String jwtToken) {
        log.info("Generating statement for account: {} from {} to {}", accountNumber, startDate, endDate);
        
        // Fetch account
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
        
        // Fetch customer details from email (either from user or account's primary holder)
        String email = userEmail != null ? userEmail : getAccountPrimaryEmail(account);
        
        // Fetch customer profile with JWT token
        CustomerProfileResponse customer = customerService.getCustomerByEmail(email, jwtToken);
        log.info("Fetched customer by email: {} -> Phone: {}", email, customer.getPhoneNumber());
        
        // Build statement notification request
        StatementNotificationRequest statementRequest = buildStatementRequest(
                account, customer, startDate, endDate);
        
        // Send to Kafka
        kafkaProducerService.sendStatementNotification(statementRequest);
        
        log.info("Statement notification sent for account: {}", accountNumber);
    }
    
    /**
     * Generate statements for all active accounts (for batch processing)
     * Uses the SAME logic as the working /statement API endpoint
     */
    public void generateStatementsForAllAccounts(LocalDate startDate, LocalDate endDate) {
        log.info("🔄 Generating statements for all active accounts from {} to {}", startDate, endDate);
        
        List<FdAccount> accounts = fdAccountRepository.findAll();
        int successCount = 0;
        int failureCount = 0;
        
        log.info("📊 Found {} accounts to process", accounts.size());
        
        if (accounts.isEmpty()) {
            log.warn("⚠️ No accounts found in database! Cannot generate statements.");
            return;
        }
        
        // Get batch JWT token once for all accounts
        String batchJwtToken = null;
        try {
            batchJwtToken = batchAuthService.getBatchAccessToken();
            log.info("✅ Got batch JWT token for processing");
        } catch (Exception e) {
            log.error("❌ Failed to get batch JWT token. Statements will fail.", e);
            return;
        }
        
        for (FdAccount account : accounts) {
            try {
                log.info("📧 Processing statement for account: {}", account.getAccountNumber());
                
                // Get account holder email
                String email = getAccountPrimaryEmail(account);
                
                // Use the SAME generateStatement method that works in the API
                // Now pass the batch JWT token
                generateStatement(account.getAccountNumber(), startDate, endDate, email, batchJwtToken);
                
                successCount++;
                log.info("✅ Statement sent successfully for account: {}", account.getAccountNumber());
            } catch (Exception e) {
                log.error("❌ FAILED to generate statement for account: {} - ERROR: {}", 
                         account.getAccountNumber(), e.getMessage(), e);
                failureCount++;
            }
        }
        
        log.info("✅ Statement generation completed. Success: {}, Failures: {}", successCount, failureCount);
    }
    
    /**
     * Build complete statement notification request
     */
    private StatementNotificationRequest buildStatementRequest(
            FdAccount account, CustomerProfileResponse customer, LocalDate startDate, LocalDate endDate) {
        
        // Get communication template
        String template = productService.getCommunicationTemplate(account.getProductCode(), "COMM_MONTHLY_STATEMENT");
        if (template == null) {
            template = "Dear ${CUSTOMER_NAME}, Your monthly statement for ${PRODUCT_NAME} account ending in ${LAST_4_DIGITS} is now available. Opening balance: ${OPENING_BALANCE}, Closing balance: ${CLOSING_BALANCE}. View full statement in the attachment.";
        }
        
        // Get transactions
        List<FdTransaction> transactions = transactionRepository.findByFdAccount_AccountNumberAndTransactionDateBetweenOrderByTransactionDateDesc(
                account.getAccountNumber(), startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay());
        
        // Get current balances
        List<FdAccountBalance> balances = balanceRepository.findByFdAccount_AccountNumber(account.getAccountNumber());
        
        // Build components
        CustomerDetailsDto customerDetails = buildCustomerDetails(customer);
        AccountDetailsDto accountDetails = buildAccountDetails(account);
        CurrentBalancesDto currentBalances = buildCurrentBalances(balances);
        List<StatementTransactionDto> statementTransactions = buildTransactions(transactions);
        
        // Calculate opening and closing balances
        BigDecimal openingBalance = calculateOpeningBalance(account, startDate);
        BigDecimal closingBalance = currentBalances.principal()
                .add(currentBalances.interest())
                .subtract(currentBalances.penalty());
        
        // Replace placeholders in template
        String body = replacePlaceholders(template, account, customer, openingBalance, closingBalance);
        
        // Build subject
        String subject = String.format("Your Fixed Deposit Statement - A/c No. ...%s", 
                account.getAccountNumber().substring(Math.max(0, account.getAccountNumber().length() - 5)));
        
        // Build PDF filename
        String pdfFileName = String.format("FD_Statement_%s_%s-%02d.pdf", 
                account.getAccountNumber(), 
                endDate.getYear(), 
                endDate.getMonthValue());
        
        // Build statement period
        StatementPeriodDto statementPeriod = new StatementPeriodDto(
                startDate.toString(), 
                endDate.toString());
        
        return new StatementNotificationRequest(
                customer.getEmail(),
                customer.getPhoneNumber(),
                subject,
                body,
                "FD_STATEMENT",
                pdfFileName,
                statementPeriod,
                LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME),
                customerDetails,
                accountDetails,
                currentBalances,
                statementTransactions
        );
    }
    
    private CustomerDetailsDto buildCustomerDetails(CustomerProfileResponse customer) {
        CustomerDetailsDto.AddressDto address = new CustomerDetailsDto.AddressDto(
                customer.getAddressLine1() != null ? customer.getAddressLine1() : "",
                customer.getAddressLine2() != null ? customer.getAddressLine2() : "",
                customer.getCity() != null ? customer.getCity() : "",
                customer.getState() != null ? customer.getState() : "",
                customer.getCountry() != null ? customer.getCountry() : "",
                customer.getPostalCode() != null ? customer.getPostalCode() : ""
        );
        
        return new CustomerDetailsDto(
                customer.getCustomerId(),
                customer.getCustomerNumber(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getDateOfBirth() != null ? customer.getDateOfBirth().toString() : "",
                customer.getPhoneNumber(),
                customer.getEmail(),
                address,
                customer.getMaskedPan() != null ? customer.getMaskedPan() : ""
        );
    }
    
    private AccountDetailsDto buildAccountDetails(FdAccount account) {
        return new AccountDetailsDto(
                account.getAccountNumber(),
                account.getAccountName(),
                account.getStatus().toString(),
                account.getCurrency() != null ? account.getCurrency() : "INR",
                account.getPrincipalAmount(),
                account.getMaturityAmount(),
                account.getEffectiveDate().toString(),
                account.getMaturityDate().toString(),
                formatTenure(account.getTenureValue(), account.getTenureUnit()),
                account.getInterestRate(),
                account.getApy(),
                account.getInterestType() != null ? account.getInterestType() : "COMPOUND",
                account.getCompoundingFrequency() != null ? account.getCompoundingFrequency() : "QUARTERLY"
        );
    }
    
    private CurrentBalancesDto buildCurrentBalances(List<FdAccountBalance> balances) {
        BigDecimal principal = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;
        BigDecimal penalty = BigDecimal.ZERO;
        
        for (FdAccountBalance balance : balances) {
            if (!balance.getIsActive()) continue;
            
            switch (balance.getBalanceType()) {
                case "FD_PRINCIPAL":
                    principal = balance.getBalanceAmount();
                    break;
                case "FD_INTEREST":
                    interest = balance.getBalanceAmount();
                    break;
                case "PENALTY":
                    penalty = balance.getBalanceAmount();
                    break;
            }
        }
        
        return new CurrentBalancesDto(
                LocalDateTime.now(ZoneOffset.UTC),
                principal,
                interest,
                penalty
        );
    }
    
    private List<StatementTransactionDto> buildTransactions(List<FdTransaction> transactions) {
        List<StatementTransactionDto> result = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        
        for (FdTransaction txn : transactions) {
            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;
            
            if (txn.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                credit = txn.getAmount();
                runningBalance = runningBalance.add(credit);
            } else {
                debit = txn.getAmount().abs();
                runningBalance = runningBalance.subtract(debit);
            }
            
            result.add(new StatementTransactionDto(
                    txn.getTransactionDate().toLocalDate().toString(),
                    txn.getDescription() != null ? txn.getDescription() : txn.getTransactionType().toString(),
                    debit,
                    credit,
                    runningBalance,
                    txn.getTransactionReference()
            ));
        }
        
        return result;
    }
    
    private BigDecimal calculateOpeningBalance(FdAccount account, LocalDate startDate) {
        // Get all transactions before start date
        List<FdTransaction> priorTransactions = transactionRepository
                .findByFdAccount_AccountNumberAndTransactionDateBetweenOrderByTransactionDateDesc(
                        account.getAccountNumber(), 
                        account.getEffectiveDate().atStartOfDay(), 
                        startDate.atStartOfDay());
        
        BigDecimal balance = BigDecimal.ZERO;
        for (FdTransaction txn : priorTransactions) {
            balance = balance.add(txn.getAmount());
        }
        
        return balance;
    }
    
    private String replacePlaceholders(String template, FdAccount account, 
            CustomerProfileResponse customer, BigDecimal openingBalance, BigDecimal closingBalance) {
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("${CUSTOMER_NAME}", customer.getFirstName());
        placeholders.put("${PRODUCT_NAME}", account.getAccountName());
        placeholders.put("${LAST_4_DIGITS}", account.getAccountNumber().substring(
                Math.max(0, account.getAccountNumber().length() - 4)));
        placeholders.put("${OPENING_BALANCE}", openingBalance.toString());
        placeholders.put("${CLOSING_BALANCE}", closingBalance.toString());
        
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        return result;
    }
    
    private String formatTenure(Integer tenureValue, String tenureUnit) {
        if (tenureValue == null || tenureUnit == null) {
            return "N/A";
        }
        return tenureValue + " " + tenureUnit;
    }
    
    private String getAccountPrimaryEmail(FdAccount account) {
        // Fetch real email from Customer Service using customerNumber
        if (account.getAccountHolders() == null || account.getAccountHolders().isEmpty()) {
            log.warn("No account holders found for account: {}. Using fallback email.", account.getAccountNumber());
            return "no-reply@nexabank.com";
        }
        
        String customerNumber = account.getAccountHolders().get(0).getCustomerId();
        
        try {
            String email = customerService.getEmailByCustomerNumber(customerNumber);
            log.info("✅ Fetched real email for customerNumber {}: {}", customerNumber, email);
            return email;
        } catch (Exception e) {
            log.error("❌ Failed to fetch customer email for customerNumber: {}", customerNumber, e);
            throw new RuntimeException("Cannot fetch customer email: " + e.getMessage(), e);
        }
    }
}
