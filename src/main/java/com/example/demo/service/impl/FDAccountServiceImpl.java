package com.example.demo.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.AccountHolderView;
import com.example.demo.dto.AddAccountHolderRequest;
import com.example.demo.dto.CreateFDAccountRequest;
import com.example.demo.dto.EarlyWithdrawlRequest;
import com.example.demo.dto.FDAccountView;
import com.example.demo.dto.FDCalculationResponse;
import com.example.demo.dto.FDTransactionView;
import com.example.demo.dto.FdAccountBalanceView;
import com.example.demo.dto.PrematureWithdrawalInquiryResponse;
import com.example.demo.dto.ProductDetailsResponse;
import com.example.demo.entities.Accountholder;
import com.example.demo.entities.FdAccount;
import com.example.demo.entities.FdAccountBalance;
import com.example.demo.entities.FdTransaction;

import com.example.demo.enums.AccountStatus;
import com.example.demo.enums.MaturityInstruction;
import com.example.demo.enums.RoleType;
import com.example.demo.enums.TransactionType;
import com.example.demo.events.AccountAlertEvent;
import com.example.demo.events.AccountClosedEvent;
import com.example.demo.events.AccountCreatedEvent;
import com.example.demo.events.AccountMaturedEvent;
import com.example.demo.events.CommunicationEvent;
import com.example.demo.exception.ResourceNotFoundException;
import com.example.demo.repository.FdAccountBalanceRepository;
import com.example.demo.repository.FdAccountRepository;
import com.example.demo.repository.FdTransactionRepository;
import com.example.demo.service.CustomerService;
import com.example.demo.service.FDAccountService;
import com.example.demo.service.FDCalculationService;
import com.example.demo.service.KafkaProducerService;
import com.example.demo.service.ProductService;
import com.example.demo.time.IClockService;
import com.example.demo.util.AccountNumberGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class FDAccountServiceImpl implements FDAccountService{
	
	private final FdAccountRepository fdAccountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final KafkaProducerService kafkaProducerService;
    private final FdTransactionRepository transactionRepository;
    private final FDCalculationService fdCalculationService;
    private final ProductService productService;
    private final FdAccountBalanceRepository balanceRepository;
    private final IClockService clockService;
    private final CustomerService customerService;

    @Override
    public FDAccountView createAccount(CreateFDAccountRequest request, String customerId) {
        log.info("Creating new FD account for customer: {} with calcId: {}", customerId, request.calcId());

        // Step 0: Fetch customer contact details for alerts
        String customerEmail = null;
        String customerPhoneNumber = null;
        try {
            customerEmail = customerService.getEmailByCustomerNumber(customerId);
            customerPhoneNumber = customerService.getPhoneByCustomerNumber(customerId);
            log.info("✅ Fetched customer contact - Email: {}, Phone: {}", customerEmail, customerPhoneNumber);
        } catch (Exception e) {
            log.error("❌ Failed to fetch customer contact details: {}", customerId, e);
        }

        // Step 1: Fetch calculation details from FD Calculation Service
        FDCalculationResponse calculation = fdCalculationService.getCalculation(request.calcId());
        log.info("Fetched calculation details: maturityValue={}, maturityDate={}, principalAmount={}", 
                 calculation.getMaturityValue(), calculation.getMaturityDate(), calculation.getPrincipalAmount());
        log.info("Product code: {}, Tenure: {} {}", 
                 calculation.getProductCode(), calculation.getTenureValue(), calculation.getTenureUnit());

        // Step 2: Fetch product details from Product Service
        ProductDetailsResponse product = productService.getProductDetails(calculation.getProductCode());
        log.info("Fetched product details: currency={}, interestType={}, compoundingFrequency={}", 
                 product.getCurrency(), product.getInterestType(), product.getCompoundingFrequency());

        // Step 3: Calculate term in months from effective date to maturity date
        LocalDate effectiveDate = clockService.getLogicalDate();
        LocalDate maturityDate = calculation.getMaturityDate();
        Integer termInMonths = (int) ChronoUnit.MONTHS.between(effectiveDate, maturityDate);

        // Step 4: Get principal amount from calculation response
        BigDecimal principalAmount = calculation.getPrincipalAmount();

        // Step 5: Build FD Account entity
        FdAccount fdAccount = new FdAccount();
        fdAccount.setAccountNumber(accountNumberGenerator.generate());
        fdAccount.setAccountName(request.accountName());
        fdAccount.setProductCode(calculation.getProductCode());
        fdAccount.setEffectiveDate(effectiveDate);
        fdAccount.setCreatedAt(clockService.getLogicalDateTime());
        fdAccount.setUpdatedAt(clockService.getLogicalDateTime());
        
        // Set fields from FD Calculation Service
        fdAccount.setCalcId(calculation.getCalcId());
        fdAccount.setResultId(calculation.getResultId());
        fdAccount.setApy(calculation.getApy());
        fdAccount.setInterestRate(calculation.getEffectiveRate());
        fdAccount.setPrincipalAmount(calculation.getPrincipalAmount());
        fdAccount.setMaturityAmount(calculation.getMaturityValue());
        fdAccount.setMaturityDate(calculation.getMaturityDate());
        fdAccount.setMaturityInstruction(MaturityInstruction.valueOf("CLOSE"));
        fdAccount.setEffectiveRate(calculation.getEffectiveRate());
        fdAccount.setPayoutFreq(calculation.getPayoutFreq());
        fdAccount.setPayoutAmount(calculation.getPayoutAmount());
        fdAccount.setCategory1Id(calculation.getCategory1Id());
        fdAccount.setCategory2Id(calculation.getCategory2Id());
        fdAccount.setTenureValue(calculation.getTenureValue());
        fdAccount.setTenureUnit(calculation.getTenureUnit());
        fdAccount.setStatus(AccountStatus.ACTIVE);
        fdAccount.setTermInMonths(termInMonths);
        
        // Set fields from Product Service
        fdAccount.setCurrency(product.getCurrency());
        fdAccount.setInterestType(product.getInterestType());
        fdAccount.setCompoundingFrequency(product.getCompoundingFrequency());

        // Step 6: Build AccountHolder entity (using customerId from JWT)
        Accountholder owner = new Accountholder();
        owner.setCustomerId(customerId);
        owner.setRoleType(RoleType.OWNER);
        owner.setOwnershipPercentage(new BigDecimal("100.00"));

        // Step 7: Build initial Transaction entity
        FdTransaction initialDeposit = new FdTransaction();
        initialDeposit.setTransactionType(TransactionType.PRINCIPAL_DEPOSIT);
        initialDeposit.setAmount(principalAmount);
        initialDeposit.setTransactionDate(clockService.getLogicalDateTime());
        initialDeposit.setTransactionReference(UUID.randomUUID().toString());
        initialDeposit.setDescription("Initial principal deposit.");

        // Step 8: Link entities together
        fdAccount.setAccountHolders(List.of(owner));
        fdAccount.setTransactions(List.of(initialDeposit));
        owner.setFdAccount(fdAccount);
        initialDeposit.setFdAccount(fdAccount);

        // Step 9: Save to database
        FdAccount savedAccount = fdAccountRepository.save(fdAccount);
        log.info("Successfully created FD account with number: {}", savedAccount.getAccountNumber());

        // Step 9b: Send transaction alert for initial deposit
        sendTransactionAlert(initialDeposit, savedAccount);

        // Step 10: Create balance types (product already fetched earlier)
        createAccountBalances(savedAccount, product, principalAmount);
        
        // Step 11: Publish Kafka event for account creation
        AccountCreatedEvent event = new AccountCreatedEvent(
                savedAccount.getAccountNumber(),
                customerId,  // Use customerId from JWT
                savedAccount.getPrincipalAmount(),
                savedAccount.getMaturityDate(),
                UUID.randomUUID().toString()
        );
        kafkaProducerService.sendAccountCreatedEvent(event);
        
        // Step 11b: Send alert event for new account creation
        AccountAlertEvent alertEvent = new AccountAlertEvent(
                savedAccount.getAccountNumber(),
                AccountAlertEvent.AlertType.ACCOUNT_CREATED,
                String.format("New FD account created: %s with principal amount: %s", 
                        savedAccount.getAccountNumber(), savedAccount.getPrincipalAmount()),
                customerId,
                customerEmail,
                customerPhoneNumber,
                clockService.getLogicalDateTime(),
                UUID.randomUUID().toString(),
                String.format("Product: %s, Maturity Date: %s, Interest Rate: %s%%", 
                        savedAccount.getProductCode(), 
                        savedAccount.getMaturityDate(),
                        savedAccount.getInterestRate())
        );
        kafkaProducerService.sendAlertEvent(alertEvent);
        log.info("Alert sent for new account creation: {} (Email: {}, Phone: {})", 
                savedAccount.getAccountNumber(), customerEmail, customerPhoneNumber);
        
        // Step 12: Send communication events based on product configuration
        sendCommunicationEvents(savedAccount, product, customerId, "COMM_OPENING");

        // Step 13: Map to DTO and return
        return mapToView(savedAccount);
    }
    
    /**
     * Create all balance types configured for the product
     */
    private void createAccountBalances(FdAccount account, ProductDetailsResponse product, BigDecimal principalAmount) {
        if (product.getProductBalances() == null || product.getProductBalances().isEmpty()) {
            log.warn("No balance types configured for product: {}", product.getProductCode());
            return;
        }
        
        for (ProductDetailsResponse.ProductBalance productBalance : product.getProductBalances()) {
            if (!productBalance.getIsActive()) {
                continue; // Skip inactive balance types
            }
            
            FdAccountBalance balance = new FdAccountBalance();
            balance.setFdAccount(account);
            balance.setBalanceType(productBalance.getBalanceType());
            balance.setIsActive(true);
            balance.setCreatedAt(clockService.getLogicalDateTime());
            
            // Set initial balance amount based on type
            switch (productBalance.getBalanceType()) {
                case "FD_PRINCIPAL":
                    balance.setBalanceAmount(principalAmount);
                    break;
                case "FD_INTEREST":
                case "PENALTY":
                    balance.setBalanceAmount(BigDecimal.ZERO);
                    break;
                default:
                    balance.setBalanceAmount(BigDecimal.ZERO);
            }
            
            balanceRepository.save(balance);
            log.info("Created balance type: {} for account: {}", 
                     balance.getBalanceType(), account.getAccountNumber());
        }
    }
    
    /**
     * Send communication events based on product configuration
     */
    private void sendCommunicationEvents(FdAccount account, ProductDetailsResponse product, 
                                         String customerId, String eventType) {
        if (product.getProductCommunications() == null || product.getProductCommunications().isEmpty()) {
            log.warn("No communication templates configured for product: {}", product.getProductCode());
            return;
        }
        
        // Fetch customer email and phone number from Customer Service using public endpoints
        String customerEmail = null;
        String customerPhoneNumber = null;
        
        try {
            // Get email using the public endpoint
            customerEmail = customerService.getEmailByCustomerNumber(customerId);
            log.info("✅ Fetched customer email for {}: {}", customerId, customerEmail);
            
            // Get phone using the public endpoint
            customerPhoneNumber = customerService.getPhoneByCustomerNumber(customerId);
            log.info("✅ Fetched customer phone for {}: {}", customerId, customerPhoneNumber);
            
        } catch (Exception e) {
            log.error("❌ Failed to fetch customer contact details for customerId: {}", customerId, e);
            // Continue with null values - communication service will handle missing contacts
        }
        
        for (ProductDetailsResponse.ProductCommunication comm : product.getProductCommunications()) {
            if (!comm.getEvent().equals(eventType)) {
                continue; // Only send communications matching the event type
            }
            
            CommunicationEvent event = new CommunicationEvent();
            event.setEventId(UUID.randomUUID().toString());
            event.setAccountNumber(account.getAccountNumber());
            event.setCustomerId(customerId);
            event.setEmail(customerEmail); // Set customer email
            event.setPhoneNumber(customerPhoneNumber); // Set customer phone number
            event.setCommunicationType(comm.getCommunicationType());
            event.setChannel(comm.getChannel());
            event.setEventType(comm.getEvent());
            event.setTemplate(comm.getTemplate());
            event.setTimestamp(clockService.getLogicalDateTime());
            
            // Prepare template variables for placeholder replacement
            event.setTemplateVariables(java.util.Map.of(
                "CUSTOMER_NAME", customerId, // In real scenario, fetch from customer service
                "PRODUCT_NAME", product.getProductName(),
                "ACCOUNT_NUMBER", account.getAccountNumber(),
                "DATE", clockService.getLogicalDate().toString(),
                "PRINCIPAL_AMOUNT", account.getPrincipalAmount().toString(),
                "MATURITY_DATE", account.getMaturityDate().toString()
            ));
            
            kafkaProducerService.sendCommunicationEvent(event);
            log.info("✅ Sent {} communication via {} for account: {} (email: {}, phone: {})", 
                     comm.getCommunicationType(), comm.getChannel(), account.getAccountNumber(),
                     customerEmail, customerPhoneNumber);
        }
    }
    
    /**
     * Send alert for every transaction
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
            log.info("✅ Fetched customer contact for transaction alert - Email: {}, Phone: {}", customerEmail, customerPhoneNumber);
        } catch (Exception e) {
            log.error("❌ Failed to fetch customer contact details for transaction alert: {}", customerId, e);
            // Continue with null values
        }
        
        // Create alert message based on transaction type
        String alertMessage = String.format("Transaction %s: %s %s on account %s", 
                transaction.getTransactionType(),
                transaction.getTransactionType().name().contains("DEBIT") || 
                transaction.getTransactionType().name().contains("WITHDRAWAL") ? "-" : "+",
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
                AccountAlertEvent.AlertType.ACCOUNT_MODIFIED, // Using ACCOUNT_MODIFIED for transactions
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
    
    public FDAccountView findAccountByNumber(String accountNumber) {
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow();
        return mapToView(account);
    }
    @Override
    public List<FDAccountView> findAccounts(String idType, String value) {
        log.info("Searching for accounts with type: {} and value: {}", idType, value);
        List<FdAccount> accounts;

        switch (idType.toUpperCase()) {
            case "ACCOUNT_NUMBER":
                accounts = fdAccountRepository.findByAccountNumber(value)
                        .map(List::of) // Convert Optional<FDAccount> to List<FDAccount>
                        .orElse(List.of()); // Or an empty list if not found
                break;
            case "CUSTOMER_ID":
                accounts = fdAccountRepository.findAccountsByCustomerId(value);
                break;
            case "ACCOUNT_NAME":
                accounts = fdAccountRepository.findByAccountNameContainingIgnoreCase(value);
                break;
            default:
                throw new IllegalArgumentException("Unsupported search idType: " + idType);
        }

        // Map the list of entities to a list of DTOs and return
        return accounts.stream()
                .map(this::mapToView) // Reuse our existing mapping method
                .collect(Collectors.toList());
    }
    
    // to add accountholder roles like nominee 
    @Override
    @Transactional // Ensure this is a transactional method
    public FDAccountView addRoleToAccount(String accountNumber, AddAccountHolderRequest request) {
        log.info("Adding role {} for customer {} to account {}", request.roleType(), request.customerId(), accountNumber);

        // 1. Find the account we want to modify
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        // 2. Validate role against product configuration
        String roleTypeStr = request.roleType().name(); // Convert enum to string
        boolean roleAllowed = productService.isRoleAllowed(account.getProductCode(), roleTypeStr);
        
        if (!roleAllowed) {
            log.error("Role {} is not allowed for product: {}", roleTypeStr, account.getProductCode());
            throw new IllegalArgumentException(
                "Role " + roleTypeStr + " is not configured for product: " + account.getProductCode()
            );
        }
        
        log.info("Role {} validated successfully for product: {}", roleTypeStr, account.getProductCode());

        // 3. Create the new AccountHolder entity from the request
        Accountholder newHolder = new Accountholder();
        newHolder.setCustomerId(request.customerId());
        newHolder.setRoleType(request.roleType());
        newHolder.setOwnershipPercentage(request.ownershipPercentage());
        newHolder.setFdAccount(account); // Set the back-reference to the parent account

        // 4. Add the new holder to the account's list of holders
        account.getAccountHolders().add(newHolder);
        
        // 5. Update timestamp for modification tracking
        account.setUpdatedAt(clockService.getLogicalDateTime());

        // 6. Save the parent account. Due to CascadeType.ALL, this will also save the new holder.
        FdAccount updatedAccount = fdAccountRepository.save(account);
        
        // 7. Fetch contact details for the new account holder
        String holderEmail = null;
        String holderPhone = null;
        try {
            holderEmail = customerService.getEmailByCustomerNumber(request.customerId());
            holderPhone = customerService.getPhoneByCustomerNumber(request.customerId());
        } catch (Exception e) {
            log.error("❌ Failed to fetch contact details for new holder: {}", request.customerId(), e);
        }
        
        // 8. Send alert event for account modification (account holder added)
        AccountAlertEvent alertEvent = new AccountAlertEvent(
                updatedAccount.getAccountNumber(),
                AccountAlertEvent.AlertType.ACCOUNT_HOLDER_ADDED,
                String.format("Account holder added to account %s: Customer %s with role %s", 
                        accountNumber, request.customerId(), request.roleType()),
                request.customerId(),
                holderEmail,
                holderPhone,
                clockService.getLogicalDateTime(),
                UUID.randomUUID().toString(),
                String.format("Role: %s, Ownership: %s%%", 
                        request.roleType(), request.ownershipPercentage())
        );
        kafkaProducerService.sendAlertEvent(alertEvent);
        log.info("Alert sent for account holder addition: {} on account {}", request.customerId(), accountNumber);

        // 8. Map the updated entity to a DTO and return it
        return mapToView(updatedAccount);
    }
    
    
    
    
    @Override
    public List<FDTransactionView> getTransactionsForAccount(String accountNumber) {
        log.info("Fetching transactions for account number: {}", accountNumber);

        // First, ensure the account actually exists. This is good practice.
        fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        // Call the repository method to get the transaction entities
        List<FdTransaction> transactions = transactionRepository.findByFdAccount_AccountNumberOrderByTransactionDateDesc(accountNumber);

        // Map the list of entities to a list of DTOs
        return transactions.stream()
                .map(transaction -> new FDTransactionView(
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getTransactionDate(),
                        transaction.getDescription(),
                        transaction.getTransactionReference()
                ))
                .collect(Collectors.toList());
    }
    public BigDecimal calculateMaturityAmount(BigDecimal principal, BigDecimal rate, Integer termInMonths) {
        // Simple Interest Formula: A = P(1 + rt)
        // r = annual rate / 100, t = term in years
        BigDecimal rateAsDecimal = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
        BigDecimal termInYears = new BigDecimal(termInMonths).divide(new BigDecimal("12"), 10, RoundingMode.HALF_UP);
        BigDecimal interest = principal.multiply(rateAsDecimal).multiply(termInYears);
        return principal.add(interest).setScale(2, RoundingMode.HALF_UP);
    }

    private FDAccountView mapToView(FdAccount account) {
        List<AccountHolderView> holderViews = account.getAccountHolders().stream()
                .map(holder -> new AccountHolderView(
                        holder.getCustomerId(),
                        holder.getRoleType(),
                        holder.getOwnershipPercentage()
                )).collect(Collectors.toList());

        return new FDAccountView(
                account.getAccountNumber(),
                account.getAccountName(),
                account.getProductCode(),
                account.getStatus(),
                account.getPrincipalAmount(),
                account.getMaturityAmount(),
                account.getEffectiveDate(),
                account.getMaturityDate(),
                account.getInterestRate(),
                account.getApy(),
                account.getCategory1Id(),
                account.getCategory2Id(),
                account.getInterestType(),
                account.getCompoundingFrequency(),
                account.getTenureValue(),
                account.getTenureUnit(),
                account.getCurrency(),
                holderViews
        );
    }
    
    @Transactional
    public FDAccountView performEarlyWithdrawal(String accountNumber, EarlyWithdrawlRequest request) {
        log.info("Performing early withdrawal for account: {} with reason: {}", accountNumber, request.reason());

        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));
        
        // Validate WITHDRAWAL transaction is allowed for this product
        boolean withdrawalAllowed = productService.isTransactionAllowed(
            account.getProductCode(), 
            TransactionType.PREMATURE_WITHDRAWAL.name()
        );
        
        if (!withdrawalAllowed) {
            log.error("WITHDRAWAL transaction not allowed for product: {}", account.getProductCode());
            throw new IllegalArgumentException(
                "Premature withdrawal is not allowed for product: " + account.getProductCode()
            );
        }
        
        log.info("Withdrawal transaction validated for product: {}", account.getProductCode());

        PrematureWithdrawalInquiryResponse inquiry = getPrematureWithdrawalInquiry(accountNumber);

        FdTransaction penaltyTransaction = new FdTransaction();
        penaltyTransaction.setFdAccount(account);
        penaltyTransaction.setTransactionType(TransactionType.PENALTY_DEBIT);
        penaltyTransaction.setAmount(inquiry.penaltyAmount());
        penaltyTransaction.setTransactionDate(clockService.getLogicalDateTime());
        penaltyTransaction.setDescription("Penalty for premature withdrawal.");
        penaltyTransaction.setTransactionReference(UUID.randomUUID().toString());

        FdTransaction withdrawalTransaction = new FdTransaction();
        withdrawalTransaction.setFdAccount(account);
        withdrawalTransaction.setTransactionType(TransactionType.PREMATURE_WITHDRAWAL);
        withdrawalTransaction.setAmount(inquiry.finalPayoutAmount());
        withdrawalTransaction.setTransactionDate(clockService.getLogicalDateTime());
        withdrawalTransaction.setDescription("Premature withdrawal payout.");
        withdrawalTransaction.setTransactionReference(UUID.randomUUID().toString());
        
        account.getTransactions().add(penaltyTransaction);
        account.getTransactions().add(withdrawalTransaction);

        account.setStatus(AccountStatus.PREMATURELY_CLOSED);
        account.setClosedAt(clockService.getLogicalDateTime());
        account.setUpdatedAt(clockService.getLogicalDateTime());

        FdAccount closedAccount = fdAccountRepository.save(account);
        
        // Send transaction alerts for penalty and withdrawal
        sendTransactionAlert(penaltyTransaction, closedAccount);
        sendTransactionAlert(withdrawalTransaction, closedAccount);

        List<String> customerIdsToNotify = account.getAccountHolders().stream()
                .map(Accountholder::getCustomerId)
                .collect(Collectors.toList());

        AccountClosedEvent event = new AccountClosedEvent(
            accountNumber, "PREMATURE_WITHDRAWAL", inquiry.finalPayoutAmount(), clockService.getLogicalDate(), UUID.randomUUID().toString(), customerIdsToNotify);
        kafkaProducerService.sendAccountClosedEvent(event);
        
        // Fetch contact details for alert
        String primaryCustomerId = customerIdsToNotify.isEmpty() ? "SYSTEM" : customerIdsToNotify.get(0);
        String closureEmail = null;
        String closurePhone = null;
        try {
            closureEmail = customerService.getEmailByCustomerNumber(primaryCustomerId);
            closurePhone = customerService.getPhoneByCustomerNumber(primaryCustomerId);
        } catch (Exception e) {
            log.error("❌ Failed to fetch contact details for closure alert: {}", primaryCustomerId, e);
        }
        
        // Send alert event for account status change (prematurely closed)
        AccountAlertEvent alertEvent = new AccountAlertEvent(
                closedAccount.getAccountNumber(),
                AccountAlertEvent.AlertType.ACCOUNT_STATUS_CHANGED,
                String.format("Account %s status changed to PREMATURELY_CLOSED", accountNumber),
                primaryCustomerId,
                closureEmail,
                closurePhone,
                clockService.getLogicalDateTime(),
                UUID.randomUUID().toString(),
                String.format("Penalty: %s, Final Payout: %s, Closure Reason: Premature Withdrawal", 
                        inquiry.penaltyAmount(), inquiry.finalPayoutAmount())
        );
        kafkaProducerService.sendAlertEvent(alertEvent);
        log.info("Alert sent for account status change: {} changed to PREMATURELY_CLOSED", accountNumber);

        return mapToView(closedAccount);
    }
    
    
   
    
    
    
    
    @Override
    public PrematureWithdrawalInquiryResponse getPrematureWithdrawalInquiry(String accountNumber) {
        log.info("Performing premature withdrawal inquiry for account: {}", accountNumber);
        FdAccount account = fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with number: " + accountNumber));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Inquiry can only be performed on ACTIVE accounts.");
        }
        
        LocalDate today = clockService.getLogicalDate();
        LocalDate effectiveDate = account.getEffectiveDate();
        LocalDate maturityDate = account.getMaturityDate();
        
        // Calculate term completion percentage
        long totalTermDays = ChronoUnit.DAYS.between(effectiveDate, maturityDate);
        long daysActive = ChronoUnit.DAYS.between(effectiveDate, today);
        double completionPercentage = (daysActive * 100.0) / totalTermDays;

        if (daysActive <= 0) {
            return new PrematureWithdrawalInquiryResponse(
                accountNumber, account.getPrincipalAmount(), BigDecimal.ZERO, BigDecimal.ZERO, account.getPrincipalAmount(), today);
        }
        
        // Get penalty charge from product configuration based on completion percentage
        ProductDetailsResponse.ProductCharge penaltyCharge = productService.getPenaltyCharge(
            account.getProductCode(), 
            completionPercentage
        );
        
        BigDecimal penaltyRate;
        if (penaltyCharge != null) {
            // Use product-configured penalty rate
            penaltyRate = new BigDecimal(penaltyCharge.getAmount().toString());
            log.info("Using product penalty rate: {}% for {}% completion (charge: {})", 
                     penaltyRate, completionPercentage, penaltyCharge.getChargeCode());
        } else {
            // Fallback to default penalty calculation (1% reduction)
            penaltyRate = new BigDecimal("1.00");
            log.warn("No product penalty found, using default 1% penalty");
        }
        
        BigDecimal penaltyInterestRate = account.getInterestRate().subtract(penaltyRate);
        if (penaltyInterestRate.compareTo(BigDecimal.ZERO) < 0) {
            penaltyInterestRate = BigDecimal.ZERO;
        }

        BigDecimal originalDailyRate = account.getInterestRate().divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP).divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);
        BigDecimal penaltyDailyRate = penaltyInterestRate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP).divide(new BigDecimal("365"), 10, RoundingMode.HALF_UP);

        BigDecimal originalInterestAccrued = account.getPrincipalAmount().multiply(originalDailyRate).multiply(new BigDecimal(daysActive)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal penalizedInterestAccrued = account.getPrincipalAmount().multiply(penaltyDailyRate).multiply(new BigDecimal(daysActive)).setScale(2, RoundingMode.HALF_UP);
        
        // Calculate penalty amount based on charge type (PERCENTAGE or FLAT)
        BigDecimal penaltyAmount;
        if (penaltyCharge != null && "PERCENTAGE".equals(penaltyCharge.getCalculationType())) {
            // Percentage-based penalty: calculate from principal
            penaltyAmount = account.getPrincipalAmount()
                .multiply(penaltyRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            log.info("Calculated percentage-based penalty: {} ({}% of principal)", penaltyAmount, penaltyRate);
        } else {
            // Original calculation: difference in interest
            penaltyAmount = originalInterestAccrued.subtract(penalizedInterestAccrued);
            log.info("Calculated interest-based penalty: {}", penaltyAmount);
        }
        
        BigDecimal finalPayoutAmount = account.getPrincipalAmount().add(penalizedInterestAccrued).subtract(penaltyAmount);

        return new PrematureWithdrawalInquiryResponse(
            accountNumber, account.getPrincipalAmount(), penalizedInterestAccrued, penaltyAmount, finalPayoutAmount, today);
    }

    @Override
    public List<FdAccountBalanceView> getAccountBalances(String accountNumber) {
        log.info("Fetching balances for account: {}", accountNumber);
        
        // Fetch balances for the account
        List<FdAccountBalance> balances = balanceRepository.findByFdAccount_AccountNumber(accountNumber);
        
        if (balances.isEmpty()) {
            log.warn("No balances found for account: {}", accountNumber);
        }
        
        // Map to view DTOs
        return balances.stream()
            .map(balance -> new FdAccountBalanceView(
                balance.getBalanceType(),
                balance.getBalanceAmount(),
                balance.getIsActive(),
                balance.getCreatedAt(),
                balance.getUpdatedAt()
            ))
            .toList();
    }

}
